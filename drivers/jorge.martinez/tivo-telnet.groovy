/********
/********TiV0 Telnet Control
/***************************
/*
ACTION_A
ACTION_B
ACTION_C
ACTION_D

*/

metadata {
	definition (name: "Tivo Telnet", namespace: "jorge.martinez", author: "Jorge Martinez") {
		capability "Telnet"
		capability "Initialize"
		capability "Switch"
		attribute "channel", "NUMBER"
		command "chUp"
		command "chDown"
		command "pause"
		command "myShows"
		command "liveTv"
		command "play"
		command "up"
		command "down"
		command "left"
		command "right"
		command "central"
		command "Netflix"
		command "select"
		command "standby"
		command "clear"
		command "stop"
		command "guide"
		command "setCH", ["STRING"]
		command "nextTrack"
		command "previousTrack"
		command "info"
		command "back"
		command "disconnect"  //////*********thanks to sthompson*********///////////
	}
	preferences {
		section("Device Settings:") {
			input "TiVoIP", "string", title:"TiVoIP", description: "", required: true, displayDuringSetup: true
			input "TiVoMini", "bool", title:"", description: "Is this a tivo mini", required: true, displayDuringSetup: true
		}
	}
}
def one() 	{sendMsg("IRCODE NUM1")} 
def two()	{sendMsg("IRCODE NUM2")}
def three()	{sendMsg("IRCODE NUM3")}
def four()	{sendMsg("IRCODE NUM4")}
def five()	{sendMsg("IRCODE NUM5")}
def six()	{sendMsg("IRCODE NUM6")}
def seven()	{sendMsg("IRCODE NUM7")}
def eight()	{sendMsg("IRCODE NUM8")}
def nine()	{sendMsg("IRCODE NUM9")}
def zero()	{sendMsg("IRCODE NUM0")}
def info()	{sendMsg("IRCODE INFO")}
def guide()	{sendMsg("IRCODE GUIDE")}
def back()	{sendMsg("IRCODE BACK")}
def testCommand(Command){sendMsg(Command)}
def setCH (CH){
	if (settings.TiVoMini){ //if is mark as mini
	}
	else{ //if is not a mini
		sendMsg("SETCH "+ CH)
	}
}
def clear()	{sendMsg("IRCODE CLEAR")}
def standby(){sendMsg("IRCODE STANDBY")}
def select(){sendMsg("IRCODE SELECT")}
def Netflix(){sendMsg("IRCODE NETFLIX")}
def central(){sendMsg("IRCODE TIVO")}
def up (){sendMsg("IRCODE UP")}
def down (){sendMsg("IRCODE DOWN")}
def left (){sendMsg("IRCODE LEFT")}
def right (){sendMsg("IRCODE RIGHT")}
def play (){sendMsg("IRCODE PLAY")}
def liveTv(){sendMsg("IRCODE LIVETV")}
def chUp (){sendMsg("IRCODE CHANNELUP")}
def nextTrack(){chUp()}
def chDown (){sendMsg("IRCODE CHANNELDOWN")}
def previousTrack(){chDown ()}
def myShows (){sendMsg("IRCODE NOWSHOWING")}
def pause (){sendMsg("IRCODE PAUSE")}
def stop(){sendMsg("IRCODE STOP")}
def installed() {
	log.info('Tivo Telnet : installed()')
	initialize()
}
def updated() {
	log.info('Tivo Telnet: updated()')
	initialize()
}
def initialize() {
	log.info('Tivo Telnet: initialize()')
	telnetClose() 
	log.info ("TiVo IP ${settings.TiVoIP}")
	telnetConnect([termChars:[13]], settings.TiVoIP, 31339, settings.username, settings.password)
}
def disconnect() {      //////*********thanks to sthompson*********///////////
	telnetClose()
}
def sendMsg(String msg){
	log.info("Sending telnet msg: " + msg)
	return new hubitat.device.HubAction(msg, hubitat.device.Protocol.TELNET)
}
private parse(String msg){
	log.debug("Parse: " + msg)
	if(msg.startsWith("CH_STATUS"))	{
		log.info "got channel update " + msg.substring(10,14)
		state.channel = msg.substring(10,14).toInteger()
		sendEvent(name: "channel", value: state.channel, isStateChange: true)
	}

}
def telnetStatus(String status){
	log.warn "telnetStatus: error: " + status
	if (status != "receive error: Stream is closed"){
		log.error "Connection was dropped."
		initialize()
	} 
}
def pause(millis) {
   def passed = 0
   def now = new Date().time
   log.debug "pausing... at Now: $now"
   /* This loop is an impolite busywait. We need to be given a true sleep() method, please. */
   while ( passed < millis ) {
       passed = new Date().time - now
   }
//   log.debug "... DONE pausing."
}
