package com.realtimeleaderboard.leaderboard.redis;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RedisLeaderboardRepository {

    private final StringRedisTemplate redisTemplate;

    public RedisLeaderboardRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void incrementScore(String key, String userId, double score) {
        redisTemplate.opsForZSet().incrementScore(key, userId, score);
    }

    public void addScore(String key, String userId, double score) {
        redisTemplate.opsForZSet().add(key, userId, score);
    }

    public Double getScore(String key, String userId) {
        return redisTemplate.opsForZSet().score(key, userId);
    }

    public Long getRank(String key, String userId) {
        Long rank = redisTemplate.opsForZSet().reverseRank(key, userId);
        return rank;
    }

    public Long size(String key) {
        Long size = redisTemplate.opsForZSet().zCard(key);
        return size;
    }

    public Set<String> topN(String key, long limit) {
        return redisTemplate.opsForZSet().reverseRange(key, 0, limit - 1);
    }

    public Map<String, Double> topNWithScores(String key, long limit) {
        return redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, limit - 1)
                .stream()
                .collect(java.util.LinkedHashMap::new,
                        (map, entry) -> map.put(entry.getValue().toString(), entry.getScore()),
                        java.util.LinkedHashMap::putAll);
    }

    public List<Map.Entry<String, Double>> topNEntries(String key, long limit) {
        var entries = redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, limit - 1);
        if (entries == null) return List.of();
        return entries.stream()
                .map(e -> (Map.Entry<String, Double>) Map.entry(e.getValue().toString(), e.getScore()))
                .toList();
    }

    public List<Map.Entry<String, Double>> pageEntries(String key, long offset, long size) {
        var entries = redisTemplate.opsForZSet().reverseRangeWithScores(key, offset, offset + size - 1);
        if (entries == null) return List.of();
        return entries.stream()
                .map(e -> (Map.Entry<String, Double>) Map.entry(e.getValue().toString(), e.getScore()))
                .toList();
    }

    public List<Map.Entry<String, Double>> nearbyEntries(String key, String userId, int range) {
        Long rank = getRank(key, userId);
        if (rank == null) return List.of();
        long start = Math.max(0, rank - range);
        long end = rank + range;
        var entries = redisTemplate.opsForZSet().reverseRangeWithScores(key, start, end);
        if (entries == null) return List.of();
        return entries.stream()
                .map(e -> (Map.Entry<String, Double>) Map.entry(e.getValue().toString(), e.getScore()))
                .toList();
    }

    public void delete(String key) {
        redisTemplate.delete(key);
    }

    public boolean hasKey(String key) {
        Boolean exists = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(exists);
    }

    public void setProcessedScore(String scoreIdKey, long ttlHours) {
        redisTemplate.opsForValue().set(scoreIdKey, "1", ttlHours, TimeUnit.HOURS);
    }

    public boolean isProcessedScore(String scoreIdKey) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(scoreIdKey));
    }

    public void bulkAdd(String key, Map<String, Double> memberScores) {
        java.util.Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<String>> tuples = new java.util.HashSet<>();
        for (var entry : memberScores.entrySet()) {
            tuples.add(new org.springframework.data.redis.core.DefaultTypedTuple<>(entry.getKey(), entry.getValue()));
        }
        redisTemplate.opsForZSet().add(key, tuples);
    }

    public void executeInTransaction(org.springframework.data.redis.core.RedisCallback<Object> callback) {
        redisTemplate.execute(callback);
    }
}
