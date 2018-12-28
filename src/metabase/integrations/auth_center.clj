(ns metabase.integrations.auth-center
  (:require [clj-http.client :as http]
            [clojure
             [set :as set]
             [string :as str]]
            [metabase.models
             [permissions-group :as group :refer [PermissionsGroup]]
             [setting :as setting :refer [defsetting]]
             [user :as user :refer [User]]]
            [metabase.util :as u]
            [metabase.util.i18n :refer [tru]]
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
  "Test the connection to an auth center server to determine if we can connect success.

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
      {:status :ERROR, :message "通信失败，无法保存"})))
