(ns clj-samplify.scraper
  (:require [net.cgrand.enlive-html :as html]))

(def ^:dynamic *base-url* "https://whosampled.com/")

(defn fetch-url [url]
  (html/html-resource (java.net.URL. url)))


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

;; a generalisation of this exists as `fn-node'
(defn first-node
  ([node]
   (fn [pred] (first-node node pred)))
  ([node pred]
   (first (html/select node pred))))

(-> (nsmap
 {:text (shake-fn #(map html/text %))
  :link (shake-fn anchors->hrefs)})
    :link/title)

(defn nsmap
  "Namespace all unqualified keys in `m` with `n'.
  Single arity takes a map of maps, returning inner maps with keys ns'd by the key
  of "
  ([m]
   (map #(nsmap (% m) (name %)) (keys m)))
  ([m n]
   (reduce-kv
    (fn [acc k v]
      (let [new-kw (if (and (keyword? k)
                            (not (qualified-keyword? k)))
                     (keyword (str n) (name k))
                     k)]
        (assoc acc new-kw v)))
    {} m)))

(defn shake
  [p-key]

  (->> (p-key app-map)
       f-node list f first
       (assoc {} p-key)))

(defn key-shake
  "Interrogate map(s) for `f' of `pred' given `:key'.
  Returns a map fitting the key structure of app-map, where the value
  is the result of `f' applied to the return of `(html/select node pred)'.

  If not `seq? nodes',
  - given a seq of maps

  shake:
  - Given a key and a map,
    look-up value (predicate) as p,
    pass p to f-node as v,
    apply f on v, assoc into map as key

  [nodes]: return a closure on nodes over recur[n f a]
  [nodes f app-map]:
  TODO: apply f as seq of fns"
  ([nodes]
   (fn [f app-map] (key-shake nodes f app-map)))
  ([nodes f app-map]
   (let [app-keys (keys app-map)
         f-node (first-node nodes)
         shake (fn [p-key]
                 (->> (p-key app-map)
                      f-node list f first
                      (assoc {} p-key)))]
     (reduce merge (map shake app-keys)))))

(comment "Closures: In search of concision
An interesting descent into variadic madness.

The below block was my first attempt at this, and I was *not* satisfied with the
approach. For starters, the duplication is horrendous, both of repeated logic;
`(first (html/select) ...)', and of symbols -- for each new binding, and/or
value type (text, link) the expansion is shocking:"

         ;; innocent
         (let [node *search-page*
               title  (first (html/select node [:.title :.trackTitle]))
               artist (first (html/select node [:.title :.trackArtist :a]))
               text-result (map html/text [title artist])
               link-result (anchors->hrefs [title artist])]
           (zipmap [:title :artist :track-link :artist-link]
                   (concat text-result link-result)))

         ;; add new type and field-value binding
         (let [node *search-page*
               title  (first (html/select node [:.title :.trackTitle]))
               artist (first (html/select node [:.title :.trackArtist :a]))
               year   (first (html/select node [:.title :.trackYear]))
               text-result (map html/text [title artist year])
               link-result (anchors->hrefs [title artist])]
           baztype-result (map bazify [title artist year])
           (zipmap [:title :artist :year
                    :track-link :artist-link
                    :title-baztype :artist-baztype :year-baztype]
                   (concat text-result link-result)))

         ".. this is simply not on. The first go at improving this lead me to
write a pretty wacky function `key-shake' that allows a pretty nice declarative
way of specifying what you'd like back. My understanding of the function I had
just written was fairly poor, so the v0 caller looked like this:"

         (let [shaker (key-shake *search-page*)
               [title artist] [[:.title :.trackTitle] [:.title :.trackArtist :a]]
               decl {:title title :artist artist}
               links  (shaker anchors->hrefs decl)
               text   (shaker #(map html/text %) decl)]
           (reduce merge links text))

         "If looking at that gave you a strange feeling, it's because there's a
collison in the key names; the horrible repitition has been cleared up, but the
dynamic naming has not been solved -- the semantics of `merge' are like an upsert,
so the last value to be bound will be of the coll `text' in this case.
  I landed on namespacing my keys to ensure uniqueness between value-types
leaning on [[this stack overflow answer][https://stackoverflow.com/questions/43722091/clojure-programmatically-namespace-map-keys]] for the initial framing of the functionality.
"

         ;; current best
         (let [shaker (key-shake *search-page*)
               sq {:title [:.title :.trackTitle]
                   :artist [:.title :.trackArtist :a]}]
           (nsmap {:links (shaker anchors->hrefs sq)
                   :text  (shaker #(map html/text %) sq)}))

         ;; ideal?
         (nsmap
          (shaker *search-page*
                  {:links anchors->hrefs
                   :text  #(map html/text %)}
                  {:title [:.title :.trackTitle]
                   :artist [:.title :.trackArtist :a]}))

         ".. I think this is a step in the right direction, although there's
still a few sketchy things going on behind the scenes, such as the use of
`first-node' in key-shake, and closures on `nodes' pervading "

         (defn shake-fn [f]
           (key-shake *search-page* f
                      {:title [:.title :.trackTitle]
                       :artist [:.title :.trackArtist :a]}))

         (nsmap
          {:text (shake-fn #(map html/text %))
           :link (shake-fn anchors->hrefs)})

         (nsmap {:title {"aa" "bb"}})

         "Reducing the amount of needless repition in a program is great, but
there are still problems with the approach as seen above; the first map is the
value-type {:name fn}, really just a function to apply to whatever is returned
by the predicates passed to `first-node', with the key as ready to namespace.
The second map specifies some binding name, and the predicate to filter the
html-resource. Every (k0,v0)function in the first map will be applied to the
values returned from (k1,v1)preds in the second map, bound as
{:k0/k1 (v0 (v1 html-resource)}. ")

(comment "General: Sane things about interning & symbols
- A key is still a function of a map, even if bound to a sym. Bliss."

         (let [key-as-sym :my-key]
           (key-as-sym {:my-key "okay"}))

         (assoc)

         ":essence:nice:")

(defn track->info
  ""
  [node]
  (let [shaker (key-shake node)
        sq {:title [:.title :.trackTitle]
            :artist [:.title :.trackArtist :a]}]
    (nsmap {:links (shaker anchors->hrefs sq)
            :text  (shaker #(map html/text %) sq)})))

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

         ;; this is the whole program so far
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

;; this is the whole program so far
(->> ["halftime" "nas"]
     (build-search *search-url*)
     fetch-url
     search->tophit
     track->info
     :links/title
     (str *base-url*))

;;; DETAIL
;;

(def ^:dynamic *detail-url*
  "https://whosampled.com/Nas/Halftime")

(def ^:dynamic *detail-page*
  (fetch-url *detail-url*))

(def pred-sampled-in)
;; (defn section->tracks
;;   ""
;;   ([nodes]
;;    (section->tracks nodes [pred-sampled-in]))
;;   ([nodes preds]
;;    (map track->info (html/select nodes
;;                                  [(concat [] preds)])))


(commment "Enlive: Building Predicates
I'm attempting to express the following set of constraints
get")



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

         (defn node->text
           [n]
           (html/text (first n)))

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
