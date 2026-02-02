package com.travel.agent.service.impl;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.travel.agent.config.GeoapifyConfig;
import com.travel.agent.dto.response.DestinationResponse.DestinationPlaceInfo;
import com.travel.agent.service.GeoapifyService;
import com.travel.agent.dto.response.GeoPlace;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeoapifyServiceImpl implements GeoapifyService {

    private final GeoapifyConfig geoapifyConfig;
    private final Gson gson = new Gson();
    private OkHttpClient client;

    /**
     * 初始化 HTTP 客户端
     */
    private OkHttpClient getClient() {
        if (client == null) {
            client = new OkHttpClient.Builder()
                    .connectTimeout(geoapifyConfig.getTimeout(), TimeUnit.MILLISECONDS)
                    .readTimeout(geoapifyConfig.getTimeout(), TimeUnit.MILLISECONDS)
                    .build();
        }
        return client;
    }

    @Override
    public String getDestinationPhoto(String destinationName, Double latitude, Double longitude) {
        // 为了性能，直接返回静态地图 URL，避免额外的 HTTP 查询
        if (latitude != null && longitude != null) {
            // 使用较小分辨率以加快加载
            return String.format(
                "https://maps.geoapify.com/v1/staticmap?style=osm-bright&width=480&height=320&center=lonlat:%f,%f&zoom=11&marker=lonlat:%f,%f;color:%%23ff0000;size:small&apiKey=%s",
                longitude, latitude, longitude, latitude, geoapifyConfig.getApiKey()
            );
        }
        return getDefaultImageUrl(destinationName);
    }

    @Override
    public DestinationPlaceInfo getPlaceInfo(String destinationName, Double latitude, Double longitude) {
        try {
            String url = String.format(
                "%s/places?categories=tourism&filter=circle:%f,%f,5000&limit=1&apiKey=%s",
                geoapifyConfig.getBaseUrl(),
                longitude,
                latitude,
                geoapifyConfig.getApiKey()
            );

            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = getClient().newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return null;
                }

                String responseBody = response.body().string();
                JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
                
                JsonArray features = jsonResponse.getAsJsonArray("features");
                if (features != null && features.size() > 0) {
                    JsonObject properties = features.get(0)
                            .getAsJsonObject()
                            .getAsJsonObject("properties");
                    
                    DestinationPlaceInfo info = new DestinationPlaceInfo();
                    
                    if (properties != null && properties.has("name")) {
                        info.setPlaceName(properties.get("name").getAsString());
                    }
                    
                    if (properties != null && properties.has("categories")) {
                        JsonArray categories = properties.getAsJsonArray("categories");
                        if (categories.size() > 0) {
                            info.setCategory(categories.get(0).getAsString());
                        }
                    }
                    
                    return info;
                }
            }

        } catch (IOException e) {
            log.error("Failed to get place info for {}", destinationName, e);
        }
        
        return null;
    }

    @Override
    @Cacheable(cacheNames = "nearbyPlaces", key = "T(java.util.Objects).hash(#latitude,#longitude,#radiusKm,#limit,#categories)", sync = true)
    public java.util.List<GeoPlace> searchNearbyPlaces(Double latitude, Double longitude, Double radiusKm, int limit, java.util.List<String> categories) {
        java.util.List<GeoPlace> results = new java.util.ArrayList<>();
        try {
            String categoryParam = categories == null || categories.isEmpty()
                    ? "tourism,entertainment,catering"
                    : String.join(",", categories);
            long radiusMeters = Math.round(radiusKm * 1000);
            String url = String.format(
                "%s/places?categories=%s&filter=circle:%f,%f,%d&limit=%d&apiKey=%s",
                geoapifyConfig.getBaseUrl(),
                categoryParam,
                longitude,
                latitude,
                radiusMeters,
                limit,
                geoapifyConfig.getApiKey()
            );

            Request request = new Request.Builder().url(url).get().build();
            try (Response response = getClient().newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return results;
                }
                String responseBody = response.body().string();
                JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
                JsonArray features = jsonResponse.getAsJsonArray("features");
                if (features == null) return results;

                for (int i = 0; i < features.size(); i++) {
                    JsonObject feature = features.get(i).getAsJsonObject();
                    JsonObject properties = feature.getAsJsonObject("properties");
                    JsonObject geometry = feature.getAsJsonObject("geometry");
                    if (properties == null || geometry == null) continue;

                    GeoPlace place = new GeoPlace();
                    place.setId(getString(properties, "place_id", "geo_" + i));
                    place.setName(getString(properties, "name", getString(properties, "address_line1", "Unknown")));

                    java.util.List<String> tags = new java.util.ArrayList<>();
                    if (properties.has("categories") && properties.get("categories").isJsonArray()) {
                        JsonArray cats = properties.getAsJsonArray("categories");
                        for (int c = 0; c < cats.size(); c++) {
                            tags.add(cats.get(c).getAsString());
                        }
                    }
                    place.setTags(tags);

                    // Map category to a coarse type
                    place.setType(mapCategoriesToType(tags));

                    // Coordinates
                    if (properties.has("lat") && properties.has("lon")) {
                        place.setLatitude(properties.get("lat").getAsDouble());
                        place.setLongitude(properties.get("lon").getAsDouble());
                    } else if (geometry.has("coordinates") && geometry.get("coordinates").isJsonArray()) {
                        JsonArray coords = geometry.getAsJsonArray("coordinates");
                        if (coords.size() >= 2) {
                            place.setLongitude(coords.get(0).getAsDouble());
                            place.setLatitude(coords.get(1).getAsDouble());
                        }
                    }

                    // Opening hours (best effort)
                    place.setOpeningHours(getString(properties, "opening_hours", "09:00-18:00"));

                    // Rating & priceLevel (Geoapify doesn't always provide) -> defaults
                    place.setRating(properties.has("rating") ? properties.get("rating").getAsDouble() : 4.2);
                    place.setPriceLevel(2);
                    
                    // Generate price range based on type and price level
                    place.setPriceRange(generatePriceRangeFromType(mapCategoriesToType(tags), 2));

                    // Description fallback from categories
                    place.setDescription(getString(properties, "formatted", place.getName()));

                    // Use static map/placeholder to avoid N+1 detail calls here
                    place.setImageUrl(getDestinationPhoto(place.getName(), place.getLatitude(), place.getLongitude()));

                    results.add(place);
                }
            }
        } catch (Exception e) {
            log.error("Failed to search nearby places from Geoapify", e);
        }
        return results;
    }

    private static String getString(JsonObject obj, String key, String def) {
        return obj != null && obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : def;
    }

    private static String mapCategoriesToType(java.util.List<String> categories) {
        if (categories == null) return "attraction";
        for (String c : categories) {
            if (c.contains("catering.restaurant")) return "restaurant";
            if (c.contains("catering.cafe")) return "cafe";
            if (c.contains("commercial.market")) return "market";
            if (c.contains("tourism")) return "attraction";
        }
        return "attraction";
    }

    @Cacheable(cacheNames = "placeImage", key = "#placeId", sync = true)
    public String getPlaceImageUrl(String placeId, String placeName, Double latitude, Double longitude) {
        try {
            // First try to get image from Geoapify Places API details endpoint
            String url = String.format(
                "%s/places/details?place_id=%s&apiKey=%s",
                geoapifyConfig.getBaseUrl(),
                placeId,
                geoapifyConfig.getApiKey()
            );

            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = getClient().newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
                    
                    JsonObject result = jsonResponse.getAsJsonObject("result");
                    if (result != null) {
                        // Try to get image from various sources
                        String imageUrl = extractImageFromPlaceDetails(result);
                        if (imageUrl != null && !imageUrl.isEmpty()) {
                            log.info("Found real image for place {}: {}", placeName, imageUrl);
                            return imageUrl;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get image from Geoapify for place {}: {}", placeName, e.getMessage());
        }

        // Fallback: Generate image based on place type and location
        return generateContextualImageUrl(placeName, latitude, longitude);
    }

    @Override
    public java.util.Map<String, java.math.BigDecimal> geocodeAddress(String address) {
        try {
            String url = String.format(
                    "%s/geocode/search?text=%s&limit=1&apiKey=%s",
                    geoapifyConfig.getBaseUrl().replace("/v2", "/v1"),
                    java.net.URLEncoder.encode(address, "UTF-8"),
                    geoapifyConfig.getApiKey()
            );

            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = getClient().newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.warn("Geocoding failed: status={}", response.code());
                    return null;
                }

                String responseBody = response.body().string();
                JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);

                JsonArray features = jsonResponse.getAsJsonArray("features");
                if (features != null && features.size() > 0) {
                    JsonObject geometry = features.get(0).getAsJsonObject().getAsJsonObject("geometry");
                    if (geometry != null && geometry.has("coordinates")) {
                        JsonArray coordinates = geometry.getAsJsonArray("coordinates");
                        java.math.BigDecimal longitude = coordinates.get(0).getAsBigDecimal();
                        java.math.BigDecimal latitude = coordinates.get(1).getAsBigDecimal();

                        java.util.Map<String, java.math.BigDecimal> result = new java.util.HashMap<>();
                        result.put("latitude", latitude);
                        result.put("longitude", longitude);
                        return result;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to geocode address: {}", address, e);
        }
        return null;
    }

    /**
     * Extract image URL from Geoapify place details
     */
    private String extractImageFromPlaceDetails(JsonObject placeDetails) {
        // Try different image sources in order of preference
        
        // 1. Try to get image from properties.datasource.raw
        if (placeDetails.has("properties")) {
            JsonObject properties = placeDetails.getAsJsonObject("properties");
            if (properties.has("datasource")) {
                JsonObject datasource = properties.getAsJsonObject("datasource");
                if (datasource.has("raw")) {
                    JsonObject raw = datasource.getAsJsonObject("raw");
                    // Check for common image fields
                    String[] imageFields = {"image", "photo", "picture", "thumbnail", "logo"};
                    for (String field : imageFields) {
                        if (raw.has(field) && !raw.get(field).isJsonNull()) {
                            String imageUrl = raw.get(field).getAsString();
                            if (isValidImageUrl(imageUrl)) {
                                return imageUrl;
                            }
                        }
                    }
                }
            }
        }

        // 2. Try to get image from osm metadata
        if (placeDetails.has("properties")) {
            JsonObject properties = placeDetails.getAsJsonObject("properties");
            String[] osmImageFields = {"image", "photo", "picture"};
            for (String field : osmImageFields) {
                if (properties.has(field) && !properties.get(field).isJsonNull()) {
                    String imageUrl = properties.get(field).getAsString();
                    if (isValidImageUrl(imageUrl)) {
                        return imageUrl;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Check if URL is a valid image URL
     */
    private boolean isValidImageUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        
        // Check if it's a valid HTTP/HTTPS URL
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return false;
        }
        
        // Check for common image extensions
        String[] imageExtensions = {".jpg", ".jpeg", ".png", ".gif", ".webp", ".svg"};
        String lowerUrl = url.toLowerCase();
        for (String ext : imageExtensions) {
            if (lowerUrl.contains(ext)) {
                return true;
            }
        }
        
        // Check for common image hosting services
        String[] imageServices = {"flickr.com", "imgur.com", "unsplash.com", "pexels.com", "pixabay.com"};
        for (String service : imageServices) {
            if (lowerUrl.contains(service)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Generate contextual image URL based on place type and location
     */
    private String generateContextualImageUrl(String placeName, Double latitude, Double longitude) {
        // Use Picsum Photos with deterministic seed for consistent images per place
        try {
            // Create a deterministic seed based on place name for consistent images
            int seed = Math.abs(placeName.hashCode());
            return String.format("https://picsum.photos/seed/%s/600/400", seed);
        } catch (Exception e) {
            log.warn("Failed to generate Picsum URL for {}", placeName);
        }
        
        // Alternative: Use Lorem Picsum with category-based images
        try {
            return String.format("https://picsum.photos/600/400?random=%d", Math.abs(placeName.hashCode()));
        } catch (Exception e) {
            log.warn("Failed to generate Lorem Picsum URL for {}", placeName);
        }
        
        // Final fallback to static map
        if (latitude != null && longitude != null) {
            return String.format(
                "https://maps.geoapify.com/v1/staticmap?style=osm-bright&width=600&height=400&center=lonlat:%f,%f&zoom=15&marker=lonlat:%f,%f;color:%%23ff0000;size:medium&apiKey=%s",
                longitude, latitude, longitude, latitude, geoapifyConfig.getApiKey()
            );
        }
        
        return getDefaultImageUrl(placeName);
    }

    /**
     * Generate price range based on place type and price level
     */
    private String generatePriceRangeFromType(String type, int priceLevel) {
        switch (type) {
            case "restaurant":
                switch (priceLevel) {
                    case 1: return "$10-20"; // Budget
                    case 2: return "$20-40"; // Moderate
                    case 3: return "$40-80"; // Expensive
                    default: return "$20-40";
                }
            case "cafe":
                switch (priceLevel) {
                    case 1: return "$5-12"; // Budget
                    case 2: return "$12-25"; // Moderate
                    case 3: return "$25-50"; // Expensive
                    default: return "$12-25";
                }
            case "market":
                switch (priceLevel) {
                    case 1: return "$5-15"; // Budget
                    case 2: return "$15-30"; // Moderate
                    case 3: return "$30-60"; // Expensive
                    default: return "$15-30";
                }
            case "attraction":
                switch (priceLevel) {
                    case 1: return "Free-$15"; // Budget
                    case 2: return "$15-30"; // Moderate
                    case 3: return "$30-60"; // Expensive
                    default: return "$15-30";
                }
            default:
                return "$15-30";
        }
    }

    /**
     * 获取默认图片 URL（占位符）
     */
    private String getDefaultImageUrl(String destinationName) {
        return "https://via.placeholder.com/600x400/667eea/ffffff?text=" + 
               destinationName.replace(" ", "+");
    }
}
