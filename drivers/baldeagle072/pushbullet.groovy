/**
 *  Pushbullet (for Pushbullet Connect) Last updated 5/31/2018 1.0HE
 *  DRIVER CODE
 * 
 *  Copyright 2015 Eric Roberts
 *  Adapted for Hubitat Elevation by Brian S. Lowrance (Rayzurbock) 5/31/2018 1.0HE
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
 */

metadata {
    definition (name: "Pushbullet", namespace: "baldeagle072", author: "Eric Roberts") {
        //command "push", ["string", "string"]
        //command "push", ["string"]
        command "test"
        //command "speak", ["string", "string"]
        //command "speak", ["string"]
        capability "Polling"
        capability "Refresh"
        capability "Speech Synthesis"
    }
}

def parse(String description) {
    log.debug "Parsing '${description}'"
}

//def push(message) {
//    push(null, message)
//}

//def push(title, message) {
//    	log.debug("push title: $title, message: $message")
//    	parent.push(title, message)
//}

def poll() { 
}

def refresh() {
    log.debug "Executing 'refresh'"
    poll()
}

def test() {
    log.debug "Executing 'test'"
    //push("Hello $location")
    speak("Pushbullet Test")
}

def speak(message) {
    speak(null, message)
}

def speak(title, message) {
    log.debug("speak title: $title, message: $message")
    parent.speak(title, message)
}
