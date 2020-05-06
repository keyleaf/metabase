(ns metabase.query-processor.middleware.splice-params-in-response
  (:require [metabase.driver :as driver]
            [cheshire.core :as json]
            [clj-http.client :as http]
            [clojure.tools.logging :as log]
            [metabase
             [config :as config]
             [events :as events]
             [public-settings :as public-settings]
             [util :as u]]
            [metabase.util
             [i18n :refer [tru]]]
            [compojure.core :refer [DELETE GET POST]]))

(defn- transport-sql-to-core-system [^String token, native_form]
  (when (public-settings/record-sql-url)
    (try
      (let [{:keys [status body]} (http/post
                                    ;(str "http://localhost:8090?id_token=" token)
                                    (str (public-settings/record-sql-url))
                                    {:body (json/generate-string native_form)
                                     :accept :json
                                     :content-type :json
                                     }
                                    )]
        (when-not (= status 200)
          (log/error (tru "调用第三方接口失败") status))
        (u/prog1 (json/parse-string body keyword)
                 (println "body is :" <>)
                 (when-not (= (:code <>) 0)
                   (log/error (tru "调用第三方接口失败") <>)
                   )))
      (catch Exception e
        (log/error e (tru "调用第三方接口失败")))))
  )

(defn- splice-params-in-metadata [{{:keys [params]} :native_form, :as metadata}]
  ;; no need to i18n this since this message is something only developers who break the QP by changing middleware
  ;; order will see
  (assert driver/*driver*
    "Middleware order error: splice-params-in-response must run *after* driver is resolved.")
  (if (empty? params)
    metadata
    (update metadata :native_form (partial driver/splice-parameters-into-native-query driver/*driver*)))
  (transport-sql-to-core-system "123" (:native_form metadata))
  metadata
  )

(defn splice-params-in-response
  "Middleware that manipulates query response. Splice prepared statement (or equivalent) parameters directly into the
  native query returned as part of successful query results. (This `:native_form` is ultimately what powers the
  'Convert this Question to SQL' feature in the Query Processor.) E.g.:

    {:data {:native_form {:query \"SELECT * FROM birds WHERE name = ?\", :params [\"Reggae\"]}}}

     -> splice params in response ->

    {:data {:native_form {:query \"SELECT * FROM birds WHERE name = 'Reggae'\"}}}

  Note that this step happens *after* a query is executed; we do not want to execute the query with literals spliced
  in, so as to avoid SQL injection attacks.

  This feature is ultimately powered by the `metabase.driver/splice-parameters-into-native-query` method. For native
  queries without `:params` (which will be all of them for drivers that don't support the equivalent of prepared
  statement parameters, like Druid), this middleware does nothing."
  [qp]
  (fn [query rff context]
    (qp query
        (fn [metadata]
          (rff (splice-params-in-metadata metadata)))
        context)))
