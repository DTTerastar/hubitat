metadata {
	// v0.2b
    definition (name: "MQTT Client", namespace: "ukusa", author: "Kevin Hawkins") {
        capability "Initialize"
		capability "Telnet"  // temporary kludge

        command "publishMsg", ["String","String"]
		command "subscribeTopic",["String"]
		
		//command "createChild", ["String"]  // not using device children currently
		//command "deleteChild", ["String"]
		//command "setStateTopic", ["String","String"]
		//command "setCommandTopic", ["String","String"]
		
		command "reset"
		command "setStateVar"; ["String","String"]
		attribute "RXTopic", "string"
		attribute "OnOffDev", "string"
		attribute "DimDev", "string"
		//attribute "OnOff", "string"
    }
    preferences {
		input name: "MQTTBroker", type: "text", title: "MQTT Broker Address", description: "e.g. tcp://192.168.1.17:1883", required: true, displayDuringSetup: true
		input name: "username", type: "text", title: "MQTT Username", description: "(blank if none)", required: false, displayDuringSetup: true
		input name: "password", type: "password", title: "MQTT Password", description: "(blank if none)", required: false, displayDuringSetup: true
		//input name: "clientID", type: "text", title: "MQTT Client ID", description: "(blank for auto)",defaultValue: "Hubitat Elevation", required: false, displayDuringSetup: true
		//input name: "spare", type: "text", title: "MQTT user", description: "User (blank if none)", required: false, displayDuringSetup: true
		//input name: "Retain", type: "bool", title: "Retain published states", required: true, defaultValue: false
    }

}

import groovy.transform.Field  // TODO needed ?

import static hubitat.helper.InterfaceUtils.alphaV1mqttConnect
import static hubitat.helper.InterfaceUtils.alphaV1mqttDisconnect
import static hubitat.helper.InterfaceUtils.alphaV1mqttSubscribe
import static hubitat.helper.InterfaceUtils.alphaV1mqttUnsubscribe
import static hubitat.helper.InterfaceUtils.alphaV1parseMqttMessage
import static hubitat.helper.InterfaceUtils.alphaV1mqttPublish



def installed() {
    log ("installed...", "WARN")
}

def initialize() {
	//def hub = location.hubs[0]
	if (state.normHubName==null) state.normHubName = "temporary"
    try {
		alphaV1mqttConnect(device, settings?.MQTTBroker, "Hubitat_${state.normHubName}", settings?.username,settings?.password)
        //give it a chance to start
        pauseExecution(1000)
    } catch(e) {
        log ("initialize error: ${e.message}", "ERROR")
		//TODO retries
    }
	log.info "MQTT client alpha2b initialised"
	log ("Connected as Hubitat_${state.normHubName} to MQTT broker ${settings?.MQTTBroker}", "INFO")
	state.connectionAttempts = 0
	state.delay=100  //delay between events - increase if you have a lot of discovered MQTT devices > 100
    state.device=""
	
	//state.numDimDevices=0  // not using local devices
	//state.DimDevices=[]
	//state.numOnOffDevices=0
    //state.OnOffDevices=[]
}

//TODO Improve error handling

def mqttClientStatus(String status){
    log ("mqttStatus- error: ${status}", "ERROR")
}



def publishMsg(String topic, String payload,int qos = 1, boolean retained = false ) {
    alphaV1mqttPublish(device, topic, payload, qos, retained)
}

def subscribeTopic (String s) {
	log ("MQTT subscribing to: " + s, "INFO")
	alphaV1mqttSubscribe(device, s)
}

def updated() {
    log ("MQTT client updated...", "INFO")
    initialize()
}

def uninstalled() {
    log ("disconnecting from mqtt", "INFO")
    alphaV1mqttDisconnect(device)
}

// Key method Handles all the data back from MQTT subscriptions
def parse(String description) {
	def topic=alphaV1parseMqttMessage(description).topic.split('/')
	def payload=alphaV1parseMqttMessage(description).payload.split(',')
	if (topic[0]==state.HAStatestreamTopic)
		{
			if (topic[1]=='sensor') { // This is a HA discovered sensor component // Not used in alpha2

				if (topic[3]=='state'){
				//log ("RX::Sensor state::  " + topic[2] + " is " + payload[0], "DEBUG")
				def evt10 = createEvent(name: "Sensor", value: topic[2], data: [status: payload[0]])  //, key2: payload])
				pause (state.delay) // pace
				return evt10
				}
				else if (topic[3]=='friendly_name'){ 
				//log ("Device ${topic[2]} is a HA sensor and is called " +payload[0], "INFO")
				def evt11= createEvent([name: "HASensorDev", value: topic[2], data: [label: payload[0]]])
				pause (state.delay) // pace
				return evt11
				}
				else if (topic[3]=='unit_of_measurement'){ 
				def evt11= createEvent([name: "SensorUnit", value: topic[2], data: [label: payload[0]]])
				pause (state.delay) // pace
				return evt11
				}
			}

		else if (topic[1]=='switch') { // This is a HA discovered switch component
			if (topic[3]=='friendly_name') {
				log ("Device ${topic[2]} is a HA switch and is called " +payload[0], "TRACE")
				def evt3= createEvent([name: "HASwitchDev", value: topic[2], data: [label: payload[0]]])
				pause (state.delay) // pace
				return evt3
			}
			if (topic[3]=='state'){
				def evt4 = createEvent(name: "OnOff", value: topic[2], data: [status: payload[0]])  //, key2: payload])
				pause (state.delay) // pace
				return evt4
			}
			log ("Unhandled HA MQTT parse  ${topic[0]}  ${topic[1]}  ${topic[2]}  ${topic[3]} - ${payload[0]}", "WARN")
			return
		}

		else if (topic[1]=='light') { // This is a HA discovered light component

			if (topic[3]=='friendly_name')
			{
				friendlyName=payload[0]
				if (friendlyName[0]=='"')  // remove double quotes
					{
						friendlyName=friendlyName.substring(1)
						friendlyName=friendlyName.substring(0, friendlyName.length() - 1)
				}
				log ("Device ${topic[2]} is a HA light and is called " +payload[0] + " " + friendlyName,"TRACE")
				def evt5= createEvent([name: "HALightDev", value: topic[2], data: [label: friendlyName]])
				pause (state.delay) // pace
				return evt5
			}
			if (topic[3]=='state'){
				def evt6 = createEvent(name: "OnOff", value: topic[2], data: [status: payload[0]])  //, key2: payload])
				pause (state.delay) // pace
				return evt6
			}
			if (topic[3]=='brightness'){
				float dLevel = Float.parseFloat(payload[0])/2.55
				int iLevel = (int) dLevel.round()
				sLevel=iLevel.toString()
				def evt9 = createEvent(name: "Dim", value: topic[2], data: [level: sLevel])
				pause (state.delay) // pace
				return evt9
			}
			log ("Unhandled HA MQTT parse  ${topic[0]}  ${topic[1]}  ${topic[2]}  ${topic[3]} - ${payload[0]}", "WARN")
			return
		}  // not a HA light
			log ("Unhandled HA MQTT parse  ${topic[0]}  ${topic[1]}  ${topic[2]}  ${topic[3]} - ${payload[0]}", "WARN")

		} // not HA
	
	else if (topic[0]=="homie" && topic[1]== "${state.normHubName}"){ //local - look for incoming 'set' commands that control Hubitat devices here	
		if (topic[4]=="set") {
				 def evt12 = createEvent(name: "Command", value: topic[2], data: [state: payload[0], cmd: topic[3]])
				 pause (state.delay) // pace
			log.error "Ive got this"
				 return evt12					
				}
				else {
					log ("Received unexpected message from homie " + topic + " " + payload, "WARN")
				}
			}
	
	else if (topic[0]=="homie" && topic[1]== state.homieDeviceDiscovery)  {   // remote subscribed homie 
			if (topic[2]=='$nodes') {
				log ("============= ${payload.size()} homie entries for device ${topic[1]} =============",, "INFO")
				return
			}
			if (topic[3]=='$properties') {  //properties for a specific device (topic[2])  //TODO messy and assumptive - errors on homie/device/$nodes outofbounds
				found = alphaV1parseMqttMessage(description).payload.indexOf("dim")
				if (found >=0) {
					//log ("Device ${topic[2]} has dim capability", "INFO")
					buildDimDevices(topic[2])
					}
			else  {  //TODO need more checks
				found = alphaV1parseMqttMessage(description).payload.indexOf("onoff")
				if (found >=0) {
					//log ("Device ${topic[2]} has onoff capability", "INFO")
					buildOnOffDevices(topic[2])
					}
				}
				return
				}	

			 if (topic[3]=='$name') {
				 def evt0 = createEvent(name: "Label", value: topic[2], data: [label: payload[0]])
				 pause (state.delay) // pace
				 return evt0
			 }
		
			if (topic[3]=='onoff') {
				log("Received homie OnOff state from MQTT ${topic[2]} " + status, "TRACE")
				def evt1 = createEvent(name: "OnOff", value: topic[2], data: [status: payload[0], topic: alphaV1parseMqttMessage(description).topic])  //, key2: payload])
				pause (state.delay) // pace
				return evt1
			}

			if (topic[3]=='dim') {  // TODO is this level handling problematic assuming 1.0 ?
				float convertedNumber = Float.parseFloat(payload[0])*100
				int intLevel = convertedNumber = convertedNumber.round()
				adjLevel=intLevel.toString()
				log("Received homie Dim state from MQTT ${topic[2]} " + payload + " " +adjLevel,"TRACE")
				def evt2 = createEvent(name: "Dim", value: topic[2], data: [state: "level", level: adjLevel, topic: alphaV1parseMqttMessage(description).topic])
				pause (state.delay) // pace
				return evt2
			}
	} // end remote homie

	else {  // unhandled parse messages arrive here
		log ("ad hoc MQTT parse  alphaV1parseMqttMessage(description).topic       ${payload[0]}", "DEBUG")
		sendEvent (name: "Lookup", value: payload[0], data: [topic: alphaV1parseMqttMessage(description).topic]) 

	}
}

def reset()
	{
		alphaV1mqttDisconnect(device)
		log ("Resetting MQTT connection", "INFO")
		initialize()
	}
	
def setStateVar(var,value)
	{
	if (var == "logLevel"){
		state.logLevel = value.toInteger()
		log ("Log Level set to " + value,"INFO")
	}
	else if (var == "homieDevice"){
		state.homieDeviceDiscovery = value
		log ("homie discovery is for device: " + value, "INFO")
	}
	else if (var == "HAStatestreamTopic"){
		state.HAStatestreamTopic = value
		log ("HAStatestreamTopic is " + value,"INFO")
	}
	else if (var == "normHubName") {
		state.normHubName = value
		log ("Normalised hub name is " + value,"INFO")	
	}
}

/*   // This was for when using device children
def createChild (String Name)  // For child devices of this device
	{
	log ("Is this createChild ever called ??", "ERROR ")
	//deleteChildDevice("MQTT-"+ Name)	
	try { 
   		addChildDevice("ukusa", "MQTT Switch", "MQTT-"+Name, [name: Name, isComponent: false])	
	} catch(Exception ex) {
      	//log ("addChild failed for " +Name + " " +(ex.toString()), "DEBUG")
}	
	//addChildDevice("ukusa", "MQTT Switch", "MQTT-"+Name, [name: Name, isComponent: false])	
	}

def deleteChild (String Name) // For child devices of this device
	{
	log ("Deleting child MQTT-${Name}", "DEBUG")
	deleteChildDevice("MQTT-"+ Name)
	}

def setStateTopic (String Name, String Topic)
{
	log ("Setting state topic for " + Name + " " + Topic, "TRACE")
	def child= getChildDevice("MQTT-"+Name)
	//log ("Child device id: $child.id", "TRACE")
	// TODO only do this if valid id
	child.setStateTopic(Topic)
}
def setCommandTopic (String Name, String Topic)
{
	//return
	log ("Setting state topic for " + Name + " " + Topic, "TRACE")
	def child= getChildDevice("MQTT-"+Name)
	//log ("Child device id: $child.id", "TRACE")
	// TODO only do this if valid id
	child.setCommandTopic(Topic)
}
*/

def buildOnOffDevices (String device)
{
	pause (state.delay) // pace
	sendEvent([name: "OnOffDev", value: device])
}

def buildDimDevices (String device)
{
	pause (state.delay) // pace
	sendEvent([name: "DimDev", value: device])
}

def pause(millis) {
	pauseExecution(millis)	
}

def log(data, type) {

	data = "MQTT> ${data ?: ''}"
    if (determineLogLevel(type) >= state.logLevel) {
        switch (type?.toUpperCase()) {
            case "TRACE":
                log.trace "${data}"
                break
            case "DEBUG":
                log.debug "${data}"
                break
            case "INFO":
                log.info "${data}"
                break
            case "WARN":
                log.warn "${data}"
                break
            case "ERROR":
                log.error "${data}"
                break
			case "DISABLED":
			    break
            default:
                log.error "MQTT -- ${device.label} -- Invalid Log Setting"
        }
    }
}

private determineLogLevel(data) {
    switch (data?.toUpperCase()) {
        case "TRACE":
            return 0
            break
        case "DEBUG":
            return 1
            break
        case "INFO":
            return 2
            break
        case "WARN":
            return 3
            break
        case "ERROR":
        	return 4
            break
		case "DISABLED":
		    return 5
		    break
        default:
            return 1
    }
}
