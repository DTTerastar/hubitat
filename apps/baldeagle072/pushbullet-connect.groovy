/**
 *  Pushbullet Connect - updated 5/31/2018 1.1HE
 *  APP CODE
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
definition(
    name: "Pushbullet Connect",
    namespace: "baldeagle072",
    author: "Eric Roberts",
    description: "Painless setup of a Pushbullet device",
    category: "My Apps",
    iconUrl: "https://baldeagle072.github.io/pushbullet_notifier/images/PushbulletLogo@1x.png",
    iconX2Url: "https://baldeagle072.github.io/pushbullet_notifier/images/PushbulletLogo@2x.png",
    iconX3Url: "https://baldeagle072.github.io/pushbullet_notifier/images/PushbulletLogo@3x.png")


preferences {
    state.version = "1.1HE"
    page name: "enterAPI"
    page name: "selectDevice"
}

def enterAPI() {
    def pageProperties = [
        name: "enterAPI",
        title: "Choose a capability",
        nextPage: "selectDevice",
        uninstall: true
    ]
    
    return dynamicPage(pageProperties) {
        section("Enter your API key") {
            input "apiKey", "text", title: "API Key", required: true
        }
    }
}

def selectDevice() {
    def pageProperties = [
        name: "selectDevice",
        title: "Choose a device",
        install: true,
        uninstall: true
    ]
    
    def deviceOptions = getDeviceOptions(apiKey)
    
    if (pushbulletDevice) { log.debug ("pushbulletDevice: $pushbulletDevice") }
    
    return dynamicPage(pageProperties) {
        section("Choose a pushbullet device to add") {
            input "pushbulletDevice", "enum", options: deviceOptions, title: "Pushbullet Device", required: true, multiple: false, refreshAfterSelection:true
        }
    }
}

private getDeviceOptions(apiKey) {
    TRACE("getDeviceOptions(apiKey: $apiKey)")
    
    def deviceOptions = [:]
    
    def deviceListParams = [
        uri: "https://${apiKey}@api.pushbullet.com",
        path: "/v2/devices",
        requestContentType: "application/json"
    ]

    httpGet(deviceListParams) { resp ->
        log.debug("resp.data.devices: ${resp.data.devices}")
        for (device in resp.data.devices) {
            log.debug("device.nickname: ${device.nickname}, device.iden: ${device.iden}")
            if (device.nickname) {
                deviceOptions[device.iden] = device.nickname
            }
        }
    }
    
    log.debug("deviceOptions, $deviceOptions")
    state.devices = deviceOptions
    return deviceOptions
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
    // addChildDevice with apiKey = apiKey, iden = pushbulletDevice
    //apiKey: settings.apiKey, iden: settings.pushbulletDevice
    log.debug("init settings.pushbulletDevice: ${settings.pushbulletDevice}, state.devices: ${state.devices}, state.devices[settings.pushbulletDevice]: ${state.devices[settings.pushbulletDevice]}")
    if (!state.stIden) {
        def d = addChildDevice("baldeagle072", "Pushbullet", settings.pushbulletDevice, null, [name: "pushbullet.${state.devices[settings.pushbulletDevice]}", label: "PB-${state.devices[settings.pushbulletDevice]}", completedSetup: true])
        state.stIden = settings.pushbulletDevice
    }
    state.apiKey = settings.apiKey
    state.iden = settings.pushbulletDevice
    state.speak = false
    state.push = false
}

def uninstalled() {
    removeChildDevices(getChildDevices())
}

private removeChildDevices(delete) {
    delete.each {
        deleteChildDevice(it.deviceNetworkId)
    }
}

/*
def push(title, message) {
    state.push = false
    log.debug "push title: $title message: $message, key: ${state.apiKey}, iden: ${state.iden}" 
       
    def successClosure = { response ->
      log.debug "PUSH request was successful, $response.data"
      //sendEvent(name:"push", value:[title: title, message: message], isStateChange:true)
      state.push = true
    }

    def postBody = [
        type: "note",
        title: title?: "Hubitat",
        body: message,
        device_iden: state.iden
    ]
    
    //state.apiKey ? postBody << [device_iden: state.apiKey] : false
	def params = [
		uri: "https://api.pushbullet.com",
        path: "/v2/pushes",
        requestContentType: "application/json",
        headers: ["Access-Token": state.apiKey, "Content-Type": "application/json"],
		body: postBody
    ]
    
    httpPost(params, successClosure)
}
*/

def speak(title, message) {
    state.speak = false
    log.debug "speak title: $title message: $message, key: ${state.apiKey}, iden: ${state.iden}" 
       
    def successClosure = { response ->
      log.debug "SPEAK request was successful, $response.data"
      sendEvent(name:"speak", value:[title: title, message: message], isStateChange:true)
      state.speak = true
    }

    def postBody = [
        type: "note",
        title: title?: "Hubitat(PushBullet)",
        body: message,
        device_iden: state.iden
    ]
    
    //state.apiKey ? postBody << [device_iden: state.apiKey] : false
	def params = [
		uri: "https://api.pushbullet.com",
        path: "/v2/pushes",
        requestContentType: "application/json",
        headers: ["Access-Token": state.apiKey, "Content-Type": "application/json"],
		body: postBody
    ]
    
    httpPost(params, successClosure)
}

def TRACE(msg) {
    log.debug(msg)
}
