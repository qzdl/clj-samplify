(ns finesse
  (:require [clojure.edn :as edn]
            [clj-samplify.core :as samplify]
            [clj-spotify.util :as util]
            [clj-spotify.core :as spotify]
            [clojure.java.io :as io]
            [loudmoauth.core :as lm]
            [ring.adapter.jetty :as ringj]
            [ring.util.response :as ringr]
            [ring.middleware.params :as ringp]
            [ring.middleware.keyword-params :as ringkp]
            [clojure.string :as str]))

(defn get-env []
  (edn/read-string (slurp ".spotify.edn")))

(def spotify-auth
  (let [env (:env (get-env))]
    {:base-url "https://accounts.spotify.com"
     :auth-endpoint "/authorize"
     :token-endpoint "/api/token"
     :client-id (:spotify-client-id env)
     :redirect-uri (:spotify-redirect env)
     :scope "playlist-read-private playlist-modify-public playlist-modify-private ugc-image-upload user-follow-modify user-read-playback-state user-modify-playback-state user-read-currently-playing streaming"
     :custom-query-params {:show-dialog "true"}
     :client-secret (:spotify-client-secret env)
     :provider :spotify}))

(defn check-creds [t]
  (spotify/get-current-users-profile {} t))

;; from https://github.com/blmstrm/clj-spotify/blob/master/dev-resources/user.clj
(defn handler [request]
  (condp = (:uri request)
    "/oauth2" (lm/parse-params request)
    "/interact"  (ringr/redirect (lm/user-interaction))
    {:status 200
     :body (:uri request)}))

(defn run-server []
  (future (ringj/run-jetty
           (ringp/wrap-params (ringkp/wrap-keyword-params handler))
           {:port 8889}))
  (lm/add-provider spotify-auth))

(run-server)
(check-creds (lm/oauth-token :spotify))
(def token (lm/oauth-token :spotify))

;;====================================
;;                                  ;;
;; Scratch Investigation of spotify ;;
;;                                  ;;
;;====================================


;; :progress_ms
(spotify/get-users-currently-playing-track {} (lm/oauth-token :spotify))

(spotify/seek-to-position-in-currently-playing-track {:position_ms 500} token)

(defn search-type [v]
  (str/join "," v))
(search-type ['artist 'song])

(def ^:dynamic *artist-res*
  (spotify/search {:q "" :type (search-type ['album])} token))

(defn ssearch [q t]
  spotify/search {:q q :type t} token)

(def ^:dynamic *album-res*
  (spotify/search {:q "train of thought reflection eternal" :type (search-type ['album])} token))

(def ^:dynamic *track-res*
  (spotify/search {:q "brown skin lady mos def" :type (search-type ['track])} token))

(def ^:dynamic *playlist-res*
  (spotify/search {:q "train of thought reflection eternal" :type (search-type ['album])} token))

(->> *album-res*
     :albums
     :items
     (map expand-album))

(defn expand-album [a]
  (let [{:keys [name type id uri artists]} a]
    [name type id uri (map :name artists)]))

(defn expand-artist [ar-vec]
  (map (fn [a] (let [{:name }])))
               )

(->> *track-res*
     :tracks
     :items
     (map expand-album))
