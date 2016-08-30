(ns sha224rch.core
  (:gen-class)
  (:import [java.io File])
  (:require [clojure.java.io :refer [as-file copy make-parents]]
            [clojure.string :refer [join]]
            [digest :refer [sha-224]]
            [robert.hooke :refer [add-hook]]))

(declare copy-file create-fileinfo)

(defn archive-dir [source-dir target-dir filter]
  (let [source (file-seq (clojure.java.io/file source-dir))]
    (loop [infiles source, copied 0, total 0]
      (if-let [entry (first infiles)]
        (if (and (.isFile entry) (filter entry))
          (let [{:keys [checksum subpath dir-path file-exists] :as fileinf}
                (create-fileinfo entry target-dir)]
            (if-not file-exists (copy-file fileinf))
            (recur (rest infiles)
                   (if file-exists copied (inc copied))
                   (inc total)))
          (recur (rest infiles) copied total))
        {:copied copied, :duplicates (- total copied)}))))

(defn ^:private copy-file [{:keys [dir-path checksum entry]}]
  (make-parents dir-path)
  (copy entry (as-file (str dir-path ".tmp")))
  (.renameTo (File. (str dir-path ".tmp")) (File. dir-path) ))

(defn ^:private copy-file-hook [f {:keys [checksum entry] :as orig-arg}]
  (print (format  "copy      %s %s" checksum (.getPath entry)))
  (f orig-arg)
  (println "."))

(defn ^:private create-fileinfo [file target-dir]
  (let [checksum (sha-224 file)
        subpath (loop [partitions [3 3 3 4 5 6 8 10 14], val checksum, res []]
                  (if-let [cur-partition (first partitions)]
                    (recur (rest partitions)
                           (drop cur-partition val)
                           (conj res (apply str (take cur-partition val))))
                    (join "/" res)))

        dir-path (join "/" [target-dir subpath])]
    {:entry file
     :checksum checksum
     :subpath subpath
     :dir-path dir-path
     :file-exists (.exists (as-file dir-path))}))

(add-hook #'copy-file #'copy-file-hook)

(defn -main [& args]
  (if (< (count args) 2)
    (println "Copies files from source to target using SHA-224 to build the target directory structure.

parameters: source target [pattern]

Example: /srv/files /backup \"(?i)(jpe?g$|png$|gif$|tiff?$|bmp$|mov$|mp4$|avi$|mkv$|mpeg$|webm$|wmv$)\"")
    (let [source (nth args 0)
          target (nth args 1)
          pattern (if (> (count args) 2) (nth args 2) ".*")]
      (println (archive-dir
                source target
                #(re-find (re-pattern pattern) (.getPath %)))))))
