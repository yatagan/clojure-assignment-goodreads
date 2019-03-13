(ns goodreads.core
  (:gen-class)
  (:require [clojure.tools.cli :as cli]
            [manifold.deferred :as d]
            [goodreads.book :as book]))

;; TODO: this implementation is still pretty useless :(
(defn build-recommentations
  [{:keys [token]}]
  (let [read-shelf (book/fetch-shelf token "read")
        reading-shelf-ids (->> "currently-reading"
                               (book/fetch-shelf token)
                               (map #(:id %))
                               (set))]
    (d/success-deferred
      (->> read-shelf
           (reduce
             #(concat %1 (->> %2 :id (book/fetch-similar-books token)))
             [])
           (sort-by #(:rating %) >)
           (remove #(contains? reading-shelf-ids (:id %)))
           (take 10)))))

(def cli-options [["-t"
                   "--timeout-ms"
                   "Wait before finished"
                   :default 5000
                   :parse-fn #(Integer/parseInt %)]
                  ["-n"
                   "--number-books"
                   "How many books do you want to recommend"
                   :default 10
                   :parse-fn #(Integer/parseInt %)]
                  ["-h" "--help"]])

(defn book->str [{:keys [title link authors]}]
  (format "\"%s\" by %s\nMore: %s"
          title
          (->> authors
               (map :name)
               (clojure.string/join ", "))
          link))

(defn -main [& args]
  (let [{:keys [options errors summary]} (cli/parse-opts args cli-options)]
    (cond
      (contains? options :help) (do (println summary) (System/exit 0))
      (some? errors) (do (println errors) (System/exit 1))
      (empty? args) (do (println "Please, specify user's token") (System/exit 1))
      :else (let [config {:token (first args)}
                  books (-> (build-recommentations config)
                            (d/timeout! (:timeout-ms options) ::timeout)
                            deref)]
              (cond
                (= ::timeout books) (println "Not enough time :(")
                (empty? books) (println "Nothing found, leave me alone :(")
                :else (doseq [[i book] (map-indexed vector books)]
                        (println (str "#" (inc i)))
                        (println (book->str book))
                        (println)))))))
