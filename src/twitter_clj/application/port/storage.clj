(ns twitter-clj.application.port.storage)

(defprotocol Storage
  (update-user! [this user])
  (update-tweet! [this tweet])
  (update-replies! [this source-tweet-id reply])
  (update-like! [this like])
  (update-retweets! [this retweet])
  (fetch-user-by-id! [this user-id])
  (fetch-tweets-by-user! [this user-id])
  (fetch-tweet-by-id! [this tweet-id])
  (fetch-retweet-by-id! [this retweet-id])
  (fetch-retweets-by-source-tweet-id! [this source-tweet-id])
  (fetch-replies-by-tweet-id! [this tweet-id])
  (remove-like! [this user-id tweet-id])
  (find-users! [this criteria])
  (find-like! [this user-id tweet-id]))
