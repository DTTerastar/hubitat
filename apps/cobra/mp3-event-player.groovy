/**
 *  Design Usage:
 *  This is the 'Parent' app for mp3 automation
 *
 *
 *  Copyright 2018 Andrew Parker
 *  
 *  This SmartApp is free!
 *
 *  Donations to support development efforts are accepted via: 
 *
 *  Paypal at: https://www.paypal.me/smartcobra
 *  
 *
 *  I'm very happy for you to use this app without a donation, but if you find it useful
 *  then it would be nice to get a 'shout out' on the forum! -  @Cobra
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
 *  Last Update: 05/04/2018
 *
 *  Changes:
 *
 * 
 *
 *
 *
 *
 *  V1.0.0 - POC
 *
 */



definition(
    name:"MP3 Event Player",
    namespace: "Cobra",
    author: "Andrew Parker",
    description: "This is the 'Parent' app for MP3 automation",
    category: "Convenience",
    iconUrl: "https://raw.githubusercontent.com/cobravmax/SmartThings/master/icons/cobra.png",
    iconX2Url: "https://raw.githubusercontent.com/cobravmax/SmartThings/master/icons/cobra.png",
    iconX3Url: "https://raw.githubusercontent.com/cobravmax/SmartThings/master/icons/cobra.png"
    )







preferences {
	
     page name: "mainPage", title: "", install: true, uninstall: true,submitOnChange: true 
     
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
	setAppVersion()
    log.info "There are ${childApps.size()} child smartapps"
    childApps.each {child ->
    log.info "Child app: ${child.label}"
    }
}
 
 
 
def mainPage() {
    dynamicPage(name: "mainPage") {
      
		section {    
			paragraph image: "https://raw.githubusercontent.com/cobravmax/SmartThings/master/icons/cobra.png",
				title: "MP3 Central",
				required: false,
				"This parent app is a container for all Mp3 Event child apps"
			}
            
		section{
			paragraph "Parent Version: $state.appversion -  Copyright � 2018 Cobra"
			}
    
		
    
    
    
// Links to child apps    
    
     
    section{
			paragraph ( title: "Events", "Add a new event automation child below"  )
			}
     
     
     
     
// New Child Apps 
      
      
      
        
      section ("Add An Event"){
		app(name: "mp3App", appName: "MP3 Event Player Child", namespace: "Cobra", title: "New Event", multiple: true)
		
            
            }
                  
   
           
           
// End: New Child Apps
  
  
              section("App name") {
                label title: "Enter a name for parent app (optional)", required: false
            }
  
  
  
  
  
 } // DynamicPage 
  
  } // Mainpage














 
 
// App Version   *********************************************************************************
def setAppVersion(){
    state.appversion = "1.0.0"
}