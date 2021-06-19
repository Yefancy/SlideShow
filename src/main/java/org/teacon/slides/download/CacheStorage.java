package org.teacon.slides.download;

import com.google.common.collect.Streams;
import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.util.Util;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.Header;
import org.apache.http.StatusLine;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.cache.HttpCacheStorage;
import org.apache.http.client.cache.HttpCacheUpdateCallback;
import org.apache.http.client.cache.Resource;
import org.apache.http.client.utils.DateUtils;
import org.apache.http.impl.client.cache.FileResource;
import org.apache.http.message.BasicLineParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.teacon.slides.SlideShow;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
final class CacheStorage implements HttpCacheStorage {
    private static final Logger LOGGER = LogManager.getLogger(SlideShow.class);
    private static final Marker MARKER = MarkerManager.getMarker("Downloader");

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private final Path parent;
    private final LinkedHashMap<String, Pair<Path, HttpCacheEntry>> entries;

    private Path resolveKeysPath() {
        return this.parent.resolve("storage-keys.json");
    }

    private Pair<Path, HttpCacheEntry> normalize(HttpCacheEntry entry) throws IOException {
        byte[] bytes = IOUtils.toByteArray(entry.getResource().getInputStream());
        Path path = Files.write(this.parent.resolve(allocateImageName(bytes)), bytes);
        return Pair.of(path, new HttpCacheEntry(entry.getRequestDate(), entry.getResponseDate(),
                entry.getStatusLine(), entry.getAllHeaders(), new FileResource(path.toFile()), entry.getVariantMap()));
    }

    private static String allocateImageName(byte[] bytes) {
        // noinspection UnstableApiUsage
        String hashString = Hashing.sha1().hashBytes(bytes).toString();
        try (ByteArrayInputStream stream = new ByteArrayInputStream(bytes)) {
            try (ImageInputStream imageStream = ImageIO.createImageInputStream(stream)) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(imageStream);
                if (readers.hasNext()) {
                    String[] suffixes = readers.next().getOriginatingProvider().getFileSuffixes();
                    if (suffixes.length > 0) {
                        return hashString + "." + suffixes[0].toLowerCase(Locale.ENGLISH);
                    }
                }
            }
            return hashString;
        } catch (IOException e) {
            return hashString;
        }
    }

    private synchronized void save() {
        try {
            JsonObject root = new JsonObject();
            for (Map.Entry<String, Pair<Path, HttpCacheEntry>> entry : this.entries.entrySet()) {
                Path filePath = entry.getValue().getKey();
                HttpCacheEntry cacheEntry = entry.getValue().getValue();
                root.add(entry.getKey(), Util.make(new JsonObject(), child -> {
                    child.addProperty("request_date", DateUtils.formatDate(cacheEntry.getRequestDate()));
                    child.addProperty("response_date", DateUtils.formatDate(cacheEntry.getResponseDate()));
                    child.addProperty("status_line", cacheEntry.getStatusLine().toString());
                    child.add("headers", Util.make(new JsonArray(), array -> {
                        for (Header header : cacheEntry.getAllHeaders()) {
                            array.add(header.toString());
                        }
                    }));
                    child.addProperty("resource", filePath.toString());
                    child.add("variant_map", Util.make(new JsonObject(), object -> {
                        for (Map.Entry<String, String> variantEntry : cacheEntry.getVariantMap().entrySet()) {
                            object.addProperty(variantEntry.getKey(), variantEntry.getValue());
                        }
                    }));
                }));
            }
            OpenOption[] options = {StandardOpenOption.WRITE, StandardOpenOption.CREATE};
            GSON.toJson(root, Files.newBufferedWriter(this.resolveKeysPath(), StandardCharsets.UTF_8, options));
        } catch (Exception e) {
            LOGGER.warn(MARKER, "Failed to save cache storage. ", e);
            this.entries.clear();
        }
    }

    private synchronized void load(Path path) {
        try {
            JsonObject root = GSON.fromJson(Files.newBufferedReader(path, StandardCharsets.UTF_8), JsonObject.class);
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                JsonObject child = entry.getValue().getAsJsonObject();
                Date requestDate = DateUtils.parseDate(child.get("request_date").getAsString());
                Date responseDate = DateUtils.parseDate(child.get("response_date").getAsString());
                StatusLine statusLine = BasicLineParser.parseStatusLine(child.get("status_line").getAsString(), null);
                // noinspection UnstableApiUsage
                Header[] headers = Streams.stream(child.get("headers").getAsJsonArray())
                        .map(elem -> BasicLineParser.parseHeader(elem.getAsString(), null)).toArray(Header[]::new);
                Path filePath = Paths.get(child.get("resource").getAsString());
                Resource resource = new FileResource(filePath.toFile());
                Map<String, String> variantMap = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> variantEntry : child.get("variant_map").getAsJsonObject().entrySet()) {
                    variantMap.put(variantEntry.getKey(), variantEntry.getValue().getAsString());
                }
                HttpCacheEntry cacheEntry = new HttpCacheEntry(requestDate, responseDate, statusLine, headers, resource, variantMap);
                this.entries.put(entry.getKey(), Pair.of(filePath, cacheEntry));
            }
        } catch (Exception e) {
            LOGGER.warn(MARKER, "Failed to load cache storage. ", e);
            this.entries.clear();
        }
    }

    public CacheStorage(Path parentPath) {
        this.parent = parentPath;
        this.entries = new LinkedHashMap<>();
        Path keysPath = this.resolveKeysPath();
        if (Files.exists(keysPath)) {
            this.load(keysPath);
        } else if (LegacyStorage.loadLegacy(parentPath, this.entries)) {
            this.save();
        }
    }

    @Nullable
    public synchronized HttpCacheEntry getEntry(String url) {
        Pair<Path, HttpCacheEntry> pair = this.entries.get(url);
        return pair != null ? pair.getValue() : null;
    }

    public synchronized void putEntry(String url, HttpCacheEntry entry) throws IOException {
        Pair<Path, HttpCacheEntry> normalizedEntry = this.normalize(entry);
        this.entries.put(url, normalizedEntry);
        this.save();
    }

    public synchronized void removeEntry(String url) {
        this.entries.remove(url);
        this.save();
    }

    public synchronized void updateEntry(String url, HttpCacheUpdateCallback cb) throws IOException {
        Pair<Path, HttpCacheEntry> pair = this.entries.get(url);
        this.entries.put(url, this.normalize(cb.update(pair != null ? pair.getValue() : null)));
        this.save();
    }
}
