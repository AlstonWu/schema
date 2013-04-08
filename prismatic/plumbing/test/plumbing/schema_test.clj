(ns plumbing.schema-test
  (:use clojure.test)
  (:require [plumbing.schema :as schema]))

(defmacro valid! [s x] `(is (do (schema/validate ~s ~x) true)))
(defmacro invalid! [s x] `(is (~'thrown? Exception (schema/validate ~s ~x))))

(deftest class-test
  (valid! String "foo")
  (invalid! String :foo))

(deftest fn-test
  (valid! odd? 1)
  (invalid! odd? 2)
  (invalid! odd? :foo))

(deftest primitive-test
  (valid! float (float 1.0))
  (invalid! float 1.0)
  (valid! double 1.0)
  (invalid! double (float 1.0))
  (valid! boolean true)
  (invalid! boolean 1)
  (doseq [f [byte char short int]]
    (valid! f (f 1))
    (invalid! f 1))
  (valid! long 1)
  (invalid! long (byte 1)))

(deftest simple-map-schema-test
 (let [schema {:foo long
               :bar double}]
   (valid! schema {:foo 1 :bar 2.0})
   (invalid! schema [[:foo 1] [:bar 2.0]])
   (invalid! schema {:foo 1 :bar 2.0 :baz 1})
   (invalid! schema {:foo 1})
   (invalid! schema {:foo 1.0 :bar 1.0})))

(deftest fancier-map-schema-test
 (let [schema {:foo long
               (schema/more-keys String) double}]
   (valid! schema {:foo 1})
   (valid! schema {:foo 1 "bar" 2.0})
   (valid! schema {:foo 1 "bar" 2.0 "baz" 10.0})  
   (invalid! schema {:foo 1 :bar 2.0})
   (invalid! schema {:foo 1 :bar 2.0 "baz" 2.0})
   (invalid! schema {:foo 1 "bar" 2})))

(deftest another-fancy-map-schema-test
 (let [schema {:foo (schema/maybe long)
               (schema/optional-key :bar) double
               :baz {:b1 odd?}}]
   (valid! schema {:foo 1 :bar 1.0 :baz {:b1 3}})
   (valid! schema {:foo 1 :baz {:b1 3}})
   (valid! schema {:foo nil :baz {:b1 3}})
   (invalid! schema {:foo 1 :bar 1.0 :baz [[:b1 3]]})
   (invalid! schema {:foo 1 :bar 1.0 :baz {:b2 3}})
   (invalid! schema {:foo 1 :bar 1.0 :baz {:b1 4}})
   (invalid! schema {:bar 1.0 :baz {:b1 3}})
   (invalid! schema {:foo 1 :bar nil :baz {:b1 3}})))

(deftest either-test
  (let [schema (schema/either
                {:l long}
                {:d double})]
    (valid! schema {:l 1})
    (valid! schema {:d 1.0})
    (invalid! schema {:l 1.0})
    (invalid! schema {:d 1})))

(deftest both-test
 (let [schema (schema/both
               (fn equal-keys? [m] (doseq [[k v] m] (schema/check (= k v) "Got non-equal key-value pair: %s %s" k v)) true)
               {(schema/more-keys clojure.lang.Keyword) clojure.lang.Keyword})]
   (valid! schema {})
   (valid! schema {:foo :foo :bar :bar})
   (invalid! schema {"foo" "foo"})
   (invalid! schema {:foo :bar})))

(deftest maybe-test
 (let [schema (schema/maybe long)]
   (valid! schema nil)
   (valid! schema 1)
   (invalid! schema 1.0)))

(deftest simple-repeated-seq-test
 (let [schema [long]]
   (valid! schema [])
   (valid! schema [1 2 3])
   (invalid! schema {})
   (invalid! schema [1 2 1.0])))

(deftest simple-single-seq-test
 (let [schema [(schema/single long) (schema/single double)]]
   (valid! schema [1 1.0])
   (invalid! schema [1])
   (invalid! schema [1 1.0 2])
   (invalid! schema [1 1])
   (invalid! schema [1.0 1.0])))

(deftest combo-seq-test
 (let [schema [(schema/single (schema/maybe long)) double]]
   (valid! schema [1])
   (valid! schema [1 1.0 2.0 3.0])
   (valid! schema [nil 1.0 2.0 3.0])
   (invalid! schema [1.0 2.0 3.0])
   (invalid! schema [])))

(deftest named-test
 (let [schema [(schema/single (schema/named "topic" String)) (schema/single (schema/named "score" double))]]
   (valid! schema ["foo" 1.0])
   (invalid! schema [1 2])))


(defrecord Foo [x ^long y])

(deftest record-test
  (let [schema (schema/record Foo {:x schema/+anything+ :y long})]
    (valid! schema (Foo. :foo 1))
    (invalid! schema {:x :foo :y 1})
    (invalid! schema (assoc (Foo. :foo 1) :bar 2))))

(deftest record-with-extra-keys test
  (let [schema (schema/record Foo {:x schema/+anything+ :y long
                                   (schema/more-keys clojure.lang.Keyword) schema/+anything+})]
    (valid! schema  (Foo. :foo 1))
    (valid! schema (assoc (Foo. :foo 1) :bar 2))
    (invalid! schema {:x :foo :y 1})))

(defprotocol PProtocol
  (do-something [this]))

(schema/defrecord Bar 
  [:foo long :bar String]
  {(schema/optional-key :baz) clojure.lang.Keyword})

(schema/defrecord Bar2
  [:foo long :bar String]
  {(schema/optional-key :baz) clojure.lang.Keyword}
  PProtocol 
  (do-something [this] 2))

(schema/defrecord Bar3
  [:foo long :bar String]
  PProtocol 
  (do-something [this] 3))

(schema/defrecord Bar4
  [:foo long :bar String]
  (fn [this] (odd? (:foo this)))
  PProtocol 
  (do-something [this] 4))

(deftest defrecord-schema-test
  (is (= (schema/record-schema Bar) 
         (schema/record Bar {:foo long :bar String (schema/optional-key :baz) clojure.lang.Keyword})))
  (is (Bar. 1 :foo))
  (is (= #{:foo :bar} (set (keys (map->Bar {:foo 1})))))
  (is (thrown? Exception (map->Bar {})))
  (valid! (schema/record-schema Bar) (Bar. 1 "test"))
  (invalid! (schema/record-schema Bar) (Bar. 1 :foo))
  (valid! (schema/record-schema Bar) (assoc (Bar. 1 "test") :baz :foo))
  (invalid! (schema/record-schema Bar) (assoc (Bar. 1 "test") :baaaz :foo))
  (invalid! (schema/record-schema Bar) (assoc (Bar. 1 "test") :baz "foo"))
  
  (valid! (schema/record-schema Bar2) (assoc (Bar2. 1 "test") :baz :foo))
  (invalid! (schema/record-schema Bar2) (assoc (Bar2. 1 "test") :baaaaz :foo))
  (is (= 2 (do-something (Bar2. 1 "test"))))
  
  (valid! (schema/record-schema Bar3) (Bar3. 1 "test"))
  (invalid! (schema/record-schema Bar3) (assoc (Bar3. 1 "test") :foo :bar))
  (is (= 3 (do-something (Bar3. 1 "test"))))
  
  (valid! (schema/record-schema Bar4) (Bar4. 1 "test"))
  (invalid! (schema/record-schema Bar4) (Bar4. 2 "test"))
  (is (= 4 (do-something (Bar4. 1 "test")))))



(defmacro valid-call! [o c] `(is (= ~o (schema/validated-call ~@c))))
(defmacro invalid-call! [c] `(is (~'thrown? Exception (schema/validated-call ~@c))))

(deftest validated-call-test
  (let [f (with-meta 
            (fn schematized-fn [l m] 
              (if (= l 100)
                {:baz l}
                {:bar (when (= l 1) (+ l (:foo m)))}))
            {:input-schema [(schema/single long) (schema/single {:foo double})]
             :output-schema {:bar (schema/maybe double)}})]
    (valid-call! {:bar nil} (f 2 {:foo 1.0}))
    (valid-call! {:bar 4.0} (f 1 {:foo 3.0}))
    (invalid-call! (f 3.0 {:foo 1.0}))
    (invalid-call! (f 3 {:foo 1}))
    (invalid-call! (f 100 {:foo 1.0}))))


