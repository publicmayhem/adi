(ns adi.process.normalise.common.alias-test
  (:use midje.sweet)
  (:require [adi.process.normalise.base :as normalise]
            [adi.process.normalise.common.alias :refer :all]
            [adi.process.normalise.common.db :as db]
            [adi.process.normalise.common.set :as set]
            [adi.process.normalise.common.paths :as paths]
            [adi.schema :as schema]
            [adi.test.family :as family]
            [adi.test.checkers :refer [raises-issue]]))

(def ^:dynamic *wrappers*
  {:normalise [db/wrap-db paths/wrap-plus wrap-alias]
   :normalise-attr [set/wrap-attr-set]
   :normalise-branch [wrap-alias]})

^{:refer adi.process.normalise.common.alias/wrap-alias :added "0.3"}
(fact "wraps normalise to process aliases for a database schema"

  (normalise/normalise {:db/id 'chris
                        :male/name "Chris"}
                       {:schema (schema/schema family/family-links)}
                       *wrappers*)
  => '{:db {:id ?chris}, :person {:gender :m, :name "Chris"}}

  (normalise/normalise {:female {:parent/name "Sam"
                                 :brother {:brother/name "Chris"}}}
                       {:schema (schema/schema family/family-links)}
                       *wrappers*)
  => {:person {:gender :f, :parent #{{:name "Sam"}},
               :sibling #{{:gender :m, :sibling #{{:name "Chris", :gender :m}}}}}}
  ^:hidden
  (normalise/normalise {:female {:granddaughter/name "Sam"}}
                       {:schema (schema/schema family/family-links)}
                       *wrappers*)
  => {:person {:gender :f, :child #{{:child #{{:gender :f} {:name "Sam"}}}}}}

  (normalise/normalise {:female/uncle {:name "Sam"}}
                       {:schema (schema/schema family/family-links)}
                       *wrappers*)
  => {:person {:gender :f, :parent #{{:sibling #{{:gender :m} {:name "Sam"}}}}}}

  (normalise/normalise {:male {:full-sibling/name "Sam"}}
                       {:schema (schema/schema family/family-links)
                        :options {:no-alias-gen true}}
                       *wrappers*)
  => {:person {:gender :m, :sibling #{{:name "Sam", :parent #{{:+ {:db {:id '?x}}, :gender :m}
                                                               {:+ {:db {:id '?y}}, :gender :f}}}},
                :parent #{{:+ {:db {:id '?x}}, :gender :m} {:+ {:db {:id '?y}}, :gender :f}}}})
