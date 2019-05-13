
math.randomseed(os.time())

table.tostring = function(tbl)
	local str_arr = {"{"}
	
	for k,v in pairs(tbl) do
		table.insert(str_arr, tostring(k))
		table.insert(str_arr, "=")
		
		if type(v) == "table" then
			table.insert(str_arr, table.tostring(v))
		else
			table.insert(str_arr, tostring(v))
		end
		
		table.insert(str_arr, ", ")
	end
	
	-- If there are any kvps in the string array, remove the last comma
	if #str_arr > 1 then
		table.remove(str_arr)
	end
	
	table.insert(str_arr, "}")
	
	return table.concat(str_arr)
end

-- initialize user module with some contants
modules.user = {
	SALT_LENGTH = 64,
	SALT_CHARS = "abcdefghijklmnopqrstuvwxyzABCDFGHIJKLMNOPQRSTUVWXYZ0123456789",
	
	AUTHCODE_LENGTH = 64,
	AUTHCODE_CHARS = "abcdefghijklmnopqrstuvwxyzABCDFGHIJKLMNOPQRSTUVWXYZ0123456789",
	
	AUTHCODE_TIMEOUT = 15 * 60
}

function random_string(length, charset)
	local str_arr = {}
	
	for i = 0, length do
		local index = math.floor(string.len(charset) * math.random())
		str_arr[i] = charset:sub(index, index)
	end
	
	return table.concat(str_arr)
end

modules.user.generate_authcode = function()
	return random_string(modules.user.AUTHCODE_LENGTH, modules.user.AUTHCODE_CHARS)
end

modules.user.generate_salt = function()
	return random_string(modules.user.SALT_LENGTH, modules.user.SALT_CHARS)
end

modules.user.username_available = function(username)
	local stmt = database:prepare("SELECT 1 FROM User WHERE Username = ?")
	
	local ret = stmt:select({username})
	
	stmt:close()
	
	-- If there are no rows in ret, then the username is available
	return next(ret) == nil
end

modules.user.from_password = function(username, password)
	local stmt = database:prepare("SELECT ID FROM User WHERE Username = ? AND PasswordHash = SHA2(CONCAT(?, Salt), 256)")
	
	local ids = stmt:select({username, password})
	
	stmt:close()
	
	local first = next(ids)
	
	-- no user found with that username/password
	if first == nil then
		return nil
	end
	
	local id = ids[first].ID
	
	local authcode = modules.user.generate_authcode()
	
	local updater = database:prepare("UPDATE User SET LastLogin = NOW(), LastAuthCode = ? WHERE ID = ?")
	
	updater:update({authcode, id})
	
	updater:close()
	
	return modules.user.from_authcode(username, authcode)
end

modules.user.from_authcode = function(username, authcode)
	local stmt = database:prepare("SELECT ID, LastAuthCode, UNIX_TIMESTAMP(LastLogin) AS `LastLogin`, UNIX_TIMESTAMP(NOW()) AS `Now` FROM User WHERE Username = ?")
	
	local rows = stmt:select({username})
	
	stmt:close()
	
	local first = next(rows)
	
	-- no user found with that username
	if first == nil then
		return nil
	end
	
	local info = rows[first]
	
	-- If the authcode expired or the authcodes don't match, the given authcode is invalid
	-- Only checks when the user has logged in before
	if info.LastLogin ~= nil then
		if (info.Now - info.LastLogin) > modules.user.AUTHCODE_TIMEOUT or info.LastAuthCode ~= authcode then
			return nil
		end
	end
	
	local updater = database:prepare("UPDATE User SET LastLogin = NOW() WHERE ID = ?")
	
	updater:update({info.ID})
	
	updater:close()
	
	return {id = info.ID, username = username, authcode = authcode}
end

modules.user.create = function(username, password)
	local salt = modules.user.generate_salt()
	
	local stmt = database:prepare("INSERT INTO User (Username, Salt, PasswordHash) VALUES (?, ?, SHA2(?, 256))")
	
	stmt:insert({username, salt, password .. salt})
	
	stmt:close()
	
	return modules.user.from_password(username, password)
end









