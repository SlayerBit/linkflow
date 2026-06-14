-- Sliding-window rate limiter using a Redis sorted set.
--
-- Each request adds a member (unique ID) with score = current timestamp in microseconds.
-- Before counting, members outside the window are pruned.
-- Returns: {allowed (1/0), current_count, ttl_seconds}
--
-- KEYS[1] = rate limit key (e.g. rate_limit:user:{id})
-- ARGV[1] = limit (max requests per window)
-- ARGV[2] = window size in microseconds
-- ARGV[3] = current timestamp in microseconds
-- ARGV[4] = unique member ID for this request

local key = KEYS[1]
local limit = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local member = ARGV[4]

-- Remove all entries outside the sliding window
local window_start = now - window
redis.call('ZREMRANGEBYSCORE', key, '-inf', window_start)

-- Count current entries in the window
local current = redis.call('ZCARD', key)

if current >= limit then
    -- Over limit — set TTL for cleanup but do not add the request
    local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
    local ttl = 0
    if #oldest >= 2 then
        -- Time until oldest entry expires from window
        ttl = math.ceil((tonumber(oldest[2]) + window - now) / 1000000)
    end
    if ttl > 0 then
        redis.call('EXPIRE', key, ttl)
    end
    return {0, current, ttl}
end

-- Under limit — add this request and set expiry
redis.call('ZADD', key, now, member)
current = current + 1

-- TTL = full window duration (in seconds, rounded up)
local ttl_seconds = math.ceil(window / 1000000)
redis.call('EXPIRE', key, ttl_seconds)

return {1, current, ttl_seconds}
