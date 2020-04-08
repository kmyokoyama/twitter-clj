(ns twitter-clj.adapter.repository.in-mem
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [twitter-clj.application.port.repository :as repository]))

(declare shutdown)

;; Driven-side.

(defrecord InMemoryStorage [users tweets likes retweets
                            join-tweet-likes join-tweet-replies join-tweet-retweets
                            passwords]
  component/Lifecycle
  (start [this]
    (log/info "Starting in-memory database")
    this)

  (stop [this]
    (log/info "Stopping in-memory database")
    (shutdown this))

  repository/Repository
  (update-password!
    [_ user-id password]
    (swap! passwords (fn [passwords] (assoc passwords user-id password))))

  (update-user!
    [_ {user-id :id :as user}]
    (swap! users (fn [users] (assoc users user-id user)))
    user)

  (update-tweet!
    [_ {tweet-id :id :as tweet}]
    (swap! tweets (fn [tweets] (assoc tweets tweet-id tweet)))
    tweet)

  (update-like!
    [_ {like-id :id source-tweet-id :source-tweet-id :as like}]
    (swap! likes (fn [likes] (assoc likes like-id like)))
    (swap! join-tweet-likes (fn [join-tweet-likes] (update join-tweet-likes
                                                           source-tweet-id
                                                           (fn [like-ids] (conj (vec like-ids) like-id)))))
    like)

  (update-replies!
    [_ source-tweet-id {reply-id :id :as reply}]
    (swap! join-tweet-replies (fn [join-tweet-replies] (update join-tweet-replies
                                                               source-tweet-id
                                                               (fn [reply-ids] (conj (vec reply-ids) reply-id)))))
    reply)

  (update-retweets!
    [_ {retweet-id :id :as retweet}]
    (swap! retweets (fn [retweets] (assoc retweets retweet-id retweet)))
    (swap! join-tweet-retweets (fn [join-tweet-retweets] (update join-tweet-retweets
                                                                 (:source-tweet-id retweet)
                                                                 (fn [retweet-ids] (conj (vec retweet-ids) retweet-id)))))
    retweet)

  (fetch-password!
    [_ user-id]
    (get @passwords user-id))

  (fetch-users!
    [_ key criteria]
    (case criteria
      :by-id (get @users key)
      :by-fields (filter (fn [user] (= key (select-keys user (keys key)))) (vals @users))))

  (fetch-tweets!
    [_ key criteria]
    (case criteria
      :by-id (get @tweets key)
      :by-user-id (filter #(= (:user-id %) key) (vals @tweets))))

  (fetch-likes!
    [_ key criteria]
    (case criteria
      [:by-source-tweet-id :by-user-id] (let [[source-tweet-id user-id] key]
                                          (->> (get @join-tweet-likes source-tweet-id [])
                                               (map (fn [like-id] (get @likes like-id)))
                                               (filter (fn [like] (= (:user-id like) user-id)))
                                               (first)))
      :by-source-tweet-id (->> (get @join-tweet-likes key [])
                               (map (fn [like-id] (get @likes like-id))))))

  (fetch-replies!
    [_ key criteria]
    (case criteria
      :by-source-tweet-id (->> (get @join-tweet-replies key [])
                               (map (fn [reply-id] (get @tweets reply-id))))))

  (fetch-retweets!
    [_ key criteria]
    (case criteria
      :by-id (get @retweets key)
      :by-source-tweet-id (->> (get @join-tweet-retweets key [])
                               (map (fn [retweet-id] (get @retweets retweet-id))))))

  (remove-like!
    [_ key criteria]
    (case criteria
      [:by-source-tweet-id :by-user-id] (let [[source-tweet-id user-id] key
                                              like-id (->> (get @join-tweet-likes source-tweet-id [])
                                                           (map (fn [like-id] (get @likes like-id)))
                                                           (filter (fn [like] (= (:user-id like) user-id)))
                                                           (first)
                                                           (:id))]
                                          (swap! join-tweet-likes (fn [join-tweet-likes]
                                                                    (update join-tweet-likes
                                                                            source-tweet-id
                                                                            (fn [like-ids] (remove #(= % like-id) like-ids)))))
                                          (swap! likes (fn [likes] (dissoc likes like-id)))))))

(defn make-in-mem-storage                                   ;; Constructor.
  []
  (map->InMemoryStorage {:passwords           (atom {})
                         :users               (atom {})
                         :tweets              (atom {})
                         :retweets            (atom {})
                         :likes               (atom {})
                         :join-tweet-likes    (atom {})
                         :join-tweet-replies  (atom {})
                         :join-tweet-retweets (atom {})}))

(defn shutdown
  [repository]
  (reset! (:users repository) {})
  (reset! (:tweets repository) {})
  (reset! (:retweets repository) {})
  (reset! (:likes repository) {})
  (reset! (:join-tweet-likes repository) {})
  (reset! (:join-tweet-replies repository) {})
  (reset! (:join-tweet-retweets repository) {})
  repository)