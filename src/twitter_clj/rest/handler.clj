(ns twitter-clj.rest.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [clojure.data.json :as json]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [twitter-clj.operations :as app]))

(defn get-query-parameter
  [req param]
  (param (:query-params req)))

(defn body-as-json [{:keys [body]}]
  (json/read-str (slurp body) :key-fn keyword))

(defn ok-json
  [body]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body body})

(def success-response
  {:status "success"})

(def failure-response
  {:status "failure"})

(defn add-response-info
  [info]
  (assoc success-response :result info))

(defn to-json
  [r]
  (json/write-str r :value-fn app/value-writer))

(def respond-with (comp ok-json to-json add-response-info))

(defn add-user
  [req]
  (let [body (body-as-json req)
        {:keys [name email nickname]} body
        user (app/add-user name email nickname)]
    (respond-with user)))

(defn get-users
  [_req]
  (let [users (app/get-users)]
    (respond-with users)))

(defn add-tweet
  [req]
  (let [user-id (get-query-parameter req :user-id)
        text (get-query-parameter req :text)
        tweet (app/add-tweet user-id text)]
    (respond-with tweet)))

(defn get-tweets-by-user
  [req]
  (let [user-id (get-query-parameter req :user-id)
        tweets (app/get-tweets-by-user user-id)]
    (respond-with tweets)))

(defroutes app-routes
           (POST "/user" [] add-user)
           (GET "/users" [] get-users)
           (GET "/tweet" [] add-tweet)
           (GET "/tweets" [] get-tweets-by-user)
           (route/not-found "Error, page not found!"))

(def handler
  (wrap-defaults #'app-routes api-defaults))