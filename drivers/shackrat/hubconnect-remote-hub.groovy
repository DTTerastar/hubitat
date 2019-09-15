/*
 *	Copyright 2019 Steve White
 *
 *	Licensed under the Apache License, Version 2.0 (the "License"); you may not
 *	use this file except in compliance with the License. You may obtain a copy
 *	of the License at:
 *
 *		http://www.apache.org/licenses/LICENSE-2.0
 *
 *	Unless required by applicable law or agreed to in writing, software
 *	distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *	WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *	License for the specific language governing permissions and limitations
 *	under the License.
 *
 *
 */
import hubitat.helper.InterfaceUtils
metadata 
{
	definition(name: "HubConnect Remote Hub", namespace: "shackrat", author: "Steve White", importUrl: "https://raw.githubusercontent.com/HubitatCommunity/HubConnect/master/Hubitat/drivers/HubConnect-Remote-Hub.groovy")
	{
		capability "Presence Sensor"
		capability "Switch"
		capability "Initialize"

		attribute "eventSocketStatus", "string"
		attribute "connectionType", "string"
		attribute "version", "string"
		attribute "hsmStatus", "enum", ["armedAway", "armingAway",  "armedHome", "armingHome", "armedNight", "armingNight", "disarmed", "allDisarmed"]
		attribute "modeStatus", "string"
		
		command "pushCurrentMode"
	}
}


/*
	installed
    
	Doesn't do much other than call initialize().
*/
def installed()
{
	initialize()
	state.connectionType = "http"
}


/*
	updated
    
	Doesn't do much other than call initialize().
*/
def updated()
{
	initialize()
	if (state.connectionType == null) state.connectionType = "http"
}


/*
	initialize
    
	Doesn't do much other than call refresh().
*/
def initialize()
{
	log.trace "Initialize virtual Hub device..."

	state.connectionAttempts = 0

	if (state.connectionType == "socket" && device.currentSwitch == "on")
	{
		def ipParts = device.deviceNetworkId.split("-")

		// Connect to the remote hubs event socket
		try
		{
			log.info "Attempting socket connection to ${device.label ?: device.name} (${state.connectionAttempts})"
			InterfaceUtils.webSocketConnect(device, "ws://${ipParts[1]}/eventsocket")
		} 
		catch(errorException)
		{
			log.error "WebSocket connect to remote hub failed: ${errorException.message}"
    	}
	}
	sendEvent([name: "connectionType", value: state.connectionType])
	sendEvent([name: "version", value: "v${driverVersion.major}.${driverVersion.minor}.${driverVersion.build}"])
}


/*
	webSocketStatus
    
	Called by the websocket to the remote Hubitat hub.
*/
def webSocketStatus(String socketStatus)
{
	if (socketStatus.startsWith("status: open"))
	{
		log.info "Connected to ${device.label ?: device.name}"
		state.connectionAttempts = 0
		sendEvent([name: "eventSocketStatus", value: "connected"])
		return
    } 
	else if (socketStatus.startsWith("status: closing"))
	{
		log.info "Closing connection to ${device.label ?: device.name}"
		sendEvent([name: "eventSocketStatus", value: "closed"])
		return
	} 
	else if (socketStatus.startsWith("failure:"))
	{
		log.warn "Connection to ${device.label ?: device.name} has failed with error [${socketStatus}].  Attempting to reconnect..."
    } 
	else
	{
		log.warn "Connection to ${device.label ?: device.name} has been lost due to an unknown error.  Attempting to reconnect..."
    }

	state.connectionAttempts = state.connectionAttempts + 1
	runIn(10, "initialize")
}


/*
	setConnectionType
    
	Called by Server Instance to set the connection type.
*/
def setConnectionType(connType)
{
	state.connectionType = connType
	
	// Switch connections
	if (connType == "http" && device.currentEventSocketStatus == "connected")
	{
		InterfaceUtils.webSocketClose(device)
		sendEvent([name: "eventSocketStatus", value: "disconnected"])
		sendEvent([name: "connectionType", value: "http"])
	}
	else if (connType == "socket" && device.currentEventSocketStatus != "connected")
	{
		initialize()
	}

	log.info "Switching connection to ${device.label ?: device.name} to ${connType}"	
}


/*
	parse
    
	In a virtual world this should never be called.
*/
def parse(String description)
{
	def eventData = null
	try
	{
		eventData = parseJson(description)
	}
	catch(errorException)
	{
		log.error "Failed to parse event data: ${errorException}"
    }

	if (eventData?.source.length() == 6) // "DEVICE"
	{
		parent.wsSendEvent((Map) eventData)
	}
}


/*
	on
    
	Enable communications from the remote hub.
*/
def on()
{
	parent.setCommStatus(false)
	if (state.connectionType == "socket")
	{
		sendEvent([name: "eventSocketStatus", value: "connecting"])
		runIn(2, "initialize")
	}
}


/*
	off
    
	Disable communications from the remote hub.
*/
def off()
{
	parent.setCommStatus(true)
	if (state.connectionType == "socket")
	{
		InterfaceUtils.webSocketClose(device)
	}
}


/*
	pushCurrentMode
    
	Pushes the current mode of the server hub to the remote hub.
*/
def pushCurrentMode()
{
	parent.pushCurrentMode()
}
def getDriverVersion() {[platform: "Hubitat", major: 1, minor:4, build: 0]}