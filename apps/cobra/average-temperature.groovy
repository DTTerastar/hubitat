/**
 *  ****************  Average_Temperature.  ****************
 *
 *  Design Usage:
 *  This was designed to try and display/set an 'average' or mean temp from a group of temperature devices
 *
 *
 *  Copyright 2018 Andrew Parker
 *  
 *  This SmartApp is free!
 *  Donations to support development efforts are accepted via: 
 *
 *  Paypal at: https://www.paypal.me/smartcobra
 *  
 *
 *  I'm very happy for you to use this app without a donation, but if you find it useful then it would be nice to get a 'shout out' on the forum! -  @Cobra
 *  Have an idea to make this app better?  - Please let me know :)
 *
 *  Website: http://securendpoint.com/smartthings
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
 *  Last Update: 06/08/2018
 *
 *  Changes:
 *
 *  V1.3.0 - Changed outgoing commands so this is now compatible with a virtual thermostat
 *  V1.2.1 - Debug
 *  V1.2.0 - Added selectable decimal places on result
 *  V1.1.0 - Added remote version checking
 *  V1.0.0 - Port to Hubitat
 *
 */



definition(
    name: "Average Temperature",
    namespace: "Cobra",
    author: "AJ Parker",
    description: "Virtual Average Temp Device Helper App",
    category: "My Apps",

    

    iconUrl: "",
    iconX2Url: ""
)
preferences {
    display()



   
    section("Choose Physical Temp Sensors"){
        input "tempSensors", "capability.temperatureMeasurement", title: "Physical Sensors", multiple: true
    }
     section("Set Virtual Temp Device "){
        input "vDevice", "capability.temperatureMeasurement", title: "Virtual Device"
        input "decimalUnit", "enum", title: "Max Decimal Places", required:true, defaultValue: "2", options: ["1", "2", "3", "4", "5"]
    }
    
   
    section("Logging") {
            input "debugMode", "bool", title: "Enable logging", required: true, defaultValue: false
  	        }
}

def installed() {
    initialize()
}

def updated() {
    unsubscribe()
    initialize()
}

def initialize() {
    version()
	logCheck()
    subscribe(tempSensors, "temperature", tempSensorsHandler)
    state.DecimalPlaces = decimalUnit.toInteger()
    log.info "Installed with settings: ${settings}"
}

def tempSensorsHandler(evt) {
    def sum = 0
    def count = 0
    def mean = 0

    for (sensor in settings.tempSensors) {
    count += 1 
    sum += sensor.currentTemperature }
	state.mean1 = sum/count
//    log.warn "state.mean1 = $state.mean1"   // debug code
    state.mean2 = state.mean1.toDouble()
//    log.warn "state.mean2 = $state.mean2"    //  debug code
    state.mean = state.mean2.round(state.DecimalPlaces)
    LOGDEBUG("Average Temp = $state.mean")
	LOGDEBUG("Sending info to $vDevice")
//    if(vDevice){
//    settings.vDevice.parse("${state.mean}")
 //   }
//    if(vStat){
    settings.vDevice.setTemperature("${state.mean}")
//    }
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
    	if (settings.debugMode) { log.debug("${app.label.replace(" ","_").toUpperCase()}  (App Version: ${state.appversion}) - ${txt}") }
    } catch(ex) {
    	log.error("LOGDEBUG unable to output requested data!")
    }
}


// Check Version   *********************************************************************************
def version(){
    cobra()
    if (state.Type == "Application"){
    schedule("0 0 14 ? * FRI *", cobra)
    }
    if (state.Type == "Driver"){
    schedule("0 45 16 ? * MON *", cobra)
    }
}

def display(){
    
    section{
            paragraph "Version Status: $state.Status"
			paragraph "Current Version: $state.version -  $state.Copyright"
			}

}


def cobra(){
    
    setAppVersion()
    def paramsUD = [uri: "http://update.hubitat.uk/cobra.json"]
       try {
        httpGet(paramsUD) { respUD ->
//   log.info " Version Checking - Response Data: ${respUD.data}"   // Debug Code 
       def copyNow = (respUD.data.copyright)
       state.Copyright = copyNow
            def newver = (respUD.data.versions.(state.Type).(state.InternalName))
            def cobraVer = (respUD.data.versions.(state.Type).(state.InternalName).replace(".", ""))
       def cobraOld = state.version.replace(".", "")
       if(cobraOld < cobraVer){
		state.Status = "<b>** New Version Available (Version: $newver) **</b>"
           log.warn "** There is a newer version of this $state.Type available  (Version: $newver) **"
       }    
       else{ 
      state.Status = "Current"
      log.info "$state.Type is the current version"
       }
       
       }
        } 
        catch (e) {
        log.error "Something went wrong: $e"
    }
}        



 
// App Version   *********************************************************************************
def setAppVersion(){
     state.version = "1.3.0"
     state.InternalName = "AverageTemp"
     state.Type = "Application"
 //  state.Type = "Driver"

}
