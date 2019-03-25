(ns metabase.api.auth-center
  "/api/auth-center endpoints"
  (:require [clojure.set :as set]
            [clojure.tools.logging :as log]
            [compojure.core :refer [DELETE GET POST PUT]]
            [metabase.api.common :refer :all]
            [metabase.api.common :as api]
            [metabase.integrations.auth-center :as auth-center]
            [metabase.models.setting :as setting]
            [metabase.util.schema :as su]))

(def ^:private ^:const mb-settings->auth-center-details
  {:auth-center-enabled             :enabled
   :auth-center-host                :host})

(defn- humanize-error-messages
  "Convert raw error message responses from our Auth-Center tests into our normal api error response structure."
  [{:keys [status message]}]
  (when (not= :SUCCESS status)
    (log/warn "Problem connecting to AUTH-CENTER server:" message)
    {:message "通信失败，无法保存"}))

(api/defendpoint PUT "/settings"
  "Update auth center related settings. You must be a superuser to do this."
  [:as {settings :body}]
  {settings su/Map}
  (api/check-superuser)
  (prn "settings is :" settings)
  (let [auth-center-settings (select-keys settings (keys mb-settings->auth-center-details))
        auth-center-details  (-> (set/rename-keys auth-center-settings mb-settings->auth-center-details)
                          (assoc :port
                            (when (seq (:auth-center-port settings))
                              (Integer/parseInt (:auth-center-port settings)))))
        results       (if-not (:auth-center-enabled settings)
                        ;; when disabled just respond with a success message
                        {:status :SUCCESS}
                        ;; otherwise validate settings
                        (auth-center/test-auth-center-connection auth-center-details))]
    (prn "auth-center-settings is :" auth-center-settings)
    (prn "auth-center-details is :" auth-center-details)
    (prn "results is :" results)
    (if (= :SUCCESS (:status results))
      ;; test succeeded, save our settings
      (setting/set-many! auth-center-settings)
      ;; test failed, return result message
      {:status 500
       :body   (humanize-error-messages results)})
    ))


(api/defendpoint GET "/fetch-system-info"
             "从权限中心获取系统信息"
             []
             (api/check-superuser)
             (prn "fetch-system-info :")
             (auth-center/fetch-system-info (auth-center/fetch-token-from-db) 999)
             )

(api/defendpoint GET "/fetch-menu-info/:sysId"
             "从权限中心获取指定系统下的菜单信息"
             [sysId]
             (api/check-superuser)
             (prn "fetch-menu-info sysId is :" sysId)
             (auth-center/fetch-menu-info (auth-center/fetch-token-from-db) sysId)
             )

(api/defendpoint PUT "/update-menu-info/:menuId"
                 "更新权限中心指定的菜单信息 即发布共享报表至权限中心"
                 [menuId :as {{:keys [sysId publicUrl]} :body}]
                 (api/check-superuser)
                 (prn "update-menu-info menuId is :" menuId)
                 (auth-center/update-menu-info (auth-center/fetch-token-from-db) sysId menuId publicUrl)
                 )


(define-routes)
