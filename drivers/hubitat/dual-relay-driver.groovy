/*
 *  Monoprice/Vision Dual Relay Parent Driver
 */
metadata {
    definition (name: "Dual Relay Driver", namespace: "hubitat", author: "hubitat") {
        capability "Refresh"
        capability "Actuator"
        
        command "childOn"
        command "childOff"
        command "recreateChildDevices"
        command "deleteChildren"

        //fingerprint manufacturer: "015D", prod: "0651", model: "F51C", deviceJoinName: "Zooz ZEN20 Power Strip"
        //fingerprint deviceId: "0x1004", inClusters: "0x5E,0x85,0x59,0x5A,0x72,0x60,0x8E,0x73,0x27,0x25,0x86"
   }
}

def installed() {
    log.debug "installed"
    createChildDevices()
    configure()
}

def updated() {
    log.debug "updated"
    
    if (!childDevices) {
        createChildDevices()
    }
    else if (device.label != state.oldLabel) {
        childDevices.each {
            def newLabel = "$device.displayName (CH${channelNumber(it.deviceNetworkId)})"
            it.setLabel(newLabel)
        }
  
        state.oldLabel = device.label
    }

    configure()
}

def configure() {
    log.debug "configure"
    def cmds = [
                   zwave.versionV1.versionGet().format(),
                   zwave.manufacturerSpecificV2.manufacturerSpecificGet().format(),
                   zwave.firmwareUpdateMdV2.firmwareMdGet().format()
               ]
    response(delayBetween(cmds, 1000))
}

def refresh() {
    log.debug "refresh"
    def cmds = []
    cmds << zwave.basicV1.basicGet().format()
    cmds << zwave.switchBinaryV1.switchBinaryGet().format()
    
    (1..2).each { endpoint ->
        cmds << encap(zwave.basicV1.basicGet(), endpoint)
        cmds << encap(zwave.switchBinaryV1.switchBinaryGet(), endpoint)
    }
 
    delayBetween(cmds, 100)
}

def recreateChildDevices() {
    log.debug "recreateChildDevices"
    deleteChildren()
    createChildDevices()
}

def deleteChildren() {
	log.debug "deleteChildren"
	def children = getChildDevices()
    
    children.each {child->
  		deleteChildDevice(child.deviceNetworkId)
    }
}


def parse(String description) {
    log.debug "parse $description"
    def result = null
 
    if (description.startsWith("Err")) {
        result = createEvent(descriptionText:description, isStateChange:true)
    } else {
        def cmd = zwave.parse(description, [0x60: 3, 0x25: 1, 0x20: 1])
        log.debug "Command: ${cmd}"
  
        if (cmd) {
            result = zwaveEvent(cmd)
            log.debug "parsed '${description}' to ${result.inspect()}"
        } else {
            log.debug "Unparsed description $description"
        }
    }
 
    result
}


def zwaveEvent(hubitat.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd) {
    log.debug "multichannelv3.MultiChannelCmdEncap $cmd"
    def encapsulatedCommand = cmd.encapsulatedCommand([0x25: 1, 0x20: 1])
    log.debug "encapsulatedCommand: $encapsulatedCommand"
 
    if (encapsulatedCommand) {
        return zwaveEvent(encapsulatedCommand, cmd.sourceEndPoint as Integer)
    } else {
        log.debug "Unable to get encapsulated command: $encapsulatedCommand"
        return []
    }
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd, endpoint = null) {
    log.debug "basicv1.BasicReport $cmd, $endpoint"
    zwaveBinaryEvent(cmd, endpoint, "digital")
}

def zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd, endpoint = null) {
    log.debug "switchbinaryv1.SwitchBinaryReport $cmd, $endpoint"
    zwaveBinaryEvent(cmd, endpoint, "physical")
}

def zwaveBinaryEvent(cmd, endpoint, type) {
    log.debug "zwaveBinaryEvent cmd $cmd, endpoint $endpoint, type $type"
    def childDevice = childDevices.find{it.deviceNetworkId.endsWith("$endpoint")}
    def result = null
 
    if (childDevice) {
        log.debug "childDevice.sendEvent $cmd.value"
        childDevice.sendEvent(name: "switch", value: cmd.value ? "on" : "off", type: type)
    } else {
        result = createEvent(name: "switch", value: cmd.value ? "on" : "off", type: type)
    }
 
    result
}

def zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
    log.debug "manufacturerspecificv2.ManufacturerSpecificReport cmd $cmd"
    updateDataValue("MSR", String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId))
}

def zwaveEvent(hubitat.zwave.commands.configurationv2.ConfigurationReport cmd) {
    log.debug "configurationv2.ConfigurationReport: parameter ${cmd.parameterNumber} with a byte size of ${cmd.size} is set to ${cmd.configurationValue}"
}

def zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
    log.debug "configurationv2.ConfigurationReport: parameter ${cmd.parameterNumber} with a byte size of ${cmd.size} is set to ${cmd.configurationValue}"
}

def zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd, endpoint) {
   log.debug "versionv1.VersionReport, applicationVersion $cmd.applicationVersion, cmd $cmd, endpoint $endpoint"
}

def zwaveEvent(hubitat.zwave.Command cmd, endpoint) {
    log.debug "${device.displayName}: Unhandled ${cmd}" + (endpoint ? " from endpoint $endpoint" : "")
}

def zwaveEvent(hubitat.zwave.Command cmd) {
    log.debug "${device.displayName}: Unhandled ${cmd}"
// My Code
  runIn( 1, refresh )
///My Code
}

def on() {
    log.debug "on"
    def cmds = []
    cmds << zwave.basicV1.basicSet(value: 0xFF).format()
    cmds << zwave.basicV1.basicGet().format()
    
    (1..2).each { endpoint ->
        cmds << encap(zwave.basicV1.basicSet(value: 0xFF), endpoint)
        cmds << encap(zwave.basicV1.basicGet(), endpoint)
    }

    return delayBetween(cmds, 1000)
}

def off() {
    log.debug "off"
    def cmds = []
    cmds << zwave.basicV1.basicSet(value: 0x00).format()
    cmds << zwave.basicV1.basicGet().format()
    
    (1..2).each { endpoint ->
        cmds << encap(zwave.basicV1.basicSet(value: 0x00), endpoint)
        cmds << encap(zwave.basicV1.basicGet(), endpoint)
    }
    
    return delayBetween(cmds, 1000)
}

def childOn(String dni) {
    onOffCmd(0xFF, channelNumber(dni))
}

def childOff(String dni) {
    onOffCmd(0, channelNumber(dni))
}

private onOffCmd(value, endpoint) {
    log.debug "onOffCmd, value: $value, endpoint: $endpoint"
    
    def cmds = []
    cmds << encap(zwave.basicV1.basicSet(value: value), endpoint)
    cmds << encap(zwave.basicV1.basicGet(), endpoint)
    
    return delayBetween(cmds, 1000)
}

private channelNumber(String dni) {
    def ch = dni.split("-")[-1] as Integer
    return ch
}

private void createChildDevices() {
    log.debug "createChildDevices"
    
    for (i in 1..2) {
        addChildDevice("hubitat", "Dual Relay Driver (Child)", "$device.deviceNetworkId-$i", [name: "ch$i", label: "$device.displayName $i", isComponent: true])
    }
}

private encap(cmd, endpoint) {
    if (endpoint) {
        zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint:endpoint).encapsulate(cmd).format()
    } else {
        cmd.format()
    }
}