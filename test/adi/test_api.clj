(ns adi.test-query
  (:use midje.sweet
        adi.utils
        [adi.data :only [iid]])
  (:require [adi.data :as ad]
            [adi.schema :as as]
            [adi.api :as aa]
            [datomic.api :as d]))

(def *uri* "datomic:mem://test-adi-query")
(d/create-database *uri*)
(comment (d/delete-database *uri*))
(def *conn* (d/connect *uri*))

(def account-map
  (flatten-keys
   {:account
    {:username    [{:type        :string}]
     :hash        [{:type        :string}]
     :joined      [{:type        :instant}]
     :isActivated [{:type        :boolean
                    :default     :false}]
     :isVerified  [{:type        :boolean
                    :default     :false}]
     :firstName   [{:type        :string}]
     :lastName    [{:type        :string}]
     :email       [{:type        :ref
                    :ref-ns      :account.contact}]
     :contacts    [{:type        :ref
                    :ref-ns      :account.contact
                    :cardinality :many}]

     :business
     {:name       [{:type         :string}]
      :abn        [{:type         :string}]
      :desc       [{:type         :string}]
      :industry   [{:type         :string
                    :cardinality  :many}]}

     :address
     {:billing    [{:type        :ref
                    :ref-ns      :account.address}]
      :shipping   [{:type        :ref
                    :ref-ns      :account.address}]
      :all        [{:type        :ref
                    :ref-ns      :account.address
                    :cardinality :many}]}}

    :account.address
    {:country     [{:type        :string}]
     :region      [{:type        :string}]
     :city        [{:type        :string}]
     :line1       [{:type        :string}]
     :line2       [{:type        :string}]
     :postcode    [{:type        :string}]}

    :account.contact
    {:type        [{:type        :keyword}]
     :field       [{:type        :string}]}}))

(def account-info
  {:account
   {:username    "chris"
    :hash        "c27e8090a3ae"
    :joined      (java.util.Date.)
    :isActivated true
    :isVerified  true
    :firstName   "Chris"
    :lastName    "Zheng"
    :email       {:+ {:db/id (iid :main-email)}}
    :contacts    #{{:type :twitter :field "zc123"}
                   {:type :skype   :field "zcaudate"}
                   {:+ {:db/id (iid :main-email)}
                    :type :email   :field "z@caudate.me"}}

    :business
    {:name       "caudate inc."
     :abn        "389474839"
     :desc       "brain work"
     :industry   #{"internet" "computing"}}

    :address
    {:billing    {:+ {:db/id (iid :main-address)}}
     :shipping   {:+ {:db/id (iid :main-address)}}
     :all        #{{:+ {:db/id (iid :main-address)}
                    :country "Australia"
                    :region  "Victoria"
                    :city    "Melbourne"
                    :line1   "45 Greenways Rd"
                    :line2   ""
                    :postcode "3122"}}}}})

(def account-info-2
  {:account
   {:username    "titan"
    :hash        "c27e8090a3ae"
    :joined      (java.util.Date.)
    :isActivated true
    :isVerified  true
    :firstName   "Chris"
    :lastName    "Strong"
    :email       {:+ {:db/id (iid :main-email)}}
    :contacts    #{{:type :twitter :field "titicular"}
                   {:type :skype   :field "titicular"}
                   {:+ {:db/id (iid :main-email)}
                    :type :email   :field "titi@cul.ar"}}

    :business
    {:name       "titicular"
     :abn        "38923433"
     :desc       "muscles work"
     :industry   #{"moving" "storms"}}

    :address
    {:billing    {:+ {:db/id (iid :main-address)}}
     :shipping   {:+ {:db/id (iid :main-address)}}
     :all        #{{:+ {:db/id (iid :main-address)}
                    :country "Australia"
                    :region  "Victoria"
                    :city    "Melbourne"
                    :line1   "101 Olympus Dr"
                    :line2   ""
                    :postcode "3000"}}}}})

@(d/transact *conn* (as/generate-schemas account-map))
@(d/transact *conn* (ad/generate-data account-map account-info))
@(d/transact *conn* (ad/generate-data account-map account-info-2))

(def link-map
  (flatten-keys
   {:link {:next  [{:type        :ref
                    :ref-ns      :link}]
           :value [{:type        :string
                    :default     "undefined"}]}}))

(def link-data
  {:link {:value "1"
          :next {:value "2"
                 :next  {:value "3"
                         :next {:value "4"}}}}})

(def link-circular
  {:db/id (iid :start)
   :link {:value "1C"
          :next {:value "2C"
                 :next  {:value "3C"
                         :next {:value "4C"
                                :next {:+ {:db/id (iid :start)}}}}}}})


@(d/transact *conn* (as/generate-schemas link-map))
@(d/transact *conn* (ad/generate-data link-map link-data))
@(d/transact *conn* (ad/generate-data link-map link-circular))

(aa/delete-linked! *conn* link-map {:link/value "2"} #{:link/_next})
(aa/delete-linked! *conn* link-map {:link/value "2C"} #{:link/next})

(aa/all-ref-ids
 (aa/find-first (d/db *conn*) {:link/value "1"}) #{:link/next})
(aa/all-ref-ids
 (aa/find-first (d/db *conn*) {:link/value "4C"}) #{:link/next})

(comment
  (aa/find-ids (d/db *conn*) {:account/firstName "Chris"})
  (aa/find-ids (d/db *conn*) {:account.address/city '_})

  (aa/find-ids (d/db *conn*)
               (aa/find (d/db *conn*) {:account/firstName "Chris"}))

  (aa/all-ref-ids
   (aa/find-first (d/db *conn*) {:account/firstName "Chris"})
   #{:account/email})

  (pprint
   (ad/deprocess-data account-map
                      (aa/find-first (d/db *conn*) {:account/firstName "Chris"})))

  (aa/delete! *conn* {:account/firstName "Chris"})

  (aa/delete-linked! *conn* account-map {:account/firstName "Chris"} #{:account/email})


  (type (first (aq/find-entities (d/db *conn*) {:account/firstName "Chris"}))))



(seq (first (aq/find-entities (d/db *conn*) {:account/firstName "Chris"})))
