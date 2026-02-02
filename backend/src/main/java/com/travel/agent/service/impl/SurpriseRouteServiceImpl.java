package com.travel.agent.service.impl;

import com.travel.agent.dto.request.SurpriseRouteRequest;
import com.travel.agent.dto.response.SurpriseRouteResponse;
import com.travel.agent.service.SurpriseRouteService;
import com.travel.agent.service.UserPreferencesService;
import com.travel.agent.service.GeoapifyService;
import com.travel.agent.dto.response.GeoPlace;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executor;

/**
 * Instant surprise route service implementation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SurpriseRouteServiceImpl implements SurpriseRouteService {

    private final UserPreferencesService userPreferencesService;
    private final GeoapifyService geoapifyService;
    private final Executor taskExecutor;

    // Removed hardcoded mock points; data will be fetched from Geoapify dynamically

    @Override
    public SurpriseRouteResponse generateSurpriseRoute(SurpriseRouteRequest request) {
        log.info("Generating surprise route for location: {}, {}", request.getLatitude(), request.getLongitude());
        
        // 1. Get user preferences (if available)
        List<String> userInterests = getUserInterests(request.getUserId());
        
        // 2. Single fetch with larger radius, then local multi-stage filtering with progressive radius expansion
        double originalRadius = request.getRadius() != null ? request.getRadius() : 5.0;
        double fetchRadius = Math.max(originalRadius, 15.0);
        request.setRadius(fetchRadius);
        List<MockPoint> sourcePoints = fetchPointsFromApi(request, userInterests);
        
        // Stage 1: strict filter with original radius
        request.setRadius(originalRadius);
        List<MockPoint> availablePoints = filterAvailablePoints(request, userInterests, sourcePoints);

        // Stage 2: loose filter with expanded radius if needed
        if (availablePoints.isEmpty()) {
            double expandedRadius = Math.min(originalRadius * 2, 10.0);
            log.warn("No points after strict filter. Trying loose filter with radius {} km.", expandedRadius);
            request.setRadius(expandedRadius);
            availablePoints = looseFilter(request, userInterests, sourcePoints);
        }

        // Stage 3: distance-only fallback with max radius if still empty
        if (availablePoints.isEmpty()) {
            log.warn("Loose filter empty. Final fallback to distance-only with radius {} km.", fetchRadius);
            request.setRadius(fetchRadius);
            availablePoints = distanceOnlyFilter(request, sourcePoints);
        }
        
        // Restore original radius for response display
        request.setRadius(originalRadius);

        // 3. Randomly select recommendation points
        List<MockPoint> selectedPoints = selectRandomPoints(availablePoints, request.getPointCount());
        
        // 4. Generate route
        SurpriseRouteResponse response = buildRouteResponse(selectedPoints, request);
        // 5. Concurrently enrich images for selected points (best-effort, 2s timeout)
        enrichSelectedPointImagesAsync(response);
        return response;
    }

    @Override
    public SurpriseRouteResponse regenerateSurpriseRoute(SurpriseRouteRequest request, List<String> excludePointIds) {
        log.info("Regenerating surprise route, excluding points: {}", excludePointIds);
        
        // 1. Get user preferences
        List<String> userInterests = getUserInterests(request.getUserId());
        
        // 2. Single fetch with larger radius, then local filtering (excluding visited ones)
        double originalRadius = request.getRadius() != null ? request.getRadius() : 5.0;
        double fetchRadius = Math.max(originalRadius, 15.0);
        request.setRadius(fetchRadius);
        List<MockPoint> sourcePoints = fetchPointsFromApi(request, userInterests);
        
        // Use expanded radius for filtering to avoid empty results
        double filterRadius = Math.min(originalRadius * 2, 10.0);
        request.setRadius(filterRadius);
        List<MockPoint> availablePoints = filterAvailablePoints(request, userInterests, sourcePoints);
        availablePoints.removeIf(point -> excludePointIds.contains(point.id));
        
        // Restore original radius for response display
        request.setRadius(originalRadius);
        
        // 3. Randomly select recommendation points
        List<MockPoint> selectedPoints = selectRandomPoints(availablePoints, request.getPointCount());
        
        // 4. Generate route
        SurpriseRouteResponse response = buildRouteResponse(selectedPoints, request);
        // 5. Concurrently enrich images for selected points (best-effort, 2s timeout)
        enrichSelectedPointImagesAsync(response);
        return response;
    }

    /**
     * Get user interest preferences
     */
    private List<String> getUserInterests(Long userId) {
        if (userId == null) {
            return Arrays.asList("coffee", "nature", "culture"); // Default interests
        }
        
        try {
            var preferences = userPreferencesService.findByUserId(userId);
            if (preferences != null && preferences.getInterests() != null) {
                return userPreferencesService.parseInterests(preferences.getInterests());
            }
        } catch (Exception e) {
            log.warn("Failed to get user preferences for user {}: {}", userId, e.getMessage());
        }
        
        return Arrays.asList("coffee", "nature", "culture"); // Default interests
    }

    /**
     * Filter available recommendation points
     */
    private List<MockPoint> filterAvailablePoints(SurpriseRouteRequest request, List<String> userInterests, List<MockPoint> sourcePoints) {
        List<MockPoint> filtered = new ArrayList<>();

        int distancePass = 0;
        int openPass = 0;
        int interestPass = 0;
        double radius = request.getRadius() != null ? request.getRadius() : 5.0;

        for (MockPoint point : sourcePoints) {
            // 1. Distance filtering
            double distance = calculateDistance(
                request.getLatitude(), request.getLongitude(),
                point.latitude, point.longitude
            );
            if (distance > radius) {
                continue;
            }
            distancePass++;

            // 2. Opening hours filtering
            if (!isCurrentlyOpen(point.openingHours)) {
                continue;
            }
            openPass++;

            // 3. Interest matching filtering
            if (hasMatchingInterests(point.tags, userInterests)) {
                filtered.add(point);
                interestPass++;
            }
        }

        log.info("Filter stats - radius={}km, distancePass={}, openPass={}, interestPass={}, final={}"
                + ", userInterests={}",
                radius, distancePass, openPass, interestPass, filtered.size(), userInterests);

        // If empty, don't do fallback here, let caller handle it uniformly
        return filtered;
    }

    /**
     * Distance-only filtering (fallback)
     */
    private List<MockPoint> distanceOnlyFilter(SurpriseRouteRequest request, List<MockPoint> sourcePoints) {
        List<MockPoint> list = new ArrayList<>();
        double radius = request.getRadius() != null ? request.getRadius() : 5.0;
        for (MockPoint point : sourcePoints) {
            double distance = calculateDistance(
                request.getLatitude(), request.getLongitude(),
                point.latitude, point.longitude
            );
            if (distance <= radius) {
                list.add(point);
            }
        }
        log.info("Distance-only fallback - radius={}km, candidates={}", radius, list.size());
        return list;
    }
    
    /**
     * Loose filtering (distance + loose opening hours)
     */
    private List<MockPoint> looseFilter(SurpriseRouteRequest request, List<String> userInterests, List<MockPoint> sourcePoints) {
        List<MockPoint> list = new ArrayList<>();
        double radius = request.getRadius() != null ? request.getRadius() : 5.0;
        
        int distancePass = 0;
        int looseOpenPass = 0;
        int looseInterestPass = 0;
        
        for (MockPoint point : sourcePoints) {
            // Distance filtering
            double distance = calculateDistance(
                request.getLatitude(), request.getLongitude(),
                point.latitude, point.longitude
            );
            if (distance > radius) {
                continue;
            }
            distancePass++;
            
            // Loose opening hours filtering
            if (!isCurrentlyOpenLoose(point.openingHours)) {
                continue;
            }
            looseOpenPass++;
            
            // Loose interest matching
            if (hasMatchingInterests(point.tags, userInterests)) {
                list.add(point);
                looseInterestPass++;
            }
        }
        
        log.info("Loose filter stats - radius={}km, distancePass={}, looseOpenPass={}, looseInterestPass={}, final={}",
                radius, distancePass, looseOpenPass, looseInterestPass, list.size());
        
        return list;
    }

    /**
     * Fetch points from Geoapify API and map to internal MockPoint structure
     */
    private List<MockPoint> fetchPointsFromApi(SurpriseRouteRequest request, List<String> userInterests) {
        double radius = request.getRadius() != null ? request.getRadius() : 5.0;
        int count = request.getPointCount() > 0 ? request.getPointCount() * 3 : 12; // fetch extra for filtering
        List<String> categories = mapInterestsToCategories(userInterests);

        List<GeoPlace> places = geoapifyService.searchNearbyPlaces(
                request.getLatitude(), request.getLongitude(), radius, count, categories);

        List<MockPoint> result = new ArrayList<>();
        for (GeoPlace p : places) {
            result.add(new MockPoint(
                    p.getId(),
                    p.getName(),
                    p.getType(),
                    p.getDescription(),
                    p.getLatitude(),
                    p.getLongitude(),
                    filterAndImproveTags(p.getTags(), p.getName(), p.getType()),
                    p.getRating() != null ? p.getRating() : 4.2,
                    p.getPriceLevel() != null ? p.getPriceLevel() : 2,
                    p.getPriceRange() != null ? p.getPriceRange() : generatePriceRange(p.getType(), p.getPriceLevel()),
                    p.getOpeningHours() != null ? p.getOpeningHours() : "09:00-18:00",
                    p.getImageUrl() != null ? p.getImageUrl() : ""
            ));
        }
        return result;
    }

    private List<String> mapInterestsToCategories(List<String> interests) {
        if (interests == null || interests.isEmpty()) {
            return Arrays.asList("tourism.attraction","catering.restaurant","catering.cafe","commercial.market");
        }
        Set<String> set = new LinkedHashSet<>();
        for (String interest : interests) {
            switch (interest.toLowerCase()) {
                case "coffee":
                case "cafe":
                    set.add("catering.cafe");
                    break;
                case "food":
                case "restaurant":
                    set.add("catering.restaurant");
                    break;
                case "market":
                case "shopping":
                    set.add("commercial.market");
                    break;
                case "nature":
                case "culture":
                default:
                    set.add("tourism.attraction");
            }
        }
        if (set.isEmpty()) {
            set.add("tourism.attraction");
        }
        return new ArrayList<>(set);
    }

    /**
     * Randomly select recommendation points
     */
    private List<MockPoint> selectRandomPoints(List<MockPoint> availablePoints, int count) {
        if (availablePoints.size() <= count) {
            return new ArrayList<>(availablePoints);
        }
        
        List<MockPoint> shuffled = new ArrayList<>(availablePoints);
        Collections.shuffle(shuffled);
        return shuffled.subList(0, count);
    }

    /**
     * Build route response
     */
    private SurpriseRouteResponse buildRouteResponse(List<MockPoint> points, SurpriseRouteRequest request) {
        String routeId = "route_" + System.currentTimeMillis();
        String routeName = generateRouteName(points);
        String description = generateRouteDescription(points);
        
        // Calculate total duration and distance
        int totalDuration = points.stream().mapToInt(MockPoint::getEstimatedStayTime).sum();
        double totalDistance = calculateTotalDistance(points, request);
        
        // Build recommendation points list
        List<SurpriseRouteResponse.RoutePoint> routePoints = new ArrayList<>();
        for (MockPoint point : points) {
            double distance = calculateDistance(
                request.getLatitude(), request.getLongitude(),
                point.latitude, point.longitude
            );
            
            routePoints.add(SurpriseRouteResponse.RoutePoint.builder()
                .pointId(point.id)
                .name(point.name)
                .type(point.type)
                .description(point.description)
                .latitude(BigDecimal.valueOf(point.latitude))
                .longitude(BigDecimal.valueOf(point.longitude))
                .distance(round(distance, 2))
                .estimatedStayTime(point.getEstimatedStayTime())
                .openingHours(point.openingHours)
                .tags(point.tags)
                .imageUrl(point.imageUrl)
                .rating(point.rating)
                .priceLevel(point.priceLevel)
                .priceRange(point.priceRange)
                .build());
        }
        
        // Generate route coordinates (simplified version, should use path planning API in practice)
        List<SurpriseRouteResponse.Coordinate> routeCoordinates = generateRouteCoordinates(points, request);
        
        return SurpriseRouteResponse.builder()
            .routeId(routeId)
            .routeName(routeName)
            .description(description)
            .estimatedDuration(totalDuration)
            .estimatedDistance(round(totalDistance, 2))
            .points(routePoints)
            .routeCoordinates(routeCoordinates)
            .build();
    }

    /**
     * Enrich images for selected points concurrently with timeout (best-effort)
     */
    private void enrichSelectedPointImagesAsync(SurpriseRouteResponse response) {
        List<SurpriseRouteResponse.RoutePoint> points = response.getPoints();
        if (points == null || points.isEmpty()) return;
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (SurpriseRouteResponse.RoutePoint p : points) {
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    String img = geoapifyService.getPlaceImageUrl(
                            p.getPointId(), p.getName(),
                            p.getLatitude() != null ? p.getLatitude().doubleValue() : null,
                            p.getLongitude() != null ? p.getLongitude().doubleValue() : null);
                    if (img != null && !img.isEmpty()) {
                        p.setImageUrl(img);
                    }
                } catch (Exception ignored) {}
            }, taskExecutor));
        }
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .orTimeout(2, TimeUnit.SECONDS)
                    .exceptionally(ex -> null)
                    .join();
        } catch (Exception ignored) {}
    }

    /**
     * Calculate distance between two points (in kilometers)
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Earth radius in kilometers
        
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return R * c;
    }

    /**
     * Calculate total route distance
     */
    private double calculateTotalDistance(List<MockPoint> points, SurpriseRouteRequest request) {
        if (points.isEmpty()) return 0.0;
        
        double totalDistance = 0.0;
        
        // From user location to first point
        totalDistance += calculateDistance(
            request.getLatitude(), request.getLongitude(),
            points.get(0).latitude, points.get(0).longitude
        );
        
        // Distance between points
        for (int i = 0; i < points.size() - 1; i++) {
            totalDistance += calculateDistance(
                points.get(i).latitude, points.get(i).longitude,
                points.get(i + 1).latitude, points.get(i + 1).longitude
            );
        }
        
        return totalDistance;
    }

    /**
     * Check if currently open (optimized: more lenient time checking)
     */
    private boolean isCurrentlyOpen(String openingHours) {
        if (openingHours == null || openingHours.isBlank()) {
            return true; // default open
        }

        String oh = openingHours.trim();
        String lower = oh.toLowerCase();

        // Common 24h variants
        if ("Open 24 hours".equals(oh) || "24/7".equals(lower) || "24h".equals(lower)) {
            return true;
        }

        // Approximate handling for sunrise-sunset and sunset-sunrise
        int sunriseIdx = lower.indexOf("sunrise");
        int sunsetIdx = lower.indexOf("sunset");
        if (sunriseIdx >= 0 && sunsetIdx >= 0) {
            LocalTime now = LocalTime.now();
            if (sunriseIdx < sunsetIdx) {
                // "sunrise-sunset": day time 06:00-18:00
                LocalTime openTime = LocalTime.of(6, 0);
                LocalTime closeTime = LocalTime.of(18, 0);
                return !now.isBefore(openTime) && !now.isAfter(closeTime);
            } else {
                // "sunset-sunrise": night time 18:00-06:00
                LocalTime openTime = LocalTime.of(18, 0);
                LocalTime closeTime = LocalTime.of(6, 0);
                return now.isAfter(openTime) || now.isBefore(closeTime);
            }
        }

        try {
            LocalTime now = LocalTime.now();
            String[] times = oh.split("-");
            if (times.length == 2) {
                LocalTime openTime = LocalTime.parse(times[0].trim());
                LocalTime closeTime = LocalTime.parse(times[1].trim());

                // Handle cross-day cases
                if (closeTime.isBefore(openTime)) {
                    return now.isAfter(openTime) || now.isBefore(closeTime);
                } else {
                    return now.isAfter(openTime) && now.isBefore(closeTime);
                }
            }
        } catch (Exception ignored) {
            // keep silent to avoid noisy logs for non-standard formats like "sunrise-sunset"
        }

        return true; // Default open
    }
    
    /**
     * Loose opening hours check (for fallback strategy)
     */
    private boolean isCurrentlyOpenLoose(String openingHours) {
        if ("Open 24 hours".equals(openingHours)) {
            return true;
        }
        
        // Loose strategy: consider open unless explicitly closed time period
        LocalTime now = LocalTime.now();
        
        // If it's late night period (23:00-06:00), most places are closed, but some are 24-hour
        if (now.isAfter(LocalTime.of(23, 0)) || now.isBefore(LocalTime.of(6, 0))) {
            return false; // Most places closed during late night
        }
        
        // Other times are relatively loose
        return true;
    }

    /**
     * Check interest matching (optimized: more lenient matching strategy)
     */
    private boolean hasMatchingInterests(List<String> pointTags, List<String> userInterests) {
        if (userInterests.isEmpty()) return true;
        
        // Direct matching
        boolean directMatch = pointTags.stream().anyMatch(userInterests::contains);
        if (directMatch) return true;
        
        // Loose matching: if user interests contain general terms, lower matching threshold
        List<String> generalInterests = Arrays.asList("culture", "food", "nature", "relaxation");
        boolean hasGeneralInterest = userInterests.stream().anyMatch(generalInterests::contains);
        
        if (hasGeneralInterest) {
            // If there are general interests, match any place with tags
            return !pointTags.isEmpty();
        }
        
        return false;
    }

    /**
     * Generate route name
     */
    private String generateRouteName(List<MockPoint> points) {
        String[] adjectives = {"Explore", "Discover", "Stroll", "Taste", "Experience"};
        String[] nouns = {"Sydney", "City", "Culture", "Food", "Art"};
        
        String adjective = adjectives[ThreadLocalRandom.current().nextInt(adjectives.length)];
        String noun = nouns[ThreadLocalRandom.current().nextInt(nouns.length)];
        
        return adjective + " " + noun + " Journey";
    }

    /**
     * Generate route description
     */
    private String generateRouteDescription(List<MockPoint> points) {
        StringBuilder desc = new StringBuilder("Carefully selected ");
        desc.append(points.size()).append(" unique locations, ");
        
        Set<String> types = new HashSet<>();
        for (MockPoint point : points) {
            types.add(getTypeName(point.type));
        }
        
        desc.append("including ").append(String.join(", ", types)).append(" and other different types of experiences. ");
        desc.append("Estimated duration: ").append(points.stream().mapToInt(MockPoint::getEstimatedStayTime).sum()).append(" minutes.");
        
        return desc.toString();
    }

    /**
     * Get type English name
     */
    private String getTypeName(String type) {
        switch (type) {
            case "restaurant": return "Restaurant";
            case "cafe": return "Cafe";
            case "market": return "Market";
            case "attraction": return "Attraction";
            default: return "Location";
        }
    }

    /**
     * Generate route coordinates (simplified version)
     */
    private List<SurpriseRouteResponse.Coordinate> generateRouteCoordinates(List<MockPoint> points, SurpriseRouteRequest request) {
        List<SurpriseRouteResponse.Coordinate> coordinates = new ArrayList<>();
        
        // Add user location
        coordinates.add(SurpriseRouteResponse.Coordinate.builder()
            .latitude(BigDecimal.valueOf(request.getLatitude()))
            .longitude(BigDecimal.valueOf(request.getLongitude()))
            .build());
        
        // Add recommendation point coordinates
        for (MockPoint point : points) {
            coordinates.add(SurpriseRouteResponse.Coordinate.builder()
                .latitude(BigDecimal.valueOf(point.latitude))
                .longitude(BigDecimal.valueOf(point.longitude))
                .build());
        }
        
        return coordinates;
    }

    /**
     * Generate price range based on place type and price level
     */
    private String generatePriceRange(String type, Integer priceLevel) {
        if (priceLevel == null) priceLevel = 2;
        
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
     * Filter and improve tags for better clarity
     */
    private List<String> filterAndImproveTags(List<String> originalTags, String placeName, String type) {
        List<String> improvedTags = new ArrayList<>();
        
        // Add type-based tags
        switch (type) {
            case "restaurant":
                improvedTags.add("Restaurant");
                break;
            case "cafe":
                improvedTags.add("Cafe");
                break;
            case "market":
                improvedTags.add("Market");
                break;
            case "attraction":
                improvedTags.add("Attraction");
                break;
        }
        
        // Filter and improve original tags
        if (originalTags != null) {
            for (String tag : originalTags) {
                String improvedTag = improveTag(tag, placeName);
                if (improvedTag != null && !improvedTags.contains(improvedTag)) {
                    improvedTags.add(improvedTag);
                }
            }
        }
        
        // Add cuisine/style tags based on place name
        addCuisineTags(improvedTags, placeName);
        
        return improvedTags;
    }

    /**
     * Improve individual tag clarity
     */
    private String improveTag(String tag, String placeName) {
        if (tag == null || tag.isEmpty()) return null;
        
        String lowerTag = tag.toLowerCase();
        String lowerName = placeName.toLowerCase();
        
        // Skip confusing or irrelevant tags
        if (lowerTag.contains("portuguese") && !lowerName.contains("portuguese")) {
            return null; // Skip Portuguese tag for non-Portuguese places
        }
        
        // Improve common tags
        if (lowerTag.contains("catering.restaurant")) {
            return "Restaurant";
        } else if (lowerTag.contains("catering.cafe")) {
            return "Cafe";
        } else if (lowerTag.contains("commercial.market")) {
            return "Market";
        } else if (lowerTag.contains("tourism.attraction")) {
            return "Attraction";
        } else if (lowerTag.contains("catering")) {
            return "Food & Drink";
        } else if (lowerTag.contains("tourism")) {
            return "Tourism";
        } else if (lowerTag.contains("commercial")) {
            return "Shopping";
        }
        
        // Return original tag if it's reasonably clear
        if (tag.length() <= 20 && !tag.contains(".")) {
            return tag;
        }
        
        return null;
    }

    /**
     * Add cuisine/style tags based on place name
     */
    private void addCuisineTags(List<String> tags, String placeName) {
        String lowerName = placeName.toLowerCase();
        
        if (lowerName.contains("japanese") || lowerName.contains("sushi")) {
            tags.add("Japanese");
        } else if (lowerName.contains("chinese")) {
            tags.add("Chinese");
        } else if (lowerName.contains("italian") || lowerName.contains("pizza")) {
            tags.add("Italian");
        } else if (lowerName.contains("thai")) {
            tags.add("Thai");
        } else if (lowerName.contains("indian")) {
            tags.add("Indian");
        } else if (lowerName.contains("korean")) {
            tags.add("Korean");
        } else if (lowerName.contains("french")) {
            tags.add("French");
        } else if (lowerName.contains("mexican")) {
            tags.add("Mexican");
        } else if (lowerName.contains("coffee") || lowerName.contains("cafe")) {
            tags.add("Coffee");
        } else if (lowerName.contains("bakery")) {
            tags.add("Bakery");
        } else if (lowerName.contains("seafood")) {
            tags.add("Seafood");
        } else if (lowerName.contains("vegetarian") || lowerName.contains("vegan")) {
            tags.add("Vegetarian");
        }
    }

    /**
     * Round to specified decimal places
     */
    private double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    /**
     * Mock data point
     */
    private static class MockPoint {
        String id;
        String name;
        String type;
        String description;
        double latitude;
        double longitude;
        List<String> tags;
        double rating;
        int priceLevel;
        String priceRange;
        String openingHours;
        String imageUrl;
        
        MockPoint(String id, String name, String type, String description, 
                 double latitude, double longitude, List<String> tags, 
                 double rating, int priceLevel, String priceRange, String openingHours, String imageUrl) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.description = description;
            this.latitude = latitude;
            this.longitude = longitude;
            this.tags = tags;
            this.rating = rating;
            this.priceLevel = priceLevel;
            this.priceRange = priceRange;
            this.openingHours = openingHours;
            this.imageUrl = imageUrl;
        }
        
        int getEstimatedStayTime() {
            switch (type) {
                case "restaurant": return 60; // 1 hour
                case "cafe": return 30; // 30 minutes
                case "market": return 45; // 45 minutes
                case "attraction": return 90; // 1.5 hours
                default: return 30;
            }
        }
    }
}

