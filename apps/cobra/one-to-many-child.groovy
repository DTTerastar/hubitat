/**
 * ****************  One_To_Many_Switching.  ****************
 *
 *  Design Usage:
 *	This was designed to force one or more switches/lights/dimmers to follow a single switch/light/dimmer 
 *
 *
 *  Copyright 2019 Andrew Parker
 *  
 *  This App is free!
 *  Donations to support development efforts are accepted via: 
 *
 *  Paypal at: https://www.paypal.me/smartcobra
 *  
 *
 *  I'm very happy for you to use this app without a donation, but if you find it useful then it would be nice to get a 'shout out' on the forum! -  @Cobra
 *  Have an idea to make this app better?  - Please let me know :)
 *
 *  
 *
 *-------------------------------------------------------------------------------------------------------------------
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *-------------------------------------------------------------------------------------------------------------------
 *
 *  If modifying this project, please keep the above header intact and add your comments/credits below - Thank you! -  @Cobra
 *
 *-------------------------------------------------------------------------------------------------------------------
 *
 *  Last Update: 14/01/2019
 *
 *  Changes:
 *
 *  V1.6.0 - Added additonal (2nd) switch for restriction & fixed other restriction bugs
 *  V1.5.1 - Debug presence restriction
 *  V1.5.0 - added disable apps code
 *  V1.4.0 - Streamlined restrictions page to action faster if specific restrictions not used.
 *  V1.3.0 - Moved update notification to parent
 *  V1.2.0 - Changed to be able to control switches AND dimmers AND Lights
 *  V1.1.0 - Added restrictions page
 *  V1.0.2 - Revised Update Checking
 *  V1.0.1 - Changed the way default settings work to prevent having to toggle switches first time
 *  V1.0.0 - POC 
 */
 
 
 
 

definition(
    name: "One To Many Child",
    namespace: "Cobra",
    author: "Andrew Parker",
    description: "If you turn on/off a switch or adjust a dimmer/light - All others follow",
    category: "Convenience",
        
      parent: "Cobra:One To Many",
    
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
    )

preferences {
	section() {
	page name: "mainPage", title: "", install: false, uninstall: true, nextPage: "restrictionsPage"
	page name: "restrictionsPage", title: "", install: true, uninstall: true
	}
}

def mainPage() {
	dynamicPage(name: "mainPage") {
	preCheck()
        
        section() {
	input "childTypeSelect", "enum", required: true, title: "What to control", submitOnChange: true,  options: ["Dimmer or Light", "Switch"] 
	state.modeType = childTypeSelect                                                                                                                     
 }  
  	  if(state.modeType == 'Dimmer or Light'){           
	section("Control Dimmer or Light"){
		input "dimmer1",  "capability.switchLevel", multiple: false, required: true
	    }  
    section("Follow Dimmer(s) or Light(s)"){
		input "dimmer2",  "capability.switchLevel", multiple: true, required: false
		}
   	}   
   	         
        
      if(state.modeType == 'Switch'){           
	section("Control Switch"){
		input "switch1",  "capability.switch", multiple: false, required: true
	    }  
    section("Follow Switch(es)"){
		input "switch2",  "capability.switch", multiple: true, required: true
		}
    section("Select Mode"){
     input "reversemode", "bool", title: " Reverse operation? (Follow switch(es) off when Control Switch on)", required: true, submitOnChange: true, defaultValue: false    
	    }
	}
    }
}    
def restrictionsPage() {
    dynamicPage(name: "restrictionsPage") {
        section(){paragraph "<font size='+1'>App Restrictions</font> <br>These restrictions are optional <br>Any restriction you don't want to use, you can just leave blank or disabled"}
        section(){
		input "enableSwitchYes", "bool", title: "Enable restriction by external on/off switch(es)", required: true, defaultValue: false, submitOnChange: true
			if(enableSwitchYes){
			input "enableSwitch1", "capability.switch", title: "Select the first switch to Enable/Disable this app", required: false, multiple: false, submitOnChange: true 
			if(enableSwitch1){ input "enableSwitchMode1", "bool", title: "Allow app to run only when this switch is On or Off", required: true, defaultValue: false, submitOnChange: true}
			input "enableSwitch2", "capability.switch", title: "Select a second switch to Enable/Disable this app", required: false, multiple: false, submitOnChange: true 
			if(enableSwitch2){ input "enableSwitchMode2", "bool", title: "Allow app to run only when this switch is On or Off", required: true, defaultValue: false, submitOnChange: true}
			}
		}
        section(){
		input "modesYes", "bool", title: "Enable restriction by current mode(s)", required: true, defaultValue: false, submitOnChange: true	
			if(modesYes){	
			input(name:"modes", type: "mode", title: "Allow actions when current mode is:", multiple: true, required: false)
			}
		}	
       	section(){
		input "timeYes", "bool", title: "Enable restriction by time", required: true, defaultValue: false, submitOnChange: true	
			if(timeYes){	
    	input "fromTime", "time", title: "Allow actions from", required: false
    	input "toTime", "time", title: "Allow actions until", required: false
        	}
		}
		section(){
		input "dayYes", "bool", title: "Enable restriction by day(s)", required: true, defaultValue: false, submitOnChange: true	
			if(dayYes){	
    	input "days", "enum", title: "Allow actions only on these days of the week", required: false, multiple: true, options: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
        	}
		}
		section(){
		input "presenceYes", "bool", title: "Enable restriction by presence sensor(s)", required: true, defaultValue: false, submitOnChange: true	
			if(presenceYes){	
    	input "restrictPresenceSensor", "capability.presenceSensor", title: "Select presence sensor 1 to restrict action", required: false, multiple: false, submitOnChange: true
    	if(restrictPresenceSensor){input "restrictPresenceAction", "bool", title: "On = Allow action only when someone is 'Present'  <br>Off = Allow action only when someone is 'NOT Present'  ", required: true, defaultValue: false}
     	input "restrictPresenceSensor1", "capability.presenceSensor", title: "Select presence sensor 2 to restrict action", required: false, multiple: false, submitOnChange: true
    	if(restrictPresenceSensor1){input "restrictPresenceAction1", "bool", title: "On = Allow action only when someone is 'Present'  <br>Off = Allow action only when someone is 'NOT Present'  ", required: true, defaultValue: false}
   			}
		}	
		section(){
		input "sunrisesetYes", "bool", title: "Enable restriction by sunrise or sunset", required: true, defaultValue: false, submitOnChange: true	
			if(sunrisesetYes){
       	input "sunriseSunset", "enum", title: "Sunrise/Sunset Restriction", required: false, submitOnChange: true, options: ["Sunrise","Sunset"] 
		if(sunriseSunset == "Sunset"){	
       	input "sunsetOffsetValue", "number", title: "Optional Sunset Offset (Minutes)", required: false
		input "sunsetOffsetDir", "enum", title: "Before or After", required: false, options: ["Before","After"]
        	}
		if(sunriseSunset == "Sunrise"){
    	input "sunriseOffsetValue", "number", title: "Optional Sunrise Offset (Minutes)", required: false
		input "sunriseOffsetDir", "enum", title: "Before or After", required: false, options: ["Before","After"]
        	}
     	}
		}	
       
        section() {input "debugMode", "bool", title: "Enable debug logging", required: true, defaultValue: false}
		 section() {label title: "Enter a name for this automation", required: false}
    }
}

           


             

def installed(){initialise()}
def updated(){initialise()}
def initialise(){
	version()
	subscribeNow()
	log.info "Initialised with settings: ${settings}"
	logCheck()	
}
def subscribeNow() {
	unsubscribe()
	if(enableSwitch1){subscribe(enableSwitch1, "switch", switchEnable1)}
	if(enableSwitch2){subscribe(enableSwitch2, "switch", switchEnable2)}
	if(enableSwitchMode == null){enableSwitchMode = true} // ????
	if(restrictPresenceSensor){subscribe(restrictPresenceSensor, "presence", restrictPresenceSensorHandler)}
	if(restrictPresenceSensor1){subscribe(restrictPresenceSensor1, "presence", restrictPresence1SensorHandler)}
	if(sunriseSunset){astroCheck()}
	if(sunriseSunset){schedule("0 1 0 1/1 * ? *", astroCheck)} // checks sunrise/sunset change at 00.01am every day
    
  // App Specific subscriptions & settings below here   
 

    subscribe(switch1, "switch", switchHandler1)
    subscribe(dimmer1, "level", dimmerHandler1)
    subscribe(dimmer1, "switch", dimmerHandler3)
    subscribe(dimmer2, "level", dimmerHandler2)
    subscribe(dimmer2, "switch", dimmerHandler2)
}



def dimmerHandler2(evt){
LOGDEBUG("$dimmer2 set to: $evt.value")
}

def dimmerHandler1(evt){
    checkAllow()
	if(state.allAllow == true){
    state.mylevel1 = evt.value
        
    if(dimmer2){
    LOGDEBUG("Control dimmer is $state.mylevel1 - Setting $dimmer2 to the same...")
 
    state.mylevel1 = evt.value.toDouble()
   	dimmer2.setLevel(state.mylevel1)
    }  
  }      
}
def dimmerHandler3(evt){
    checkAllow()
	if(state.allAllow == true){

    state.mylevel1 = evt.value
        
    if(dimmer2){
    LOGDEBUG("Control dimmer is $state.mylevel1 - Setting $dimmer2 to the same...")
    
    if(state.mylevel1 == 'on'){dimmer2.on()}
    if(state.mylevel1 == 'off'){dimmer2.off()}
     }
    }      
}


def switchHandler1 (evt) {
//	log.warn "running switchhandler"
    checkAllow()
	if(state.allAllow == true){

	state.reverse = reversemode
if(reversemode == null){state.reverse = false}
	state.currS1 = evt.value
    LOGDEBUG("Control switch is $state.currS1")
    
if (state.reverse == false || state.reverse == null){
	if (state.currS1 == "on") { 
	LOGDEBUG("Turning on $switch2")
	switch2.on()
	}

	else if (state.currS1 == "off") { 
	LOGDEBUG("Turning off $switch2")
	switch2.off()
	}
 }
if (state.reverse == true){
	if (state.currS1 == "on") { 
	LOGDEBUG("Turning off $switch2")
	switch2.off()
	}

	else if (state.currS1 == "off") { 
	LOGDEBUG("Turning on $switch2")
	switch2.on()
	}
   } 
 } 
}
    
    
    
    
def checkAllow(){
    state.allAllow = false
    LOGDEBUG("Checking for any restrictions...")
    if(state.pauseApp == true){log.warn "Unable to continue - App paused"}
    if(state.pauseApp == false){
        LOGDEBUG("Continue - App NOT paused")
        state.noPause = true
		state.modeCheck = true
		state.presenceRestriction = true
		state.presenceRestriction1 = true
		state.dayCheck = true
		state.sunGoNow = true
		state.timeOK = true
		state.modes = modes
		state.fromTime = fromTime
		state.days = days
		state.sunriseSunset = sunriseSunset
		state.restrictPresenceSensor = restrictPresenceSensor
		state.restrictPresenceSensor1 = restrictPresenceSensor1
		state.timeYes = timeYes
		state.enableSwitchYes = enableSwitchYes
		state.modesYes = modesYes
		state.dayYes = dayYes
		state.sunrisesetYes = sunrisesetYes
		state.presenceYes = presenceYes
		
		if(state.enableSwitchYes == false){
		state.appgo1 = true
		state.appgo2 = true
		}
		if(state.modes != null && state.modesYes == true){modeCheck()}	
		if(state.fromTime !=null && state.timeYes == true){checkTime()}
		if(state.days!=null && state.dayYes == true){checkDay()}
		if(state.sunriseSunset !=null && state.sunrisesetYes == true){checkSun()}
		if(state.restrictPresenceSensor != null && state.presenceYes == true){checkPresence()}
        if(state.restrictPresenceSensor1 != null && state.presenceYes == true){checkPresence1()}
 
	if(state.modeCheck == false){
	LOGDEBUG("Not in correct 'mode' to continue")
	    }    
	if(state.presenceRestriction ==  false || state.presenceRestriction1 ==  false){
	LOGDEBUG( "Cannot continue - Presence failed")
	}
	if(state.appgo1 == false){
	LOGDEBUG("$enableSwitch1 is not in the correct position so cannot continue")
	}
	if(state.appgo2 == false){
	LOGDEBUG("$enableSwitch2 is not in the correct position so cannot continue")
	}
	if(state.appgo1 == true && state.appgo2 == true && state.dayCheck == true && state.presenceRestriction == true && state.presenceRestriction1 == true && state.modeCheck == true && state.timeOK == true && state.noPause == true && state.sunGoNow == true){
	state.allAllow = true 
 	  }
	else{
 	state.allAllow = false
	LOGWARN( "One or more restrictions apply - Unable to continue")
 	LOGDEBUG("state.appgo1 = $state.appgo1, state.appgo2 = $state.appgo2, state.dayCheck = $state.dayCheck, state.presenceRestriction = $state.presenceRestriction, state.presenceRestriction1 = $state.presenceRestriction1, state.modeCheck = $state.modeCheck, state.timeOK = $state.timeOK, state.noPause = $state.noPause, state.sunGoNow = $state.sunGoNow")
      }
   }

}

def checkSun(){
	LOGDEBUG("Checking Sunrise/Sunset restrictions...")
	if(!sunriseSunset){
        state.sunGoNow = true
        LOGDEBUG("No Sunrise/Sunset restrictions in place")	
	}
        if(sunriseSunset){
        if(sunriseSunset == "Sunset"){	
        if(state.astro == "Set"){
        state.sunGoNow = true
        LOGDEBUG("Sunset OK")
            } 
    	if(state.astro == "Rise"){
        state.sunGoNow = false
        LOGDEBUG("Sunset NOT OK")
            } 
        }
	if(sunriseSunset == "Sunrise"){	
        if(state.astro == "Rise"){
        state.sunGoNow = true
        LOGDEBUG("Sunrise OK")
            } 
    	if(state.astro == "Set"){
        state.sunGoNow = false
        LOGDEBUG("Sunrise NOT OK")
            } 
        }  
    } 
		return state.sunGoNow
}    

def astroCheck() {
    state.sunsetOffsetValue1 = sunsetOffsetValue
    state.sunriseOffsetValue1 = sunriseOffsetValue
    if(sunsetOffsetDir == "Before"){state.sunsetOffset1 = -state.sunsetOffsetValue1}
    if(sunsetOffsetDir == "after"){state.sunsetOffset1 = state.sunsetOffsetValue1}
    if(sunriseOffsetDir == "Before"){state.sunriseOffset1 = -state.sunriseOffsetValue1}
    if(sunriseOffsetDir == "after"){state.sunriseOffset1 = state.sunriseOffsetValue1}
	def both = getSunriseAndSunset(sunriseOffset: state.sunriseOffset1, sunsetOffset: state.sunsetOffset1)
	def now = new Date()
	def riseTime = both.sunrise
	def setTime = both.sunset
	LOGDEBUG("riseTime: $riseTime")
	LOGDEBUG("setTime: $setTime")
	unschedule("sunriseHandler")
	unschedule("sunsetHandler")
	if (riseTime.after(now)) {
	LOGDEBUG("scheduling sunrise handler for $riseTime")
	runOnce(riseTime, sunriseHandler)
		}
	if(setTime.after(now)) {
	LOGDEBUG("scheduling sunset handler for $setTime")
	runOnce(setTime, sunsetHandler)
		}
	LOGDEBUG("AstroCheck Complete")
}

def sunsetHandler(evt) {
	LOGDEBUG("Sun has set!")
	state.astro = "Set" 
}
def sunriseHandler(evt) {
	LOGDEBUG("Sun has risen!")
	state.astro = "Rise"
}

def modeCheck() {
    LOGDEBUG("Checking for any 'mode' restrictions...")
	def result = !modes || modes.contains(location.mode)
    LOGDEBUG("Mode = $result")
    state.modeCheck = result
    return state.modeCheck
 }



def checkTime(){
    LOGDEBUG("Checking for any time restrictions")
	def timecheckNow = fromTime
	if (timecheckNow != null){
    
def between = timeOfDayIsBetween(toDateTime(fromTime), toDateTime(toTime), new Date(), location.timeZone)
    if (between) {
    state.timeOK = true
   LOGDEBUG("Time is ok so can continue...")
    
}
	else if (!between) {
	state.timeOK = false
	LOGDEBUG("Time is NOT ok so cannot continue...")
	}
  }
	else if (timecheckNow == null){  
	state.timeOK = true
  	LOGDEBUG("Time restrictions have not been configured -  Continue...")
  }
}



def checkDay(){
    LOGDEBUG("Checking for any 'Day' restrictions")
	def daycheckNow = days
	if (daycheckNow != null){
 	def df = new java.text.SimpleDateFormat("EEEE")
    df.setTimeZone(location.timeZone)
    def day = df.format(new Date())
    def dayCheck1 = days.contains(day)
    if (dayCheck1) {
	state.dayCheck = true
	LOGDEBUG( "Day ok so can continue...")
 }       
 	else {
	LOGDEBUG( "Cannot run today!")
 	state.dayCheck = false
 	}
 }
if (daycheckNow == null){ 
	LOGDEBUG("Day restrictions have not been configured -  Continue...")
	state.dayCheck = true 
	} 
}

def restrictPresenceSensorHandler(evt){
	state.presencestatus1 = evt.value
	LOGDEBUG("state.presencestatus1 = $evt.value")
	checkPresence()
}



def checkPresence(){
	LOGDEBUG("Running checkPresence - restrictPresenceSensor = $restrictPresenceSensor")
	if(restrictPresenceSensor){
	LOGDEBUG("Presence = $state.presencestatus1")
	def actionPresenceRestrict = restrictPresenceAction
	if (state.presencestatus1 == "present" && actionPresenceRestrict == true){
	LOGDEBUG("Presence ok")
	state.presenceRestriction = true
	}
	if (state.presencestatus1 == "not present" && actionPresenceRestrict == true){
	LOGDEBUG("Presence not ok")
	state.presenceRestriction = false
	}

	if (state.presencestatus1 == "not present" && actionPresenceRestrict == false){
	LOGDEBUG("Presence ok")
	state.presenceRestriction = true
	}
	if (state.presencestatus1 == "present" && actionPresenceRestrict == false){
	LOGDEBUG("Presence not ok")
	state.presenceRestriction = false
	}
}
	else if(restrictPresenceSensor == null){
	state.presenceRestriction = true
	LOGDEBUG("Presence sensor restriction not used")
	}
}


def restrictPresence1SensorHandler(evt){
	state.presencestatus2 = evt.value
	LOGDEBUG("state.presencestatus2 = $evt.value")
	checkPresence1()
}


def checkPresence1(){
	LOGDEBUG("running checkPresence1 - restrictPresenceSensor1 = $restrictPresenceSensor1")
	if(restrictPresenceSensor1){
	LOGDEBUG("Presence = $state.presencestatus1")
	def actionPresenceRestrict1 = restrictPresenceAction1
	if (state.presencestatus2 == "present" && actionPresenceRestrict1 == true){
	LOGDEBUG("Presence 2 ok - Continue..")
	state.presenceRestriction1 = true
	}
	if (state.presencestatus2 == "not present" && actionPresenceRestrict1 == true){
	LOGDEBUG("Presence 2 not ok")
	state.presenceRestriction1 = false
	}
	if (state.presencestatus2 == "not present" && actionPresenceRestrict1 == false){
	LOGDEBUG("Presence 2 ok - Continue..")
	state.presenceRestriction1 = true
	}
	if (state.presencestatus2 == "present" && actionPresenceRestrict1 == false){
	LOGDEBUG("Presence 2 not ok")
	state.presenceRestriction1 = false
	}
  }
	if(restrictPresenceSensor1 == null){
	state.presenceRestriction1 = true
	LOGDEBUG("Presence sensor 2 restriction not used - Continue..")
	}
}

def switchEnable1(evt){
	state.enableInput1 = evt.value
	LOGDEBUG("Switch changed to: $state.enableInput")  
    if(enableSwitchMode1 == true && state.enableInput1 == 'off'){
	state.appgo1 = false
	LOGDEBUG("Cannot continue - App disabled by switch1")  
    }
	if(enableSwitchMode1 == true && state.enableInput1 == 'on'){
	state.appgo1 = true
	LOGDEBUG("Switch1 restriction is OK.. Continue...") 
    }    
	if(enableSwitchMode1 == false && state.enableInput1 == 'off'){
	state.appgo1 = true
	LOGDEBUG("Switch1 restriction is OK.. Continue...")  
    }
	if(enableSwitchMode1 == false && state.enableInput1 == 'on'){
	state.appgo1 = false
	LOGDEBUG("Cannot continue - App disabled by switch1")  
    }    
	LOGDEBUG("Allow by switch1 is $state.appgo1")
}

def switchEnable2(evt){
	state.enableInput2 = evt.value
	LOGDEBUG("Switch changed to: $state.enableInput")  
    if(enableSwitchMode2 == true && state.enableInput2 == 'off'){
	state.appgo2 = false
	LOGDEBUG("Cannot continue - App disabled by switch2")  
    }
	if(enableSwitchMode2 == true && state.enableInput2 == 'on'){
	state.appgo2 = true
	LOGDEBUG("Switch2 restriction is OK.. Continue...") 
    }    
	if(enableSwitchMode2 == false && state.enableInput2 == 'off'){
	state.appgo2 = true
	LOGDEBUG("Switch2 restriction is OK.. Continue...")  
    }
	if(enableSwitchMode2 == false && state.enableInput2 == 'on'){
	state.appgo2 = false
	LOGDEBUG("Cannot continue - App disabled by switch2")  
    }    
	LOGDEBUG("Allow by switch2 is $state.appgo2")
}





def version(){
	setDefaults()
	pauseOrNot()
	logCheck()
	resetBtnName()
	schedule("0 0 9 ? * FRI *", updateCheck) //  Check for updates at 9am every Friday
	checkButtons()
   
}






def logCheck(){
    state.checkLog = debugMode
    if(state.checkLog == true){log.info "All Logging Enabled"}
    if(state.checkLog == false){log.info "Further Logging Disabled"}
}

def LOGDEBUG(txt){
    try {
    	if (settings.debugMode) { log.debug("${app.label.replace(" ","_").toUpperCase()}  (App Version: ${state.version}) - ${txt}") }
    } catch(ex) {
    	log.error("LOGDEBUG unable to output requested data!")
    }
}
def LOGWARN(txt){
    try {
    	if (settings.debugMode) { log.warn("${app.label.replace(" ","_").toUpperCase()}  (App Version: ${state.version}) - ${txt}") }
    } catch(ex) {
    	log.error("LOGWARN unable to output requested data!")
    }
}



def display(){
    setDefaults()
    if(state.status){section(){paragraph "<img src='http://update.hubitat.uk/icons/cobra3.png''</img> Version: $state.version <br><font face='Lucida Handwriting'>$state.Copyright </font>"}}
    if(state.status != "<b>** This app is no longer supported by $state.author  **</b>"){section(){input "updateBtn", "button", title: "$state.btnName"}}
    if(state.status != "Current"){section(){paragraph "<hr><b>Updated: </b><i>$state.Comment</i><br><br><i>Changes in version $state.newver</i><br>$state.UpdateInfo<hr><b>Update URL: </b><font color = 'red'> $state.updateURI</font><hr>"}}
    section(){input "pause1", "bool", title: "Pause This App", required: true, submitOnChange: true, defaultValue: false }
}



def checkButtons(){
    LOGDEBUG("Running checkButtons")
    appButtonHandler("updateBtn")
}


def appButtonHandler(btn){
    state.btnCall = btn
    if(state.btnCall == "updateBtn"){
    LOGDEBUG("Checking for updates now...")
    updateCheck()
    pause(3000)
    state.btnName = state.newBtn
    runIn(2, resetBtnName)
    }
    if(state.btnCall == "updateBtn1"){
    state.btnName1 = "Click Here" 
    httpGet("https://github.com/CobraVmax/Hubitat/tree/master/Apps' target='_blank")
    }
    
}   
def resetBtnName(){
    LOGDEBUG("Resetting Button")
    if(state.status != "Current"){
    state.btnName = state.newBtn
    }
    else{
    state.btnName = "Check For Update" 
    }
}    
    

def pushOverUpdate(inMsg){
    if(updateNotification == true){  
    newMessage = inMsg
    LOGDEBUG(" Message = $newMessage ")  
    state.msg1 = '[L]' + newMessage
    speakerUpdate.speak(state.msg1)
    }
}

def pauseOrNot(){
LOGDEBUG(" Calling 'pauseOrNot'...")
    state.pauseNow = pause1
    if(state.pauseNow == true){
    state.pauseApp = true
    if(app.label){
    if(app.label.contains('red')){
    log.warn "Paused"}
    else{app.updateLabel(app.label + ("<font color = 'red'> (Paused) </font>" ))
    log.warn "App Paused - state.pauseApp = $state.pauseApp "   
    }
   }
  }
    if(state.pauseNow == false){
    state.pauseApp = false
    if(app.label){
    if(app.label.contains('red')){ app.updateLabel(app.label.minus("<font color = 'red'> (Paused) </font>" ))
    LOGDEBUG("App Released - state.pauseApp = $state.pauseApp ")                          
    }
   }
  }    
}


def stopAllChildren(disableChild, msg){
	state.disableornot = disableChild
	state.message1 = msg
	LOGDEBUG(" $state.message1 - Disable app = $state.disableornot")
	state.appgo = state.disableornot
	state.restrictRun = state.disableornot
	if(state.disableornot == true){
	unsubscribe()
//	unschedule()
	}
	if(state.disableornot == false){
	subscribeNow()}

	
}

def updateCheck(){
    setVersion()
    def paramsUD = [uri: "http://update.hubitat.uk/json/${state.CobraAppCheck}"]
    try {
    httpGet(paramsUD) { respUD ->
//  log.warn " Version Checking - Response Data: ${respUD.data}"   // Troubleshooting Debug Code 
       		def copyrightRead = (respUD.data.copyright)
       		state.Copyright = copyrightRead
            def commentRead = (respUD.data.Comment)
       		state.Comment = commentRead

            def updateUri = (respUD.data.versions.UpdateInfo.GithubFiles.(state.InternalName))
            state.updateURI = updateUri   
            
            def newVerRaw = (respUD.data.versions.Application.(state.InternalName))
            state.newver = newVerRaw
            def newVer = (respUD.data.versions.Application.(state.InternalName).replace(".", ""))
       		def currentVer = state.version.replace(".", "")
      		state.UpdateInfo = (respUD.data.versions.UpdateInfo.Application.(state.InternalName))
                state.author = (respUD.data.author)
           
		if(newVer == "NLS"){
            state.status = "<b>** This app is no longer supported by $state.author  **</b>"  
             log.warn "** This app is no longer supported by $state.author **" 
            
      		}           
		else if(currentVer < newVer){
        	state.status = "<b>New Version Available ($newVerRaw)</b>"
        	log.warn "** There is a newer version of this app available  (Version: $newVerRaw) **"
        	log.warn " Update: $state.UpdateInfo "
             state.newBtn = state.status
            state.updateMsg = "There is a new version of '$state.ExternalName' available (Version: $newVerRaw)"
            
       		} 
		else{ 
      		state.status = "Current"
       		LOGDEBUG("You are using the current version of this app")
       		}
      					}
        	} 
        catch (e) {
        	log.error "Something went wrong: CHECK THE JSON FILE AND IT'S URI -  $e"
    		}
    if(state.status != "Current"){
		state.newBtn = state.status
		inform()
        
    }
    else{
        state.newBtn = "No Update Available"
    }
        
        
}


def inform(){
	log.warn "An update is available - Telling the parent!"
	parent.childUpdate(true,state.updateMsg) 
}



def preCheck(){
	setVersion()
    state.appInstalled = app.getInstallationState()  
    if(state.appInstalled != 'COMPLETE'){
    section(){ paragraph "$state.preCheckMessage"}
    }
    if(state.appInstalled == 'COMPLETE'){
    display()   
 	}
}

def cobra(){
	log.warn "Previous schedule for old 'Cobra Update' found... Removing......"
	unschedule(cobra)
	log.info "Cleanup Complete!"
}


def setDefaults(){
    LOGDEBUG("Initialising defaults...")
    if(pause1 == null){pause1 = false}
    if(state.pauseApp == null){state.pauseApp = false}
    if(enableSwitch1 == null){
    LOGDEBUG("Enable switch1 is NOT used.. Continue..")
    state.appgo1 = true
	}
	if(enableSwitch2 == null){
    LOGDEBUG("Enable switch2 is NOT used.. Continue..")
    state.appgo2 = true	
    }
	state.restrictRun = false
}




def setVersion(){
    state.version = "1.6.0"
     state.InternalName = "OneToManyChild"
     state.ExternalName = "One To Many Child"
	 state.preCheckMessage = "This app is designed to force one or more switches/lights/dimmers to follow a single switch/light/dimmer." 
     state.CobraAppCheck = "one2many.json"
}