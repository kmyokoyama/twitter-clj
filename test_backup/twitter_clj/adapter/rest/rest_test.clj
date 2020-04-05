(ns twitter-clj.adapter.rest.rest-test
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [twitter-clj.adapter.rest.test_utils :refer :all]
            [twitter-clj.adapter.rest.test_component :refer [start-test-system! stop-test-system!]]
            [twitter-clj.adapter.rest.test_configuration :refer [system-config]]))

(use-fixtures :each (fn [f]
                      (let [system (start-test-system! system-config)]
                        (f)
                        (stop-test-system! system))))

(deftest add-single-user
  (testing "Add a single user"
    (let [response (post-json (resource "user") (new-user))]
      (is (= "success" (:status (body-as-json response))))
      (is (= 201 (:status response)))))) ;; HTTP 201 Created.

(deftest add-single-tweet
  (testing "Add a single tweet"
    (let [user (post-json (resource "user") (new-user))
          user-id (get-in (body-as-json user) [:result :id])
          text "My first tweet"
          response (post-json (resource "tweet") (new-tweet user-id text))
          body (body-as-json response)
          result (:result body)]
      (is (= "success" (:status body)))
      (is (= 201 (:status response))) ;; HTTP 201 Created.
      (is (= user-id (:user-id result)))
      (is (= text (:text result)))
      (is (= 0 (:likes result) (:retweets result) (:replies result))))))

(deftest get-tweets-from-user
  (testing "Get two tweets from the same user"
    (let [user (post-json (resource "user") (new-user))
          user-id (get-in (body-as-json user) [:result :id])
          first-tweet (post-json (resource "tweet") (new-tweet user-id))
          second-tweet (post-json (resource "tweet") (new-tweet user-id))
          first-tweet-id (get-in (body-as-json first-tweet) [:result :id])
          second-tweet-id (get-in (body-as-json second-tweet) [:result :id])
          response (client/get (resource "tweet") {:query-params {:user-id user-id}})
          body (body-as-json response)
          result (:result body)]
      (is (= "success" (:status body)))
      (is (= 200 (:status response))) ;; HTTP 200 OK.
      (is (= 2 (count result)))
      (is (= #{first-tweet-id second-tweet-id} (into #{} (map :id result)))))))

(deftest get-empty-tweets
  (testing "Returns no tweet if user has not tweet yet"
    (let [user (post-json (resource "user") (new-user))
          user-id (get-in (body-as-json user) [:result :id])]
      ;; No tweet.
      (let [response (client/get (resource "tweet") {:query-params {:user-id user-id}})
            body (body-as-json response)
            result (:result body)]
        (is (= "success" (:status body)))
        (is (= 200 (:status response))) ;; HTTP 200 OK.
        (is (= 0 (count result)))))))

(deftest get-user-by-id
  (testing "Get an existing user returns successfully"
    (let [expected-user (new-user)
          create-user-response (post-json (resource "user") expected-user)
          user-id (get-in (body-as-json create-user-response) [:result :id])
          get-user-response (client/get (resource (str "user/" user-id)))
          body (body-as-json get-user-response)
          actual-user (:result body)
          attributes [:name :email :username]]
      (is (= 200 (:status get-user-response)))
      (is (= (select-keys expected-user attributes) (select-keys actual-user attributes))))))

(deftest get-user-by-missing-id
  (testing "Get a missing user returns failure"
    (let [user-id (random-uuid)
          get-user-response (client/get (resource (str "user/" user-id)))
          body (body-as-json get-user-response)
          result (:result body)]
      (is (= 200 (:status get-user-response)))
      (is (= "failure" (:status body)))
      (is (= (str user-id) (:id result))))))

(deftest get-tweet-by-id
  (testing "Get an existing tweet returns successfully"
    (let [expected-tweet (new-tweet)
          create-tweet-response (post-json (resource "tweet") expected-tweet)
          tweet-id (get-in (body-as-json create-tweet-response) [:result :id])
          get-tweet-response (client/get (resource (str "tweet/" tweet-id)))
          zeroed-attributes [:likes :retweets :replies]
          body (body-as-json get-tweet-response)
          actual-tweet (:result body)]
      (is (= 200 (:status get-tweet-response)))
      (is (= (str (:user-id expected-tweet)) (:user-id actual-tweet)))
      (is (= (:text expected-tweet) (:text actual-tweet)))
      (is (every? zero? (vals (select-keys expected-tweet zeroed-attributes)))))))

(deftest get-tweet-by-missing-id
  (testing "Get a missing tweet returns failure"
    (let [tweet-id (random-uuid)
          get-tweet-response (client/get (resource (str "tweet/" tweet-id)))
          body (body-as-json get-tweet-response)
          result (:result body)]
      (is (= 200 (:status get-tweet-response)))
      (is (= "failure" (:status body)))
      (is (= (str tweet-id) (:id result))))))

(deftest like-tweet
  (testing "Like an existing tweet"
    (let [user (post-json (resource "user") (new-user))
          user-id (get-in (body-as-json user) [:result :id])
          tweet (post-json (resource "tweet") (new-tweet user-id))
          tweet-id (get-in (body-as-json tweet) [:result :id])
          like-tweet-response (post-json (resource (str "tweet/" tweet-id)) {:action "like"})
          body (body-as-json like-tweet-response)
          result (:result body)]
      (is (= 200 (:status like-tweet-response)))
      (is (= "success" (:status body)))
      (is (= tweet-id (:id result)))
      (is (= 1 (:likes result))))))

(deftest like-tweet-by-missing-id
  (testing "Like a missing tweet returns failure"
    (let [tweet-id (random-uuid)
          like-tweet-response (post-json (resource (str "tweet/" tweet-id)) {:action "like"})
          body (body-as-json like-tweet-response)
          result (:result body)]
      (is (= 200 (:status like-tweet-response)))
      (is (= "failure" (:status body)))
      (is (= (str tweet-id) (:id result))))))

(deftest unlike-tweet-with-positive-likes
  (testing "Unlike an existing tweet with positive likes"
    (let [user (post-json (resource "user") (new-user))
          user-id (get-in (body-as-json user) [:result :id])
          tweet (post-json (resource "tweet") (new-tweet user-id))
          tweet-id (get-in (body-as-json tweet) [:result :id])
          _ (post-json (resource (str "tweet/" tweet-id)) {:action "like"})
          unlike-tweet-response (post-json (resource (str "tweet/" tweet-id)) {:action "unlike"})
          body (body-as-json unlike-tweet-response)
          result (:result body)]
      (is (= 200 (:status unlike-tweet-response)))
      (is (= "success" (:status body)))
      (is (= tweet-id (:id result)))
      (is (= 0 (:likes result))))))

(deftest unlike-tweet-with-zero-likes
  (testing "Unlike an existing tweet with positive likes"
    (let [user (post-json (resource "user") (new-user))
          user-id (get-in (body-as-json user) [:result :id])
          tweet (post-json (resource "tweet") (new-tweet user-id))
          tweet-id (get-in (body-as-json tweet) [:result :id])
          unlike-tweet-response (post-json (resource (str "tweet/" tweet-id)) {:action "unlike"})
          body (body-as-json unlike-tweet-response)
          result (:result body)]
      (is (= 200 (:status unlike-tweet-response)))
      (is (= "success" (:status body)))
      (is (= tweet-id (:id result)))
      (is (= 0 (:likes result))))))

(deftest unlike-tweet-with-mising-id
  (testing "Unlike a missing tweet returns failure"
    (let [tweet-id (random-uuid)
          like-tweet-response (post-json (resource (str "tweet/" tweet-id)) {:action "like"})
          body (body-as-json like-tweet-response)
          result (:result body)]
      (is (= 200 (:status like-tweet-response)))
      (is (= "failure" (:status body)))
      (is (= (str tweet-id) (:id result))))))