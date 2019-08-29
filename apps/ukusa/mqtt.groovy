
//  Logging code was adapted from Eric Vitale's ST LIFX application, with thanks.
//  Copyright 2016 ericvitale@gmail.com

//    Support email support@ukusa.co.uk   - please do not hassle Hubitat with any support Q's as their client is an alpha release

/* ==============================================KNOWN ISSUES============================================== 


TODO's !!

maxLevels with decimal points are not handled completely. This may impact homie discovery and scaling of level devices

No current recovery after an MQTT disconnect although have never seen one. Subscriptions are lost and the app needs restarting.
Need to consider automatic restart or reconnect with a retained list of all subs.

==========================================================================================================
*/

definition(
	name: "MQTT",
	namespace: "ukusa",
	author: "Kevin Hawkins",
	description: "Links MQTT with HE devices",
	category: "My Apps",
	iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/App-BigButtonsAndSwitches.png",
	iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/App-BigButtonsAndSwitches@2x.png",
	iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/App-BigButtonsAndSwitches@2x.png"
)

preferences {
    	//section {
		page(name: "configuration", title: "MQTT Configuration", nextPage: "discovery", uninstall: false){
			section{
				input "mqtt", "device.MQTTClient", required: true, title: "MQTT Broker", submitOnChange: true
				input name: "hubName",  type: "text", title: "Hub Name", description: "  choose a unique name for this Hubitat Hub", required: true, displayDuringSetup: true, submitOnChange: false
				input "adhoc", "capability.telnet", multiple: true, required: false, title: "MQTT virtual devices", submitOnChange: false
			}
			section{
				input "mqttRemoveDevices", "bool", title: "Purge Discovered Devices <br> WARNING: Setting this will delete all MQTT 'discovered devices' when you click 'Done'. However your selected devices in HA and homie discovery will be re-added automatically but you will need to re-add them manually in your Dashboards. Your selected 'published' devices and any manually created devices will not be affected", required: true, defaultValue: false, submitOnChange: true
				input "logging", "enum", title: "Log Level", required: false, defaultValue: "INFO", options: ["TRACE", "DEBUG", "INFO", "WARN", "ERROR", "DISABLED"]

			}
			
			section ("Publish these Hubitat devices to MQTT") {
				input "switches", "capability.switch", multiple: true, required: false, title: "HE Switch Devices > MQTT", submitOnChange: true 
				//input "test", "capability.switch  && capability.dim", multiple: true, required: false, title: "Test", submitOnChange: true 
				input "dimmers", "capability.switchLevel", multiple: true, required: false, title: "HE Dimmer devices > MQTT", submitOnChange: true 
				}
				
			section ("MQTT Publish Formats"){
				input "HEBasic", "bool", title: "Hubitat basic MQTT", required: true, defaultValue: false, submitOnChange: true 
				//input "HADiscovery", "bool", title: "HA MQTT discovery protocol (not yet implemented)", required: true, defaultValue: false, submitOnChange: true 	
				input "homiePublish", "bool", title: "homie 3 protocol", required: true, defaultValue: false, submitOnChange: true
				}
			
			}
	}
	page(name: "discovery", title: "Select MQTT discovered devices", install: true, uninstall: true)

		
def discovery() {
    dynamicPage(name: "discovery", title: "", install: true, nextPage:"hrefPage", uninstall: false) { 
		
		
	section ("MQTT Discovery Protocols"){
			}

		
	section ("homie devices"){
		input "homieDiscovery", "bool", title: "homie 3 protocol", required: true, defaultValue: false, submitOnChange: true
		input name: "homieDevice", type: "text", title: "homie device topic name", description: "... to import devices from", required: false, displayDuringSetup: false
		//input "Homie_onoff", "enum", multiple: true, title: "Discovered homie switches [" + atomicState.onoffDevices.size()+"]", options: atomicState.onoffDevices
		input "Homie_onoff", "enum", multiple: true, title: "Discovered homie switches [removed]", options: atomicState.onoffDevices
		//input "Homie_dim", "enum", multiple: true, title: "Discovered homie dimmers [" + atomicState.dimDevices.size()+"]", options: atomicState.dimDevices
		input "Homie_dim", "enum", multiple: true, title: "Discovered homie dimmers [removed]", options: atomicState.dimDevices
		
		}
	section {}
	section ("Home Assistant devices") {
		input "HAStatestream", "bool", title: "HA statestream", required: true, defaultValue: false, submitOnChange: true 
		input name: "HAStatestreamTopic", type: "text", title: "HA Statestream topic", description: "", required: false, displayDuringSetup: false
		//input "HA_Switches", "enum", multiple: true, title: "Discovered HA switches [" + atomicState.HASwitchDevices.size()+"]", options: atomicState.HASwitchDevices
		//input "HA_Lights", "enum", multiple: true, title: "Discovered HA lights [" + atomicState.HALightDevices.size()+"]" , options: atomicState.HALightDevices
		input "HA_Switches", "enum", multiple: true, title: "Discovered HA switches [removed]", options: atomicState.HASwitchDevices
		input "HA_Lights", "enum", multiple: true, title: "Discovered HA lights [removed]" , options: atomicState.HALightDevices
	   
		}
	
	}
}

def installed()
{
	log ( "${app.name} Installed","INFO")
	atomicState.onoffDevices=[] 
	atomicState.dimDevices=[]
	atomicState.HASwitchDevices=[]
	atomicState.HALightDevices=[]
	atomicState.topicMap=[:]
	atomicState.nameMap=[:]
	wipe() // also initialises 
	initialize()
}

def updated()
{
	log ("${app.name} Updated", "INFO")
	unsubscribe()
    atomicState.count=0
	unschedule()
	initialize()
}

def uninstalled() {
    log ("Deleting all child devices", "WARN")
    wipe()
	removeAllChildDevices()
}	


def initialize()
{
	log.info "${app.name} alpha2b Initialized"
	//def hub = location.hubs[0]
	atomicState.normHubName = normalize(settings?.hubName)
	log ("Hubitat hub name is : " + settings?.hubName,"INFO")
	mqtt.setStateVar("normHubName",normalize(settings?.hubName))  // pass normalized name to MQTT client
	if (settings?.HAStatestream) mqtt.setStateVar ("HAStatestreamTopic",settings?.HAStatestreamTopic)
	log("Initializing...", "DEBUG")
	atomicState.started=false
	if (settings?.homiePublish)
	{
		mqtt.publishMsg ("homie/${atomicState.normHubName}/" +'$state','init',1,true)
		mqtt.publishMsg ("homie/${atomicState.normHubName}/" +'$homie','3.0.1',1,true) 
		mqtt.publishMsg ("homie/${atomicState.normHubName}/" +'$implementation','groovy:uk.co.ukusa.mqtt',1,true)
		mqtt.publishMsg ("homie/${atomicState.normHubName}/" +'$fw/version','2b',1,true)
		mqtt.publishMsg ("homie/${atomicState.normHubName}/" +'$fw/name','alpha',1,true)
		mqtt.publishMsg ("homie/${atomicState.normHubName}/" +'$name',settings?.hubName,1,true)
		mqtt.publishMsg ("homie/${atomicState.normHubName}/" +'$mac','BB:AA:DD:AA:55:55',1,true)
		mqtt.publishMsg ("homie/${atomicState.normHubName}/" +'$localip','1.2.3.4',1,true)
		mqtt.publishMsg ("homie/${atomicState.normHubName}/" +'$fw/name','alpha',1,true)
		mqtt.publishMsg ("homie/${atomicState.normHubName}/" +'$implementation/version','alpha2b',1,true)
	}
	mqtt.setStateVar("logLevel",determineLogLevel(settings?.logging ?: "INFO"))
	if ((settings?.mqttRemoveDevices == true))
	{
		log ("Deleting all the MQTT child devices", "DEBUG")
		removeAllChildDevices()  // somehow these stay orphaned, so need this or need to create differently
	}
	mqtt.reset()
	atomicState.homie=settings?.homieDevice  // setting the homie tree master device topic for discovery
	atomicState.HA=settings?.HAStatestreamTopic
	subscribe(switches, "switch", switched)
	subscribe(dimmers, "level", dimmed)
	subscribe(dimmers, "switch", switchedDim)
	
	subscribe(mqtt,"OnOffDev", onoffCapability)
	subscribe(mqtt,"DimDev", dimCapability)
	subscribe(mqtt,"Label", homieName)
	subscribe(mqtt,"HASwitchDev", HASwitchCapability)
	subscribe(mqtt,"HALightDev", HALightCapability)
	subscribe(mqtt,"HASensorDev", HASensorCapability)
	subscribe(mqtt,"OnOff", onoffEvent)
	subscribe(mqtt,"Dim",dimEvent)
	subscribe(mqtt,"Command",cmdEvent)
	subscribe(mqtt,"Sensor",sensorEvent)
	subscribe(mqtt,"SensorUnit",sensorUOM)
	subscribe(mqtt,"Lookup", LookupManual)
	
	subscribe(adhoc,"OnOff",onoffEvent)
	subscribe(adhoc,"topic", stateTopics)
	subscribe(adhoc,"mapTopic", mapTopics)
	subscribe(adhoc,"changeState",stateChange)
	subscribe(adhoc,"changeLevel",levelChange)
	
	if (settings?.homieDevice) mqtt.setStateVar("homieDevice",settings?.homieDevice)
	atomicState.delay = 0
	if (adhoc!= null)
	{
		log ("["+(adhoc.size()) +"] manual/adhoc devices enabled for MQTT","INFO")
		atomicState.delay += 10
		index=0
		for (String item : settings?.adhoc) {
			atomicState.delay += 1
			pauseExecution(500)  // TODO paces responses - NB sendTopics runs after 10 seconds currently i.e 20 devices - need to tweak
			adhoc[index++].getStateTopics()
		}
	}
	
	log ("reinitializing", "TRACE")
	atomicState.createDevices=false
	if (settings?.HEBasic){
		subscribeOneOffTopics("Hubitat/${settings?.hubName}/+/+/set") // incoming cmds to control HE devices - all types .. or ..
	}
	if (settings?.homiePublish) {
		subscribeOneOffTopics("homie/${atomicState.normHubName}/+/+/set") // incoming cmds to control HE devices - all types .. or could restrict to supported types
	}
	if (settings?.HAStatestream)  //TODO
	{
		// not used in alpha2
		//subscribeOneOffTopics("${atomicState.HA}/sensor/outdoor_temperature_measurement/friendly_name")  // adds device
		//subscribeOneOffTopics("${atomicState.HA}/sensor/outdoor_temperature_measurement/unit_of_measurement")  // adds device
		//subscribeOneOffTopics("${atomicState.HA}/sensor/outdoor_temperature_measurement/state") // updates state
	}
	atomicState.nameMap = [:]
	atomicState.topicMap=[:]
	MQTTswitches = (settings?.switches)
	count=0
	atomicState.MQTTDevices=0
	nodes=""
	if (MQTTswitches != null) {
		log ("["+(MQTTswitches.size()) +  "] switches enabled for MQTT ", "TRACE")
		def temp = atomicState.nameMap
		for (String item : MQTTswitches) {
   			log ("    MQTT Switch Device " + item, "TRACE")
			normName= normalize(item)
			nodes=nodes+normName +','
			// Build a lookup table for normName to full name
			temp[normName] = item
			switched(null, item, MQTTswitches.currentSwitch[0]) // sends an initial status message to MQTT
			atomicState.MQTTDevices++
		}
		atomicState.nameMap = temp
	}
	else log ("[0] switches enabled for MQTT", "TRACE")
	MQTTdimmers = (settings?.dimmers)
	if (MQTTdimmers != null){
		log ("["+(MQTTdimmers.size()) +  "] dimmers enabled for MQTT","TRACE")
		temp=atomicState.nameMap
		for (String item : MQTTdimmers) {
   			log ("    MQTT Dimmer Device " + item, "TRACE")
			normName= normalize(item)
			nodes=nodes+normName +','
			temp[normName] = item  //TODO check we dont have duplicate names for two devices ??
			switchedDim(null, item, MQTTdimmers.currentSwitch[0])
			dimmed(null, item, MQTTdimmers.currentLevel[0])
			atomicState.MQTTDevices++
		}
		atomicState.nameMap = temp
	}
	else log ("[0] dimmers enabled for MQTT","TRACE")
	if (nodes.length()>0)    nodes=nodes.substring(0, nodes.length() - 1)
	mqtt.publishMsg ("homie/${atomicState.normHubName}/"+'$nodes',nodes,1,true)
	
	
	if(settings?.homieDiscovery) {
		log ("homie discovery enabled", "INFO")
		subscribeHomieTopic()
		atomicState.delay += 10
		runIn(atomicState.delay, "subscribeHomieStateTopics")
		atomicState.delay += 10
		runIn (atomicState.delay, "subscribeHomieNameTopics")
		atomicState.delay += 10 
	}
	else log ("Skipping homie MQTT discovery", "INFO")
	
	if(settings?.HAStatestream){
		log ("HA stateStream enabled", "INFO")
		atomicState.delay += 10
		runIn (atomicState.delay, "subscribeHASwitches")
		atomicState.delay += 10
		runIn (atomicState.delay, "subscribeHASwitchEvents")
		atomicState.delay += 15  
	}
	else log ("Skipping HA stateStream MQTT discovery", "INFO")
	
	log ("Total startup time will be around " + atomicState.delay + " seconds", "INFO")
    runIn (atomicState.delay, "devSummary")
}

def wipe()
{
	atomicState.onoffDevices=[]
	atomicState.dimDevices=[]
	atomicState.HASwitchDevices=[]
	atomicState.HALightDevices=[]
	atomicState.HASensorDevices=[]
	atomicState.sensorDevices=[]
}


def stateTopics(evt) {  // Returns names of all registered state topics for adhoc devices
	subscribeOneOffTopics(evt.value)
}

def mapTopics(evt) {  	
						//data.stateON and data.stateOFF available here too - (if defined in device)
						//if there is no onoff state topic for the device then it will be created with the dim name and the map will have the same entry so lookup still works
	log ( "###################### "+evt.value+" ######################", "TRACE")
	def tempMap=atomicState.topicMap
	def content=[:]
	def data = parseJson(evt.data)
	log ("Topic mapped key " + data.level + " to " + data.state + " with " + data.valueMax, "DEBUG")
	if (data.valueMax!=null) valueMax=data.valueMax else valueMax='?'
	if (data.stateON!=null) valueON=data.stateON else valueON='?'
	if (data.stateOFF!=null) valueOFF=data.stateOFF else valueON='?'		
	content=[topic: data.state, maxLevel: valueMax, stateON: data.stateON, stateOFF: data.stateOFF, type: "dim"]
	tempMap[data.level]=content
	content=[topic: data.state, maxLevel: valueMax, stateON: data.stateON, stateOFF: data.stateOFF, type: "onoff"]
	tempMap[data.state]=content   // this creates an entry for the switch part of a dimmer so states are available
	atomicState.topicMap = tempMap
		for (e in tempMap) {
    		log ( "[topic]: key = ${e.key}, value = ${e.value}","DEBUG")
		}
}

def stateChange(evt) {  // for a manual adhoc device
	def data = parseJson(evt.data)Ftype are
			index=0
			for (String item : settings?.adhoc) {  // TODO optimise/eliminate this loop  !!!
			if (item == evt.displayName){
				log ("Matched item [" + index + "] "  + index, "TRACE")
				mqtt.publishMsg (data.topic,evt.value)
				}
			index++
	}
}

def levelChange(evt) {  // for a manual adhoc device
	def data = parseJson(evt.data)
			index=0  
			for (String item : settings?.adhoc) {  //TODO optimise/eliminate this loop  !!!
			if (item == evt.displayName){
				log ("Matched item [" + index + "] "  + index,"TRACE")
				mqtt.publishMsg (data.dimTopic,evt.value)
				}
			index++
	}
}

private logDebug(msg) {
	if (settings?.debugOutput || settings?.debugOutput == null) {
		log ("$msg", "DEBUG")
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

def log(data, type) {
	data = "MQTT: ${data ?: ''}"
    if (determineLogLevel(type) >= determineLogLevel(settings?.logging ?: "INFO")) {
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
                log.error "MQTT: -- ${device.label} -- Invalid Log Setting"
        }
    }
}

private removeAllChildDevices() {   
	getChildDevices().each {deleteChildDevice(it.deviceNetworkId)}
	log ("Deleted all child devices", "WARN")
}

def subscribeHomieTopic()
{
	log ("Adding homie property and node subscription topic for " + atomicState.homie, "INFO")
	mqtt.subscribeTopic('homie/'+atomicState.homie+'/$nodes')
	mqtt.subscribeTopic('homie/'+atomicState.homie+'/+/$properties')
}


def subscribeHomieStateTopics()
{
	log ("Adding homie onoff & dim events subscription topic", "INFO")
	mqtt.subscribeTopic('homie/'+atomicState.homie+'/+/onoff')
	mqtt.subscribeTopic('homie/'+atomicState.homie+'/+/dim')
}

def subscribeHomieNameTopics()
{
	log ("Adding homie name subscription topic", "INFO")
	mqtt.subscribeTopic('homie/'+atomicState.homie+'/+/$name')
}

def subscribeHASwitches()
{
	log ("Adding HA switches and lights names topic", "INFO")
	mqtt.subscribeTopic(atomicState.HA+'/switch/+/friendly_name')
	mqtt.subscribeTopic(atomicState.HA+'/light/+/friendly_name')
}

def subscribeHASwitchEvents()
{
	log ("Adding HA switch and light events topic", "INFO")
	mqtt.subscribeTopic(atomicState.HA+'/switch/+/state')
	mqtt.subscribeTopic(atomicState.HA+'/light/+/state')
	mqtt.subscribeTopic(atomicState.HA+'/light/+/brightness')
}

def subscribeOneOffTopics(topic)
{
	mqtt.subscribeTopic(topic)
}

def switchedDim(evt, name=null, state=null) 
{
	if (evt!=null)
	{
		name=evt.displayName
		state=evt.value
		xName=evt.name
	} else xName=name
	log("Device switchedDim ${state}  ${name} ${xName}", "DEBUG")
		if (settings?.HEBasic)
		{
				mqtt.publishMsg ("Hubitat/${settings?.hubName}/${name}/onoff","${state}") // basic MQTT status
		}

		if ((settings?.homiePublish)== true) {
			normName = normalize(name) 
			if (state=="on") nState = "true" else nState = "false"
				
		
			//================  Limited homie spec implemenation ================
			mqtt.publishMsg ("homie/${atomicState.normHubName}/${normName}"+'/$properties',"onoff",1,true)   // limited homie implementation
			mqtt.publishMsg ("homie/${atomicState.normHubName}/${normName}"+'/$name',name,1,true)   
			mqtt.publishMsg ("homie/${atomicState.normHubName}/${normName}"+'/$type',"socket",1,true)  
			mqtt.publishMsg ("homie/${atomicState.normHubName}/${normName}"+'/onoff/$settable',"true",1,true)   
			mqtt.publishMsg ("homie/${atomicState.normHubName}/${normName}"+'/onoff/name',name,1,true)  
			mqtt.publishMsg ("homie/${atomicState.normHubName}/${normName}"+'/onoff/$datatype',"boolean",1,true)
			mqtt.publishMsg ("homie/${atomicState.normHubName}/${normName}"+'/onoff',nState)
			
			//===================================================================
		}
}

def switched(evt, name=null, state=null)
{
	if (evt!=null)
	{
		name=evt.displayName
		state=evt.value
		xName=evt.name
	} else xName=name
	//log("Device switched ${evt.value}  ${evt.displayName} " + evt.name, "DEBUG")
	log("Device switched ${state}  ${name} ${xName}", "DEBUG")
	if (settings?.HEBasic)
		{
				mqtt.publishMsg ("Hubitat/${settings?.hubName}/${name}/onoff","${state}") // basic MQTT status
		}
	if ((settings?.homiePublish)== true) {
			normName = normalize(name) 
			if (state=="on") nState = "true" else nState = "false"
				
		
			//================  Limited homie spec implemenation ================
			mqtt.publishMsg ("homie/${atomicState.normHubName}/${normName}"+'/$properties',"onoff",1,true)  // limited homie implementation
			mqtt.publishMsg ("homie/${atomicState.normHubName}/${normName}"+'/$name',name,1,true)   
			mqtt.publishMsg ("homie/${atomicState.normHubName}/${normName}"+'/$type',"socket",1,true)  
			mqtt.publishMsg ("homie/${atomicState.normHubName}/${normName}"+'/onoff/$settable',"true",1,true) 
			mqtt.publishMsg ("homie/${atomicState.normHubName}/${normName}"+'/onoff/name',name,1,true) 
			mqtt.publishMsg ("homie/${atomicState.normHubName}/${normName}"+'/onoff/$datatype',"boolean",1,true)
			mqtt.publishMsg ("homie/${atomicState.normHubName}/${normName}"+'/onoff',nState) 
			//===================================================================
	}
}

def dimmed(evt, name=null, state=null)
{
	if (evt!=null)
	{
		name=evt.displayName
		state=evt.value
		xName=evt.name
	} else xName=name
	log ("Device dimmed ${state}  ${name}", "DEBUG")
	if (settings?.HEBasic) {
		mqtt.publishMsg ("Hubitat/${settings?.hubName}/${name}/dim","${state}")
	}
	
	if ((settings?.homiePublish)== true) {
    	normName = normalize(name) 
		//================  Limited homie spec implemenation ================
		mqtt.publishMsg ("homie/${atomicState.normHubName}/${normName}"+'/dim',"${state}",1,true)
		mqtt.publishMsg ("homie/${atomicState.normHubName}/${normName}"+'/$properties',"dim",1,true)  // limited homie implementation
		mqtt.publishMsg ("homie/${atomicState.normHubName}/${normName}"+'/$name',"${name}",1,true)  
		mqtt.publishMsg ("homie/${atomicState.normHubName}/${normName}"+'/$type',"light",1,true)   
		mqtt.publishMsg ("homie/${atomicState.normHubName}/${normName}"+'/dim/$settable',"true",1,true)  
		mqtt.publishMsg ("homie/${atomicState.normHubName}/${normName}"+'/dim/name',"${name}",1,true) 
		mqtt.publishMsg ("homie/${atomicState.normHubName}/${normName}"+'/dim/$datatype',"integer",1,true)
		mqtt.publishMsg ("homie/${atomicState.normHubName}/${normName}"+'/dim/$format',"0:100",1,true)
		//===================================================================
	}
}

def mqttRX(evt) {
	log ("${evt.name} ${evt.value}", "INFO")
}

def normalize(name) {
	//TODO research how to include NFD
	//log ("Normalize: " + name + " >>> " + name.trim().toLowerCase().replaceAll(/[^\w-]/,"_").replaceAll(/[-]/,'_'),"TRACE")
	return name ? name.trim().toLowerCase().replaceAll(/[^\w-]/,"-").replaceAll(/[_]/,'-') : undefined
	//return name ? name.trim().toLowerCase().replaceAll(/[.*+?^${} ()|]/,"_").replaceAll(/[^a-z0-9_]/,""): undefined
	//return name ? name.trim().toLowerCase().normalize("NFD").replace(/[ -]+/g, "_").replace(/[^a-z0-9_]/g, "") : undefined
}


def onoffCapability(evt) {
	temp = atomicState.onoffDevices
	if (temp.contains(evt.value)) {
		// log ("Already in dropdown list: ${evt.value}", "TRACE")
	}
	else { 
		//log ("Adding to dropdown list: ${evt.value}", "DEBUG")
		temp.add(evt.value)
		atomicState.onoffDevices=temp
	}
	//log("Creating homie device " + evt.value,"TRACE")
	createChildDevice (evt.value, "onoff", "homie", evt.value)

}

def dimCapability(evt) {  //TODO merge with above
	temp = atomicState.dimDevices
	if (temp.contains(evt.value)) {
		//log ("Already in list: ${evt.value}", "DEBUG")
	}
	else {
		//log ("Homie adding dim to list: ${evt.value}", "DEBUG")
		temp.add(evt.value)
		atomicState.dimDevices = temp
	}
	createChildDevice (evt.value, "dim", "homie", evt.value)
}

def HASwitchCapability(evt) {  //TODO merge with above
	temp = atomicState.HASwitchDevices
	def data = parseJson(evt.data)
	label=data.label
	if (temp.contains(evt.value)) {
		//log ("Already in list: ${evt.value}", "INFO")
	}
	else {
		log ("HA Adding switch to list: ${evt.value}  label ", "TRACE")
		temp.add(evt.value)
		atomicState.HASwitchDevices = temp
	}
	createChildDevice (evt.value, "onoff", "HA", label)
}

def HALightCapability(evt) { //TODO merge with above
	temp = atomicState.HALightDevices
	def data = parseJson(evt.data)
	label=data.label
	if (temp.contains(evt.value)) {
		//log("Already in list: ${evt.value}", "INFO")
	}
	else {
		log ("HA Adding light to list: ${evt.value}  label ", "TRACE")
		temp.add(evt.value)
		atomicState.HALightDevices = temp
	}
	createChildDevice (evt.value, "dim", "HA", label)
}

def HASensorCapability(evt) {  // TODO merge with above
	temp = atomicState.HASensorDevices
	def data = parseJson(evt.data)
	label=data.label
	if (temp.contains(evt.value)) {
		//log ("Already in list: ${evt.value}", "INFO")
	}
	else {
		log ("HA Adding sensor to list: ${evt.value}  label ", "TRACE")
		temp.add(evt.value)
		atomicState.HASensorDevices = temp
	}
	createChildDevice (evt.value, "sensor", "HA", label)
}


def homieName(evt) { 

		//log ("Trying to add label to MQTT+${evt.value}", "INFO")
		dID= "MQTT:homie_"+evt.value
		child=getChildDevice(dID)
	    if (child == null) 
	   {
		   log ("Child doesn't exist (no state or unsupported type ?) "+ evt.value + " " + evt.name , "TRACE")
		   return
	   }
	
	//log ("Found child ${evt.value}", "DEBUG")
	def data = parseJson(evt.data)
	//log ("... label is " + data.label, "DEBUG")
	child.label= data.label
	log ("Added label ${data.label} to ${evt.value}" ,"TRACE")
	} 

def devSummary()
{
	log ("==================================================", "INFO")
	log ("    ${atomicState.MQTTDevices} Hubitat devices enabled on MQTT", "INFO")
	if (settings?.homieDiscovery){
		log ("    Discovered ${atomicState.onoffDevices.size()} homie onoff devices", "INFO")
        log ("    Discovered ${atomicState.dimDevices.size()} homie dim devices", "INFO")
	}
	if (settings?.HAStatestream) {
		log ("    Discovered ${atomicState.HASwitchDevices.size()} HA switch devices", "INFO")
		log ("    Discovered ${atomicState.HALightDevices.size()} HA light devices", "INFO")
	}
	log ("================== Startup complete ==================", "INFO")
	atomicState.started=true
	if (settings?.homiePublish) mqtt.publishMsg ("homie/${atomicState.normHubName}/" +'$state','ready',1,true) 
}

def LookupManual(evt) {
	def dimDevice=false
	def onoffDevice=false
	def data = parseJson(evt.data)
	deviceID=data.topic
	
	if (settings?.HEBasic)
	{
		if (deviceID.indexOf ("Hubitat/"+settings?.hubName)==0)  {  // this is an incoming MQTT command on the basic topic control topic 
			log ("Need to action this command " + data + " " + deviceID, "TRACE")
		}
	}
	
	
	
	tempMap=atomicState.topicMap
	// content=[topic: data.state, maxLevel: valueMax, stateON: data.stateON, stateOFF, data.stateOFF]
	log ("Need to find devices using [" + evt.value + "] "+ deviceID,"TRACE")
	log ("Settings " + settings?.adhoc, "TRACE")
	log ("Looking up in topic map for "+ deviceID + "  ==  "  + tempMap[deviceID], "DEBUG")
	LookupID = tempMap[deviceID]
	if (LookupID != null)
		{
			log ("***** Found in map ***** for "+ data.topic + "  ==  "  + LookupID.topic, "DEBUG")
			devType="map"
			if (LookupID.type=="onoff") 
			{
				onoffDevice=true
				stateON = LookupID.stateON
				stateOFF = LookupID.stateOFF
			}
			
			else if (LookupID.type=="dim") 
			{
				dimDevice=true
				LookupLevel=LookupID.maxLevel
				if (LookupLevel == '?') {
					log ("However there was no corresponding MaxValue for " + data.topic, "WARN")
					log ("The valueMap is ... " + LookupLevel,"WARN")
				}
			}
			deviceID = LookupID.topic
		}
	
	else {
			log ("Didnt find this entry in the topicMap lookup for " + deviceID  , "WARN")
			for ( e in tempMap ) {
    			log ("<topic>: key = ${e.key}, value = ${e.value}","DEBUG")
			}
			devType='?'
	}
		
	// should be found later by deviceNetworkID lookup (below)
		index=0	
		for (String item : settings?.adhoc) {
			if (adhoc[index].deviceNetworkId == "MQTT:Internal "+ deviceID){
				log ("Manual device found via Lookup " + item, "DEBUG")
				if (devType != "map") devType="manual"
				// TODO quit loop when found ??
				if (!dimDevice) onoffDevice=true //TODO - is it right to assume this ?
				device=adhoc[index]  
				//TODO exit loop on match or match multiple ? - currently matching last
			}
			index++
		}
	
		if (device==null) // try the HE local devices published to MQTT
		{
				topic = data.topic.split('/')
				if (topic[0] != "Hubitat")
				{
					log ("Unexpected topic " + data.topic + " " + topic[0], "ERROR")
					return
				}
				dName=topic[2]
			    log ("Didnt find the device by DNI lookup either " + dName, "WARN")
				index=0
				for (String item : settings?.switches) {   // SWITCHES 
					if (item==dName) 
					{
						log ("Found " + dName + " in switches. DNI: " + switches[index].deviceNetworkId, "INFO")
						device=switches[index]
						if (topic[3]=="onoff") onoffDevice=true
						devType="system"
					}
					index++
				}
				index=0
				for (String item : settings?.dimmers) {   // DIMMERS
					if (item==dName) 
					{
					    log ("Found " + dName + " in dimmers. DNI:  " + dimmers[index].deviceNetworkId, "INFO")
						device=dimmers[index]
						if (topic[3]=="onoff") onoffDevice=true
						else if (topic[3]=="dim") dimDevice=true
						devType="system"
					}
					index++
				}
		}
		
	if (device==null) 	log ("Can't find " + deviceID + " " + dName + " device by DNI","ERROR")
	//}
	if (devType!='?'){	
		log ("Types are dim:" + dimDevice + " onoff:"+ onoffDevice + " type:"+ devType,"DEBUG")
		if (onoffDevice)  
		{
		log ("Handling as onoff", "DEBUG")
			if (devType=="map") {
				if (evt.value==LookupID.stateOFF) device.toOFF()
				else if (evt.value==LookupID.stateON) device.toON()
			}
			else {  // will have to surmise state required
				log ("Surmising required state [" +evt.value+"] for " + device.name,"DEBUG")
				OnValues="on,true,yes,1"  
				OffValues="off,false,no,0"
				if (OnValues.contains(evt.value.toLowerCase()))
				{
					if (devType == "system") device.on() else device.toON()
				}
				else if (OffValues.contains(evt.value.toLowerCase()))
				{
					if (devType == "system") device.off() else device.toOFF()
				}
				else log ("Unknown state value " + evt.value +" - need to add to lookup","WARN")	
			}
		}
	
	
		if (dimDevice) {
			//if (devType=="dim") {
				 log ("Handling as dim", "DEBUG")
				 try {  // see if its numeric  // LookupLevel should be valid
					float convertedNumber = Float.parseFloat(evt.value)  // TODO messy and repeatedly used

					if (LookupLevel == null)
					 {
						 if (devType=="system"){
							 device.setLevel (evt.value.toInteger(),0)
						 }
						 else log ("There's no corresponding MaxValue for " + data.topic, "ERROR")
					 }
					else {
						convertedNumber = convertedNumber * (100/Float.parseFloat(LookupLevel.toString()))  //think this will work for 1.0 too
						intLevel = convertedNumber = convertedNumber.round()
						adjLevel=intLevel.toString()
						log ( " The numeric payload for " + device.name + " was converted from " + evt.value + " to " + adjLevel, "DEBUG")
						device.toLevel(adjLevel,1)
					}
			}
			catch (Exception e1) {
				log ("This payload wasnt numeric  " + evt.value + "  " + evt.data + "  " + e1, "WARN")
				}
			//}
		}
		if (devType=="unknown") log ("Incoming MQTT message for unknown device "+deviceID,"ERROR")
	}
}


def onoffEvent (evt) {
	def data = parseJson(evt.data)
    //log ("event data: ${data}", "INFO")
	log ("Received an OnOff event from ${evt.value} turned ${data.status}", "TRACE")
	child=getChildDevice("MQTT:homie_"+evt.value)  //TODO check this method is only for homie devices
	if (child==null) {
		//log ("getChild failed for " + evt.value, "WARN")
		child=getChildDevice("MQTT:HA_"+evt.value)
		if (child==null) {
			//log ("getChild failed for " + evt.value, "WARN")
		index=0	
		for (String item : settings?.adhoc) {
			if (adhoc[index].deviceNetworkId == "MQTT:Internal "+ data.topic){
				log ("FOUND (OnOff) matches " + item +" " + index,"DEBUG")
				// TODO quit loop when found
				//child=item
				child=adhoc[index]  // this isn't actually a child it is a real adhoc device
				//exit  // return might be bad
			}
				index++
		}
			if (child==null) {
				log ("Device not enabled: "+ evt.value,"DEBUG")
				return
			}
		}	
	}	
	log  ("Got " + child, "TRACE")
	currStatus=data.status
	isStatus="off"
	if (currStatus == "off") child.setStateType ("off","on")
	else if (currStatus == "off") child.setStateType ("off","on")
	else if (currStatus == "Off") child.setStateType ("Off","On")
	else if (currStatus == "OFF") child.setStateType ("OFF","ON")
	else if (currStatus == "false") child.setStateType ("false","true")
	else if (currStatus == "False") child.setStateType ("False","True")
	else if (currStatus == "FALSE") child.setStateType ("FALSE","TRUE")
	else if (currStatus == "0") child.setStateType ("0","1")
	else 
	{
	isStatus = 'on'
	if (currStatus == "on") child.setStateType ("off","on")
	else if (currStatus == "On") child.setStateType ("off","on")
	else if (currStatus == "On") child.setStateType ("Off","On")
	else if (currStatus == "ON") child.setStateType ("OFF","ON")
	else if (currStatus == "true") child.setStateType ("false","true")
	else if (currStatus == "True") child.setStateType ("False","True")
	else if (currStatus == "TRUE") child.setStateType ("FALSE","TRUE")
	else if (currStatus == "1") child.setStateType ("0","1")
	else isStatus = "#"
	}
	if (currStatus == "unavailable") isStatus="?"
		if (isStatus=="off") {
			//child.off
			child.toOFF()
			//log ("OFF "+ child, "DEBUG")
		}
		else if (isStatus=="on"){
			//child.on()
			child.toON()
			//log ("ON "+ child, "DEBUG")
		}
		else if (isStatus=="?"){  // This is likely a Philips Hue bulb that is powered off
			log ("Reported OnOff status was ${currStatus} for device ${evt.value}","INFO")
		}
	    else log("Bad reported OnOff status... was ${currStatus} for device ${evt.value}","WARN")
}

def dimEvent (evt) {
	def data = parseJson(evt.data)
    //log ("event data: ${data}", "INFO")
	log ("Received a dim event from ${evt.value} level ${data.level}", "TRACE")
	child=getChildDevice("MQTT:homie_"+evt.value) 
	if (child==null) {
		//log ("getChild failed for " + evt.value, "WARN")
		child=getChildDevice("MQTT:HA_"+evt.value)
		// now check adhoc virtual devices
		index=0	
		for (String item : settings?.adhoc) {
			if (adhoc[index].deviceNetworkId == "MQTT:Internal "+ data.topic){
				log ("FOUND (Dim) matches " + item +" " + index,"DEBUG")
				// TODO quit loop when found or match all - currently match last
				child=adhoc[index]  // this isnt actually a child it is a real adhoc device
			}
				index++
		}
			if (child==null) return	
	}	
	//log ("Setting Child dim level to ${data.level}", "INFO")
	child.toLevel(data.level,1)
}

def sensorEvent(evt) {
	def data = parseJson(evt.data)
    //log ("event data: ${data}", "INFO")
	//log ("Received a dim event from ${evt.value} level ${data.level}", "INFO")
	child=getChildDevice("MQTT:homie_"+evt.value)
	if (child==null) {
		//log ("getChild failed for " + evt.value, "WARN")
		child=getChildDevice("MQTT:HA_"+evt.value)		
		if (child==null) {
			//log ("getChild failed for " + evt.value, "WARN")
			return
		}	
	}	
	//log ("Setting Child dim level to ${data.level}", "INFO")
	child.setValue(data.status)
}
def sensorUOM(evt) {  // TODO unused in alpha2
	def data = parseJson(evt.data)
	child=getChildDevice("MQTT:homie_"+evt.value)
	if (child==null) {
		//log ("getChild failed for " + evt.value, "WARN")
		child=getChildDevice("MQTT:HA_"+evt.value)
		if (child==null) {
			//log ("getChild failed for " + evt.value, "WARN")
			return
		}	
	}		
	unit = "°" +data.label[7]  // TODO hack until I work it out better
	child.setPrefix("")
	child.setSuffix(unit)
}

def cmdEvent (evt) {  // 'set' command from homie for a Hubitat or manual device
	// TODO This has value and data transposed in the two versions for discovered and adhoc .. tidy up
	def data = parseJson(evt.data)
	log ("MQTT set command received for " + evt.value + " " + data.state,"DEBUG")
	normName=evt.value
	if (data.cmd=="onoff") {
		MQTTswitches = (settings?.switches)
    	//log ("Switch Lookup for  "+  evt.value + " is " + atomicState.nameMap[normName] + " [" + atomicState.nameMap[index] +"] ","INFO")		
		MQTTswitches.each { MQTTswitches ->  // This is awful - looping through - do it the other way		
			if (MQTTswitches.displayName==atomicState.nameMap[normName]){			
				// DUPLICATED TODO combine this as one method for all
				log ("Surmising required state [" +data.state+"] for " + evt.value,"DEBUG")
				OnValues="on,true,yes,1"  
				OffValues="off,false,no,0"
				if (OnValues.contains(data.state.toLowerCase()))
				{
					//if (devType == "system") 
					MQTTswitches.on()  // think can only be a system type here
					//else device.toON()
				}
				else if (OffValues.contains(data.state.toLowerCase()))
				{
					//if (devType == "system") 
					MQTTswitches.off() // think can only be a system type here
					//else device.toOFF()
				}
				else log ("Unknown state value " + data.state +" - need to add to lookup","WARN")	
			}
		}	
					
		MQTTdimmers = (settings?.dimmers)
    	//log ("Dimmer Lookup for  "+  evt.value + " is " + atomicState.nameMap[normName] + " [" + atomicState.nameMap[index] +"] ","TRACE")
		MQTTdimmers.each { MQTTdimmers ->  // This is awful - looping through - do it the other way
		if (MQTTdimmers.displayName==atomicState.nameMap[normName]){
			// TODO combine this below as one method for all
				log ("Surmising required state [" +data.state+"] for " + evt.value,"DEBUG")
				OnValues="on,true,yes,1"  
				OffValues="off,false,no,0"
				if (OnValues.contains(data.state.toLowerCase()))
				{
					//if (devType == "system") 
					MQTTdimmers.on()  // think can only be a system type here
					//else device.toON()
				}
				else if (OffValues.contains(data.state.toLowerCase()))
				{
					//if (devType == "system") 
					MQTTdimmers.off() // think can only be a system type here
					//else device.toOFF()
				}
				else log ("Unknown state value " + data.state +" - need to add to lookup","WARN")
			}
		}		
	}
	else if (data.cmd=="dim") {
		intLevel=data.state.toInteger()
		if ((0 <= intLevel) && (intLevel <= 100))
		{
			//log ("Dimmer Lookup for  "+  evt.value + " is " + atomicState.nameMap[normName] + " [" + atomicState.nameMap[index] +"] ","TRACE")
			MQTTdimmers = (settings?.dimmers)
			MQTTdimmers.each { MQTTdimmers ->  // This is awful - looping through - do it the other way
				if (MQTTdimmers.displayName==atomicState.nameMap[normName]){
					log ("Found " + MQTTdimmers.displayName, "TRACE")
					MQTTdimmers.setLevel(intLevel,1)
				}
			}
			adhoc=(settings?.adhoc) 
			adhoc.each { adhoc ->
				if (adhoc.displayName==atomicState.nameMap[normName]){ 
				adhoc.setLevel(intLevel,1)
				}
			}
		}
	}
}


def createChildDevice(name, type, system, friendlyName) {
	log("CreateDevice called " + name + " " + type + " " + system + " " + friendlyName, "DEBUG")
	if (name==null) return
	if (atomicState.started) return // TODO Stops creation of devices after timed startup has elapsed - but also stops ongoing incremental discovery.... decide which to use
	devEnabled=false
	def prefix = "MQTT:"
	String enabledDevices=(settings?.HA_Lights) + ',' + (settings?.HA_Switches) + ',' + (settings?.Homie_dim) + "," + (settings?.Homie_onoff) + ','
	if (system=="HA") {
		if (settings?.HA_Switches != null)
		{									   
			if (settings?.HA_Switches.contains (name))
			{
				devEnabled=true
				prefix="MQTT:HA_"
			}
		}
		if (settings?.HA_Lights != null) {
			if (settings?.HA_Lights.contains (name))
			{
				devEnabled=true
				prefix="MQTT:HA_"
			}
		}
	}
	else if (system=="homie")
	{
		if (settings?.Homie_onoff != null)
		{									   
			if (settings?.Homie_onoff.contains (name))
			{
				devEnabled=true
				prefix="MQTT:homie_"
			}
		}
		if (settings?.Homie_dim != null) {
			if (settings?.Homie_dim.contains (name))
			{
				devEnabled=true
				prefix="MQTT:homie_"
			}
		}
	}
	else if (system=='internal') {
		devEnabled=true
		prefix="MQTT:Internal_"
	}

	if (!devEnabled)
	{
		log ("["+system+"] Create rejected for " + name, "TRACE")
		log 
		return
	}
	else log ("["+system+"] Create OK for " + name, "INFO")
	
	child=getChildDevice(prefix+name) 
	if (child != null) 
		{
			// dont expect this if mqttRemoveDevices was true
			// TODO decide ..this gets fired (but ignored) on every change of state update - can we eliminate that without breaking incremental discovery ?
			// no but could use ...  if(atomicState.started)
			if (!atomicState.started && (settings?.mqttRemoveDevices == true)) log ("Child already exists " + prefix+name, "TRACE")
			log ("Child already exists " + prefix+name, "TRACE")
			return
		}
	if (type=='onoff') devType='MQTT Switch'
	else if (type=='dim') devType='MQTT Dimmer'
	else if (type=='sensor') devType= "MQTT Text"  // TODO - map to specific capabilities
			 
	// ######  This is where to add additional device types and their matching driver ######
			 
	else {
			log ("Skipping creating device " + name +" as no type yet for " + type, "WARN")
			return
	}
			
		log ("Creating " +type+ " app child MQTT:" + name + " " + friendlyName, "DEBUG")
		//mqtt.createChild(name)  // This was to create device as driver child
		def pfix = ""
		try { 
			   
				childDevice = addChildDevice("ukusa", devType, prefix+name, null,[completedSetup: true, label: pfix + friendlyName])
				child=getChildDevice(prefix+name)  //hmm seems childDevice is not a device object 
			    if (child == null) log ("Child was never created" + prefix+name , "ERROR")
			    else log ("Created Child with label "+ child.label,"TRACE")
			atomicState.count++			
			childrenCount = getChildDevices().size()		
				if (system=='homie'){
					child.setStateTopic('homie/'+atomicState.homie+'/'+name+'/onoff')
					child.setStateCmdTopic('homie/'+atomicState.homie+'/'+name+'/onoff/set')
				}
				else if (system=="HA"){
					if (type == 'sensor') {
						child.setStateTopic(atomicState.HA+'/sensor/'+name+'/state')
					}
					else {
						child.setStateTopic(atomicState.HA+'/switch/'+name+'/state')
						child.setStateCmdTopic(atomicState.HA+'/switch/'+name+'/state/cmd')
						 }
				}
				else if (system=="internal"){  // was setting state topics but now not child devices
				}
				if (type=='dim') {
					if (system=="homie"){
						child.setLevelTopic('homie/'+atomicState.homie+'/'+name+'/dim')
						child.setLevelCmdTopic('homie/'+atomicState.homie+'/'+name+'/dim/set')
						child.setMaxBrightness('1')
					}
					else if (system=="HA"){
						child.setStateTopic(atomicState.HA+'/light/'+name+'/state')  //overwrite the switch entries
						child.setStateCmdTopic(atomicState.HA+'/light/'+name+'/state/cmd')	 
						child.setLevelTopic(atomicState.HA+'/light/'+name+'/brightness') //0-255 in HA ?
						child.setLevelCmdTopic(atomicState.HA+'/light/'+name+'/brightness/cmd')
						child.setMaxBrightness('255') 
					}
					if (type=='sensor') {
						child.setValue ("")
					}
			}
			child.setType(system)  // identify as a discovered device
		} catch(Exception ex) {
			log( "Device " + name + " already exists", "QUERY")
			//log ("addChild/topics failed for " +name + " " +(ex.toString()), "TRACE")
		}	

}

def clearDevices()
{
	 atomicState.onoffDevices=[]
	 atomicState.dimDevices=[]
}


def sendPayload(topic,payload)
{
	if (payload==null) {
		log ("Null payload for topic " + topic, "WARN")
		return
	}
	if (topic==null) {
		log ("Null topic for payload " + payload, "WARN")
		return
	}
	log ("Send MQTT " + topic + " " + payload, "TRACE")
	mqtt.publishMsg (topic,payload)
}



