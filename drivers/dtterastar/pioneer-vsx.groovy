/**
 *  Pioneer VSX
 *  
 *  Changes:
 *   
 *  V1.0.0 - 10/15/18 - Initial release
 *  V2.0.0 - 09/01/19 - Input support
 *  V2.1.0 - 09/15/19 - Motion sensor
 */

metadata {
	definition (name: "Pioneer VSX", namespace: "DTTerastar", author: "Darrell Turner") {
    capability "Initialize"
    capability "Switch"
    capability "Audio Volume"
    capability "Refresh"
    capability "Motion Sensor"
    capability "Sensor"

    attribute "status", ""
    attribute "inputId", ""
    attribute "input", ""
    attribute "info", ""
  }

  command "mute"
  command "unmute"
  command "toggleMute"
  command "setCurrentInputName", ["New name"]
  command "inputSelectId", ["Id"]
  command "inputSelect", ["Name"]
  command "inputNext"
  command "inputPrev"
  
  preferences() {    	
    section("")  {
      input "ipaddress", "text", required: true, title: "Receiver IP Address", defaultValue: "0.0.0.0"
      input "port", "number", required: true, title: "Receiver Port", defaultValue: "8182"
      input "debugOutput", "bool", title: "Enable debug logging?", defaultValue: true, displayDuringSetup: false, required: false
      input "logEnabled", "bool", title: "Enable descriptionText logging", defaultValue: "true", displayDuringSetup: false, required: false
    }
  }
}

private dedupEvent(Map event) {
  try {
    current = device.currentValue(event.name)
  } catch(e) {
    logDebug(e)
    current = null
  }
  if (current != event.value) {
    sendEvent(event)
    motion(event)
    if (logEnabled && event.descriptionText) log.info(event.descriptionText)
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
 
def setVolume(volume) {
	if (volume==0) 
		mute()
	else
	{
    scaled = (int)Math.floor(volume * 1.61)
    send(sprintf('%03d', scaled) + "VL")
	}
}

def mute() {
    send("MO")
}

def unmute() {
    send("MF")
}

def toggleMute() {
  if (device.currentValue('mute')=='muted')
    unmute()
  else
    mute()
}

def volumeUp() {
    send("VU")
    send("VU")
}

def volumeDown() {
    send("VD")
    send("VD")
}

def inputPrev() {
    send("FD")
}

def inputNext() {
    send("FU")
}

def inputSelectId(String id) {
  if (id.length()!=2) log.error("Id must be 2 digits")
  send("${id}FN")
}

def inputSelect(String name) {
    id = state.inputs.find { it.value == name }?.key
    if (id)
      inputSelectId(id)
    else
      log.warn("Input named ${name} not found!")
}

def setCurrentInputName(name) {
    if (!name) return
    inputs = state.inputs ?: [:]
    inputs[device.currentValue('inputId')] = name
    state.inputs = inputs
    dedupEvent([name: 'input', value: name, descriptionText: "Input set to ${name}"])
}

def initialize() {
  //state.clear()
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

def presets(String id) {
    switch(id)
    {
        case "04": return "DVD"
        case "25": return "BD"
        case "05": return "TV/SAT"
        case "06": return "SAT/CBL"
        case "15": return "DVR/BDR"
        case "10": return "Video 1"
        case "14": return "Video 2"
        case "19": return "HDMI 1"
        case "20": return "HDMI 2"
        case "21": return "HDMI 3"
        case "22": return "HDMI 4"
        case "23": return "HDMI 5"
        case "24": return "HDMI 6"
        case "34": return "HDMI 7"
        case "35": return "HDMI 8"
        case "26": return "Internet Radio"
        case "44": return "Media Server"
        case "38": return "Internet Radio"
        case "17": return "iPod/USB"
        case "48": return "MHL"
        case "01": return "CD"
        case "03": return "CD-R/Tape"
        case "02": return "Tuner"
        case "00": return "Phono"
        case "13": return "USB-DAC"
        case "12": return "Multi Channel"
        case "33": return "Adapter Port"
        case "18": return "XM"
        case "27": return "Sirius"
        case "40": return "SiriusXM"
        case "41": return "Pandora"
        case "45": return "Favorites"
        case "53": return "Spotify"
        case "57": return "Spotify"
        case "49": return "Game"
    }
    return "Input ${id}"
}

def mapInput(String id) {
    inputs = state.inputs ?: [:]
    name = inputs[id]
    if (name) return name
    name = presets(id)
    if (!name) return 
    log.warn("Found new input ${name}")
    inputs[id] = name
    state.inputs = inputs
    return name
}

def motion(event) {
  if (event.name=="switch") {
    if (event.value=="on"){
      state.mins = 60
      dedupEvent([name: "motion", value:"active"])
    } else {
      state.mins = 0
      dedupEvent([name: "motion", value:"inactive"])
    }
  }

  if (event.name=="volume" || event.name=="mute" || event.input=="input") {
    state.mins=(state.mins?:0)+(event.input=="input"?60:15)
    if (state.mins>120) state.mins=120
	  dedupEvent([name: "motion", value:"active"])
  }
}

def refresh() {
  if(state.mins>0) {
    state.mins-=1
    if (state.mins<=0)
	    dedupEvent([name: "motion", value:"inactive"])
  }
  if (state.refresh<1) state.refresh = 5
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
      case 4:
        send("?F")
          break;
      case 5:
        send("?FL")
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

public static String convertMessageFromIpControl(String responsePayload) {
  StringBuilder sb = new StringBuilder();
  for (int i = 2; i < responsePayload.length() - 1; i += 2) {
    String hexAsciiValue = responsePayload.substring(i, i + 2);
    sb.append((char) Integer.parseInt(hexAsciiValue, 16));
  }
  return sb.toString().trim();
}

def parse(String msg) {
    msg = new String(hubitat.helper.HexUtils.hexStringToByteArray(msg), "UTF-8").trim();
    logDebug "parse ${msg}"
    state.reconnectDelay = 1
	  dedupEvent([name: "status", value:"connected", descriptionText: "Socket connected..."])
    
    if (msg.startsWith("VOL"))
    {
        volume = (int) Math.floor(msg.substring(3).toInteger() / 1.61)
        if (volume>100) volume=100
		    dedupEvent([name: 'volume', value: volume, unit:"%", descriptionText:"Volume at ${volume}%"])
    }
    if (msg.startsWith("MUT"))
    {
        mute = msg.substring(3).toInteger()
        dedupEvent([name: 'mute', value: mute==0?'muted':'unmuted', descriptionText: mute==0?'Muted':'Unmuted'])
    }
    if (msg.startsWith("PWR"))
    {
        pwr = msg.substring(3).toInteger()
        dedupEvent([name: 'switch', value: pwr==0?'on':'off', descriptionText: pwr==0?'Turned on':'Turned off'])
    }
    if (msg.startsWith("FN"))
    {
        id = msg.substring(2)
        name = mapInput(id)
        dedupEvent([name: 'inputId', value: id])
        dedupEvent([name: 'input', value: name, descriptionText: "Input set to ${name}"])
    }
    if (msg.startsWith("FL"))
    {
        dedupEvent([name: 'info', value: convertMessageFromIpControl(msg.substring(2))])
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
    // if the device is offline, give it some time before trying to reconnect
    runIn(state.reconnectDelay, initialize)
}

private logDebug(msg) {
	if (settings?.debugOutput) {
		log.debug msg
	}
}