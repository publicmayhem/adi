(ns adi.process.normalise.common.type-check
  (:require [adi.schema.meta :as meta]
            [adi.data.coerce :refer [coerce]]
            [hara.event :refer [raise]]))

(defn wrap-single-type-check
  "wraps normalise to type check inputs as well as to coerce incorrect inputs
  (normalise/normalise {:account {:age \"10\"}}
                       {:schema (schema/schema examples/account-name-age-sex)}
                       {:normalise-single [wrap-single-type-check]})
  => (raises-issue {:type :long,
                    :data \"10\",
                    :wrong-type true})

  (normalise/normalise {:account {:age \"10\"}}
                       {:schema (schema/schema examples/account-name-age-sex)
                        :options {:use-coerce true}}
                       {:normalise-single [wrap-single-type-check]})
  => {:account {:age 10}}"
  {:added "0.3"}
  [f]
  (fn [subdata [attr] nsv interim fns adi]
    (let [t (:type attr)
          chk (meta/type-checks t)]
      (cond
       (chk subdata) (f subdata [attr] nsv interim fns adi)

       (-> adi :options :use-coerce)
       (f (coerce subdata t) [attr] nsv interim fns adi)

       :else
       (raise [:adi :normalise :wrong-type
               {:data subdata :nsv nsv :key-path (:key-path interim) :type t}]
               (str "WRAP_SINGLE_TYPE_CHECK: " subdata " in " nsv " is not of type " t))))))
