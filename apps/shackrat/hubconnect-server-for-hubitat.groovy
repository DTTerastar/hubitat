/**
 * HubConnect Server for Hubitat
 *
 * Copyright 2019 Steve White, Retail Media Concepts LLC.
 *
 * HubConnect for Hubitat is a software package created and licensed by Retail Media Concepts LLC.
 * HubConnect, along with associated elements, including but not limited to online and/or electronic documentation are
 * protected by international laws and treaties governing intellectual property rights.
 *
 * This software has been licensed to you. All rights are reserved. You may use and/or modify the software.
 * You may not sublicense or distribute this software or any modifications to third parties in any way.
 *
 * By downloading, installing, and/or executing this software you hereby agree to the terms and conditions set forth in the HubConnect license agreement.
 * <http://irisusers.com/hubitat/hubconnect/HubConnect_License_Agreement.html>
 * 
 * Hubitat is the trademark and intellectual property of Hubitat, Inc. Retail Media Concepts LLC has no formal or informal affiliations or relationships with Hubitat.
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License Agreement
 * for the specific language governing permissions and limitations under the License.
 *
 */
import groovy.transform.Field
definition(
	name: "HubConnect Server for Hubitat",
	namespace: "shackrat",
	author: "Steve White",
	description: "Synchronizes devices and events across hubs..",
	category: "My Apps",
	iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/App-BigButtonsAndSwitches.png",
	iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/App-BigButtonsAndSwitches@2x.png",
	iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/App-BigButtonsAndSwitches@2x.png"
)

// Preference pages
preferences
{
	page(name: "mainPage")
	page(name: "aboutPage")
	page(name: "customDriverPage")
	page(name: "createCustomDriver")
	page(name: "editCustomDriver")
	page(name: "saveCustomDriverPage")
	page(name: "utilitesPage")
	page(name: "modeReportPage")
	page(name: "versionReportLoadingPage")
	page(name: "versionReportPage")
	page(name: "hubUtilitiesPage")
}


// Map containing driver and attribute definitions for each device class
@Field ATTRIBUTE_TO_SELECTOR =
[
	"alarm":			"alarm",
	"audioVolume":		"audioVolume",
	"battery":			"battery",
	"button":			"button",
	"bulb":			"bulb",
	"carbonMonoxide":		"carbonMonoxideDetector",
	"colorMode":		"colorMode",
	"colorTemperature":	"colorTemperature",
	"contact":			"contactSensor",
	"door":			"garageDoorControl",
	"doubleTapped":		"doubleTapableButton",
	"held":			"holdableButton",
	"illuminance":		"illuminanceMeasurement",
	"level":			"switchLevel",
	"lock":			"lock",
	"motion":			"motionSensor",
	"power":			"powerMeter",
	"pushed":			"pushableButton",
	"presence":			"presenceSensor",
	"refresh":			"refresh",
	"securityKeypad":		"securityKeypad",
	"shock":			"shockSensor",
	"smoke":			"smokeDetector",
	"speechSynthesis":	"speechSynthesis",
	"speed":			"fanControl",
	"switch":			"switch",
	"temperature":		"temperatureMeasurement",
	"thermostat":		"thermostat",
	"valve":			"valve",
	"water":			"waterSensor",
	"windowshade":		"windowShade"
]


/*
	mainPage
    
	Purpose: Displays the main (landing) page.

	Notes: 	Not very exciting.
*/
def mainPage()
{
	if (state.serverInstalled && state.installedVersion != appVersion) return upgradePage()

	dynamicPage(name: "mainPage", uninstall: true, install: true)
	{
		section("<h2>${app.label}</h2>"){}
		if (state?.serverInstalled == null || state.serverInstalled == false)
		{
			section("<b style=\"color:green\">HubConnect Installed!</b>")
			{
				paragraph "Click <i>Done</i> to save then return to the HubConnect app to connect a remote hub."
			}
			return
		}
		
		section
		{
			app(name: "hubClients", appName: "HubConnect Server Instance", namespace: "shackrat", title: "Connect a Hub...", multiple: true)
		}
		section("-= <b>Custom Drivers</b> =-")
		{
			href "customDriverPage", title: "Manage Custom Drivers", required: false
		}
		section("-= <b>Communications</b> =-")
		{
			input "haltComms", "bool", title: "Suspend all communications between this server and all remote hubs?  (Resets at reboot)", required: false, defaultValue: false
		}
		section("-= <b>Utilities</b> =-")
		{
			href "utilitesPage", title: "HubConnect Utilites", required: false
		}
		section("-= <b>HubConnect v${appVersion.major}.${appVersion.minor}</b> =-")
		{
			href "aboutPage", title: "Help Support HubConnect!", description: "HubConnect is provided free of charge for the benefit the Hubitat community.  If you find HubConnect to be a valuable tool, please help support the project."
			paragraph "<span style=\"font-size:.8em\">Server Container v${appVersion.major}.${appVersion.minor}.${appVersion.build} ${appCopyright}</span>"
		}
	}
}


/*
	upgradePage
    
	Purpose: Displays the splash page to force users to initialize the app after an upgrade.
*/
def upgradePage()
{
	dynamicPage(name: "upgradePage", uninstall: false, install: true)
	{
		section("New Version Detected!")
		{
			paragraph "<b style=\"color:green\">HubConnect Server has detected an upgrade that has been installed...</b> <br /> Please click [Done] to complete the installation on the server and all remotes."
		}
	}
}


/*
	customDriverPage
    
	Purpose: Displays the page where shackrat's custom device drivers (SmartPlug, Z-Wave Repeater) are selected to be linked to the controller hub.

	Notes: 	First attempt at organization.
*/
def customDriverPage()
{
	dynamicPage(name: "customDriverPage", uninstall: false, install: false, nextPage: "mainPage")
	{
		section("Currently Installed Custom Drivers")
		{
			state.customDrivers.each
			{
			  groupname, driver ->
				href "editCustomDriver", title: "${driver.driver} (${groupname})", description: "${driver.attr}", params: [groupname: groupname]
			}

			href "createCustomDriver", title: "Add Driver", description: "Define a custom driver type.", params: [newdriver: true]
		}
	}
}


/*
	editCustomDriver
    
	Purpose: Loads the custom driver details into preferences so they can be edited in the UI.

	Notes: 	This requires a page refresh in order to work properly.  Hubitat cache issue??
*/
def editCustomDriver(params)
{
	if (params?.groupname)
	{
		def dtMap = state?.customDrivers[params.groupname]
		if (dtMap)
		{
			app.updateSetting("newDev_AttributeGroup", [type: "string", value: params.groupname])
			app.updateSetting("newDev_DriverName", [type: "string", value: dtMap.driver])

			dtMap.attr.eachWithIndex
			{
			  attr, idx ->
				app.updateSetting("attr_${idx+1}", [type: idx == 0 ? "enum" : "string", value: attr])
			}
		}
		params.editgroup = params.groupname
		params.groupname = null
		dynamicPage(name: "editCustomDriver", uninstall: false, install: false, refreshInterval: 1)
		{
			section("Loading, please wait..."){}
		}
	}
	else driverPage("editCustomDriver", params?.editgroup)
}


/*
	createCustomDriver
    
	Purpose: Clears out all preferences so the UI page displays as if it were new.

	Notes: 	This requires a page refresh in order to work properly.  Hubitat cache issue??
*/
def createCustomDriver(params)
{
	if (params?.newdriver == true)
	{
		app.updateSetting("newDev_AttributeGroup", [type: "string", value: ""])
		app.updateSetting("newDev_DriverName", [type: "string", value: ""])
		app.updateSetting("attr_1", [type: "enum", value: ""])
		17.times
		{
			app.updateSetting("attr_${it+1}", [type: "string", value: ""])
		}
		params.newdriver = false
	}
	
	driverPage("createCustomDriver")
}


/*
	createCustomDriver
    
	Purpose: Displays the page where shackrat's custom device drivers (SmartPlug, Z-Wave Repeater) are selected to be linked to the controller hub.

	Notes: 	First attempt at organization.
*/
def driverPage(pageName, groupName = null)
{
	dynamicPage(name: pageName, uninstall: false, install: false)
	{
		section("<b>-= Configure Driver =- </b>")
		{ 
			if (groupName) paragraph "Attribute Class Name (letters & numbers only):<br />${groupName}"
			else input "newDev_AttributeGroup", "text", title: "Attribute Class Name (letters & numbers only):", required: true, defaultValue: null
			input "newDev_DriverName", "text", title: "Device Driver Name:", required: true, defaultValue: null, submitOnChange: true
		}
		if (newDev_AttributeGroup?.size() && newDev_DriverName?.size())
		{
			section("<b>-= Supported Attributes =- </b>")
			{ 
				input "attr_1", "enum", title: "Attribute 1/18 (This will act as the primary capability for selecting devices):", required: true, multiple: false, options: ATTRIBUTE_TO_SELECTOR, defaultValue: null, submitOnChange: true
				if (attr_1?.size())
				{
					17.times
					{
						input "attr_"+(it+2), "string", title: "Attribute ${it+2}/18:", required: false, multiple: false, defaultValue: null
					}
				}
			}
			if (attr_1?.size())
			{
				section("<b>-= Save Custom Device Type =- </b>")
				{ 
					href "saveCustomDriverPage", title: "Save", description: "Save this custom device type.",  params: [update: groupName]
					href "saveCustomDriverPage", title: "Delete", description: "Delete this custom device type.", params: [delete: groupName]
				}			
			}		
		}
	}
}


/*
	saveCustomDriverPage
    
	Purpose: Displays the page where shackrat's custom device drivers (SmartPlug, Z-Wave Repeater) are selected to be linked to the controller hub.

	Notes: 	First attempt at organization.
*/
def saveCustomDriverPage(params)
{
	if (params?.delete)
	{
		state?.customDrivers.remove(params.delete)
		app.updateSetting("newDev_AttributeGroup", [type: "string", value: ""])
		app.updateSetting("newDev_DriverName", [type: "string", value: ""])
		app.updateSetting("attr_1", [type: "enum", value: ""])
		17.times
		{
			app.updateSetting("attr_${it+1}", [type: "string", value: ""])
		}
		saveCustomDrivers()
		return customDriverPage()
	}
	
	def deviceClass = params?.update != null ? params?.update : newDev_AttributeGroup

	if (deviceClass.size() && newDev_DriverName?.size() && attr_1?.size())
	{
		def attr = []
		18.times
		{
			if (settings."attr_${it}"?.size())
			{
				attr << settings."attr_${it}"
				app.updateSetting("attr_${it}", [type: idx == 0 ? "enum" : "string", value: ""])
			}
		}
		
		def selector = ATTRIBUTE_TO_SELECTOR.find{it.key == attr_1}
		def randString = Long.toUnsignedString(new Random().nextLong(), 16).toUpperCase()
		state.customDrivers[deviceClass] = 
		[
			driver: newDev_DriverName,
			selector: state.customDrivers[deviceClass]?.selector ?: "${randString}_${selector.value}",
			attr: attr
		]

		log.debug state.customDrivers
		saveCustomDrivers()

		app.updateSetting("newDev_AttributeGroup", [type: "string", value: ""])
		app.updateSetting("newDev_DriverName", [type: "string", value: ""])
	}
	else return createCustomDevice()

	log.debug "Saving custom driver map: ${state.customDrivers}"

	dynamicPage(name: "saveCustomDriverPage", uninstall: false, install: false)
	{
		section()
		{
			paragraph title: "Driver Saved!", "The driver definition has been saved."
			href "customDriverPage", title: "Custom Drivers", description: "Manage Custom Drivers"
		}
	}
}


/*
	saveCustomDrivers
    
	Purpose: Saves custom defined drivers to each child app instance.

	Notes: Calls like-named method in child app
*/
def saveCustomDrivers()
{
	childApps.each
	{
	  child ->
		child.saveCustomDrivers(state.customDrivers)
	}
}


/*
	installed
    
	Purpose: Standard install function.

	Notes: Doesn't do much.
*/
def installed()
{
	log.info "${app.name} Installed"

	state.customDrivers = [:]
	state.serverInstalled = true
	state.installedVersion = appVersion

	initialize()
}


/*
	updated
    
	Purpose: Standard update function.

	Notes: Still doesn't do much.
*/
def updated()
{
	log.info "${app.name} Updated"
	
	if (state?.customDrivers == null)
	{
		state.customDrivers = [:]
	}

	if (state?.serverInstalled == null)
	{
		state.serverInstalled = true
	}
	
	unsubscribe()
	initialize()

	// Upgrade the system if necessary
	if (state.installedVersion != appVersion)
	{
		log.info "An upgrade has been detected...  Updating the HubConnect system..."
		childApps.each
		{
		  child ->
			log.info "Running updated() on ${child.label}..."
			child.updated()
		}
	}

	state.installedVersion = appVersion
}


/*
	initialize
    
	Purpose: Initialize the server.

	Notes: Subscribes to the systemStart event to synchronize modes.
*/
def initialize()
{
	log.info "${app.name} Initialized"

	childApps.each
	{
	  child ->
		child.setCommStatus(haltComms)
		log.debug "Found server instance: ${child.label}"
	}

	subscribe(location, "systemStart", systemStartEventHandler)
}


/*
	systemStartEventHandler
    
	Purpose: Event handler for the systemStart event.

	Notes: May do some cleanup in the future, but for now just schedules a mode sync.
*/
def systemStartEventHandler(evt)
{
	app.updateSetting("haltComms",[type: "bool", value: false])
	childApps.each
	{
	  child ->
		child.setCommStatus(false)
		log.debug "System Start: Restoring communications to remote instance: ${child.label}..."
	}
	
	// Push out a mode sync in 20 seconds, after other automations have a chance to determine the correct mode
	runIn(20, "pushSystemMode")
}


/*
	pushSystemMode
    
	Purpose: Pushes the current system mode to all remote hubs.

	Notes: Calls pushCurrentMode() in each server instance.
*/
def pushSystemMode()
{
	log.debug "System Start: Synchronizing modes across all hubs..."
	childApps.each
	{
	  child ->
		child.pushCurrentMode()
	}
}


/*
	hsmSetState
    
	Purpose: Sets the HSM state for each child instance to <state>.

	Notes: Calls realtimeHSMChangeHandler with a "fake" event.
*/
def setHSMState(hsmState, appId)
{
	log.debug "Setting HSM state across all hubs to ${hsmState}..."
	childApps.each
	{
	  child ->
		if (child.id != appId) child.realtimeHSMChangeHandler([value: hsmState, data: 0])
	}
}


/*
	hsmSendAlert
    
	Purpose: Pushes an HSM alert to all remotes.

	Notes: Calls pushCurrentMode() in each server instance.
*/
def hsmSendAlert(hsmAlertText, appId)
{
	log.debug "Sending HSM alert state across all hubs to ${hsmState}..."
	childApps.each
	{
	  child ->
		if (child.id != appId) child.realtimeHSMChangeHandler([value: hsmState, data: 0])
	}
}


/*
	remoteHubControl
    
	Purpose: Helper function to power off/reboot the remote hub.

	Notes: Supported remote commands: reboot, shutdown
*/
def remoteHubControl(child, command)
{
	def port = child.remoteType == "homebridge" ? ":20009" : ""
	def requestParams =
	[
		uri:  "http://${child.clientIP}${port}/hub/${command}",
		requestContentType: "text/html",
		body: []
	]

	httpPost(requestParams)
	{
	  response ->
		if (response?.status != 200)
		{
			log.error "httpPost() request failed with error ${response?.status}"
		}
	}
}


/*
	utilitiesPage

	Purpose: Displays a menu of utilities.
*/
def utilitesPage(params)
{
	atomicState.versionReportStatus = null
	if (params?.debug != null)
	{
		childApps.each
		{
		  child ->
			child.updateSetting("enableDebug", [type: "bool", value: params.debug])	
			log.debug "${params.debug ? "Enabling" : "Disabling"} debug logging on ${child.label}"
		}
	}

	dynamicPage(name: "utilitesPage", title: "HubConnect Utilites", uninstall: false, install: false)
	{
		section("<b>-= Utilites =-</b>")
		{
			href "modeReportPage", title: "System Mode Report", description: "Lists all modes configured on each remote hub..."
			href "versionReportLoadingPage", title: "App & Driver Version Report", description: "Displays all app and driver versions configured on each hub...  (May be slow to load)"
			href "hubUtilitiesPage", title: "Remote Hub Utilities", description: "Shutdown/reboot hubs on the same LAN..."
		}
		section("<b>-= Master Debug Control =-</b>")
		{
			if (params?.debug != null) paragraph "Debug has been ${params.debug ? "enabled" : "disabled"} for all hubs."
			href "utilitesPage", title: "Enable debug logging for all instances?", description: "Click to enable", params: [debug: true]
			href "utilitesPage", title: "Disable debug logging for all instances?", description: "Click to disable",  params: [debug: false]
		}
		section()
		{
			href "mainPage", title: "Home", description: "Return to HubConnect main menu..."
		}
	}
}


/*
	modeReportPage

	Purpose: Displays a report of configured system modes.
*/
def modeReportPage()
{
	// Query all children for modes
	def allModes = [:]

	allModes[0] = [label: "Server Hub", modes: location.modes.collect{it?.name}.sort().join(", "), active: location.mode, pushModes: "-", receiveModes: "-"]
	childApps.each
	{
	  child ->
		def remoteModes = child.getAllRemoteModes()
		allModes[child.id] = [label: child.label, modes: remoteModes?.status == "error" ? ["<span style=\"color:red\">error</span>"] : remoteModes?.modes.collect{it?.name}.sort().join(", "), active: remoteModes.active, pushModes: child.pushModes, receiveModes: child.receiveModes]
	}
	
	dynamicPage(name: "modeReportPage", title: "System Mode Report", uninstall: false, install: false)
	{
		section()
		{
			def html = "<table style=\"width:100%\"><thead><tr><th>Hub</th><th>Active</th><th>TX</th><th>RX</th><th>Configured Modes</th></tr></thead><tbody>"
			allModes.each
			{
			  k, v->
				html += "<tr><td>${v?.label}</td><td>${v?.active}</td><td>${v.pushModes}</td><td>${v.receiveModes}</td><td>${v?.modes}</td></tr>"
			}
			paragraph "${html}</tbody></table>"
			paragraph "TX = Send Mode Change to Remote"
			paragraph "RX = Receive Mode Change from Remote"
		}
		section()
		{
			href "utilitesPage", title: "Utilities", description: "Return to Utilities menu..."
			href "mainPage", title: "Home", description: "Return to HubConnect main menu..."
		}
	}
}


/*
	getVersionReportData

	Purpose: Queries all hubs for current app & driver version data.
*/
def getVersionReportData()
{
	// Get the latest available versions
	versionCheck()

	// Server hub apps & drivers
	def serverDrivers = [:]
	childApps.each
	{
  	  child ->
		child.getChildDevices()?.each
		{
 	     device ->
			if (serverDrivers[device.typeName] == null) serverDrivers[device.typeName] = device.getDriverVersion()
		}
	}
	
	def allHubs = [:]	
	allHubs[0] = [name: "Server Hub", report: [apps: [[appName: app.label, appVersion: appVersion], [appName: childApps?.first()?.name, appVersion: childApps?.first()?.getAppVersion()]], drivers: serverDrivers]]
	
	// Remote hubs apps & drivers	
	childApps.each
	{
	  child ->
		atomicState.versionReportStatus = "Gathering report data for ${child.label}..."
		allHubs[child.id] = [name: child.label, report: child.getAllVersions()]
	}
	state.versionReport = allHubs
	
	atomicState.versionReportStatus = "Checking for latest versions..."

	// This is to allow another 5 seconds for the version check to complete...  It should never be needed!
	def lp = 0
	while (atomicState.currentVersions == null)
	{
		pauseExecution(1000)
		if (++lp > 5) break
	}
	
	atomicState.versionReportStatus = "Rendering..."
}


/*
	versionReportLoadingPage

	Purpose: Displays loading page while data is pulled from hubs.
*/
def versionReportLoadingPage()
{
	if (atomicState.versionReportStatus == null)
	{
		atomicState.versionReportStatus = "Gathering report data for for Server Hub..."
		runIn(1, getVersionReportData)
	}
	else if (atomicState.versionReportStatus == "Rendering...") return versionReportPage()

	dynamicPage(name: "versionReportLoadingPage", title: "Loading system version report...", uninstall: false, install: false, refreshInterval: 1)
	{
		section()
		{
			paragraph "${atomicState.versionReportStatus}"
		}
	}
}


/*
	versionReportPage

	Purpose: Displays a report of app & driver versions.
*/
def versionReportPage()
{
	atomicState.versionReportStatus = null

	dynamicPage(name: "versionReportPage", title: "System Version Report", uninstall: false, install: false)
	{
		state.versionReport.each
		{
		  k, v ->
			section("<b>=== ${v.name} === </b>")
			{
				log.trace v
				if (v?.report == null)
				{
					paragraph "Hub is not reachable or remote client is not reporting version information."
					return
				}
				def html = "<table style=\"width:100%\"><thead><tr><th style=\"width:46%\">Component</th><th style=\"width:10%\">Type</th><th style=\"width:16%\">Platform</th><th style=\"width:14%\">Installed</th><th style=\"width:14%\">Latest</th></tr></thead><tbody>"
				v.report?.apps?.sort()?.each
				{
					def latest = (it?.appVersion?.platform != null && state?.currentVersions != null) ? atomicState.currentVersions?.(it.appVersion.platform.toLowerCase())?.app?.(it.appName) : null
					def latestVersion = latest ?  "<span style=\"color:${isNewer(latest, it?.appVersion) ? "red" : "green"}\">${latest.major}.${latest.minor}.${latest.build}</span>" : ""
					html += "<tr><td>${it?.appName}</td><td>app</td><td>${it?.appVersion?.platform}</td><td>${it?.appVersion?.major}.${it?.appVersion?.minor}.${it?.appVersion?.build}</td><td>${latestVersion}</td></tr>"
				}
				v.report?.drivers?.sort()?.each
				{
			 	  dk, dv ->
	
					def latest = (dv?.platform != null && state?.currentVersions != null) ? atomicState.currentVersions?.(dv.platform.toLowerCase())?.driver?.(dk?.toString()) : null
					def latestVersion = latest ?  "<span style=\"color:${isNewer(latest, dv) ? "red" : "green"}\">${latest.major}.${latest.minor}.${latest.build}</span>" : ""
					html += "<tr><td>${dk}</td><td>driver</td><td>${dv?.platform}</td><td>${dv?.major}.${dv?.minor}.${dv?.build}</td><td>${latestVersion}</td></tr>"
				}
				paragraph "${html}</tbody></table>"
			}
		}	
		section()
		{
			paragraph "Note: The version report can only report on drivers that are currently in use by HubConnect devices."
			href "versionReportLoadingPage", title: "Refresh", description: "Refresh this report with the latest data...  (May be slow to load)"
			href "utilitesPage", title: "Utilities", description: "Return to Utilities menu..."
			href "mainPage", title: "Home", description: "Return to HubConnect main menu..."
		}
	}
}


/*
	hubUtilitiesPage

	Purpose: Reboots a remote hub that is on the same LAN.
*/
def hubUtilitiesPage()
{
	// Rebooting or shutting down a hub?
	childApps.each
	{
	  child ->
		if (settings."reboot_${child.id}" == true)
		{
			log.warn "Sending the reboot command to ${child.label}."
			app.updateSetting("reboot_${child.id}",[type: "bool", value: false])
			remoteHubControl(child, "reboot")
		}
		else if (settings."shutdown_${child.id}" == true)
		{
			log.warn "Sending the shutdown command to ${child.label}."
			app.updateSetting("shutdown_${child.id}",[type: "bool", value: false])
			remoteHubControl(child, "shutdown")
		}
	}

	dynamicPage(name: "hubUtilitiesPage", title: "Remote Hub Utilities", uninstall: false, install: false)
	{
		section()
		{
			paragraph "<b>All commands take effect immediately!</b>"
		}

		childApps.each
		{
		  child ->
			section("=== ${child.label} ===")
			{
				if (child.remoteType == "local" || child.remoteType == "homebridge")
				{
					input "reboot_${child.id}", "bool", title: "Reboot this hub?", required: false, defaultValue: false, submitOnChange: true
					if (child.remoteType == "local") input "shutdown_${child.id}", "bool", title: "Shutdown this hub?", required: false, defaultValue: false, submitOnChange: true
				}
				else paragraph "This is a remote hub and cannot be controlled."
			}
		}
		section("<b>-= Navigation =-</b>")
		{
			href "utilitesPage", title: "Utilities", description: "Return to Utilities menu..."
			href "mainPage", title: "Home", description: "Return to HubConnect main menu..."
		}
	}
}


/*
	aboutPage

	Purpose: Displays the about page with credits.
*/
def aboutPage()
{
	dynamicPage(name: "aboutPage", title: "HubConnect v${appVersion.major}.${appVersion.minor}", uninstall: false, install: false)
	{
		section()
		{
			paragraph "HubConnect is provided free for personal and non-commercial use.  Countless hours went into the development and testing of this project.  If you like it and would like to see it succeed, or see more apps like this in the future, please consider making a small donation to the cause."
			href "donate", style:"embedded", title: "Please consider making a \$20 or \$40 donation to show your support!", image: "http://irisusers.com/hubitat/hubconnect/donate-icon.png", url: "https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=T63P46UYH2TJC&source=url"
		}
		section()
		{
			href "mainPage", title: "Home", description: "Return to HubConnect main menu..."
			paragraph "<span style=\"font-size:.8em\">Server Container  v${appVersion.major}.${appVersion.minor}.${appVersion.build} ${appCopyright}</span>"
		}
	}
}


/*
	versionCheck
    
	Purpose: Fetches the latest available module versions.
*/
def versionCheck()
{
	atomicState.currentVersions = null
	def token = URLEncoder.encode(location.hubs[0].toString())

	def requestParams =
	[
		uri: "http://irisusers.com/hubitat/hubconnect/latest.php?s=${token}",
		requestContentType: 'application/json',
		contentType: 'application/json'
	]
    
	asynchttpGet("versionCheckResponse", requestParams)
}


/*
	versionCheckResponse
    
	Purpose: Stores the retreived version information to state.
*/
def versionCheckResponse(response, data)
{
	if (response?.status == 200 && response.json.versions != null)
	{
		atomicState.currentVersions = response.json.versions
	}
}

def isNewer(latest, installed) { (latest.major.toInteger() > installed.major ||  (latest.major.toInteger() == installed.major && latest.minor.toInteger() > installed.minor) || (latest.major.toInteger() == installed.major && latest.minor.toInteger() == installed.minor && latest.build.toInteger() > installed.build)) ? true : false }
def getAppVersion() {[platform: "Hubitat", major: 1, minor: 4, build: 6001]}
def getAppCopyright(){"&copy; 2019 Steve White, Retail Media Concepts LLC <a href=\"https://github.com/shackrat/Hubitat-Private/blob/master/HubConnect/License%20Agreement.md\" target=\"_blank\">HubConnect License Agreement</a>"}
