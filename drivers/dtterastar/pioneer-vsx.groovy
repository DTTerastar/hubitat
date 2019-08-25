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
    capability "Switch"
    capability "Refresh"
	capability "Audio Volume"

    attribute "status", ""
    attribute "mute", ""
    attribute "switch", ""
	attribute "level", ""
    }
    
    preferences() {    	
        section("")  {
            input "ipaddress", "text", required: true, title: "Receiver IP Address", defaultValue: "0.0.0.0"
            input "port", "number", required: true, title: "Receiver Port", defaultValue: "8182"
            input "debugOutput", "bool", title: "Enable debug logging?", defaultValue: true, displayDuringSetup: false, required: false			
        }
    }
}

private dedupEvent(Map event) {
  String current = device.currentValue(name)
  if (current != event.value) {
    sendEvent(event)
    return true
  } else {
    return false
  }
}

def send(msg) {
    logDebug("send ${msg}")
    msg = hubitat.helper.HexUtils.byteArrayToHexString("${msg}\r\n".getBytes())
    interfaces.rawSocket.sendMessage(msg)
}

def on() {
    send("PO")
}


def off() {
    send("PF")
}
 
def setLevel(level) {
	if (level==0) 
		mute()
	else
	{
    	scaled = (int)Math.ceil(level * 1.61)
    	send(sprintf('%03d', scaled) + "VL")
	}
}

def mute() {
    send("MO")
}

def unmute() {
    send("MF")
}

def volumeUp() {
    send("VU")
}

def volumeDown() {
    send("VD")
}

def speak(msg) {
    send(msg)
}

def playText(message) {
    send(message)
}

def initialize() {
    logDebug "initialize"
    interfaces.rawSocket.close()
    schedule("0 * * * * ? *", refresh)
	try {
		dedupEvent([name: "status", value: "connecting"])
        interfaces.rawSocket.connect("${ipaddress}", (int)port, byteInterface:true, eol:"\n")
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
    state.refresh = state.refresh - 1
}

def installed() {
	initialize()
}

def updated() {
	unschedule()
	initialize()
}

def parse(String msg) {
    
    msg = new String(hubitat.helper.HexUtils.hexStringToByteArray(msg), "UTF-8").trim();
    
    logDebug "parse ${msg}"
    
    state.reconnectDelay = 1
	dedupEvent([name: "status", value:"connected", descriptionText: "Socket connected..."])
    
    if (msg.startsWith("VOL"))
    {
        level = (int) Math.ceil(msg.substring(3).toInteger() / 1.61)
        if (level>100) level=100
		dedupEvent([name: 'level', value: level, unit:"%", descriptionText:"Volume at ${level}%"])
    }
    if (msg.startsWith("MUT"))
    {
        level = msg.substring(3).toInteger()
        dedupEvent([name: 'mute', value: level==0?'muted':'unmuted', descriptionText: level==0?'Muted':'Unmuted'])
    }
    if (msg.startsWith("PWR"))
    {
        level = msg.substring(3).toInteger()
        dedupEvent([name: 'switch', value: level==0?'on':'off', descriptionText: level==0?'Turned on':'Turned off'])
    }
}

def socketStatus(String status) {
	logDebug "socketStatus: ${status}"
	if (status.startsWith("receive error") || status.startsWith("send error")) {
		dedupEvent([name: "status", value: "disconnected", descriptionText: status])
		reconnect()
    }
}

def reconnect() {
    // first delay is 2 seconds, doubles every time
    state.reconnectDelay = (state.reconnectDelay ?: 1) * 2
    // don't let delay get too crazy, max it out at 10 minutes
    if(state.reconnectDelay > 600) state.reconnectDelay = 600

    //If the Harmony Hub is offline, give it some time before trying to reconnect
    runIn(state.reconnectDelay, initialize)
}

private logDebug(msg) {
	if (settings?.debugOutput) {
		log.debug msg
	}
}