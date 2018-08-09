/**
 *  Mp3 Event Player
 *
 *  Copyright 2018 Andrew Parker
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Last Update: 04/05/2018
 *
 *  Changes:
 *
 * 
 *  V1.2.3 - Debug - error in goNow method relating to enable/disable switch
 *  V1.2.2 - Debug - appliance trigger
 *  V1.2.1 - Debug "time" trigger
 *  V1.2.0 - Converted Sonos command for Hubitat compatibility
 *  V1.0.0 - POC
 *
 */
 
definition(
    name: "MP3 Event Player Child",
    namespace: "Cobra",
    author: "Andrew Parker",
    description: "Plays an MP3 (stored on a webserver) when an event occurs",
    category: "Fun & Social",
    
    parent: "Cobra:MP3 Event Player",
    
    iconUrl: "https://raw.githubusercontent.com/cobravmax/SmartThings/master/icons/voice.png",
    iconX2Url: "https://raw.githubusercontent.com/cobravmax/SmartThings/master/icons/voice.png",
    iconX3Url: "https://raw.githubusercontent.com/cobravmax/SmartThings/master/icons/voice.png")



preferences {
	
    page name: "mainPage", title: "", install: false, uninstall: true, nextPage: "timeIntervalInput"
    page name: "timeIntervalInput", title: "Restrictions", install: false, uninstall: true, nextPage: "namePage" 
   page name: "namePage", title: "", install: true, uninstall: true

    
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
	
    state.timer = 'yes'
    state.currS1 =  'on'
    setAppVersion()
    logCheck()
    if(acceleration){subscribe(acceleration, "acceleration.active", eventHandler)}
    if(button1){subscribe(button1, "button.pushed", eventHandler)}
    if(contact){subscribe(contact, "contact.open", eventHandler)}
    if(contactClosed){subscribe(contactClosed, "contact.closed", eventHandler)}
	if(arrivalPresence){subscribe(arrivalPresence, "presence.present", eventHandler)}
	if(departurePresence){subscribe(departurePresence, "presence.not present", eventHandler)}
    if(motion){subscribe(motion, "motion.active", eventHandler)}
    if(smoke){subscribe(smoke, "smoke.detected", eventHandler)}
	if(smoke){subscribe(smoke, "carbonMonoxide.detected", eventHandler)}
	if(mySwitch){subscribe(mySwitch, "switch.on", eventHandler)}
	if(mySwitchOff){subscribe(mySwitchOff, "switch.off", eventHandler)}
	if (water){subscribe(water, "water.wet", eventHandler)}
    if (triggerModes) {subscribe(location, modeChangeHandler)}
	if (timeOfDay) {schedule(timeOfDay, scheduledTimeHandler)}
	if(switch1){subscribe(switch1, "switch", switchHandler)}
    if(powerSensor){subscribe(powerSensor, "power", powerApplianceNow)}
}




def timeIntervalInput() {
	dynamicPage(name: "timeIntervalInput") {
section {
if(trigger != "Time"){ 
			input "starting", "time", title: "Starting", required: false
			input "ending", "time", title: "Ending", required: false
            }
      	 input "days", "enum", title: "Only on certain days of the week", multiple: true, required: false, options: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
if(trigger != "Mode Change"){ 
		input "modes", "mode", title: "Only when mode is", multiple: true, required: false
            }  
           
     
      } 
           section("Select Enable/Disable Switch (Optional)") {
    		input "switch1", "capability.switch", title: "", required: false, multiple: false 
    }
    section("Set different volume on messages between these times?") {
	input "volume2", "number", title: "Quiet Time Speaker volume", description: "0-100%", defaultValue: "0",  required: true
    input "fromTime2", "time", title: "Quiet Time Start", required: false
    input "toTime2", "time", title: "Quiet Time End", required: false
    }
 }
}


def mainPage() {
    dynamicPage(name: "mainPage") {
section("Speaker Settings") {
		 input "speaker1", "capability.musicPlayer", title: "Speaker ", required: true, multiple: true
         input "volume1", "number", title: "volume", description: "0-100%", required: true
//         input "duration", "number", title: "Duration of mp3 (seconds)",  required: true
         input "msgDelay", "number", title: "Number of minutes between messages", description: "Minutes", required: true
	}

 section("mp3 URI") {
    input "pathURI", "text", title: "Enter URI to mp3 files (e.g. http://mydomain.com/files or http://localwebserver/files)",  required: true 
    input "sound", "text", title: "Enter mp3 name here (e.g. alert.mp3)",  required: true 
   } 

section("Trigger") {
  input "trigger", "enum", title: "Action to trigger mp3", required: true, submitOnChange: true, options: ["Acceleration", "Appliance Power Monitor",  "Contact Open", "Contact Closed", "Carbon Monoxide Detected", "Presence - Arrival", "Presence - Departure", "Leak Detected", "Mode Change", "Motion Active", "Smoke Detected", "Switch On", "Switch Off", "Time"]
// "Button Pushed",

}
section() {
chooseInputs()
}
}
}
  
def chooseInputs(){    
    if (trigger) {
    state.selection = trigger

    if(trigger == "Acceleration"){
    input  "acceleration", "capability.accelerationSensor", title: "Play when acceleration detected here...", required: true, multiple: true
   }
   if(trigger == "Appliance Power Monitor"){
   input "powerSensor", "capability.powerMeter", title: "Select power sensor to trigger message", required: true, multiple: false 
    input(name: "belowThreshold", type: "number", title: "Below Power Threshold (Watts)", required: true, description: "Trigger below this number of watts", defaultValue: '0')
    input(name: "delay2", type: "number", title: "Only if it stays that way for this number of minutes...", required: true, description: "this number of minutes", defaultValue: '0')
    input(name: "aboveThreshold", type: "number", title: "Activate Power Threshold (Watts)", required: true, description: "Start monitoring above this number of watts", defaultValue: '0')
	}
   
   
   
   
   
	if(trigger == "Button Pushed"){
	input "button1", "capability.button", title: "Play when this button pushed", required:true, multiple:true 
   }
   
	if(trigger == "Contact Open"){
	input "contact", "capability.contactSensor", title: "Play when this contact opens", required: true, multiple: true
   }
   
   	if(trigger == "Contact Closed"){
	input "contactClosed", "capability.contactSensor", title: "Play when this contact closes", required: true, multiple: true
   }
   
   if(trigger == "Carbon Monoxide Detected"){
   input "smoke", "capability.smokeDetector", title: "Play when C02 detected here...", required: true, multiple: true
   }
   
   if(trigger == "Presence - Arrival"){
   input "arrivalPresence", "capability.presenceSensor", title: "Play when this presence sensor arrives", required: true, multiple: true
   }
   
   if(trigger == "Presence - Departure"){
   input "departurePresence", "capability.presenceSensor", title: "Play when this presence sensor departs", required: true, multiple: true
   }
   
   if(trigger == "Leak Detected"){
   input  "water", "capability.waterSensor", title: "Play when this detector shows 'wet' ", required: true, multiple: true
   }
   
   if(trigger == "Mode Change"){
   input "triggerModes", "mode", title: "System Changes Mode", description: "Select mode(s)", required: false, multiple: true
   }
   
   
   if(trigger == "Motion Active"){   
   input "motion", "capability.motionSensor", title: "Play when there is motion here", required: true, multiple: true
   }
   
   if(trigger == "Smoke Detected"){   
   input "smoke", "capability.smokeDetector", title: "Play when there is smoke detected by this sensor", required: true, multiple: true
   }
   
   if(trigger == "Switch On"){ 
   input "mySwitch", "capability.switch", title: "This switch turns on", required: true, multiple: true
  }   
  
   if(trigger == "Switch Off"){ 
   input "mySwitchOff", "capability.switch", title: "This switch turns off", required: true, multiple: true
  } 
  
   if(trigger == "Time"){ 
  input "timeOfDay", "time", title: "At a Scheduled Time", required: false
 }
 
 }
}

// Appliance Power Monitor
def powerApplianceNow(evt){
    getAllOk()
state.meterValue = evt.value as double
state.activateThreshold = aboveThreshold

LOGDEBUG( "Power reported $state.meterValue watts")
if(state.activate != true){
if(state.meterValue > state.activateThreshold){
state.activate = true
LOGDEBUG( "Activate threshold reached or exceeded setting state.activate to: $state.activate")
}
}

LOGDEBUG( "state.currS1 == $state.currS1 && state.activate == $state.activate")

 if(state.currS1 == 'on' && state.activate == true){
LOGDEBUG( "powerApplianceNow -  Power is: $state.meterValue")
    state.belowValue = belowThreshold as int
    if (state.meterValue < state.belowValue) {
   def mydelay = 60 * delay2 
LOGDEBUG( "Checking again after delay: $delay2 minutes... Power is: $state.meterValue")
       runIn(mydelay, checkApplianceAgain1, [overwrite: false])     
       
      }
}

	 if(state.activate == false){
LOGDEBUG( "Not reached threshold yet to activate monitoring")
     
     }
     
     
 if(state.currS1 == false){
LOGDEBUG( "App disabled by $enableswitch being off")

}

}


def checkApplianceAgain1() {
   
     if (state.meterValue < state.belowValue) {
LOGDEBUG(" checkApplianceAgain1 - Checking again now... Power is: $state.meterValue")
    def evt1 = "APPLIANCE POWER"
      eventHandler(evt1)
      state.activate = false  
			}
     else  if (state.meterValue > state.belowValue) {
LOGDEBUG( "checkApplianceAgain1 -  Power is: $state.meterValue so cannot run yet...")
	}	
}	





def switchHandler(evt) {
   state.currS1 = evt.value  // Note: Optional if switch is used to control action
LOGDEBUG("$switch1 = $evt.value")
   
    
    
  					   }



def scheduledTimeHandler(evt) {
   def evt1 = 'SCHEDULED TIME EVENT'
	eventHandler(evt1)
}

def modeChangeHandler(evt) {
LOGDEBUG( "modeChangeHandler $evt.name: $evt.value ($triggerModes)")
	if (evt.value in triggerModes) {
		eventHandler(evt)
	}
}



def eventHandler(evt){
LOGDEBUG( "state.timer = $state.timer")
if (state.timer == 'yes'){
goNow(evt)
	}
}


def goNow(evt){
LOGDEBUG("goNow evt = $evt.value - state.currS1 = $state.currS1")
 if (state.currS1 == null || state.currS1 == 'on') {
    LOGDEBUG("state.currS1 = $state.currS1") 
if (allOk) {
    LOGDEBUG("Playing: $soundURI " )
def soundURI = pathURI + "/" + sound 

LOGDEBUG("Playing: $soundURI " )
setVolume()
def speaker = speaker1
speaker.playTrack(soundURI)  // might need to change command at some point.
	}
   }
           
	state.timer = 'no'
    
LOGDEBUG("Message allow: set to '$state.timer' as I have just played a message")
state.timeDelay = 60 * msgDelay
LOGDEBUG("Waiting for $state.timeDelay seconds before resetting timer to allow further messages")
runIn(state.timeDelay, resetTimer)
}

def resetTimer() {
state.timer = 'yes'
LOGDEBUG( "Timer reset - Messages allowed")

}

def namePage() {
       dynamicPage(name: "namePage") {
       
     
            section("Automation name") {
                label title: "Enter a name for this mp3 automation", required: false
            }
           section("Logging") {
           input "debugMode", "bool", title: "Enable logging", required: true, defaultValue: false
  	       }
      }  
    }
    
    
private getAllOk() {
	modeOk && daysOk && timeOk
}

private getModeOk() {
	def result = !modes || modes.contains(location.mode)
LOGDEBUG( "modeOk = $result")
	result
}

private getDaysOk() {
	def result = true
	if (days) {
		def df = new java.text.SimpleDateFormat("EEEE")
		if (location.timeZone) {
			df.setTimeZone(location.timeZone)
		}
		else {
			df.setTimeZone(TimeZone.getTimeZone("America/New_York"))
		}
		def day = df.format(new Date())
		result = days.contains(day)
	}
LOGDEBUG( "daysOk = $result")
	result
}

private getTimeOk() {
	def result = true
	if (starting && ending) {
		def currTime = now()
		def start = timeToday(starting, location?.timeZone).time
		def stop = timeToday(ending, location?.timeZone).time
		result = start < stop ? currTime >= start && currTime <= stop : currTime <= stop || currTime >= start
	}
LOGDEBUG("timeOk = $result")
	result
}

private hhmm(time, fmt = "h:mm a")
{
	def t = timeToday(time, location.timeZone)
	def f = new java.text.SimpleDateFormat(fmt)
	f.setTimeZone(location.timeZone ?: timeZone(time))
	f.format(t)
}

private getTimeLabel()
{
	(starting && ending) ? hhmm(starting) + "-" + hhmm(ending, "h:mm a z") : ""
}    
    
def setVolume(){
    LOGDEBUG("Calling setVolume...")
def timecheck = fromTime2
if (timecheck != null){
def between2 = timeOfDayIsBetween(fromTime2, toTime2, new Date(), location.timeZone)
    if (between2) {
        state.volume = volume2
       
LOGDEBUG("Quiet Time = Yes - Setting Quiet time - Volume = $state.volume Speaker = $state.speakerNow")
    
}
else if (!between2) {
state.volume = volume1

LOGDEBUG("Quiet Time = No - Setting Normal time - Volume = $state.volume Speaker = $state.speakerNow")

	}
}
else if (timecheck == null){
state.volume = volume1
LOGDEBUG("Quiet Time = No - Setting Normal time - Volume = $state.volume Speaker = $state.speakerNow")
	}
}    

// define debug action
def logCheck(){
state.checkLog = debugMode
if(state.checkLog == true){
log.info "All Logging Enabled"
}
else if(state.checkLog == false){
log.info "Further Logging Disabled"
}

}
def LOGDEBUG(txt){
    try {
    	if (settings.debugMode) { log.debug("${app.label.replace(" ","_").toUpperCase()}  (Childapp Version: ${state.appversion}) - ${txt}") }
    } catch(ex) {
    	log.error("LOGDEBUG unable to output requested data!")
    }
}    
    
// App Version   *********************************************************************************
def setAppVersion(){
    state.appversion = "1.2.3"
}