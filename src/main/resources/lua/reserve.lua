-- Atomic ticket reservation with duplicate detection
-- KEYS[1]: ticket:inventory:{concertId}
-- KEYS[2]: ticket:users:{concertId}
-- ARGV[1]: userId
-- Returns: 1 (SUCCESS), 0 (SOLD_OUT), -1 (DUPLICATE)

local inventory_key = KEYS[1]
local users_key = KEYS[2]
local user_id = ARGV[1]

-- Check if user already reserved
if redis.call('SISMEMBER', users_key, user_id) == 1 then
  return -1  -- DUPLICATE
end

-- Check if tickets available
local remaining = redis.call('GET', inventory_key)
if remaining == false or tonumber(remaining) < 1 then
  return 0   -- SOLD_OUT
end

-- Atomic decrement and record user
redis.call('DECR', inventory_key)
redis.call('SADD', users_key, user_id)

return 1    -- SUCCESS
