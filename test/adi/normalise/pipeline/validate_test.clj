(ns adi.normalise.pipeline.validate-test
  (:use midje.sweet)
  (:require [adi.test.examples :as examples]
            [adi.test.checkers :refer :all]
            [adi.schema :as schema]
            [adi.normalise.base :as normalise]
            [adi.normalise.common.paths :as paths]
            [adi.normalise.pipeline.validate :as validate]))

(def ^:dynamic *wrappers*
  {:normalise        [paths/wrap-plus]
   :normalise-single [validate/wrap-single-model-validate]
   :normalise-branch [paths/wrap-key-path]
   :normalise-attr   [paths/wrap-key-path]})

^{:refer adi.normalise.pipeline.validate/wrap-single-model-validate :added "0.3"}
(fact "validates input according to model"

 (normalise/normalise {:account/name "Chris"}
                     {:schema (schema/schema examples/account-name-age-sex)
                      :model {:validate {:account {:name number?}}}}
                     *wrappers*)
  => (raises-issue {:not-validated true :nsv [:account :name]})

  (normalise/normalise {:account/name "Bob"}
                       {:schema (schema/schema examples/account-name-age-sex)
                        :model {:validate {:account {:name #(= % "Bob")}}}}
                       *wrappers*)
  => {:account {:name "Bob"}})
