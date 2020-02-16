(ns twitter-clj.rest-test
  (:require [twitter-clj.rest.handler :refer [handler]]
            [twitter-clj.operations :as app]
            [twitter-clj.test-utils :refer :all]
            [clojure.test :refer :all]
            [clj-http.client :as client]
            [ring.server.standalone :as server]))

(def ^:const url "http://localhost:3000/")

(def server (atom nil))

(defn start-server [port]
  (println "Starting server...")
  (reset! server
          (server/serve handler {:port port :open-browser? false :auto-reload? false})))

(defn stop-server []
  (println "Stopping server.")
  (.stop @server)
  (reset! server nil)
  (app/shutdown))

(use-fixtures :each (fn [f] (start-server 3000) (f) (stop-server)))

(def resource (partial resource-path url))

(deftest add-single-user
  (testing "Add a single user"
    (let [response (post-json (resource "user") (new-user))]
      (is (= "success" (:status (body-as-json response)))))))

(deftest get-users-successfully
  (testing "Get two users successfully"
    ;; Given.
    (post-json (resource "user") (new-user))
    (post-json (resource "user") (new-user))
    ;; Then.
    (let [response (client/get (resource "users") {})]
      (is (= "success" (:status (body-as-json response))))
      (is (= 2 (count (:result (body-as-json response))))))))

(deftest add-single-tweet
  (testing "Add a single tweet"
    (let [user (post-json (resource "user") (new-user))
          user-id (get-in (body-as-json user) [:result :id])
          text "My first tweet"
          response (post-json (resource "tweet") (new-tweet user-id text))
          body (body-as-json response)
          result (:result body)]
      (is (= "success" (:status body)))
      (is (= user-id (:user-id result)))
      (is (= text (:text result)))
      (is (= 0 (:likes result) (:retweets result) (:replies result))))))

(deftest get-tweets-successfully
  (testing "Get two tweets from the same user"
    (let [user (post-json (resource "user") (new-user))
          user-id (get-in (body-as-json user) [:result :id])
          first-tweet (post-json (resource "tweet") (new-tweet user-id))
          second-tweet (post-json (resource "tweet") (new-tweet user-id))
          first-tweet-id (get-in (body-as-json first-tweet) [:result :id])
          second-tweet-id (get-in (body-as-json second-tweet) [:result :id])
          response (client/get (resource "tweets") {:query-params {:user-id user-id}})
          body (body-as-json response)
          result (:result body)]
      (is (= "success" (:status body)))
      (is (= 2 (count result)))
      (is (= #{first-tweet-id second-tweet-id} (into #{} (map :id result)))))))

(deftest get-empty-tweets
  (testing "Get tweets returns no tweet if user has not tweet yet"
    (let [user (post-json (resource "user") (new-user))
          user-id (get-in (body-as-json user) [:result :id])]
      ;; No tweet.
      (let [response (client/get (resource "tweets") {:query-params {:user-id user-id}})
            body (body-as-json response)
            result (:result body)]
        (is (= "success" (:status body)))
        (is (= 0 (count result)))))))