definition (
    name: "Annuciator Groups",
    namespace: "DTTerastar",
    author: "Darrell Turner",
    description: "Grouping for Notification / Speech Synthesis Devices",
    category: "Convenience",
	iconUrl: "",
	iconX2Url: ""
)

preferences {page name: "mainPage", title: "", install: true, uninstall: true}
def installed() {initialize()}
def updated() {initialize()}
def initialize() {
    version()
    log.debug "Initialised with settings: ${settings}"
    log.info "There are ${childApps.size()} child apps"
    childApps.each {child ->
    log.info "Child app: ${child.label}"
    }    
}

def mainPage() {
    dynamicPage(name: "mainPage") {   
	installCheck()
	if(state.appInstalled == 'COMPLETE'){
	section (){app(name: "Annuciator", appName: "Annuciator", namespace: "DTTerastar", title: "<b>Add a new annuciator</b>", multiple: true)}
	}
  }
}

def installCheck(){         
	state.appInstalled = app.getInstallationState() 
	if(state.appInstalled != 'COMPLETE'){
	section{paragraph "Please hit 'Done'"}
	  }
	}

