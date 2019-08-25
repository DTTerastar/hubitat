definition (
    name: "Motion Aggregation",
    namespace: "DTTerastar",
    author: "Darrell Turner",
    description: "Aggregation for Motion / Contact devices",
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
	section (){app(name: "Motion Aggregator", appName: "Motion Aggregation Child", namespace: "DTTerastar", title: "<b>Add a new Motion Aggregator</b>", multiple: true)}
	}
  }
}

def installCheck(){         
	state.appInstalled = app.getInstallationState() 
	if(state.appInstalled != 'COMPLETE'){
	section{paragraph "Please hit 'Done'"}
	  }
	}

