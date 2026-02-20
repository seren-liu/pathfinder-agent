package com.travel.agent.service.impl;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.ortools.Loader;
import com.google.ortools.constraintsolver.Assignment;
import com.google.ortools.constraintsolver.FirstSolutionStrategy;
import com.google.ortools.constraintsolver.LocalSearchMetaheuristic;
import com.google.ortools.constraintsolver.RoutingIndexManager;
import com.google.ortools.constraintsolver.RoutingModel;
import com.google.ortools.constraintsolver.RoutingSearchParameters;
import com.google.ortools.constraintsolver.main;
import com.google.protobuf.Duration;
import com.travel.agent.config.MapboxConfig;
import com.travel.agent.monitoring.RouteOptimizationMetrics;
import com.travel.agent.service.RouteOptimizationService;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 路线优化服务实现
 * 使用 Mapbox Matrix API 获取距离矩阵 + Google OR-Tools 求解 TSP
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RouteOptimizationServiceImpl implements RouteOptimizationService {

    private final MapboxConfig mapboxConfig;
    private final RouteOptimizationMetrics metrics;
    private final Gson gson = new Gson();
    private OkHttpClient client;
    private boolean orToolsLoaded = false;

    @PostConstruct
    public void init() {
        try {
            Loader.loadNativeLibraries();
            orToolsLoaded = true;
            log.info("Google OR-Tools native libraries loaded successfully");
        } catch (Exception e) {
            log.error("Failed to load OR-Tools native libraries. Route optimization will be disabled.", e);
        }
    }

    private OkHttpClient getClient() {
        if (client == null) {
            client = new OkHttpClient.Builder()
                    .connectTimeout(mapboxConfig.getTimeout(), TimeUnit.MILLISECONDS)
                    .readTimeout(mapboxConfig.getTimeout(), TimeUnit.MILLISECONDS)
                    .writeTimeout(mapboxConfig.getTimeout(), TimeUnit.MILLISECONDS)
                    .build();
        }
        return client;
    }

    @Override
    public List<Map<String, Object>> optimizeDayRoute(
            List<Map<String, Object>> activities,
            Map<String, Map<String, BigDecimal>> geoCoordinates,
            String destinationContext) {

        if (activities == null || activities.size() < 3) {
            log.debug("Skipping route optimization: fewer than 3 activities");
            return activities;
        }

        if (!orToolsLoaded) {
            log.warn("OR-Tools not loaded, returning original order");
            return activities;
        }

        try {
            // 1. 构建有坐标的活动索引列表
            List<Integer> geocodedIndices = new ArrayList<>();
            List<double[]> coordinates = new ArrayList<>();

            for (int i = 0; i < activities.size(); i++) {
                Map<String, Object> activity = activities.get(i);
                String location = (String) activity.get("location");
                if (location == null) continue;

                // 尝试带目的地上下文的 key
                String fullKey = location + ", " + destinationContext;
                Map<String, BigDecimal> coords = geoCoordinates.get(fullKey);
                if (coords == null) {
                    coords = geoCoordinates.get(location);
                }

                if (coords != null && coords.get("latitude") != null && coords.get("longitude") != null) {
                    geocodedIndices.add(i);
                    coordinates.add(new double[]{
                            coords.get("longitude").doubleValue(),
                            coords.get("latitude").doubleValue()
                    });
                }
            }

            if (coordinates.size() < 3) {
                log.debug("Fewer than 3 geocoded activities, skipping optimization");
                return activities;
            }

            metrics.recordOptimizationAttempt();

            // 2. 获取距离矩阵
            long[][] distanceMatrix = getDistanceMatrix(coordinates);

            // 3. 找到 accommodation 类型的索引（作为固定起点）
            int depotIndex = findDepotIndex(activities, geocodedIndices);

            // 4. 求解 TSP
            int[] optimizedOrder = solveTSP(distanceMatrix, depotIndex);
            if (optimizedOrder == null) {
                log.warn("TSP solver returned no solution, keeping original order");
                metrics.recordOptimizationFailure();
                return activities;
            }

            // 5. 重排活动
            List<Map<String, Object>> result = reorderActivities(
                    activities, geocodedIndices, optimizedOrder, distanceMatrix);

            metrics.recordOptimizationSuccess();
            log.info("Route optimized: {} activities reordered", geocodedIndices.size());
            return result;

        } catch (Exception e) {
            log.error("Route optimization failed, returning original order", e);
            metrics.recordOptimizationFailure();
            return activities;
        }
    }

    @Override
    public long[][] getDistanceMatrix(List<double[]> coordinates) {
        try {
            return fetchMapboxMatrix(coordinates);
        } catch (Exception e) {
            log.warn("Mapbox Matrix API failed, falling back to Haversine estimation", e);
            metrics.recordHaversineFallback();
            return computeHaversineMatrix(coordinates);
        }
    }

    /**
     * 调用 Mapbox Matrix API 获取驾驶时间矩阵
     */
    private long[][] fetchMapboxMatrix(List<double[]> coordinates) throws Exception {
        if (coordinates.size() > 25) {
            log.warn("Too many coordinates ({}) for Mapbox Matrix API (max 25), using Haversine",
                    coordinates.size());
            metrics.recordHaversineFallback();
            return computeHaversineMatrix(coordinates);
        }

        // 构建坐标字符串: lon1,lat1;lon2,lat2;...
        String coordString = coordinates.stream()
                .map(c -> String.format(Locale.US, "%.6f,%.6f", c[0], c[1]))
                .collect(Collectors.joining(";"));

        String url = String.format("%s/%s?access_token=%s&annotations=duration",
                mapboxConfig.getMatrixUrl(),
                coordString,
                mapboxConfig.getAccessToken());

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        metrics.recordMatrixApiCall();
        Timer.Sample matrixSample = metrics.startMatrixApi();
        try (Response response = getClient().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("Mapbox Matrix API returned status: " + response.code());
            }

            String body = response.body().string();
            JsonObject json = gson.fromJson(body, JsonObject.class);

            String code = json.has("code") ? json.get("code").getAsString() : "";
            if (!"Ok".equals(code)) {
                throw new RuntimeException("Mapbox Matrix API error: " + code);
            }

            JsonArray durations = json.getAsJsonArray("durations");
            int n = coordinates.size();
            long[][] matrix = new long[n][n];

            for (int i = 0; i < n; i++) {
                JsonArray row = durations.get(i).getAsJsonArray();
                for (int j = 0; j < n; j++) {
                    if (row.get(j).isJsonNull()) {
                        // 无法路由时用大值填充
                        matrix[i][j] = 999999;
                    } else {
                        matrix[i][j] = (long) row.get(j).getAsDouble();
                    }
                }
            }

            log.debug("Mapbox Matrix API returned {}x{} duration matrix", n, n);
            return matrix;
        }
        finally {
            metrics.stopMatrixApi(matrixSample);
        }
    }

    /**
     * Haversine 距离回退方案
     * 假设平均速度 40km/h，将距离转换为预估驾驶时间（秒）
     */
    long[][] computeHaversineMatrix(List<double[]> coordinates) {
        int n = coordinates.size();
        long[][] matrix = new long[n][n];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    matrix[i][j] = 0;
                } else {
                    double distKm = haversine(
                            coordinates.get(i)[1], coordinates.get(i)[0],
                            coordinates.get(j)[1], coordinates.get(j)[0]);
                    // 40 km/h -> 距离(km) / 40(km/h) * 3600(秒/小时)
                    matrix[i][j] = (long) (distKm / 40.0 * 3600.0);
                }
            }
        }

        log.debug("Computed Haversine-based {}x{} duration matrix", n, n);
        return matrix;
    }

    /**
     * Haversine 公式计算两点间大圆距离（km）
     */
    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371.0; // 地球半径 (km)
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    /**
     * 使用 Google OR-Tools 求解 TSP
     *
     * @param durationMatrix NxN 驾驶时间矩阵（秒）
     * @param depotIndex     起点索引（accommodation 活动）
     * @return 最优访问顺序数组，null 表示无解
     */
    int[] solveTSP(long[][] durationMatrix, int depotIndex) {
        int n = durationMatrix.length;
        if (n <= 2) {
            // 2 个点无需优化
            int[] trivial = new int[n];
            for (int i = 0; i < n; i++) trivial[i] = i;
            return trivial;
        }

        Timer.Sample tspSample = metrics.startTspSolver();

        // 创建路由索引管理器：n 个节点，1 辆车，从 depotIndex 出发
        RoutingIndexManager manager = new RoutingIndexManager(n, 1, depotIndex);
        RoutingModel routing = new RoutingModel(manager);

        // 注册运输成本回调
        final int transitCallbackIndex = routing.registerTransitCallback(
                (long fromIndex, long toIndex) -> {
                    int fromNode = manager.indexToNode(fromIndex);
                    int toNode = manager.indexToNode(toIndex);
                    return durationMatrix[fromNode][toNode];
                });

        routing.setArcCostEvaluatorOfAllVehicles(transitCallbackIndex);

        // 配置搜索参数（Demo优化：3秒超时确保快速响应）
        RoutingSearchParameters searchParameters = main.defaultRoutingSearchParameters()
                .toBuilder()
                .setFirstSolutionStrategy(FirstSolutionStrategy.Value.PATH_CHEAPEST_ARC)
                .setLocalSearchMetaheuristic(LocalSearchMetaheuristic.Value.GUIDED_LOCAL_SEARCH)
                .setTimeLimit(Duration.newBuilder().setSeconds(3).build())  // 降低到3秒
                .build();

        // 求解
        Assignment solution = routing.solveWithParameters(searchParameters);

        if (solution == null) {
            log.warn("OR-Tools TSP solver found no solution for {} nodes", n);
            metrics.stopTspSolver(tspSample);
            return null;
        }

        // 提取路线
        int[] order = new int[n];
        long index = routing.start(0);
        int step = 0;
        while (!routing.isEnd(index)) {
            order[step++] = manager.indexToNode(index);
            index = solution.value(routing.nextVar(index));
        }

        // 计算并记录优化效果
        long originalCost = computeRouteCost(durationMatrix, n);
        long optimizedCost = computeOptimizedRouteCost(durationMatrix, order);
        if (originalCost > 0) {
            double savings = (1.0 - (double) optimizedCost / originalCost) * 100;
            log.info("TSP optimization: {} nodes, original={}s, optimized={}s, savings={}%",
                    n, originalCost, optimizedCost, String.format("%.1f", savings));
        }

        metrics.stopTspSolver(tspSample);
        return order;
    }

    /**
     * 计算原始顺序的总路线成本
     */
    private long computeRouteCost(long[][] matrix, int n) {
        long cost = 0;
        for (int i = 0; i < n - 1; i++) {
            cost += matrix[i][i + 1];
        }
        return cost;
    }

    /**
     * 计算优化后顺序的总路线成本
     */
    private long computeOptimizedRouteCost(long[][] matrix, int[] order) {
        long cost = 0;
        for (int i = 0; i < order.length - 1; i++) {
            cost += matrix[order[i]][order[i + 1]];
        }
        return cost;
    }

    /**
     * 查找 accommodation 类型活动的索引作为 depot（起点）
     * 如果找不到则返回 0
     */
    private int findDepotIndex(List<Map<String, Object>> activities, List<Integer> geocodedIndices) {
        for (int geoIdx = 0; geoIdx < geocodedIndices.size(); geoIdx++) {
            int activityIdx = geocodedIndices.get(geoIdx);
            String type = (String) activities.get(activityIdx).get("type");
            if ("accommodation".equalsIgnoreCase(type)) {
                return geoIdx;
            }
        }
        return 0; // 默认第一个活动为起点
    }

    /**
     * 根据 TSP 求解结果重排活动并更新 startTime
     */
    private List<Map<String, Object>> reorderActivities(
            List<Map<String, Object>> activities,
            List<Integer> geocodedIndices,
            int[] optimizedOrder,
            long[][] distanceMatrix) {

        // 构建优化后的活动列表
        List<Map<String, Object>> result = new ArrayList<>();

        // 先加入优化后的有坐标活动
        Set<Integer> geocodedSet = new HashSet<>(geocodedIndices);
        for (int orderIdx : optimizedOrder) {
            int activityIdx = geocodedIndices.get(orderIdx);
            result.add(new HashMap<>(activities.get(activityIdx)));
        }

        // 附加没有坐标的活动到末尾（保持原始相对顺序）
        for (int i = 0; i < activities.size(); i++) {
            if (!geocodedSet.contains(i)) {
                result.add(new HashMap<>(activities.get(i)));
            }
        }

        // 更新 startTime
        updateStartTimes(result, distanceMatrix, optimizedOrder, geocodedIndices.size());

        return result;
    }

    /**
     * 更新活动的 startTime
     * 规则：第一个活动保持原始时间，后续活动 = 前一活动结束时间 + 旅行时间 + 15分钟缓冲
     */
    private void updateStartTimes(
            List<Map<String, Object>> activities,
            long[][] distanceMatrix,
            int[] optimizedOrder,
            int geocodedCount) {

        LocalTime currentTime = null;

        for (int i = 0; i < activities.size(); i++) {
            Map<String, Object> activity = activities.get(i);

            if (i == 0) {
                // 第一个活动保持原始 startTime
                currentTime = parseStartTime(activity);
                if (currentTime == null) {
                    currentTime = LocalTime.of(9, 0); // 默认 9:00
                    activity.put("startTime", "09:00");
                }
            } else {
                if (currentTime != null) {
                    // 获取旅行时间
                    long travelSeconds = 0;
                    if (i < geocodedCount && i - 1 >= 0 && i - 1 < geocodedCount) {
                        travelSeconds = distanceMatrix[optimizedOrder[i - 1]][optimizedOrder[i]];
                    }

                    // 前一活动的持续时间
                    int prevDuration = parseDurationMinutes(activities.get(i - 1));

                    // 新 startTime = 前一活动结束 + 旅行时间 + 15分钟缓冲
                    int travelMinutes = (int) Math.ceil(travelSeconds / 60.0);
                    int bufferMinutes = 15;
                    currentTime = currentTime
                            .plusMinutes(prevDuration)
                            .plusMinutes(travelMinutes)
                            .plusMinutes(bufferMinutes);

                    activity.put("startTime", currentTime.toString().substring(0, 5));
                }
            }
        }
    }

    private LocalTime parseStartTime(Map<String, Object> activity) {
        Object startTime = activity.get("startTime");
        if (startTime instanceof String) {
            try {
                return LocalTime.parse((String) startTime);
            } catch (DateTimeParseException e) {
                return null;
            }
        }
        return null;
    }

    private int parseDurationMinutes(Map<String, Object> activity) {
        Object duration = activity.get("durationMinutes");
        if (duration instanceof Number) {
            return ((Number) duration).intValue();
        }
        return 60; // 默认 60 分钟
    }
}
