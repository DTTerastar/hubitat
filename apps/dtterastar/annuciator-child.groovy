definition (
    name: "Annuciator Child",
    namespace: "DTTerastar",
    author: "Darrell Turner",
    description: "Grouping for Notification / Speech Synthesis Devices",
    category: "Convenience",
    parent: "DTTerastar:Annuciator Groups",
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
			label title: "Annuciator Group Name", required: true
        }
        
        section("") {
			input "whichPlayAnnouncementAll", "capability.notification", title: "Which play announce all device (for Echo Speaks)", multiple: false
		}
		
		section("") {
			input "whichSpeechSynthesizers", "capability.speechSynthesis", title: "Which speech synthesizers", multiple: true
		}
		
		section("") {
			input "whichNotificationDevices", "capability.notification", title: "Which notification devices", multiple: true
		}
        

	}   
}

def childDevices() {
	return getChildDevices()?.find { true }
}

def installed() {
	log.debug "Installed with settings: ${settings}"
    
	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
	initialize()
}

def initialize() {
    log.debug "initialize()"
    createChildDevices()
}

private void createChildDevices() {
	if (childDevices) {
		childDevices.each {
			it.setLabel(app.label)
        }
	} else {
		addChildDevice("DTTerastar", "Annunciator Child Device", UUID.randomUUID().toString(), null, [completedSetup: true, isComponent: true, name: "Annunciator Child Device", label: app.label])
	}
}

void speak(phrase) {
    whichPlayAnnouncementAll?.playAnnouncementAll(phrase)
	whichSpeechSynthesizers?.speak(phrase)
	whichNotificationDevices?.deviceNotification(phrase)
} 