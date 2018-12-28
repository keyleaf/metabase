(ns metabase.api.auth-center
  "/api/auth-center endpoints"
  (:require [clojure.set :as set]
            [clojure.tools.logging :as log]
            [compojure.core :refer [PUT]]
            [metabase.api.common :refer :all]
            [metabase.integrations.auth-center :as auth-center]
            [metabase.models.setting :as setting]
            [metabase.util.schema :as su]))

(def ^:private ^:const mb-settings->auth-center-details
  {:auth-center-enabled             :enabled
   :auth-center-host                :host})

(defendpoint PUT "/settings"
  "Update auth center related settings. You must be a superuser to do this."
  [:as {settings :body}]
  {settings su/Map}
  (check-superuser)
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


(define-routes)
