(ns crystaluniverse.output
  (:use adi.utils)
  (:require [crystaluniverse.datamaps :as dm]
            [crystaluniverse.parsing :as p]
            [fs.core :as fs]
            [cheshire.core :as json]
            [adi.api :as aa]
            [adi.core :as adi]
            [datomic.api :as d]))


(def ^:dynamic *ds* (adi/datastore "datomic:mem://magento.output"
                         dm/all true true))

(defn install-categories [ds file]
  (let [c-json (slurp file)
        c-clj  (json/parse-string c-json)
        c-data (p/parse-all-categories c-clj)]
    (adi/insert! ds c-data)))

(defn parse-products [p-dir]
  (let [p-files (map #(str p-dir "/" %)
                    (fs/list-dir p-dir))
        p-clj   (->> p-files
                    (map slurp)
                    (map json/parse-string))
        p-ids   (map #(get % "product_id") p-clj)
        p-all   (zipmap p-ids p-clj)]
    (p/parse-products p-all)))



;; Generating
(defn get-all-leaf-categories [ds]
  (clojure.set/difference
   (set (adi/q ds '[:find ?cname ?cid :where
                      [?e :category/name ?cname]
                      [?e :magento/category/id ?cid]] []))
   (set (adi/q ds '[:find ?cname ?cid :where
                      [?e :category/name ?cname]
                      [?e :category/children ?c]
                      [?e :magento/category/id ?cid]
                      ] []))))

;; Generating
(defn get-category-products [ds id]
  (adi/select ds {:magento/product/categories id
                    :#/not {:magento/product/type :variant}}
              (aa/emit-refroute (:fschm ds))))

(defn spit-category-products-json [ds [n id]]
  (spit (str "outputs/" id "-" (clean-name n) ".json")
        (-> (get-category-products ds id)
            json/generate-string)))

(defn gen-category-urls [ds [n id]]
  (let [ps (get-category-products ds id)]
    (concat
     [(str "/#!/catalogue/" (clean-name n) "/list")
      (str "/#!/catalogue/" (clean-name n) "/detailed")]
     (map #(str "/#!/catalogue/" (clean-name n) "/product/" (-> % :product :slug)) ps))))

(defn gen-all-urls [ds]
  (let [cats (get-all-leaf-categories ds)]
    (mapcat #(gen-category-urls ds %) cats)))


(defn install-data []
  ;; Installing the Data
  (install-categories *ds* "php/categories.json")
  (def ppd (parse-products "php/grouped"))
  (adi/insert! *ds* ppd))



(defn output-all-data []
  (install-data)

  ;; generate-categories
  (spit "categories.json"
        (json/generate-string
         (adi/select *ds* {:category/name "Crystal Universe"}
                     (aa/emit-refroute (:fschm *ds*)))))


  ;; generate all urls

  (spit "urls.json"
        (json/generate-string (gen-all-urls *ds*)))

  ;; generate all category outputs


  (doseq [c (get-all-leaf-categories *ds*)]
    (spit-category-products-json *ds* c)))


(comment

  (output-all-data)

  )
