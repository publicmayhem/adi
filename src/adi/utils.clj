(ns adi.utils
  (:require [clojure.string :as st]))

(defn boolean? [x] (instance? java.lang.Boolean x))

(defn hash-map? [x]
  (and (instance? clojure.lang.IPersistentMap x)
       (not (instance? datomic.db.DbId x))))

(defn hash-set? [x] (instance? clojure.lang.PersistentHashSet x))

(defn ref? [x] (or (hash-map? x) (instance? datomic.query.EntityMap x)))

(defn long? [x] (instance? java.lang.Long x))

(defn bigint? [x] (instance? clojure.lang.BigInt x))

(defn bigdec? [x] (instance? java.math.BigDecimal x))

(defn instant? [x] (instance? java.util.Date x))

(defn uuid? [x] (instance? java.util.UUID x))

(defn uri? [x] (instance? java.net.URI x))

(defn bytes? [x] (= (Class/forName "[B")
                    (.getClass x)))

(defn lazy-seq? [x] (instance? clojure.lang.LazySeq x))

;; Misc Methods

(defn replace-all [x b a]
  (.replaceAll x b a))

(defn clean-name [n]
  (clojure.string/join
   "-" (-> (replace-all n "&" "and")
           (replace-all "'" "")
           clojure.string/lower-case
           (clojure.string/split #" "))))

(defn ?sym []
  (symbol (str "?" (name (gensym)))))

(defn dissoc-in
  [m [k & ks]]
  (if-not ks
    (dissoc m k)
    (let [nm (dissoc-in (m k) ks)]
      (cond (empty? nm) (dissoc m k)
            :else (assoc m k nm)))))

(defn dissoc-in-keepempty
  [m [k & ks]]
  (if-not ks
    (dissoc m k)
    (assoc m k (dissoc-in-keepempty (m k) ks))))

(defn no-repeats
  ([coll] (no-repeats identity coll))
  ([f coll] (no-repeats f coll [] nil))
  ([f coll output last]
     (if-let [v (first coll)]
       (cond (and last (= (f last) (f v)))
             (no-repeats f (next coll) output last)
             :else (no-repeats f (next coll) (conj output v) v))
       output)))

(defn what-is-different
  ([d1 d2] (what-is-different d1 d2 []))
  ([d1 d2 trail]
     (cond
      (and (hash-map? d1) (hash-map? d2))
      (let [ks1 (set (keys d1)) ks2 (set (keys d2))]
        (if (= ks1 ks2)
          (mapv what-is-different  (map d1 ks1) (map d2 ks1) )
          (throw (Exception. (format "keys are different at %s %s %s" trail ks1 ks2 )))))

      (and (hash-set? d1) (hash-set? d2))
      (map #(what-is-different %1 %2 trail) d1 d2)

      (not= d1 d2)
      (throw (Exception. (format "different at %s, %s, %s " trail d1 d2))))))


;; Keyword Manipulation

(defn k-str
  "Returns the string representation without the colon.\n
   (k-str :hello/there) ;;=> \"hello/there\""
  [k]
  (if (nil? k) "" (#'st/replace-first-char (str k) \: "")))

(defn k-merge
  "Merges a vector of keywords into one keyword"
  [ks]
  (if (empty? ks) nil
      (->> (filter identity ks)
           (map k-str)
           (st/join "/")
           keyword)))

(defn k-unmerge [k]
  (if (nil? k) []
      (mapv keyword (st/split (k-str k) #"/"))))

(defn k-nsv [k]
  (or (butlast (k-unmerge k)) []))

(defn k-nsv? [k nsv]
  (= nsv (k-nsv k)))

(defn k-ns [k]
  (k-merge (k-nsv k)))

(defn k-ns?
  ([k] (< 0 (.indexOf (str k) "/")))
  ([k ns] (if-let [tkns (k-ns k)]
            (= 0 (.indexOf (str k)
                 (str ns "/")))
            (nil? ns))))

(defn k-z [k]
  (last (k-unmerge k)))

(defn k-z? [k z]
  (= z (k-z k)))

(defn list-ns [cm]
  (let [ks (keys cm)]
    (set (map k-ns ks))))

(defn list-ks [cm ns]
  (let [ks (keys cm)]
    (->> ks
         (filter #(= ns (k-ns %)))
         set)))

(defn list-all-keys
  ([m] (list-all-keys m #{}))
  ([m ks]
     (if-let [[k v] (first m)]
       (cond (hash-map? v)
             (clojure.set/union
              (list-all-keys (next m) (conj ks k))
              (list-all-keys v))

             :else (list-all-keys (next m) (conj ks k)))
       ks)))

(defn remove-all-keys
  ([m ks] (remove-all-keys m (set ks) {}))
  ([m ks output]
    (if-let [[k v] (first m)]
      (cond (ks k) (remove-all-keys (next m) ks output)

            (hash-map? v)
            (remove-all-keys (next m) ks
              (assoc output k (remove-all-keys v ks)))
            :else
            (remove-all-keys (next m) ks (assoc output k v)))
      output)))

;; Map Manipulation
(defn flatten-all-keys
  "flatten-all-keys will take a map of maps and make it into a single map"
  ([m] (flatten-all-keys m [] {}))
  ([m nskv output]
     (if-let [[k v] (first m)]
       (cond (hash-map? v)
             (->> output
                  (flatten-all-keys (next m) nskv)
                  (flatten-all-keys v (conj nskv k)))

             (nil? v)
             (flatten-all-keys (next m) nskv output)

             :else
             (flatten-all-keys (next m)
                           nskv
                           (assoc output (k-merge (conj nskv k)) v)))
       output)))

(defn flatten-keys
  "flatten-keys will flatten only one layer"
  ([m] (flatten-keys m {}))
  ([m output]
     (if-let [[k v] (first m)]
       (cond (hash-map? v)
             (flatten-keys
               (next m)
               (merge (zipmap (map #(k-merge [k %]) (keys v))
                              (vals v))
                       output))

              :else
              (flatten-keys (next m) (assoc output k v)))
      output)))

(defn treeify-keys
  "treeify-keys will take a single map of compound keys and make it into a tree"
  ([m] (treeify-keys m {}))
  ([m output]
     (if-let [[k v] (first m)]
       (recur (rest m)
              (assoc-in output (k-unmerge k) v))
       output)))

(defn treeify-all-keys [m]
  (let [kvs (seq m)
        hm? #(hash-map? (second %))
        ms  (filter hm? kvs)
        vs  (filter (complement hm?) kvs)
        outm (reduce #(assoc-in %1 (k-unmerge (first %2))
                                  (treeify-all-keys (second %2)))
                    {} ms)]
    (reduce #(assoc-in %1 (k-unmerge (first %2)) (second %2))
                     outm vs)))


(defn contains-ns-keys [cm ns]
  (some #(k-ns? % ns) (keys cm)))

(defn extend-keys [m nskv ex]
  (let [e-map (select-keys m ex)
        x-map (apply dissoc m ex)]
    (merge e-map (if (empty? nskv)
                   x-map
                   (assoc-in {} nskv x-map)))))

(defn contract-keys [m nskv ex]
  (let [tm     (treeify-keys m)
        c-map  (get-in tm nskv)
        x-map  (dissoc-in tm nskv)]
    (merge c-map (if (empty? ex)
                   x-map
                   (assoc-in {} ex x-map)))))
