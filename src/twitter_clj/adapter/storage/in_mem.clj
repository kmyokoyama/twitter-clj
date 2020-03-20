(ns twitter-clj.adapter.storage.in-mem
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [twitter-clj.application.port.storage :as storage])
  (:import [java.util UUID]))

(declare shutdown)

(defn- to-uuid
  [str]
  (UUID/fromString str))

;; Driven-side.

(defrecord InMemoryStorage [users tweets threads likes]
  component/Lifecycle
  (start [this]
    (log/info "Starting in-memory database")
    this)

  (stop [this]
    (log/info "Stopping in-memory database")
    (shutdown this))

  storage/Storage
  (update-user!
    [_ {user-id :id :as user}]
    (swap! users (fn [users] (assoc users user-id user)))
    user)

  (update-tweet!
    [_ {tweet-id :id :as tweet}]
    (swap! tweets (fn [tweets] (assoc tweets tweet-id tweet)))
    tweet)

  (update-like!
    [_ like]
    (swap! likes (fn [likes] (assoc-in likes [(:tweet-id like) (:user-id like)] like)))
    like)

  (fetch-tweets-by-user!
    [_ user-id]
    (filter #(= (:user-id %) user-id) (vals @tweets)))

  (fetch-tweet-by-id!
    [_ tweet-id]
    (get @tweets (to-uuid tweet-id)))

  (fetch-user-by-id!
    [_ user-id]
    (get @users (to-uuid user-id)))

  (remove-like!
    [_ user-id tweet-id]
    (swap! likes (fn [likes] (update-in likes [tweet-id] dissoc user-id))))

  (find-users!
    [_ criteria]
    (filter (fn [user] (= criteria (select-keys user (keys criteria)))) (vals @users)))

  (find-like!
    [_ user-id tweet-id]
    (get-in @likes [tweet-id user-id])))

(defn make-in-mem-storage ;; Constructor.
  []
  (map->InMemoryStorage {:users (atom {})
                         :tweets (atom {})
                         :threads (atom {})
                         :likes (atom {})}))

(defn shutdown
  [storage]
  (reset! (:users storage) {})
  (reset! (:tweets storage) {})
  (reset! (:threads storage) {})
  (reset! (:likes storage) {})
  storage)