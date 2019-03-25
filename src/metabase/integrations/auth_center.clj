(ns metabase.integrations.auth-center
  (:require [clj-http.client :as http]
            [clojure.tools.logging :as log]
            [cheshire.core :as json]
            [clojure.data.json :as datajson]
            [clojure
             [set :as set]
             [string :as str]]
            [metabase.models
             [permissions-group :as group :refer [PermissionsGroup]]
             [setting :as setting :refer [defsetting]]
             [user :as user :refer [User]]]
            [metabase.util :as u]
            [metabase.util.i18n :refer [tru]]
            [metabase.api.common :as api]
            [metabase.api.common :refer [*current-user*]]
            [toucan.db :as db]))

(defsetting auth-center-enabled
  (tru "Enable auth-center authentication.")
  :type    :boolean
  :default false)

(defsetting auth-center-host
  (tru "Server hostname."))

(defn auth-center-configured?
  "Check if auth-center is enabled and that the mandatory settings are configured."
  []
  (boolean (and (auth-center-enabled)
                (auth-center-host))))

(def ^:private call-auth-center-error  {:status :ERROR, :message "调用权限中心失败"})

(defn test-auth-center-connection
  "Test the connection to an auth center server to determine if we can connect success. http://test-ucenter.mamcharge.com/

   Takes in a dictionary of properties such as:
       {:host       \"localhost\"}"
  [{:keys [host], :as details}]
  (prn "test-auth-center-connection details is :" details)
  (try
    (let [{:keys [status body] :as response} (http/post (str host "/api/login") {:body (str "{\"username\":\"" "admin" "\",\"password\":\"" "123" "\"}")
                                                                                              :content-type :json :accept :json})]
      (or (when-not (= status 200) call-auth-center-error)
          {:status :SUCCESS})
      )
    (catch Exception e
      {:status :ERROR, :message (.getMessage e)})))


(defn fetch-token-from-db
  "从数据库中获取保存的登录权限中心的token"
  []
  (let [current-user @api/*current-user*
        login_attributes (:login_attributes current-user)]
    (prn ":first_name is :" (:first_name current-user))
    (prn ":email is :" (:email current-user))
    (prn ":rest_token is :" (:rest_token current-user))
    (prn "login_attributes is :" login_attributes)

    (when (and (not (nil? login_attributes)) (not (nil? (.get login_attributes "auth_center_token"))))
      (.get login_attributes "auth_center_token")
      )
    )
  )

(defn fetch-token
  "从权限中心获取登录token 系统以及系统下的模块菜单信息 https://test-authority.mamcharge.com"
  [username password]
  (log/info
    (u/format-color 'green
                    (tru "从权限中心获取登录token")))

  (if (not (nil? (fetch-token-from-db)))
    (fetch-token-from-db)
    (let [{:keys [status body] :as response} (http/post (str (auth-center-host) "/api/login") {:body (str "{\"username\":\"" username "\",\"password\":\"" password "\"}") :content-type :json :accept :json})]
      (log/info (u/format-color 'green (tru "对接权限中心验证账号密码 response is : {0}" response)))
      (when-not (= status 200)
        {:token {}})
      (:data (u/prog1 (json/parse-string body keyword)
                      (when-not (= (:errcode <>) 0)
                        (log/error (u/format-color 'red (tru "对接权限中心登录接口失败 response is : {0}" <>))))
                      (prn "从权限中心获取登录token is :" (:data <>)))))
    )

  )

(defn fetch-system-info
  "从权限中心获取系统信息"
  [token size]
  (log/info (u/format-color 'green (tru "从权限中心获取系统信息 token is : {0}, size is : {1}" token size)))
  (when (nil? token) nil)
  (let [{:keys [status body] :as response} (http/get (str (auth-center-host) "/data/systems/page" "?size=" (if (nil? size) 999 size)) {:headers {:token token} :content-type :json :accept :json})]
    (log/info (u/format-color 'green (tru "从权限中心获取系统信息 response is : {0}" response)))
    (when-not (= status 200)
      {:token {}})
    (u/prog1 (json/parse-string body keyword)
             (when-not (= (:errcode <>) 0)
               (log/error (u/format-color 'red (tru "从权限中心获取系统信息失败 response is : {0}" <>)))))))

(defn fetch-menu-info
  "从权限中心获取指定系统下的菜单信息"
  [token sysId]
  (log/info (u/format-color 'green (tru "从权限中心获取指定系统下的菜单信息 token is : {0}, sysId is : {1}" token sysId)))
  (when (nil? token) nil)
  (when (nil? sysId) nil)

  (let [{:keys [status body] :as response} (http/get (str (auth-center-host) "/data/menus/group-by-sys" "?sysId=" sysId) {:headers {:token token} :content-type :json :accept :json})]
    (log/info (u/format-color 'green (tru "从权限中心获取指定系统下的菜单信息 response is : {0}" response)))
    (when-not (= status 200)
      {:token {}})
    (u/prog1 (json/parse-string body keyword)
             (when-not (= (:errcode <>) 0)
               (log/error (u/format-color 'red (tru "从权限中心获取指定系统下的菜单信息失败 response is : {0}" <>)))))))

(defn update-menu-info
  "更新权限中心指定的菜单信息 即发布共享报表至权限中心"
  [token sysId menuId public_url]
  (log/info (u/format-color 'green (tru "更新权限中心指定的菜单信息 token is : {0}, sysId is : {1}, menuId is {2}, public_url is : {3}" token sysId menuId public_url)))
  (when (nil? token) nil)
  (when (nil? sysId) nil)

  (let [{:keys [status body] :as response} (http/put (str (auth-center-host) "/data/menus/" menuId) {:body (datajson/write-str {:sysId sysId :type "1" :url public_url}) :headers {:token token} :content-type :json :accept :json})]
    (log/info (u/format-color 'green (tru "更新权限中心指定的菜单信息 response is : {0}" response)))
    (when-not (= status 200)
      {:token {}})
    (u/prog1 (json/parse-string body keyword)
             (when-not (= (:errcode <>) 0)
               (log/error (u/format-color 'red (tru "更新权限中心指定的菜单信息失败 response is : {0}" <>)))))))


