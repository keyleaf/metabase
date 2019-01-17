(ns metabase.api.session
  "/api/session endpoints"
  (:require [cemerick.friend.credentials :as creds]
            [cheshire.core :as json]
            [clj-http.client :as http]
            [clojure.tools.logging :as log]
            [compojure.core :refer [DELETE GET POST]]
            [metabase
             [config :as config]
             [events :as events]
             [public-settings :as public-settings]
             [util :as u]]
            [metabase.api.common :as api]
            [metabase.email.messages :as email]
            [metabase.integrations.ldap :as ldap]
            [metabase.integrations.auth-center :as auth-center]
            [metabase.models
             [session :refer [Session]]
             [setting :refer [defsetting]]
             [user :as user :refer [User]]]
            [metabase.util
             [i18n :as ui18n :refer [trs tru]]
             [password :as pass]
             [schema :as su]]
            [schema.core :as s]
            [throttle.core :as throttle]
            [toucan.db :as db]))

(defn- create-session!
  "Generate a new `Session` for a given `User`. Returns the newly generated session ID."
  [user]
  {:pre  [(map? user) (integer? (:id user)) (contains? user :last_login)]
   :post [(string? %)]}
  (u/prog1 (str (java.util.UUID/randomUUID))
    (db/insert! Session
      :id      <>
      :user_id (:id user))
    (events/publish-event! :user-login
      {:user_id (:id user), :session_id <>, :first_login (not (boolean (:last_login user)))})))

;;; ## API Endpoints

(def ^:private login-throttlers
  {:username   (throttle/make-throttler :username)
   ;; IP Address doesn't have an actual UI field so just show error by username
   :ip-address (throttle/make-throttler :username, :attempts-threshold 50)})

(def ^:private password-fail-message (tru "Password did not match stored password."))
(def ^:private password-fail-snippet (tru "did not match stored password"))
(def ^:private check-password-fail (tru "对接权限中心验证账号密码失败"))
(def ^:private call-check-password-fail (tru "调用权限中心登录接口失败"))
(def ^:private call-check-role-info-fail (tru "调用权限中心登录接口失败"))
(def ^:private check-role-info-fail (tru "对接权限中心验证角色信息失败"))
(def ^:private no-right-role-info (tru "没有对应的角色权限"))



(defn- ldap-login
  "If LDAP is enabled and a matching user exists return a new Session for them, or `nil` if they couldn't be
  authenticated."
  [username password]
  (when (ldap/ldap-configured?)
    (try
      (when-let [user-info (ldap/find-user username)]
        (when-not (ldap/verify-password user-info password)
          ;; Since LDAP knows about the user, fail here to prevent the local strategy to be tried with a possibly
          ;; outdated password
          (throw (ui18n/ex-info password-fail-message
                   {:status-code 400
                    :errors      {:password password-fail-snippet}})))
        ;; password is ok, return new session
        {:id (create-session! (ldap/fetch-or-create-user! user-info password))})
      (catch com.unboundid.util.LDAPSDKException e
        (log/error
         (u/format-color 'red
             (trs "Problem connecting to LDAP server, will fall back to local authentication: {0}" (.getMessage e))))))))

(defn- auth-center-login
  "权限中心登录"
  [username password]
  (log/info
    (u/format-color 'green
      (trs "auth-center-login : username is {0}, password is {1}" username password)))
  (prn "是否走权限中心登录？" (auth-center/auth-center-configured?))
  (prn "权限中心接口地址为" (str (auth-center/auth-center-host)))
  (when (auth-center/auth-center-configured?)
    (let [{:keys [status body] :as response} (http/post (str (auth-center/auth-center-host) "/api/login") {:body (str "{\"username\":\"" username "\",\"password\":\"" password "\"}") :content-type :json :accept :json})]
      (log/info (u/format-color 'green (trs "对接权限中心验证账号密码 response is : {0}" response)))
      (when-not (= status 200)
        (throw (ui18n/ex-info call-check-password-fail {:status-code 400 :errors {:password call-check-password-fail}})))
      (u/prog1 (json/parse-string body keyword)
               (when-not (= (:errcode <>) 0)
                 (throw (ui18n/ex-info check-password-fail {:status-code 400 :errors {:password check-password-fail}})))
               (def token (:data <>))))

    (let [{:keys [status body] :as response} (http/post (str (auth-center/auth-center-host) "/api/roleInfo/token") {:body (str "{\"id\":\"101\"}") :content-type :json :accept :json :headers {"token" token}})]
      (log/info (u/format-color 'green (trs "对接权限中心验证角色信息 response is : {0}" response)))
      (when-not (= status 200)
        (throw (ui18n/ex-info call-check-role-info-fail {:status-code 400 :errors {:password call-check-role-info-fail}})))
      (u/prog1 (json/parse-string body keyword)
               (when-not (= (:errcode <>) 0)
                 (throw (ui18n/ex-info check-role-info-fail {:status-code 400 :errors {:password check-role-info-fail}})))
               (when (or (nil? (:roleList (:data <>))) (= (.length (:roleList (:data <>))) 0))
                 (throw (ui18n/ex-info check-role-info-fail {:status-code 400 :errors {:password check-role-info-fail}})))
               (def bi-roles (filter (fn [x] (or (.endsWith (:name x) "猛犸BI管理员") (.endsWith (:name x) "猛犸BI普通用户"))) (:roleList (:data <>))))
               (when (= (count bi-roles) 0)
                 (throw (ui18n/ex-info no-right-role-info {:status-code 400 :errors {:password no-right-role-info}})))))

    ;用户密码验证成功后需要判断用户是否有配置metabase的权限或者角色
    ;如果上述条件满足，需要判断该用户是否存在于metabase的数据库中
    (let [user (db/select-one [User :id :password_salt :password :last_login :is_superuser], :email username)]
      (def is-admin-in-auth-center (> (count (filter (fn [x] (.endsWith (:name x) "猛犸BI管理员")) bi-roles)) 0))
      (when (nil? user)
        (prn "用户为空，需要创建新用户")
        ;用户不存在，需要创建用户 (select-keys body [:first_name :last_name :email :password :login_attributes])
        (let [new-user-id (u/get-id (user/insert-new-user! {:first_name username :last_name "." :email username :password password}))]
          (when is-admin-in-auth-center
            (db/update! User new-user-id, :is_superuser true))
          {:id (create-session! (user/fetch-user :id new-user-id))}))
      (when-not (nil? user)
        (prn "用户不为空")
        ;用户存在的情况下，要判断下当前用户是否有管理员权限，如果BI和权限中心不一致，需要同步更新。
        (when-not (= (:is_superuser user) is-admin-in-auth-center)
          (prn "BI和权限中心的用户角色不一致，需同步更新，权限中心是否有配置管理员角色？ " is-admin-in-auth-center)
          (db/update! User (:id user), :is_superuser is-admin-in-auth-center))
        {:id (create-session! user)}
        ))
    ))

(defn- email-login
  "Find a matching `User` if one exists and return a new Session for them, or `nil` if they couldn't be authenticated."
  [username password]
  (when-let [user (db/select-one [User :id :password_salt :password :last_login], :email username, :is_active true)]
    (when (pass/verify-password password (:password_salt user) (:password user))
      {:id (create-session! user)})))

(def ^:private throttling-disabled? (config/config-bool :mb-disable-session-throttle))

(defn- throttle-check
  "Pass through to `throttle/check` but will not check if `throttling-disabled?` is true"
  [throttler throttle-key]
  (when-not throttling-disabled?
    (throttle/check throttler throttle-key)))

(api/defendpoint POST "/"
  "Login."
  [:as {{:keys [username password]} :body, remote-address :remote-addr}]
  {username su/NonBlankString
   password su/NonBlankString}
  (throttle-check (login-throttlers :ip-address) remote-address)
  (throttle-check (login-throttlers :username)   username)
  ;; Primitive "strategy implementation", should be reworked for modular providers in #3210
  (or (ldap-login username password)  ; First try LDAP if it's enabled
      (auth-center-login username password)
      (email-login username password) ; Then try local authentication
      ;; If nothing succeeded complain about it
      ;; Don't leak whether the account doesn't exist or the password was incorrect
      (throw (ui18n/ex-info password-fail-message
               {:status-code 400
                :errors      {:password password-fail-snippet}}))))


(api/defendpoint DELETE "/"
  "Logout."
  [session_id]
  {session_id su/NonBlankString}
  (api/check-exists? Session session_id)
  (db/delete! Session :id session_id)
  api/generic-204-no-content)

;; Reset tokens: We need some way to match a plaintext token with the a user since the token stored in the DB is
;; hashed. So we'll make the plaintext token in the format USER-ID_RANDOM-UUID, e.g.
;; "100_8a266560-e3a8-4dc1-9cd1-b4471dcd56d7", before hashing it. "Leaking" the ID this way is ok because the
;; plaintext token is only sent in the password reset email to the user in question.
;;
;; There's also no need to salt the token because it's already random <3

(def ^:private forgot-password-throttlers
  {:email      (throttle/make-throttler :email)
   :ip-address (throttle/make-throttler :email, :attempts-threshold 50)})

(api/defendpoint POST "/forgot_password"
  "Send a reset email when user has forgotten their password."
  [:as {:keys [server-name] {:keys [email]} :body, remote-address :remote-addr}]
  {email su/Email}
  (throttle-check (forgot-password-throttlers :ip-address) remote-address)
  (throttle-check (forgot-password-throttlers :email)      email)
  ;; Don't leak whether the account doesn't exist, just pretend everything is ok
  (when-let [{user-id :id, google-auth? :google_auth} (db/select-one ['User :id :google_auth]
                                                        :email email, :is_active true)]
    (let [reset-token        (user/set-password-reset-token! user-id)
          password-reset-url (str (public-settings/site-url) "/auth/reset_password/" reset-token)]
      (email/send-password-reset-email! email google-auth? server-name password-reset-url)
      (log/info password-reset-url))))


(def ^:private ^:const reset-token-ttl-ms
  "Number of milliseconds a password reset is considered valid."
  (* 48 60 60 1000)) ; token considered valid for 48 hours

(defn- valid-reset-token->user
  "Check if a password reset token is valid. If so, return the `User` ID it corresponds to."
  [^String token]
  (when-let [[_ user-id] (re-matches #"(^\d+)_.+$" token)]
    (let [user-id (Integer/parseInt user-id)]
      (when-let [{:keys [reset_token reset_triggered], :as user} (db/select-one [User :id :last_login :reset_triggered
                                                                                 :reset_token]
                                                                   :id user-id, :is_active true)]
        ;; Make sure the plaintext token matches up with the hashed one for this user
        (when (u/ignore-exceptions
                (creds/bcrypt-verify token reset_token))
          ;; check that the reset was triggered within the last 48 HOURS, after that the token is considered expired
          (let [token-age (- (System/currentTimeMillis) reset_triggered)]
            (when (< token-age reset-token-ttl-ms)
              user)))))))

(api/defendpoint POST "/reset_password"
  "Reset password with a reset token."
  [:as {{:keys [token password]} :body}]
  {token    su/NonBlankString
   password su/ComplexPassword}
  (or (when-let [{user-id :id, :as user} (valid-reset-token->user token)]
        (user/set-password! user-id password)
        ;; if this is the first time the user has logged in it means that they're just accepted their Metabase invite.
        ;; Send all the active admins an email :D
        (when-not (:last_login user)
          (email/send-user-joined-admin-notification-email! (User user-id)))
        ;; after a successful password update go ahead and offer the client a new session that they can use
        {:success    true
         :session_id (create-session! user)})
      (api/throw-invalid-param-exception :password (tru "Invalid reset token"))))


(api/defendpoint GET "/password_reset_token_valid"
  "Check is a password reset token is valid and isn't expired."
  [token]
  {token s/Str}
  {:valid (boolean (valid-reset-token->user token))})


(api/defendpoint GET "/properties"
  "Get all global properties and their values. These are the specific `Settings` which are meant to be public."
  []
  (public-settings/public-settings))


;;; -------------------------------------------------- GOOGLE AUTH ---------------------------------------------------

;; TODO - The more I look at all this code the more I think it should go in its own namespace.
;; `metabase.integrations.google-auth` would be appropriate, or `metabase.integrations.auth.google` if we decide to
;; add more 3rd-party SSO options

(defsetting google-auth-client-id
  (tru "Client ID for Google Auth SSO. If this is set, Google Auth is considered to be enabled."))

(defsetting google-auth-auto-create-accounts-domain
  (tru "When set, allow users to sign up on their own if their Google account email address is from this domain."))

(defn- google-auth-token-info [^String token]
  (let [{:keys [status body]} (http/post (str "https://www.googleapis.com/oauth2/v3/tokeninfo?id_token=" token))]
    (when-not (= status 200)
      (throw (ui18n/ex-info (tru "Invalid Google Auth token.") {:status-code 400})))
    (u/prog1 (json/parse-string body keyword)
      (when-not (= (:email_verified <>) "true")
        (throw (ui18n/ex-info (tru "Email is not verified.") {:status-code 400}))))))

;; TODO - are these general enough to move to `metabase.util`?
(defn- email->domain ^String [email]
  (last (re-find #"^.*@(.*$)" email)))

(defn- email-in-domain? ^Boolean [email domain]
  {:pre [(u/email? email)]}
  (= (email->domain email) domain))

(defn- autocreate-user-allowed-for-email? [email]
  (when-let [domain (google-auth-auto-create-accounts-domain)]
    (email-in-domain? email domain)))

(defn- check-autocreate-user-allowed-for-email [email]
  (when-not (autocreate-user-allowed-for-email? email)
    ;; Use some wacky status code (428 - Precondition Required) so we will know when to so the error screen specific
    ;; to this situation
    (throw
     (ui18n/ex-info (tru "You''ll need an administrator to create a Metabase account before you can use Google to log in.")
       {:status-code 428}))))

(s/defn ^:private google-auth-create-new-user!
  [{:keys [email] :as new-user} :- user/NewUser]
  (check-autocreate-user-allowed-for-email email)
  ;; this will just give the user a random password; they can go reset it if they ever change their mind and want to
  ;; log in without Google Auth; this lets us keep the NOT NULL constraints on password / salt without having to make
  ;; things hairy and only enforce those for non-Google Auth users
  (user/create-new-google-auth-user! new-user))

(defn- google-auth-fetch-or-create-user! [first-name last-name email]
  (if-let [user (or (db/select-one [User :id :last_login] :email email)
                    (google-auth-create-new-user! {:first_name first-name
                                                   :last_name  last-name
                                                   :email      email}))]
    {:id (create-session! user)}))

(api/defendpoint POST "/google_auth"
  "Login with Google Auth."
  [:as {{:keys [token]} :body, remote-address :remote-addr}]
  {token su/NonBlankString}
  (throttle-check (login-throttlers :ip-address) remote-address)
  ;; Verify the token is valid with Google
  (let [{:keys [given_name family_name email]} (google-auth-token-info token)]
    (log/info (trs "Successfully authenticated Google Auth token for: {0} {1}" given_name family_name))
    (google-auth-fetch-or-create-user! given_name family_name email)))


(api/define-routes)
