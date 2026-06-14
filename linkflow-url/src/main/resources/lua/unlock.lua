-- Atomic compare-and-delete unlock.
-- Ensures only the lock owner can release the lock.
-- KEYS[1] = lock key
-- ARGV[1] = expected lock value (owner token)
-- Returns 1 if deleted, 0 if not owner or key missing.
if redis.call('GET', KEYS[1]) == ARGV[1] then
    return redis.call('DEL', KEYS[1])
end
return 0
