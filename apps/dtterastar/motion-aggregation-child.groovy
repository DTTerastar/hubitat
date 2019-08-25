definition (
    name: "Motion Aggregation Child",
    namespace: "DTTerastar",
    author: "Darrell Turner",
    description: "Aggregation for Motion / Contact devices",
    category: "Convenience",
    parent: "DTTerastar:Motion Aggregation",
	iconUrl: "",
	iconX2Url: ""
)

preferences {
	section() {
		page name: "mainPage", title: "", install: true, uninstall: true
	}
}

 def mainPage() {
	dynamicPage(name: "mainPage") {
		section(title: "") {                        
			label title: "Name", required: true
        }
		
		section("Primary Motion") {
			input "primaryDevs", "capability.motionSensor", title: "Primary motion detectors (these can start aggregated motion and continue it)", multiple: true
            input "primaryIncr", "number", title: "If motion detected, turn the aggregated motion on for an addl X minutes", required: true, defaultValue: 5
            input "primaryDecr", "bool", title: "Only start counting down once all primary are inactive", defaultValue: true
            input "primaryMax", "number", title: "Maximum countdown minutes due to primary motion sensors", defaultValue: 30
		}
        
   		section("Secondary Motion") {
			input "secondaryDevs", "capability.motionSensor", title: "Secondary motion detectors (these can continue aggregated motion)", multiple: true
            input "secondaryIncr", "number", title: "If motion detected, keep the aggregated motion on for X minutes", required: true, defaultValue: 5
            input "secondaryDecr", "bool", title: "Only start counting down once all secondary are inactive", defaultValue: true
            input "secondaryMax", "number", title: "Maximum countdown minutes due to secondary motion sensors", defaultValue: 30
		}
		
		section("Contacts") {
			input "contactDevs", "capability.contactSensor", title: "Contact sensors", multiple: true
            input "contactOpenIncr", "number", title: "If a contact opens, turn the aggregated motion on for X minutes"
            input "contactCloseIncr", "number", title: "If a contact closes, turn the aggregated motion on for X minutes"
            input "contactDecr", "enum", title: "Countdown when", defaultValue: "always", options:[["always": "always"], ["closed": "all contacts are closed"], ["open": "all contacts are open"]]
            input "contactMax", "number", title: "Maximum countdown minutes due to contact sensors", defaultValue: 30
		}

        section("Switches") {
			input "switchDevs", "capability.switch", title: "Switches", multiple: true
            input "switchOnIncr", "number", title: "If a switch turns on, turn the aggregated motion on for X minutes"
            input "switchOffIncr", "number", title: "If a switch turns off, turn the aggregated motion on for X minutes"
            input "switchDecr", "enum", title: "Countdown when", defaultValue: "always", options:[["always": "always"], ["off": "all switches are off"], ["on": "all switches are on"]]
            input "switchMax", "number", title: "Maximum countdown minutes due to switch changes", defaultValue: 30
		}
        
        section("")  {
            input "debugOutput", "bool", title: "Enable debug logging?", defaultValue: true, displayDuringSetup: false, required: false			
        }
	}   
}

def childDevices() {
	return getChildDevices()?.find { true }
}

def installed() {
	logDebug "Installed with settings: ${settings}"
    
	initialize()
}

def updated() {
	logDebug "Updated with settings: ${settings}"
	unsubscribe()
	initialize()
    if (settings?.debugOutput) runIn(1800,logsOff)
}

def initialize() {
    logDebug "initialize()"
    createChildDevices()
	if(primaryDevs) subscribe(primaryDevs, "motion", primaryHandler)
    if(secondaryDevs) subscribe(secondaryDevs, "motion", secondaryHandler)
    if(contactDevs) subscribe(contactDevs, "contact", contactHandler)
    if(switchDevs) subscribe(switchDevs, "switch", switchHandler)
    schedule("0 * * * * ? *", elapsed)
}

def elapsed() {
    if (state.primaryMins>0) { 
        if (!primaryDecr || primaryDevs?.every { it.currentValue("motion") == "inactive" })
            state.primaryMins-=1
    }

    if (state.secondaryMins>0) {
        if (!secondaryDecr || secondaryDevs?.every { it.currentValue("motion") == "inactive" })
            state.secondaryMins-=1
    }
    if (state.contactMins>0) {
        if ((contactDecr!="open" && contactDecr!="closed") || contactDevs?.every { it.currentValue("contact") == contactDecr })
            state.contactMins-=1
    }
    if (state.switchMins>0) {
        if ((switchDecr!="on" && switchDecr!="off") || switchDevs?.every { it.currentValue("switch") == switchDecr })
            state.switchMins-=1
    }
    refresh()
}

def refresh() {
    if (primaryMax>0 && state.primaryMins>primaryMax) state.primaryMins=primaryMax
    if (secondaryMax>0 && state.secondaryMins>secondaryMax) state.secondaryMins=secondaryMax
    if (contactMax>0 && state.contactMins>contactMax) state.contactMins=contactMax
    if (switchMax>0 && state.switchMins>switchMax) state.switchMins=switchMax

    if (isActive) {
        
        if (state.primaryMins>0 || state.secondaryMins>0 || state.contactMins>0 || state.switchMins>0) {
            logDebug "${app.label} not turning off (primary=${state.primaryMins?:0}m secondary=${state.secondaryMins?:0}m contacts=${state.contactMins?:0}m switches=${state.switchMins?:0}m)"
            return;
        }
        
        childDevices.each { it.inactive() }
        
    } else {
        
        if (state.primaryMins>0 || state.contactMins>0 || state.switchMins>0) {
            logDebug "${app.label} turning ON (primary=${state.primaryMins?:0}m contacts=${state.contactMins?:0}m switches=${state.switchMins?:0}m)"
            childDevices.each { it.active() }
        }
    }        
}

private void createChildDevices() {
	if (childDevices) {
		childDevices.each {
			it.name = app.label + " Aggregate Motion"
        }
	} else {
		addChildDevice("hubitat", "Virtual Motion Sensor", UUID.randomUUID().toString(), null, [completedSetup: true, isComponent: true, name: app.label + " Aggregate Motion"])
	}
}

def primaryHandler(evt) {
	if(evt.value == "inactive" && primaryIncr>0) {
		state.primaryMins = (state.primaryMins?:0)+primaryIncr
	}
    refresh()
}

def secondaryHandler(evt) {
	if(evt.value == "inactive" && secondaryIncr>0) {
		state.secondaryMins = (state.secondaryMins?:0)+secondaryIncr
	}
    refresh()
}

def contactHandler(evt) {
    if(evt.value == "open" && contactOpenIncr>0) {
        state.contactMins = (state.contactMins?:0)+contactOpenIncr
    }
    if(evt.value == "closed" && contactCloseIncr>0) {
        state.contactMins = (state.contactMins?:0)+contactCloseIncr
    }
    refresh()
}

def switchHandler(evt) {
    if(evt.value == "on" && switchOnIncr>0) {
        state.switchMins = (state.switchMins?:0)+switchOnIncr
    }
    if(evt.value == "off" && contactOffIncr>0) {
        state.switchMins = (state.switchMins?:0)+switchOffIncr
    }
    refresh()
}

def getIsActive() {
    return (childDevices?.any { sensor -> sensor.currentValue("motion") == "active" });
}

def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("debugOutput",[value:"false",type:"bool"])
}

private logDebug(msg) {
	if (settings?.debugOutput) {
		log.debug msg
	}
}