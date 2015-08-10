(ns adzerk.boot-cljs.js-deps)

(defn- max* [& args] (apply max (or (seq args) [0])))

(defn- start-dep-order
  "Returns the next dependency order index, one greater than the maximum dep
  order index in the given metadata map (path => {:dependency-order n})."
  [maps]
  (->> maps (keep :dependency-order) (apply max*) inc))

(defn compiled
  "Given a dep order metadata map (path => {:dependency-order n}) and a seq of
  fileset paths in dependency order, creates a new metadata map adding entries
  for the paths such that the paths depend on the preexisting entries."
  [dep-order-meta paths]
  (let [start (->> dep-order-meta vals start-dep-order)]
    (->> paths
         (map-indexed (fn [a b] [b {:dependency-order (+ start a)}]))
         (into dep-order-meta))))

(defn analyzed
  [analysis-meta]
  (reduce-kv (fn [xs k v] (assoc xs k {:cljs-analysis v})) {} analysis-meta))
