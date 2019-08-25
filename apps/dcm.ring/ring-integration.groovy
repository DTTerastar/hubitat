/**
 *  Ring Integration
 *
 *  Copyright 2019 Dominick Meglio
 *
 */
definition(
    name: "Ring Integration",
    namespace: "dcm.ring",
    author: "Dominick Meglio",
    description: "Integrate your Ring Doorbells and Cameras with Hubitat",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {
	page(name: "prefApiAccess", title: "Ring")
	page(name: "prefRingDevices", title: "Ring")
	page(name: "prefRingTriggerSettings", title: "Ring")
}

def prefApiAccess() {
	return dynamicPage(name: "prefApiAccess", title: "Connect to Ring", nextPage: "prefRingDevices", uninstall:false, install: false) {
		section("Ring Login Information"){
			input("ringUsername", "text", title: "Ring Username", description: "Enter your Ring username", required: true)
			input("ringPassword", "password", title: "Ring Password", description: "Enter your Ring password", required: true)
			input "debugOutput", "bool", title: "Enable debug logging?", defaultValue: true, displayDuringSetup: false, required: false			
		}
	}
}

def prefRingDevices() {
	if (!getRingDevices())
	{
		return dynamicPage(name: "prefRingDevices", title: "Login Error", install: false, uninstall: false) {
			section("Error") {
				paragraph "The specified username/password are invalid"
			}
		}
	}
	else
	{
		return dynamicPage(name: "prefRingDevices", title: "Ring Devices", nextPage: "prefRingTriggerSettings", install: false, uninstall: false) {
			section("Select your Ring Devices") {
				if (state.doorbells.size() > 0)
					input(name: "doorbells", type: "enum", title: "Doorbells", required:false, multiple:true, options:state.doorbells, hideWhenEmpty: true)
				if (state.cameras.size() > 0)
					input(name: "cameras", type: "enum", title: "Cameras", required:false, multiple:true, options:state.cameras, hideWhenEmpty: true)
			}
		}
	}
}

def prefRingTriggerSettings() {
	return dynamicPage(name: "prefRingTriggerSettings", title: "Ring Device Trigger Settings", install: true, uninstall: true) {
		section("Triggers") {
			paragraph "Specify the trigger value used to signal each type of event. These will be the dimmer values set by your Alexa routine."
			for (doorbell in doorbells) {
				input(name: "doorbellMotionTrigger${doorbell}", type: "number", title: "Motion Trigger for ${state.doorbells[doorbell]}", required: true)
				input(name: "doorbellButtonTrigger${doorbell}", type: "number", title: "Button Trigger for ${state.doorbells[doorbell]}", required: true)
			}
			for (camera in cameras) {
				input(name: "cameraMotionTrigger${camera}", type: "number", title: "Motion Trigger for ${state.cameras[camera]}", required: true)
			}
		}
	}
}

def installed() {
	logDebug "Installed with settings: ${settings}"

	initialize()
}

def updated() {
	logDebug "Updated with settings: ${settings}"
	unschedule()
	unsubscribe()
	initialize()
}

def initialize() {
	logDebug "initializing"
	cleanupChildDevices()
	createChildDevices()
	cleanupSettings()
	runEvery5Minutes(updateDevices)
	schedule("0 * * * * ? *", updateDevices)
}

def updateDevices()
{
	logDebug "refreshing ring devices"
	def authToken = getAuthToken(state.token)
	if (!authToken) return false

    def params = [
		uri: "https://api.ring.com",
		path: "/clients_api/ring_devices",
		query: [
        	api_version: "9",
            "auth_token": authToken
    	]
	]
    try
	{
		httpGet(params) { resp ->
			for (camera in resp.data.stickup_cams) {
				def cameraDevice = getChildDevice("ring:" + camera.id)
				if (camera.led_status == "off")
				{
					cameraDevice.sendEvent(name: "switch", value: "off")
				}
				else if (camera.led_status == "on") 
				{
					cameraDevice.sendEvent(name: "switch", value: "on")
				}
				else if (camera.led_status.seconds_remaining.toInteger() > 0)
				{
					cameraDevice.sendEvent(name: "switch", value: "on")
				}
				else
				{
					cameraDevice.sendEvent(name: "switch", value: "off")
				}
				
				if (camera.siren_status != null)
				{
					if (camera.siren_status.seconds_remaining.toInteger() > 0)
					{
						if (cameraDevice.getDataValue("strobing") == "true")
							cameraDevice.sendEvent(name: "alarm", value: "both")
						else
							cameraDevice.sendEvent(name: "alarm", value: "siren")
						
					}
					else
					{
						if (cameraDevice.getDataValue("strobing") == "true")
							cameraDevice.sendEvent(name: "alarm", value: "strobe")
						else
							cameraDevice.sendEvent(name: "alarm", value: "off")
					}
				}
				
				if (camera.battery_voltage != null || camera.battery_voltage_2 != null)
				{
					def batteryLevel = 0
					if (camera.battery_life != null && camera.battery_life_2 != null)
						batteryLevel = (camera.battery_life.toInteger() + camera.battery_life_2.toInteger())/2
					else if (camera.battery_life != null)
						batteryLevel = camera.battery_life.toInteger()
					else if (camera.battery_life_2 != null)
						batteryLevel = camera.battery_life_2.toInteger()
					
					cameraDevice.sendEvent(name: "battery", value: batteryLevel)
				}
			}
		}
	}
	catch (e)
	{
        logDebug e
	}
}

def getRingDevices() {
	state.doorbells = [:]
	state.cameras = [:]
	state.cameraDetails = [:]
    
	def token = login()
	if (!token) return false
    
    def authToken = getAuthToken(token)
    if (!authToken) return false
	
	def params = [
		uri: "https://api.ring.com",
		path: "/clients_api/ring_devices",
		query: [
        	api_version: "9",
            "auth_token": authToken
    	]
	]
	try
	{
		httpGet(params) { resp ->
			for (doorbell in resp.data.doorbots) {
				state.doorbells[doorbell.id] = doorbell.description
			}
			for (camera in resp.data.stickup_cams) {
				state.cameras[camera.id] = camera.description
				state.cameraDetails[camera.id] = [
					name: camera.description,
					hasBattery: camera.kind.contains("stickup_cam")
				]
			}
		}
	}
	catch (e)
	{
		logDebug e
	}
	return true
}

def createChildDevices() {
	if (!getChildDevice("ring:manager"))
	{
		addChildDevice("ring", "Ring Manager", "ring:manager", 1234, ["name": "Ring Manager", isComponent: true])
	}
	if (doorbells != null) 
	{
		for (doorbell in doorbells)
		{
			if (!getChildDevice("ring:" + doorbell))
				addChildDevice("ring", "Ring Doorbell", "ring:" + doorbell, 1234, ["name": state.doorbells[doorbell], isComponent: false])
		}
	}
	
	if (cameras != null) 
	{
		for (camera in cameras)
		{
			if (!getChildDevice("ring:" + camera))
			{
				if (state.cameraDetails[camera].hasBattery)
					addChildDevice("ring", "Ring Battery Camera", "ring:" + camera, 1234, ["name": state.cameras[camera], isComponent: false])
				else
					addChildDevice("ring", "Ring Camera", "ring:" + camera, 1234, ["name": state.cameras[camera], isComponent: false])
			}
		}
	}
}

def cleanupChildDevices()
{
	for (device in getChildDevices())
	{
		if (device.deviceNetworkId == "ring:manager")
			continue
		
		def deviceId = device.deviceNetworkId.replace("ring:","")
		
		def deviceFound = false
		for (doorbell in doorbells)
		{
			if (doorbell == deviceId)
			{
				deviceFound = true
				break
			}
		}
		
		if (deviceFound == true)
			continue
		
		for (camera in cameras)
		{
			if (camera == deviceId)
			{
				deviceFound = true
				break
			}
		}
		if (deviceFound == true)
			continue
		
		deleteChildDevice(device.deviceNetworkId)
	}
}

def cleanupSettings()
{
	def allProperties = this.settings
	def deviceName = null

	for (property in allProperties) {
		if (property.key.startsWith("doorbellMotionTrigger")) {
			deviceName = property.key.replace("doorbellMotionTrigger","")
			if (!getChildDevice("ring:" + deviceName)) {
				app.removeSetting(property.key)
			}
		}
		else if (property.key.startsWith("doorbellButtonTrigger")) {
			deviceName = property.key.replace("doorbellButtonTrigger","")
			if (!getChildDevice("ring:" + deviceName)) {
				app.removeSetting(property.key)
			}
		}
		else if (property.key.startsWith("cameraMotionTrigger")) {
			logDebug "checking for ${property.key}"
			deviceName = property.key.replace("cameraMotionTrigger","")
			if (!getChildDevice("ring:" + deviceName)) {
				logDebug "deleting it"
				app.removeSetting(property.key)
			}
		}
	}
}

def login() 
{
	state.token = null
    
	def s = "${ringUsername}:${ringPassword}"
    logDebug "login()"
	
    String encodedUandP = s.bytes.encodeBase64()
    
    def token = "EMPTY"
    def params = [
    	uri: "https://oauth.ring.com",
    	path: "/oauth/token",
        headers: [
            "User-Agent": "iOS"
    	],
        requestContentType: "application/json",
        body: "{\"client_id\": \"ring_official_ios\",\"grant_type\": \"password\",\"password\": \"${ringPassword}\",\"scope\": \"client\",\"username\": \"${ringUsername}\"}"
	]
    try {
        httpPost(params) { resp ->
            token = resp.data.access_token
        }
    } catch (e) {
		if (e.statusCode == 401)
			return false
        log.error "HTTP Exception Received on POST: $e"
        return        
    }
    
	state.token = token
    return token
}


def getAuthToken(token)
{
    logDebug "getAuthToken()"
    params = [
    	uri: "https://api.ring.com",
    	path: "/clients_api/session",
        headers: [
        	Authorization: "Bearer ${token}",
            "User-Agent": "iOS"
    	],
        requestContentType: "application/x-www-form-urlencoded",
        body: "device%5Bos%5D=ios&device%5Bhardware_id%5D=a565187537a28e5cc26819e594e28213&api_version=9"
	]

    def authToken = "EMPTY"
    try {
        httpPost(params) { resp ->
            authToken = resp.data.profile.authentication_token
        }
    } catch (e) {
        if (e.statusCode == 401)
            login()
        else
            log.error "HTTP Exception Received on POST: $e"
        
        return        
    }
    
    return authToken
}


def handleOn(device, cameraId) {
	logDebug "Handling On event for ${cameraId}"

	runCommandWithRetry(cameraId, "floodlight_light_on")
	pause(250)
	runCommandWithRetry(cameraId, "floodlight_light_on")
	
	device.sendEvent(name: "switch", value: "on")
}

def handleOff(device, cameraId) {
	logDebug "Handling Off event for ${cameraId}"
	device.updateDataValue("strobing", "false")
	logDebug device.getDataValue("strobing")
	runCommandWithRetry(cameraId, "floodlight_light_off")
	runCommandWithRetry(cameraId, "siren_off")
	
	device.sendEvent(name: "switch", value: "off")
}

def handleSiren(device, cameraId) {
	logDebug "Handling Siren event for ${cameraId}"
	runCommandWithRetry(cameraId, "siren_on", "PUT", [duration: 10])
	device.sendEvent(name: "alarm", value: "siren")
}

def handleBoth(device, cameraId) {
	runCommandWithRetry(cameraId, "siren_on", "PUT", [duration: 10])
	device.sendEvent(name: "alarm", value: "siren")
}

def handleStrobe(device, cameraId) {
/*	def strobePauseInMs = 3000
	def strobeCount = 5
	device.updateDataValue("strobing", "true")
	logDebug "Handling Strobe event for ${cameraId}"
	device.sendEvent(name: "alarm", value: "strobe")
	
	for (def i = 0; i < strobeCount; i++) {
		logDebug device.getDataValue("strobing")
		if (device.getDataValue("strobing") == "false")
			return
		runCommandWithRetry(cameraId, "floodlight_light_on")
		if (device.getDataValue("strobing") == "false")
			return
		runCommandWithRetry(cameraId, "floodlight_light_on")
		if (device.getDataValue("strobing") == "false")
			return
		pause(strobePauseInMs)
		if (device.getDataValue("strobing") == "false")
			return
		runCommandWithRetry(cameraId, "floodlight_light_off")
		if (device.getDataValue("strobing") == "false")
			return
		pause(strobePauseInMs)
	}
	device.updateDataValue("strobing", "false")*/
	runCommandWithRetry(cameraId, "siren_on", "PUT", [duration: 10])
	device.sendEvent(name: "alarm", value: "siren")
}

def handleRefresh() {
	updateDevices()
}

def handleRecord(device, cameraId) {
	runCommandWithRetry(cameraId, "vod", "POST")
}

def runCommand(deviceId, command, method = "PUT", parameters = null) {
    def authToken = getAuthToken(state.token)
	if (!authToken) return false
	
    def params = [
		uri: "https://api.ring.com",
		path: "/clients_api/doorbots/${deviceId}/${command}",
		headers: [
			"User-Agent": "iOS"
		],
		query: [
        	api_version: "10",
            "auth_token": authToken
    	]
	]
	if (parameters != null) {
		map.each { key, value -> 
			params.query[key] = value
		}
	}
	logDebug "/clients_api/doorbots/${deviceId}/${command}"
	def result = null
	if (method == "PUT")
	{
		httpPut(params) { resp ->
			result = resp.data
		}
	}
	else if (method == "POST")
	{
		httpPost(params) { resp ->
			result = resp.data
		}
	}
	return result
}

def runCommandWithRetry(deviceId, command, method = "PUT", parameters = null) {
	try
	{
		return runCommand(deviceId, command, method, parameters)
	}
	catch (e)
	{
		if (e.statusCode == 401)
		{
			login()
			return runCommand(deviceId, command, method, parameters)
		}
		else if (e.statusCode >= 200 && e.statusCode <= 299)
			return
		else
			logDebug e
	}
}

def trigger(level) {
	if (level == 0)
		return

	def allProperties = this.settings
	def deviceName = null
	def device = null
	for (property in allProperties) {
		if (property.key.startsWith("doorbellMotionTrigger")) {
			if (this.getProperty(property.key) == level) {
				deviceName = property.key.replace("doorbellMotionTrigger","")
				device = getChildDevice("ring:" + deviceName)
				if (device == null)
					continue
				logDebug "Triggering motion for ${device}"
				device.sendEvent(name: "motion", value: "active")
				runIn(5, inactivate, [overwrite: false, data: [device: deviceName]])
				break
			}
		}
		else if (property.key.startsWith("doorbellButtonTrigger")) {
			if (this.getProperty(property.key) == level) {
				deviceName = property.key.replace("doorbellButtonTrigger","")
				device = getChildDevice("ring:" + deviceName)
				if (device == null)
					continue
				logDebug "Triggering button press for ${device}"
				device.sendEvent(name: "pushed", value: "1")
				break
			}
		}
		else if (property.key.startsWith("cameraMotionTrigger")) {
			if (this.getProperty(property.key) == level) {
				deviceName = property.key.replace("cameraMotionTrigger","")
				device = getChildDevice("ring:" + deviceName)
				if (device == null)
					continue
				logDebug "Triggering motion for ${device}"
				device.sendEvent(name: "motion", value: "active")
				runIn(5, inactivate, [overwrite: false, data: [device: deviceName]])
				break
			}
		}
	}
}

def inactivate(data) {

	def device = getChildDevice("ring:" + data.device)
	logDebug "Cancelling motion for ${device}"
	device.sendEvent(name:"motion", value: "inactive")
}

private logDebug(msg) {
	if (settings?.debugOutput) {
		log.debug msg
	}
}