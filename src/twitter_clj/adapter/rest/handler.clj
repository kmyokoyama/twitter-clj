(ns twitter-clj.adapter.rest.handler
  (:require [taoensso.timbre :as log]
            [twitter-clj.application.app :as app]
            [twitter-clj.adapter.rest.util :refer [get-parameter get-from-body
                                                   ok-with-success
                                                   ok-with-failure
                                                   created]]))

(defn add-user
  "This will be moved to user management API in the future."
  [req app]
  (let [{:keys [name email username]} (:body req)
        user-info (str name " @" username " [" email"]")]
    (log/info "Received request to add user" user-info)
    (let [user (app/add-user app name email username)]
      (created user))))

(defn get-user-by-id
  [req app]
  (let [user-id (get-parameter req :user-id)
        user (app/get-user-by-id app user-id)]
    (log/info "Received request to get user with id" user-id)
    (ok-with-success user)))

(defn add-tweet
  [req app]
  (let [{:keys [user-id text]} (:body req)
        tweet (app/add-tweet app user-id text)]
    (log/info "Received request to add new tweet from user" user-id)
    (created tweet)))

(defn get-tweet-by-id
  [req app]
  (let [tweet-id (get-parameter req :tweet-id)
        tweet (app/get-tweet-by-id app tweet-id)]
    (log/info "Received request to get tweet with id" tweet-id)
    (ok-with-success tweet)))

(defn get-tweets-by-user
  [req app]
  (let [user-id (get-parameter req :user-id)
        tweets (app/get-tweets-by-user app user-id)]
    (log/info "Received request to get tweets from user" user-id)
    (ok-with-success tweets)))

(defn add-reply
  [req app]
  (let [source-tweet-id (get-parameter req :tweet-id)
        {:keys [user-id text]} (:body req)
        reply (app/add-reply app user-id text source-tweet-id)]
    (log/info "Received request to add new reply from user" user-id "to tweet" source-tweet-id)
    (created reply)))

(defn add-retweet
  [req app]
  (let [source-tweet-id (get-parameter req :tweet-id)
        {:keys [user-id text]} (:body req)
        retweet (app/retweet app user-id source-tweet-id)]
    (log/info "Received request to add new retweet from user" user-id "to tweet" source-tweet-id)
    (created retweet)))

(defn add-retweet-with-comment
  [req app]
  (let [source-tweet-id (get-parameter req :tweet-id)
        {:keys [user-id comment]} (:body req)
        retweet (app/retweet-with-comment app user-id comment source-tweet-id)]
    (log/info "Received request to add new retweet from user" user-id "to tweet" source-tweet-id)
    (created retweet)))

(defn get-retweet-by-id
  [req app]
  (let [retweet-id (get-parameter req :retweet-id)
        retweet (app/get-retweet-by-id app retweet-id)]
    (log/info "Received request to get retweet with id" retweet-id)
    (ok-with-success retweet)))

(defn- like-tweet
  [app user-id tweet-id]
  (log/info "Received request to like tweet" tweet-id)
  (try
    (-> (app/like app user-id tweet-id)
        (ok-with-success))))

(defn- unlike-tweet
  [app user-id tweet-id]
  (log/info "Received request to unlike tweet" tweet-id)
  (-> (app/unlike app user-id tweet-id)
      (ok-with-success)))

(defn tweet-react
  [req app]
  (let [tweet-id (get-parameter req :tweet-id)
        user-id (get-from-body req :user-id)
        action (keyword (get-from-body req :action))]
    (case action
      :like (like-tweet app user-id tweet-id)
      :unlike (unlike-tweet app user-id tweet-id))))

;; Exception-handling functions.

(defn wrap-resource-not-found
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        (case (:type (ex-data e))
          :resource-not-found (let [{:keys [resource-type resource-id]} (ex-data e)]
                                (log/warn (.getMessage e) resource-id)
                                (ok-with-failure {:cause "resource not found"
                                                  :resource-type resource-type
                                                  :resource-id resource-id}))
          (throw e))))))

(defn wrap-duplicate-resource
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        (case (:type (ex-data e))
          :duplicate-resource (let [{:keys [resource-type resource-key]} (ex-data e)]
                                (log/warn (.getMessage e) resource-key)
                                (ok-with-failure {:cause "resource already exists"
                                                  :resource-type resource-type
                                                  :resource-key resource-key}))
          (throw e))))))

(defn wrap-default-exception
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        (log/debug e)
        (ok-with-failure {:cause "unknown error" :message (.getMessage e)})))))