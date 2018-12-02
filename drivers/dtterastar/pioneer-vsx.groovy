/**
 *  Pioneer VSX
 *  
 *  Changes:
 *   
 *  V1.0.0 - 10/15/18 - Initial release
 */

metadata {
	definition (name: "Pioneer VSX", namespace: "DTTerastar", author: "Darrell Turner") {
	capability "Initialize"
    capability "Telnet"
    capability "Music Player"
    capability "Switch"
    capability "Refresh"

    attribute "Telnet", ""
}
    
preferences() {    	
        section(""){
            input "ipaddress", "text", required: true, title: "Receiver IP Address", defaultValue: "0.0.0.0"
            input "port", "number", required: true, title: "Receiver Port", defaultValue: "8182"
            input "debugOutput", "bool", title: "Enable debug logging?", defaultValue: true, displayDuringSetup: false, required: false			
        }
    }
}

def send(msg) {
    logDebug("Sending Message: ${msg}")
    return new hubitat.device.HubAction("${msg}\r\n", hubitat.device.Protocol.TELNET)
}

def on() {
    send("PO")
}


def off() {
    send("PF")
}
 
def setLevel(level) {
    scaled = (int)Math.ceil(level * 1.61)
    send(sprintf('%03d', scaled) + "VL")
}

def mute() {
    send("MO")
}

def unmute() {
    send("MF")
}

def speak(msg) {
    send(msg)
}

def playText(message) {
    send(message)
}

def initialize(){
	try {
        telnetConnect([terminalType: 'VT100'], "${ipaddress}", (int)port, null, null)
    } catch(e) {
		logDebug("initialize error: ${e.message}")
    }
}

def refresh() {
    if (state.refresh<1) state.refresh = 3
    switch(state.refresh){
        case 1:	
            send("?V")
            break;
        case 2:
	        send("?M")
            break;
        case 3:
        	send("?P")
            break;
    }
    state.refresh = state.refresh-1
}

def installed() {
	initialize()
}

def updated() {
	initialize()
}

def parse(String msg) {
    logDebug "parse ${msg}"
    if (device.currentValue('telnet') != "connected")
	    sendEvent([name: "telnet", value: "connected"])
    
    if (msg.startsWith("VOL"))
    {
        level = (int) Math.ceil(msg.substring(3).toInteger() / 1.61)
        if (level>100) level=100
        sendEvent([name:'level', value: level, unit: "%"])
    }
    if (msg.startsWith("MUT"))
    {
        level = msg.substring(3).toInteger()
        sendEvent([name:'mute', value: level==0])
    }
    if (msg.startsWith("PWR"))
    {
        level = msg.substring(3).toInteger()
        sendEvent([name:'switch', value: level==0 ? 'on':'off'])
    }
    if (state.refresh>0) refresh() 
}

def telnetStatus(String status) {
	logDebug "telnetStatus: ${status}"
	if (status == "receive error: Stream is closed" || status == "send error: Broken pipe (Write failed)") {
		log.error("Telnet connection dropped...")
		initialize()
    }
}

private logDebug(msg) {
	if (settings?.debugOutput || settings?.debugOutput == null) {
		log.debug msg
	}
}