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
            [ring.middleware.keyword-params :as ringkp]))

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
