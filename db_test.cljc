(ns zerol.db-test
  (:require [clojure.test :refer [is are deftest testing]]
            [zerol.db :as db]))

(deftest from-map
  (is (= {:a 1 :b 2}
         (db/map->eav {:b 2 :a 1}))))

(def test-db (db/map->eav {[:person/identity 1] 42
                           [:person/name 1] "Sam"
                           [:person/on? 1] true
                           [:person/age 1] 23
                           [:person/friend 1] [:person/id 2]
                           [:person/name 2] "Jim"
                           [:person/on? 2] false
                           [:person/age 2] 88
                           [:person/friend 2] [:person/id 1]
                           [:company/name 1] "Co#1"
                           [:company/name 2] "Co#2"
                           [:person/company 1] [:company/id 1]
                           [:person/company 2] [:company/id 2]
                           [:company/team 1] [[:person/id 1] [:person/id 2]]}))

(deftest find-attribute-neg-ids
  (is (= [[[:a/b -1] :a] [[:a/b 10] :b]]
         (db/find-attribute (db/map->eav {[:a/b -1] :a
                                          [:a/b 10] :b})
                            :a/b
                            nil))))

(deftest query
  (testing "One prop"
    (is (= [#:person{:name "Sam" :id 1}
            #:person{:name "Jim" :id 2}]
           (db/query test-db [:person/name]))))
  (testing "Many props"
    (is (= [#:person{:name "Sam" :age 23 :id 1}
            #:person{:name "Jim" :age 88 :id 2}]
           (db/query test-db [:person/name :person/age]))))
  (testing "Missing props"
    (is (= [#:person{:name "Sam" :identity 42 :id 1}
            #:person{:name "Jim" :id 2}]
           (db/query test-db [:person/name :person/identity]))))
  (testing "Missing all props"
    (is (= []
           (db/query test-db [:person/a :person/b]))))
  (testing "Filter one prop"
    (is (= [#:person{:id 1 :name "Sam"}]
           (db/query test-db [{:person/on? true} :person/name]))))
  (testing "Filter all out"
    (is (= []
           (db/query test-db [{:person/on? nil} :person/name]))))
  (testing "Filter function"
    (is (= [#:person {:id 2 :name "Jim"}]
           (db/query test-db [{:person/age even?} :person/name]))))
  (testing "Filter many props"
    (is (= [#:person{:id 1 :age 23}]
           (db/query test-db [{:person/on? true :person/name "Sam"} :person/age]))))
  (testing "Filter many props + additional prop"
    (is (= [#:person{:id 1 :name "Sam"}]
           (db/query test-db [{:person/on? true} :person/name]))))
  (testing "Filter many props + additional prop"
    (is (= []
           (db/query test-db [{:person/name "Jane"}]))))
  (testing "Join"
    (is (= [{:person/id 1 :person/name "Sam" :person/friend {:person/id 2 :person/name "Jim"}}
            {:person/id 2 :person/name "Jim" :person/friend {:person/id 1 :person/name "Sam"}}]
           (db/query test-db [:person/name {:person/friend [:person/name]}])))))

(deftest save
  (testing "No new data"
    (is (= [test-db []]
           (db/save test-db [[[:person/age 1] 23]]))))
  (testing "New data"
    (let [prop [[:person/height 1] 177]]
      (is (= [(assoc test-db (first prop) (second prop)) [prop]]
             (db/save test-db [prop])))))
  (testing "Changed data"
    (let [prop [[:person/age 1] 100]]
      (is (= [(assoc test-db (first prop) (second prop)) [prop]]
             (db/save test-db [prop])))))
  (testing "Remove existing data"
    (let [prop [[:person/age 1] nil]]
      (is (= [(dissoc test-db (first prop)) [prop]]
             (db/save test-db [prop])))))
  (testing "Remove missing data"
    (let [prop [[:person/height 1] nil]]
      (is (= [test-db []]
             (db/save test-db [prop])))))
  (testing "Add/change/remove"
    (let [prop-add [[:person/height 2] 23]
          prop-change [[:person/name 1] "Yo"]
          prop-remove [[:person/age 2] nil]]
      (is (= [(-> test-db
                  (assoc (first prop-add) (second prop-add))
                  (assoc (first prop-change) (second prop-change))
                  (dissoc (first prop-remove)))
              [prop-add prop-change prop-remove]]
             (db/save test-db [prop-add prop-change prop-remove]))))))