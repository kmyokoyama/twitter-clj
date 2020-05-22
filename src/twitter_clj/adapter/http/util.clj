(ns twitter-clj.adapter.http.util
  (:require [buddy.sign.jwt :as jwt]
            [clojure.data.json :as json]
            [taoensso.timbre :as log]
            [twitter-clj.application.config :refer [http-api-jws-secret http-api-path-prefix http-api-version]]))

;; Private functions.

(defn- is-better-str
  [key]
  (or
    (= key :id)
    (some #(.endsWith (str key) %) ["-id", "-date"])))

(defn- value-writer
  [key value]
  (if (is-better-str key)
    (str value)
    value))

;; Public functions.

(defn get-from-body
  [req param]
  (param (:body req)))

(defn get-parameter
  [req param]
  (param (:params req)))

(defn get-user-id
  [req]
  (get-in req [:identity :user-id]))

(def ^:const status-success
  {:status "success"})

(def ^:const status-failure
  {:status "failure"})

(defn add-success-result
  [result]
  (assoc status-success :result result))

(defn add-failure-result
  [result]
  (assoc status-failure :result result))

(defn to-json
  [r]
  (json/write-str r :value-fn value-writer))

(defn json-response
  [status body]
  {:status  status
   :headers {"Content-Type" "application/json"}
   :body    body})

(def ok-response (partial json-response 200))
(def created-response (partial json-response 201))
(def bad-request-response (partial json-response 400))
(def unauthorized-response (partial json-response 401))

(def ok-with-success (comp ok-response to-json add-success-result))
(def ok-with-failure (comp ok-response to-json add-failure-result))
(def created (comp created-response to-json add-success-result))
(def bad-request (comp bad-request-response to-json add-failure-result))
(defn unauthorized
  []
  (-> {:cause "you are not logged in"}
      (add-failure-result)
      (to-json)
      (unauthorized-response)))

(defn new-token
  [secret user-id role]
  (jwt/sign {:user-id user-id :role role} secret {:alg :hs512}))

(def create-token (partial new-token http-api-jws-secret))

(defn add-leading-slash
  [path]
  (if (clojure.string/starts-with? path "/")
    path
    (str "/" path)))

(defn remove-duplicate-slashes
  [path]
  (clojure.string/replace path #"/[/]+" "/"))

(defn join-path
  [& path-parts]
  (-> (clojure.string/join "/" path-parts)
      (clojure.string/replace #"://" "!")
      (remove-duplicate-slashes)
      (clojure.string/replace #"!" "://")))

(defn path-prefix
  [path]
  (->> (list http-api-path-prefix
             http-api-version
             path)
       (apply join-path)
       (add-leading-slash)))

(defn f-id
  [id]
  (str "[ID: " id "]"))

(defn f
  ([entity]
   (if-let [id (:id entity)]
     (f-id id)
     (str "[" entity "]")))

  ([entity attr]
   (if-let [attr-val (attr entity)]
     (let [formatted-attr (-> attr (name) (clojure.string/replace #"-" ""))]
       (str "[" formatted-attr ": " attr-val "]"))
     entity)))

(defn log-failure
  [& args]
  (log/warn (clojure.string/join " " (cons "Failure -" args))))