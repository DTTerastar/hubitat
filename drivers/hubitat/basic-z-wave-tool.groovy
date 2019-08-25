/*
	Basic Z-Wave tool

	Copyright 2016, 2017, 2018 Hubitat Inc.  All Rights Reserved
	
	2018-11-28 maxwell
		-add command hints
	2018-11-09 maxwell
		-add association and version reports

	usage:
		-replace existing driver with this driver
		-set your paremeters
		-replace this driver with previous driver
	
	WARNING!
		--Setting device parameters is an advanced feature, randomly poking values to a device
		can lead to unexpected results which may result in needing to perform a factory reset 
		or possibly bricking the device
		--Refer to the device documentation for the correct parameters and values for your specific device
		--Hubitat cannot be held responsible for misuse of this tool or any unexpected results generated by its use
*/

import groovy.transform.Field

metadata {
    definition (name: "Basic Z-Wave tool",namespace: "hubitat", author: "Mike Maxwell") {
        
    	command "getAssociationReport"
	command "getVersionReport"
	command "getCommandClassReport"
	command "getParameterReport", [[name:"parameterNumber",type:"NUMBER", description:"Parameter Number (omit for a complete listing of parameters that have been set)", constraints:["NUMBER"]]]
	command "setParameter",[[name:"parameterNumber",type:"NUMBER", description:"Parameter Number", constraints:["NUMBER"]],[name:"size",type:"NUMBER", description:"Parameter Size", constraints:["NUMBER"]],[name:"value",type:"NUMBER", description:"Parameter Value", constraints:["NUMBER"]]]
		
    }
}

@Field Map zwLibType = [
	0:"N/A",1:"Static Controller",2:"Controller",3:"Enhanced Slave",4:"Slave",5:"Installer",
	6:"Routing Slave",7:"Bridge Controller",8:"Device Under Test (DUT)",9:"N/A",10:"AV Remote",11:"AV Device"
]

def parse(String description) {
	//log.debug description
    def cmd = zwave.parse(description,[0x85:1,0x86:1])
    if (cmd) {
        zwaveEvent(cmd)
    }
}

//Z-Wave responses
def zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd) {
	log.info "VersionReport- zWaveLibraryType:${zwLibType.find{ it.key == cmd.zWaveLibraryType }.value}"
	log.info "VersionReport- zWaveProtocolVersion:${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}"
	log.info "VersionReport- applicationVersion:${cmd.applicationVersion}.${cmd.applicationSubVersion}"
}

def zwaveEvent(hubitat.zwave.commands.associationv1.AssociationReport cmd) {
    log.info "AssociationReport- groupingIdentifier:${cmd.groupingIdentifier}, maxNodesSupported:${cmd.maxNodesSupported}, nodes:${cmd.nodeId}"
}

def zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
    log.info "ConfigurationReport- parameterNumber:${cmd.parameterNumber}, size:${cmd.size}, value:${cmd.scaledConfigurationValue}"
}

def zwaveEvent(hubitat.zwave.commands.versionv1.VersionCommandClassReport cmd) {
    log.info "CommandClassReport- class:${ "0x${intToHexStr(cmd.requestedCommandClass)}" }, version:${cmd.commandClassVersion}"		
}	

def zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
    def encapCmd = cmd.encapsulatedCommand()
    def result = []
    if (encapCmd) {
		result += zwaveEvent(encapCmd)
    } else {
        log.warn "Unable to extract encapsulated cmd from ${cmd}"
    }
    return result
}

def zwaveEvent(hubitat.zwave.Command cmd) {
    log.debug "skip: ${cmd}"
}

//cmds
def getVersionReport(){
	return secureCmd(zwave.versionV1.versionGet())		
}

def setParameter(parameterNumber = null, size = null, value = null){
    if (parameterNumber == null || size == null || value == null) {
		log.warn "incomplete parameter list supplied..."
		log.info "syntax: setParameter(parameterNumber,size,value)"
    } else {
		return delayBetween([
	    	secureCmd(zwave.configurationV1.configurationSet(scaledConfigurationValue: value, parameterNumber: parameterNumber, size: size)),
	    	secureCmd(zwave.configurationV1.configurationGet(parameterNumber: parameterNumber))
		],500)
    }
}

def getAssociationReport(){
	def cmds = []
	1.upto(5, {
		cmds.add(secureCmd(zwave.associationV1.associationGet(groupingIdentifier: it)))
    })
    return cmds	
}

def getParameterReport(param = null){
    def cmds = []
    if (param) {
		cmds = [secureCmd(zwave.configurationV1.configurationGet(parameterNumber: param))]
    } else {
		0.upto(255, {
	    	cmds.add(secureCmd(zwave.configurationV1.configurationGet(parameterNumber: it)))	
		})
    }
    return cmds
}	

def getCommandClassReport(){
    def cmds = []
    def ic = getDataValue("inClusters").split(",").collect{ hexStrToUnsignedInt(it) }
    ic.each {
		if (it) cmds.add(secureCmd(zwave.versionV1.versionCommandClassGet(requestedCommandClass:it)))
    }
    return delayBetween(cmds,500)
}

def installed(){}

def configure() {}

def updated() {}

private secureCmd(cmd) {
    if (getDataValue("zwaveSecurePairingComplete") == "true") {
		return zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
    } else {
		return cmd.format()
    }	
}
