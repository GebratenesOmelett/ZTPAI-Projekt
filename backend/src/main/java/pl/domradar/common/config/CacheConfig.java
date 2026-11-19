package pl.domradar.common.config;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import pl.domradar.search.dto.SearchFacets;
import pl.domradar.search.stats.DistrictStatsDto;

import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

/**
 * Cache w Redisie dla faset wyszukiwania i statystyk dzielnic (TTL 5 min, unieważniany po imporcie).
 * Klucz faset = SHA-256 kanonicznego JSON-a kryteriów. Awaria Redisa nie psuje wyszukiwania —
 * błędy cache są tylko logowane (graceful degradation).
 */
@Slf4j
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    public static final String FACETS = "facets";
    public static final String DISTRICT_STATS = "districtStats";
    public static final Duration TTL = Duration.ofMinutes(5);

    @Bean
    RedisCacheManagerBuilderCustomizer redisCacheCustomizer(ObjectMapper objectMapper) {
        ObjectMapper mapper = objectMapper.copy();
        Jackson2JsonRedisSerializer<SearchFacets> facets = new Jackson2JsonRedisSerializer<>(mapper, SearchFacets.class);
        JavaType statsType = mapper.getTypeFactory().constructCollectionType(List.class, DistrictStatsDto.class);
        Jackson2JsonRedisSerializer<Object> stats = new Jackson2JsonRedisSerializer<>(mapper, statsType);
        return builder -> builder
                .withCacheConfiguration(FACETS, base().serializeValuesWith(SerializationPair.fromSerializer(facets)))
                .withCacheConfiguration(DISTRICT_STATS, base().serializeValuesWith(SerializationPair.fromSerializer(stats)));
    }

    private static RedisCacheConfiguration base() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(TTL)
                .disableCachingNullValues()
                .prefixCacheNameWith("domradar:");
    }

    /** Klucz cache = SHA-256 z kanonicznego (posortowanego) JSON-a argumentów. */
    @Bean
    KeyGenerator criteriaHashKeyGenerator() {
        ObjectMapper canonical = JsonMapper.builder()
                .findAndAddModules()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .build();
        return (target, method, params) -> {
            try {
                byte[] json = canonical.writeValueAsBytes(params);
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
            } catch (Exception e) {
                throw new IllegalStateException("Cannot compute cache key", e);
            }
        };
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache get failed ({}): {}", cache.getName(), e.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("Cache put failed ({}): {}", cache.getName(), e.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache evict failed ({}): {}", cache.getName(), e.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("Cache clear failed ({}): {}", cache.getName(), e.getMessage());
            }
        };
    }
}
