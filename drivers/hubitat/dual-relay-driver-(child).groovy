/*
 *  Monoprice/Vision Dual Relay Child Device Driver
 */
metadata {
    definition (name: "Dual Relay Driver (Child)", namespace: "hubitat", author: "hubitat") {
        capability "Switch"
        capability "Actuator"
    }
}

void on() { 
    log.debug "$device on"
    parent.childOn(device.deviceNetworkId)
}

void off() {
    log.debug "$device off"
    parent.childOff(device.deviceNetworkId)
}