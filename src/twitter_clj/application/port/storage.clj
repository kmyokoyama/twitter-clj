(ns twitter-clj.application.port.storage)

(defprotocol Storage
  (update-user! [this user])
  (update-tweet! [this tweet])
  (update-thread! [this thread])
  (fetch-users! [this])
  (fetch-tweets! [this])
  (fetch-tweets-by-user! [this user-id])
  (fetch-tweet-by-id! [this tweet-id])
  (fetch-threads! [this])
  (fetch-thread-by-id! [this thread-id])
  (shutdown! [this]))