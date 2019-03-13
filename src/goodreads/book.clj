(ns goodreads.book
  (:require [aleph.http :as http]
            [clojure.xml :as xml]))

(defrecord Book
  [id
   title
   authors
   link
   rating])

(defn get-item-by-tag
  [tag data]
  (some->> data
           (filter #(= tag (:tag %)))
           first
           :content))

(defn edn->Book
  [book]
  (when book
    (let [id (->> book (get-item-by-tag :id) first Integer/parseInt)
          title (->> book (get-item-by-tag :title) first)
          link (->> book (get-item-by-tag :link) first)
          rating (->> book (get-item-by-tag :average_rating) first Float/parseFloat)
          authors (->> book
                       (get-item-by-tag :authors)
                       (map #(->> % :content (get-item-by-tag :name) first (hash-map :name))))]
      (map->Book {:id      id
                  :title   title
                  :authors authors
                  :link    link
                  :rating  rating}))))

(defn fetch-shelf
  [key shelf]
  (->> @(http/get "https://www.goodreads.com/review/list/94745457.xml"
                  {:query-params {:key key :v 2 :shelf shelf}})
       :body
       (xml/parse)
       :content
       (get-item-by-tag :reviews)
       (map #(->> % :content (get-item-by-tag :book) edn->Book))))

(defn fetch-similar-books
  [key book-id]
  (->> @(http/get "https://www.goodreads.com/book/show.xml"
                  {:query-params {:key key :id book-id}})
       :body
       (xml/parse)
       :content
       (get-item-by-tag :book)
       (get-item-by-tag :similar_books)
       (map #(->> % :content edn->Book))))