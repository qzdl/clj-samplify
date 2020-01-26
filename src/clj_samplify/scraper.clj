(ns clj-samplify.scraper
  (:require [net.cgrand.enlive-html :as html]))

(def ^:dynamic *base-url* "https://whosampled.com/")

(defn fetch-url [url]
  (html/html-resource (java.net.URL. url)))

(defn node->text
  [n]
  (html/text (first n)))

;;; SEARCH
;;


(def ^:dynamic *search-url*
  "https://whosampled.com/search/")

(def ^:dynamic *search-page*
  (->> ["halftime" "nas"]
       (build-search *search-url*)
       fetch-url))

(def ^:dynamic *tophit-selector*
  [html/root :.topHit])

(def *track-fragment*
  "Header/Body content for each section is built from a sequence of divs,
  with each distinct section marked by the occurance of `:div.sectionHeader'.
  It's not clear if we can rely on the above logic of 'all siblings until parent
  closes' as `:.layout-container' holds other items.

  Other things to note:
  - after `:topHit', tracks are contained within `:ul.list', and share the
  structure from detail pages"
  "TODO")

(defn build-search
  "Constructs query string params for url & list/vec of terms.
  Returns url as string.

  E.g.
  (build-search *search-url* [\"halftime\" \"nas\"])
  ;; => \"https://whosampled.com/search/?q=+halftime+nas\""
  [url terms]
  (str url "?q="
       (apply str
              (mapcat #(str "+" %) terms))))

(defn search->tophit [p]
  (html/select p *tophit-selector*))

(defn anchors->hrefs [a]
  (map #(first (html/attr-values % :href)) a))

(defn track->info [node]
  ""
  (let [title  (first (html/select node [:.title :.trackTitle]))
        artist (first (html/select node [:.title :.trackArtist :a]))
        text-result (map html/text [title artist])
        link-result (anchors->hrefs [title artist])]
    (zipmap [:title :artist :track-link :artist-link]
            (concat text-result link-result))))

(comment ".: Threading to the max
  Here we're composing a bunch of steps to get to a valid search link,
first from the bound `*search-page*', then a fresh e2e, dropping the
init values straight into build-search. It's satisfying to put things together
like this."

         ;; iterate on bound values
         (->> (search->tophit *search-page*)
              track->info
              :track-link
              (str *base-url*)) ; "https://whosampled.com/Nas/Halftime"

         ;; regress e2e
         (->> ["halftime" "nas"]
              (build-search *search-url*)
              fetch-url
              search->tophit
              track->info
              :track-link
              (str *base-url*))

         "I'm still thinking of how to write tests as a part of this process;
still lots of reading to do."
         ":threading:composition:")



;;; DEV-RESOURCES
;;


(comment "General: Dirty REPL
  When messing around with Clojure, it's common for me to try a few
ranges of parameters to some function, I'm interested in, or even start writing
tests directly below the newly composed unit; using the available space as a
*scratch* space. If you don't change to a throwaway namespace, the amount of
nonsense where one works will accumulate over time, which may pose issues with
resource consumption if there's any heavy lifting with streams, connections, or
file APIs that haven't been closed or GC'ed. Stack Overflow has delivered an
absolute gem to clear up the situation we describe here:
;; => "

         (ns-interns *ns*)
         (ns-unmap *ns* 'build-search_single-input)
         (ns-unmap *ns* 'build-search_n-inputs)

         ":repl:general:housekeeping:")

(def ^:dynamic *base-page*
  "HTML from base-url"
  (fetch-url *base-url*))

(defn fetch-title
  "Applies 'fetch-url' to '*base-url', interrogates for text in 'head title'"
  ([] (fetch-title (fetch-url *base-url*)))
  ([dirt] (html/select dirt [:head :title])))

(comment "Enlive: Initial Text Retrieval:
  My very first attempt to parse html with enlive. It seems this way of ripping
out basic text nodes is clumsy, though it does seem to work.

  My assumption at present is that if text were the only, or first node for any
given node (element), this chaining of functions would certainly return the text
content. Behaviour around nil for such a chain is to return nil.

  In the large, I would like to write tests in this format, though it is unclear
how the tests will fit together; some more thinking around how deep a function
should parse into the page-tree is required, which will hopefully arise by
looking at the re-use of component structures on the whosampled site. All of
this content is to be moved to dev-resources eventually, but for now it's
comforting to have the context of the basic operations at my fingertips whilst
thinking about how everything will fit together.

This section will read linearly, in the present tense, as I think, research, and
experiment."

  ;; initial reasoning
         (first
          (:content
           (first
            (explore-fetch *base-page*))))

  ;; restructuring chained calls for stoile (later found out that no parens req'd)
         (-> (explore-fetch *base-page*)
             (first)
             (:content)
             (first))

  ;; confirmation of nil behaviour
         (-> nil
             (first)
             (:content)
             (first))

         "Picking this up later, it seems as though this approach is more general for
node->0th-content->0th-node. This of course can be 'surrounded' by other nodes;
0-n (*) potential siblings/children depending on whether element is self-closing.
For reference: https://github.com/cgrand/enlive/blob/119ab97dd683681683354b60e356ed748bad5e78/src/net/cgrand/enlive_html.clj#L132
;; => (def self-closing-tags #{:area :base :basefont :br :hr :input :img :link :meta})

  It strikes me that I don't know what '0th-content' means in the context of node->0th-content->0th-node.
When hitting:"

         (-> *base-page*
             (html/select)
             [:head :title]) ; , d v to inspect


         ".. it's a list of nodes that is returned. Maybe worth digging into html/select
to check out whats happening here; is every result from html/select a list? It
would seem to follow the emphasis on set operations that;
 'if no matches for the given predicate(s) are found, return an empty list
  else, return the node(s) matching the predicates (list of arbitrarily nested maps)
;; => https://github.com/cgrand/enlive/blob/119ab97dd683681683354b60e356ed748bad5e78/src/net/cgrand/enlive_html.clj#L553
  Reading through the implementation of html/select has left me pretty confident
in the function only ever returning matches to specified predicates, or an empty
list. The following is far from covering, but it helps to illustrate the point:"

         (html/select nil [:head]) ; => ()
         (html/select nil nil)     ; => ()


         "Harking back to the original node->0th-content->first piece, I'm not sure that
I have a need for such an extractor just yet, but it's good to explore
datastructures in this fashion to best think about how to manipulate them. This
 (enlive) might be a good fit for a v0 mapping from ox:html-export->clojure data."

         ":thoughtstream:live:")

(defn dirt->test-data!
  "With use for test data persistence.
  Takes paydirt, evals all (dirt is probably lazy), dumps output to
  '{project root}/test/data/whosampled_`~name.edn'

e.g. (-> (fetch-url *base-url*)
         (dirt->test-data! % \"base\"))"
  [dirt name]
  (let [path (str "test/data/whosampled_" name ".edn")]
    (-> (into () *base-page*)
        (spit path))))

(comment "General: Style & Threading Macros
  The following is an example of function application to get something useful.
Side note: My go-to at this point is `->`, the top-down threading macro; it
seems like a great way to structure chained function calls, but my concern is
that each function is doing too much if a default is to reach for this macro.
 "

         (-> *base-page*
             (html/select [:head :title])
             node->text)

         "Having asked the community on the usage of `->', I received some guidance
around sense; it's important to understand what the threading macros are doing,
so reading reference material on the subject:
;; => https://clojure.org/guides/threading_macros
;; => https://stuartsierra.com/2018/07/06/threading-with-style.
  Ultimately though, my main take-away is that I'm focusing too much on problems
of the implementation, of style, when what really matters at this stage of my
learning is making some stuff work."

         ":fundamentals:threading:")

(comment "General: Destructuring and ISeq
  I'm trying to figure out how and why the datastructures:ISeq are transformed
 when passing though various functions that operate on sequences."

         (-> [{['a 'b] ['c]}
              {"second" "ignore-it"}]
             first   ; => {[a b] [c]}
             first   ; => [[a b] [c]]
             first)  ; => [a b]
         ":destructuring:sequences:")
