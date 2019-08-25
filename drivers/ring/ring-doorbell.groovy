/**
 *  Ring Doorbell
 *
 *  Copyright 2019 Dominick Meglio
 *
 */
metadata {
    definition (name: "Ring Doorbell", namespace: "ring", author: "Dominick Meglio") {
		capability "Momentary"
		capability "Motion Sensor"
		capability "PushableButton"
		
		command "record"
    }
}

def record() {
	parent.handleRecord(device, device.deviceNetworkId.split(":")[1])
}

def installed() {
	sendEvent(name: "numberOfButtons", value: "1")
}

def push() {
	sendEvent(name: "pushed", value: "1")
}