
function onlogin(self, request)
	local user = modules.user.from_password(request:getQueryValue("username"), request:getQueryValue("password"))
	
	if user == nil then
		return {
			status=200,
			content={
				type="bad_input",
				fields={"username", "password"}
			}
		}
	end
	
	return {
		status=200,
		content={
			type="ok",
			username = user.username,
			authcode = user.authcode
		}
	}
end

function onregister(self, request)
	if not modules.user.username_available(request:getQueryValue("username")) then
		return {
			status=200,
			content={
				type="bad_input",
				fields={"username"}
			}
		}
	end
	
	local user = modules.user.create(request:getQueryValue("username"), request:getQueryValue("password"))
	
	return {
		status=200,
		content={
			type="ok",
			username = user.username,
			authcode = user.authcode
		}
	}
end

table.insert(endpoints, {
	type = "script",
	name = "/session",
	handlers = {
		login = onlogin,
		register = onregister
	}
})
