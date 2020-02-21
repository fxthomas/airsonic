package org.airsonic.player.service;

import chameleon.playlist.*;
import org.airsonic.player.domain.InternetRadio;
import org.airsonic.player.domain.InternetRadioSource;
import org.apache.commons.io.input.BoundedInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.*;
import java.util.*;

@Service
public class InternetRadioService {

    private static final Logger LOG = LoggerFactory.getLogger(InternetRadioService.class);

    /**
     * The maximum number of source URLs in a remote playlist.
     */
    private static final int PLAYLIST_REMOTE_MAX_LENGTH = 250;

    /**
     * The maximum size, in bytes, for a remote playlist response.
     */
    private static final long PLAYLIST_REMOTE_MAX_BYTE_SIZE = 100 * 1024;  // 100 kB

    /**
     * The maximum number of redirects for a remote playlist response.
     */
    private static final int PLAYLIST_REMOTE_MAX_REDIRECTS = 20;

    /**
     * Used to determine how to connect to the stream.
     */
    private enum StreamType {
        AUDIO,
        AUDIO_SHOUTCAST,
        PLAYLIST,
        HTML_OR_TEXT,
        OTHER,
    }

    /**
     * List of all known audio types
     * https://developer.mozilla.org/en-US/docs/Web/Media/Formats/Containers
     */
    private static List<String> AUDIO_CONTENT_TYPES = Arrays.asList(
        "audio/3gpp",
        "audio/aac",
        "audio/flac",
        "audio/mpeg",
        "audio/mp3",
        "audio/mp4",
        "audio/ogg",
        "audio/wav",
        "audio/webm"
    );

    /**
     * https://en.wikipedia.org/wiki/M3U#Internet_media_types
     * https://en.wikipedia.org/wiki/PLS_(file_format)
     * https://en.wikipedia.org/wiki/Windows_Media_Player_Playlist
     * https://en.wikipedia.org/wiki/XML_Shareable_Playlist_Format
     */
    private static List<String> PLAYLIST_CONTENT_TYPES = Arrays.asList(
        "application/vnd.apple.mpegurl",
        "application/vnd.apple.mpegurl.audio",
        "audio/mpegurl",
        "audio/x-mpegurl",
        "application/mpegurl",
        "application/x-mpegurl",
        "audio/x-scpls",
        "application/smil+xml",
        "application/vnd.ms-wpl",
        "application/xspf+xml"
    );

    /**
     * A list of cached source URLs for remote playlists.
     */
    private final Map<Integer, List<InternetRadioSource>> cachedSources;

    /**
     * Generic exception class for playlists.
     */
    private static class PlaylistException extends Exception {
        public PlaylistException(String message) {
            super(message);
        }
    }

    /**
     * Exception thrown when the remote playlist is too large to be parsed completely.
     */
    private class PlaylistTooLarge extends PlaylistException {
        public PlaylistTooLarge(String message) {
            super(message);
        }
    }

    /**
     * Exception thrown when the remote playlist format cannot be determined.
     */
    private class PlaylistFormatUnsupported extends PlaylistException {
        public PlaylistFormatUnsupported(String message) {
            super(message);
        }
    }

    /**
     * Exception thrown when too many redirects occurred when retrieving a remote playlist.
     */
    private class PlaylistHasTooManyRedirects extends PlaylistException {
        public PlaylistHasTooManyRedirects(String message) {
            super(message);
        }
    }

    public InternetRadioService() {
        this.cachedSources = new HashMap<>();
    }

    /**
     * Clear the radio source cache.
     */
    public void clearInternetRadioSourceCache() {
        cachedSources.clear();
    }

    /**
     * Clear the radio source cache for the given radio id
     * @param internetRadioId a radio id
     */
    public void clearInternetRadioSourceCache(Integer internetRadioId) {
        if (internetRadioId != null) {
            cachedSources.remove(internetRadioId);
        }
    }

    /**
     * Retrieve a list of sources for the given internet radio.
     *
     * This method caches the sources using the InternetRadio.getId
     * method as a key, until clearInternetRadioSourceCache is called.
     *
     * @param radio an internet radio
     * @return a list of internet radio sources
     */
    public List<InternetRadioSource> getInternetRadioSources(InternetRadio radio) {
        List<InternetRadioSource> sources;
        if (cachedSources.containsKey(radio.getId())) {
            LOG.debug("Got cached sources for internet radio {}!", radio.getStreamUrl());
            sources = cachedSources.get(radio.getId());
        } else {
            LOG.debug("Retrieving sources for internet radio {}...", radio.getStreamUrl());
            try {
                sources = retrieveInternetRadioSources(radio);
                if (sources.isEmpty()) {
                    LOG.warn("No entries found for internet radio {}.", radio.getStreamUrl());
                } else {
                    LOG.info("Retrieved playlist for internet radio {}, got {} sources.", radio.getStreamUrl(), sources.size());
                }
            } catch (Exception e) {
                LOG.error("Failed to retrieve sources for internet radio {}.", radio.getStreamUrl(), e);
                sources = new ArrayList<>();
            }
            cachedSources.put(radio.getId(), sources);
        }
        return sources;
    }

    /**
     * Retrieve a list of sources from the given internet radio
     *
     * This method uses a default maximum limit of PLAYLIST_REMOTE_MAX_LENGTH sources.
     *
     * @param radio an internet radio
     * @return a list of internet radio sources
     */
    private List<InternetRadioSource> retrieveInternetRadioSources(InternetRadio radio) throws Exception {
        return retrieveInternetRadioSources(
            radio,
            PLAYLIST_REMOTE_MAX_LENGTH,
            PLAYLIST_REMOTE_MAX_BYTE_SIZE,
            PLAYLIST_REMOTE_MAX_REDIRECTS
        );
    }

    /**
     * Retrieve a list of sources from the given internet radio.
     *
     * @param radio an internet radio
     * @param maxCount the maximum number of items to read from the remote playlist, or 0 if unlimited
     * @param maxByteSize maximum size of the response, in bytes, or 0 if unlimited
     * @param maxRedirects maximum number of redirects, or 0 if unlimited
     * @return a list of internet radio sources
     */
    private List<InternetRadioSource> retrieveInternetRadioSources(InternetRadio radio, int maxCount, long maxByteSize, int maxRedirects) throws Exception {
        // Retrieve the remote playlist
        String playlistUrl = radio.getStreamUrl();
        LOG.debug("Parsing internet radio playlist at {}...", playlistUrl);

        HttpURLConnection urlConnection = connectToURLWithRedirects(new URL(playlistUrl), maxRedirects);
        List<InternetRadioSource> entries = new ArrayList<>();
        try {
            switch (guessStreamType(urlConnection)) {
                case AUDIO:
                    entries.add(new InternetRadioSource(playlistUrl));
                    break;
                case AUDIO_SHOUTCAST:
                    // Workaround for old Shoutcast radios that behave differently when
                    // retrieved from a browser User Agent.
                    entries.add(new InternetRadioSource(playlistUrl + ";"));
                    break;
                case PLAYLIST:
                    entries.addAll(retrievePlaylist(urlConnection, maxCount, maxByteSize));
                    break;
                case HTML_OR_TEXT:
                    LOG.warn("Playlist format for URL {} likely unsupported (HTML/text)", playlistUrl.toString());
                    entries.addAll(tryRetrievePlaylist(urlConnection, maxCount, maxByteSize));
                    break;
                default:
                    LOG.warn("Playlist format for URL {} unknown", playlistUrl.toString());
                    entries.addAll(tryRetrievePlaylist(urlConnection, maxCount, maxByteSize));
                    break;
            }
        } finally {
            urlConnection.disconnect();
        }

        return entries;
    }

    private List<InternetRadioSource> tryRetrievePlaylist(HttpURLConnection urlConnection, int maxCount, long maxByteSize) throws Exception {

        // First try to retrieve playlist sources
        List<InternetRadioSource> internetRadioSources = null;
        try {
            internetRadioSources = retrievePlaylist(urlConnection, maxCount, maxByteSize);
        } catch (Exception e) {
            LOG.debug("Cannot retrieve playlist from URL {}", urlConnection.getURL());
        }

        // But if that fails in any way, stop now and return a single URL
        if (internetRadioSources == null || internetRadioSources.size() == 0) {
            internetRadioSources = new ArrayList<>();
            internetRadioSources.add(new InternetRadioSource(urlConnection.getURL().toString()));
        }

        return internetRadioSources;
    }

    private List<InternetRadioSource> retrievePlaylist(HttpURLConnection urlConnection, int maxCount, long maxByteSize) throws Exception {

        // Retrieve a populated playlist instance from the given URL
        SpecificPlaylist playlist;
        try (InputStream in = urlConnection.getInputStream()) {
            String contentEncoding = urlConnection.getContentEncoding();
            if (maxByteSize > 0) {
                playlist = SpecificPlaylistFactory.getInstance().readFrom(new BoundedInputStream(in, maxByteSize), contentEncoding);
            } else {
                playlist = SpecificPlaylistFactory.getInstance().readFrom(in, contentEncoding);
            }
        } finally {
            urlConnection.disconnect();
        }
        if (playlist == null) {
            throw new PlaylistFormatUnsupported("Unsupported playlist format " + urlConnection.getURL().toString());
        }

        // Retrieve stream URLs
        List<InternetRadioSource> entries = new ArrayList<>();
        try {
            playlist.toPlaylist().acceptDown(new PlaylistVisitor() {
                @Override
                public void beginVisitPlaylist(Playlist playlist) {

                }

                @Override
                public void endVisitPlaylist(Playlist playlist) {

                }

                @Override
                public void beginVisitParallel(Parallel parallel) {

                }

                @Override
                public void endVisitParallel(Parallel parallel) {

                }

                @Override
                public void beginVisitSequence(Sequence sequence) {

                }

                @Override
                public void endVisitSequence(Sequence sequence) {

                }

                @Override
                public void beginVisitMedia(Media media) throws Exception {
                    // Since we're dealing with remote content, we place a hard
                    // limit on the maximum number of items to load from the playlist,
                    // in order to avoid parsing erroneous data.
                    if (maxCount > 0 && entries.size() >= maxCount) {
                        throw new PlaylistTooLarge("Remote playlist has too many sources (maximum " + maxCount + ")");
                    }
                    URL streamUrl = media.getSource().getURL();
                    if (isShoutcastURL(streamUrl)) {
                        LOG.debug("Got source media at {} (working around ShoutCAST URL)", streamUrl.toString());
                        streamUrl = new URL(streamUrl.toString() + ";");
                    } else {
                        LOG.debug("Got source media at {}", streamUrl.toString());
                    }
                    entries.add(new InternetRadioSource(streamUrl.toString()));
                }

                @Override
                public void endVisitMedia(Media media) {

                }
            });
        } catch (PlaylistTooLarge e) {
            // Ignore if playlist is too large, but truncate the rest and log a warning.
            LOG.warn(e.getMessage());
        }

        return entries;
    }

    /**
     * Retrieve playlist data from a given URL.
     *
     * @param url URL to the remote playlist
     * @param maxByteSize maximum size of the response, in bytes, or 0 if unlimited
     * @param maxRedirects maximum number of redirects, or 0 if unlimited
     * @return the remote playlist data
     */
    protected SpecificPlaylist retrievePlaylist(URL url, long maxByteSize, int maxRedirects) throws IOException, PlaylistException {

        SpecificPlaylist playlist;
        HttpURLConnection urlConnection = connectToURLWithRedirects(url, maxRedirects);
        try (InputStream in = urlConnection.getInputStream()) {
            String contentEncoding = urlConnection.getContentEncoding();
            if (maxByteSize > 0) {
                playlist = SpecificPlaylistFactory.getInstance().readFrom(new BoundedInputStream(in, maxByteSize), contentEncoding);
            } else {
                playlist = SpecificPlaylistFactory.getInstance().readFrom(in, contentEncoding);
            }
        } finally {
            urlConnection.disconnect();
        }
        if (playlist == null) {
            throw new PlaylistFormatUnsupported("Unsupported playlist format " + url.toString());
        }
        return playlist;
    }

    /**
     * Start a new connection to a remote URL, and follow redirects.
     *
     * @param url the remote URL
     * @param maxRedirects maximum number of redirects, or 0 if unlimited
     * @return an open connection
     */
    protected HttpURLConnection connectToURLWithRedirects(URL url, int maxRedirects) throws IOException, PlaylistException {

        int redirectCount = 0;
        URL currentURL = url;

        // Start a first connection.
        HttpURLConnection connection = connectToURL(currentURL);

        // While it redirects, follow redirects in new connections.
        while (connection.getResponseCode() == HttpURLConnection.HTTP_MOVED_PERM ||
               connection.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP ||
               connection.getResponseCode() == HttpURLConnection.HTTP_SEE_OTHER) {

            // Check if redirect count is not too large.
            redirectCount += 1;
            if (maxRedirects > 0 && redirectCount > maxRedirects) {
                connection.disconnect();
                throw new PlaylistHasTooManyRedirects(String.format("Too many redirects (%d) for URL %s", redirectCount, url));
            }

            // Reconnect to the new URL.
            currentURL = new URL(connection.getHeaderField("Location"));
            connection.disconnect();
            connection = connectToURL(currentURL);
        }

        // Return the last connection that did not redirect.
        return connection;
    }

    /**
     * Start a new connection to a remote URL.
     *
     * @param url the remote URL
     * @return an open connection
     */
    protected HttpURLConnection connectToURL(URL url) throws IOException {
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        urlConnection.setAllowUserInteraction(false);
        urlConnection.setConnectTimeout(10000);
        urlConnection.setDoInput(true);
        urlConnection.setDoOutput(false);
        urlConnection.setReadTimeout(60000);
        urlConnection.setUseCaches(true);
        urlConnection.connect();
        return urlConnection;
    }

    /**
     * Try to guess the streaming type supported by a given URL by connecting to it and reading content
     */
    protected StreamType guessStreamType(HttpURLConnection connection) throws IOException {

        URL url = connection.getURL();
        String contentType = connection.getContentType();

        if (contentType == null) {
            LOG.debug("Did not found content type for URL {}", url.toString());
            return StreamType.OTHER;
        }

        if (PLAYLIST_CONTENT_TYPES.contains(contentType)) {
            LOG.debug("Found content type for URL {}: {} (known playlist type)", url.toString(), contentType);
            return StreamType.PLAYLIST;
        } else if (AUDIO_CONTENT_TYPES.contains(contentType)) {
            LOG.debug("Found content type for URL {}: {} (known audio type)", url.toString(), contentType);
            return StreamType.AUDIO;
        } else if (contentType.startsWith("audio/")) {
            LOG.debug("Found content type for URL {}: {} (guessing audio data)", url.toString(), contentType);
            return StreamType.AUDIO;
        } else if (contentType.startsWith("html/") || contentType.startsWith("text/")) {
            LOG.debug("Found content type for URL {}: {} (guessing HTML/text data)", url.toString(), contentType);
            return StreamType.HTML_OR_TEXT;
        } else if ("unknown/unknown".equals(contentType) && isShoutcastURL(url)) {
            LOG.debug("Found ShoutCast protocol on URL {}", url.toString());
            return StreamType.AUDIO_SHOUTCAST;
        } else {
            LOG.debug("Found content type for URL {}: {} (non-streamable data?)", url.toString(), contentType);
            return StreamType.OTHER;
        }
    }

    /**
     * Try to guess if the provided URL responds with old ShoutCast "ICY" headers instead of standard HTTP.
     */
    protected boolean isShoutcastURL(URL url) throws IOException {
        // Try to connect to a recent Shoutcast URL that supports standard HTTP protocols
        try {
            return isRecentShoutcastURL(url);
        // If that failed, try to use an old protocol that responds with "ICY 200 OK".
        } catch (IOException e) {
            return isOldShoutcastURL(url);
        }
    }

    protected boolean isRecentShoutcastURL(URL url) throws IOException {
        LOG.debug("Trying to probe for Shoutcast servers at URL {}", url.toString());
        HttpURLConnection connection = connectToURL(url);
        try {
            if (connection.getHeaderFields().keySet().stream().anyMatch(s -> s.toLowerCase().startsWith("icy"))) {
                return true;
            }
        } finally {
            connection.disconnect();
        }
        return false;
    }

    protected boolean isOldShoutcastURL(URL url) throws IOException {
        LOG.debug("Trying to probe for old Shoutcast servers at URL {}", url.toString());
        String queryString = url.getPath();
        if (url.getQuery() != null) queryString += url.getQuery();
        String req = String.format("GET %s HTTP/1.0\r\nUser-Agent: Airsonic\r\nIcy-MetaData: 1\r\nConnection: keep-alive\r\n\r\n", queryString);

        try (Socket socket = new Socket(url.getHost(), url.getPort())) {
            try (OutputStream os = socket.getOutputStream()) {
                try (InputStream is = socket.getInputStream()) {
                    os.write(req.getBytes());
                    byte[] bytes = new byte[3];
                    is.read(bytes);
                    String header = bytes.toString();
                    if ("ICY".equals(bytes.toString())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
