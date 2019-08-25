metadata {
    definition (name: "Aeotec MultiSensor 6 - DT", namespace: "cSteele", author: "cSteele") {
        capability "Motion Sensor"
        capability "Temperature Measurement"
        capability "Relative Humidity Measurement"
        capability "Illuminance Measurement"
        capability "Ultraviolet Index"
        capability "Configuration"
        capability "Sensor"
        capability "Battery"
        capability "Power Source"
        capability "Acceleration Sensor"

        command    "refresh"
		
        attribute  "firmware", "decimal"
		attribute  "configured", "string"
        
		fingerprint deviceId: "0x2101", inClusters: "0x5E,0x86,0x72,0x59,0x85,0x73,0x71,0x84,0x80,0x30,0x31,0x70,0x7A", outClusters: "0x5A"
    }

    preferences {

        // Note: Hubitat doesn't appear to honour 'sections' in device handler preferences just now, but hopefully one day...
        section("Motion sensor settings") {
            input "motionDelayTime", "enum", title: "Motion Sensor Delay Time?",
                    options: ["20 seconds", "30 seconds", "1 minute", "2 minutes", "3 minutes", "4 minutes"], defaultValue: "1 minute", displayDuringSetup: true
            input "motionSensitivity", "number", title: "Motion Sensor Sensitivity? 0(min)..5(max)", range: "0..5", defaultValue: 5, displayDuringSetup: true
        }

        section("Automatic report settings") {
            input "reportInterval", "enum", title: "Sensors Report Interval?",
                    options: ["20 seconds", "30 seconds", "1 minute", "2 minutes", "3 minutes", "4 minutes", "5 minutes", "10 minutes", "15 minutes", "30 minutes", "1 hour", "6 hours", "12 hours", "18 hours", "24 hours"], defaultValue: "5 minutes", displayDuringSetup: true
            input "tempChangeAmount", "number", title: "Temperature Change Amount (Tenths of a degree)?", range: "1..70", description: "The tenths of degrees the temperature must change to induce an automatic report.", defaultValue: 2, required: false
            input "humidChangeAmount", "number", title: "Humidity Change Amount (%)?", range: "1..100", description: "The percentage the humidity must change to induce an automatic report.", defaultValue: 10, required: false
            input "luxChangeAmount", "number", title: "Luminance Change Amount (LUX)?", range: "-1000..1000", description: "The amount of LUX the luminance must change to induce an automatic report.", defaultValue: 100, required: false
        }

        section("Calibration settings") {
            input "tempOffset", "number", title: "Temperature Offset -128 to +127 (Tenths of a degree)?", range: "-127..128", description: "If your temperature is inaccurate this will offset/adjust it by this many tenths of a degree.", defaultValue: 0, required: false, displayDuringSetup: true
            input "humidOffset", "number", title: "Humidity Offset/Adjustment -50 to +50 in percent?", range: "-10..10", description: "If your humidity is inaccurate this will offset/adjust it by this percent.", defaultValue: 0, required: false, displayDuringSetup: true
            input "luxOffset", "number", title: "Luminance Offset/Adjustment -10 to +10 in LUX?", range: "-10..10", description: "If your luminance is inaccurate this will offset/adjust it by this percent.", defaultValue: 0, required: false, displayDuringSetup: true
        }

        input "ledOptions", "enum", title: "LED Options",
            options: [0:"Fully Enabled", 1:"Disable When Motion", 2:"Fully Disabled"], defaultValue: "0", displayDuringSetup: true
        input name: "selectiveReporting", type: "bool", title: "Enable Selective Reporting?", defaultValue: false
        input name: "debugOutput", type: "bool", title: "Enable debug logging?", defaultValue: true
    }
}

def updated() {
    logDebug "In Updated with settings: ${settings}"
    logDebug "${device.displayName} is now on ${device.latestValue("powerSource")} power"
    unschedule()
    if (debugOutput) runIn(1800,logsOff)

    // Check for any null settings and change them to default values
    if (motionDelayTime == null)  motionDelayTime = "1 minute"
    if (motionSensitivity == null) motionSensitivity = 3
    if (reportInterval == null) reportInterval = "5 minutes"
    if (tempChangeAmount == null) tempChangeAmount = 2
    if (humidChangeAmount == null) humidChangeAmount = 10
    if (luxChangeAmount == null) luxChangeAmount = 100
    if (tempOffset == null) tempOffset = 0
    if (humidOffset == null) humidOffset = 0
    if (luxOffset == null) luxOffset = 0
   
    if (motionSensitivity < 0)
    {
        logDebug "Illegal motion sensitivity ... resetting to 0!"
        motionSensitivity = 0
    }

    if (motionSensitivity > 5)
    {
        logDebug "Illegal motion sensitivity ... resetting to 5!"
        motionSensitivity = 5
    }

    // fix temp offset
    if (tempOffset < -128)
    {
        tempOffset = -128
        logDebug "Temperature Offset too low... resetting to -128 (-12.8 degrees)0"
    }

    if (tempOffset > 127)
    {
        tempOffset = 127
        logDebug "Temperature Offset too high ... resetting to 127 (+12.7 degrees)"
    }

    // fix humidity offset
    if (humidOffset < -50)
    {
        humidOffset = -50
        logDebug "Humidity Offset too low... resetting to -50%"
    }

    if (humidOffset > 50)
    {
        humidOffset = 50
        logDebug "Humidity Adjusment too high ... resetting to +50%"
    }

    if (luxOffset < -1000)
    {
        luxOffset = -1000
        logDebug "Luminance Offset too low ... resetting to -1000LUX"
    }

    if (luxOffset > 1000)
    {
        luxOffset = 1000
        logDebug "Luminance Offset too high ... resetting to +1000LUX"
    }

    if (device.latestValue("powerSource") == "dc") {  //case1: USB powered
        response(configure())
	} else {
		setConfigured("false") //wait until the next time device wakeup to send configure command after user change preference
	}
}

def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("debugOutput",[value:"false",type:"bool"])
}

def parse(String description) {
    // log.debug "In parse() for description: $description"
    def result = null
    if (description.startsWith("Err 106")) {
        log.warn "parse() >> Err 106"
        result = createEvent( name: "secureInclusion", value: "failed",
                descriptionText: "This sensor failed to complete the network security key exchange. If you are unable to control it via Hubitat, you must remove it from your network and add it again.")
    } else if (description != "updated") {
        // log.debug "About to zwave.parse($description)"
        def cmd = zwave.parse(description, [0x31: 5, 0x30: 1, 0x70: 1, 0x72: 1, 0x84: 1])
        if (cmd) {
            // log.debug "About to call handler for ${cmd.toString()}"
            result = zwaveEvent(cmd)
        }
    }
    //log.debug "After zwaveEvent(cmd) >> Parsed '${description}' to ${result.inspect()}"
    return result
}

//this notification will be sent only when device is battery powered
def zwaveEvent(hubitat.zwave.commands.wakeupv1.WakeUpNotification cmd) {
	logDebug("${device.displayName} woke up")
    def result = [createEvent(descriptionText: "${device.displayName} woke up")]
    def cmds = []
    if (!isConfigured()) {
        result << response(configure())
    } else {
        cmds << zwave.wakeUpV1.wakeUpNoMoreInformation().format()
        result << response(cmds)
    }
    result
}

def zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
    def encapsulatedCommand = cmd.encapsulatedCommand([0x31: 5, 0x30: 1, 0x70: 1, 0x72: 1, 0x84: 1])
    state.sec = 1
    logDebug "encapsulated: ${encapsulatedCommand}"
    if (encapsulatedCommand) {
        zwaveEvent(encapsulatedCommand)
    } else {
        log.warn "Unable to extract encapsulated cmd from $cmd"
        createEvent(descriptionText: cmd.toString())
    }
}

def zwaveEvent(hubitat.zwave.commands.securityv1.SecurityCommandsSupportedReport cmd) {
    log.info "Executing zwaveEvent 98 (SecurityV1): 03 (SecurityCommandsSupportedReport) with cmd: $cmd"
    state.sec = 1
}

def zwaveEvent(hubitat.zwave.commands.securityv1.NetworkKeyVerify cmd) {
    state.sec = 1
    log.info "Executing zwaveEvent 98 (SecurityV1): 07 (NetworkKeyVerify) with cmd: $cmd (node is securely included)"
    def result = [createEvent(name:"secureInclusion", value:"success", descriptionText:"Secure inclusion was successful")]
    result
}

def zwaveEvent(hubitat.zwave.commands.manufacturerspecificv1.ManufacturerSpecificReport cmd) {
    logDebug "ManufacturerSpecificReport cmd = $cmd"

    def model = ""   // We'll decode the specific model for the log, but we don't currently use this info
    switch(cmd.productTypeId >> 8) {
        case 0: model = "EU"
                break
        case 1: model = "US"
                break
        case 2: model = "AU"
                break
        case 10: model = "JP"
                break
        case 29: model = "CN"
                break
        default: model = "unknown"
    }
    logDebug "model:            ${model}"
    logDebug "productTypeId:    ${cmd.productTypeId}"
    def msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
    updateDataValue("MSR", msr)
}

def zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd) {
    logDebug "In BatteryReport"
	
    def result = []
    def map = [ name: "battery", unit: "%" ]
    if (cmd.batteryLevel == 0xFF) {
        map.value = 1
        map.descriptionText = "${device.displayName} battery is low"
    } else {
        map.value = cmd.batteryLevel
		map.descriptionText = "${device.displayName} battery is ${cmd.batteryLevel}%"
    }

	log.info map.descriptionText
    createEvent(map)
}

def zwaveEvent(hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd){
    logDebug "In multi level report cmd = $cmd"

    def map = [:]
    switch (cmd.sensorType) {
        case 1:
            logDebug "scaled sensor value = $cmd.scaledSensorValue  scale = $cmd.scale  precision = $cmd.precision"

            // Convert temperature (if needed) to the system's configured temperature scale
            def finalval = convertTemperatureIfNeeded(cmd.scaledSensorValue, cmd.scale == 1 ? "F" : "C", cmd.precision)

			log.info "$device.displayName temperature $finalval ${getTemperatureScale()}"
            map.value = finalval
            map.unit = "\u00b0" + getTemperatureScale()
            map.name = "temperature"
            break
        case 3:
			log.info "$device.displayName illuminance is ${cmd.scaledSensorValue.toInteger()} lux"
            map.name = "illuminance"
            map.value = cmd.scaledSensorValue.toInteger()
            map.unit = "lux"
            break
        case 5:
			log.info "$device.displayName humidity is ${cmd.scaledSensorValue.toInteger()}%"
            map.value = cmd.scaledSensorValue.toInteger()
            map.unit = "%"
            map.name = "humidity"
            break
        case 27:
			log.info "$device.displayName ultravioletIndex is ${cmd.scaledSensorValue.toInteger()}"
            map.name = "ultravioletIndex"
            map.value = cmd.scaledSensorValue.toInteger()
            break
    }
    createEvent(map)
}

def zwaveEvent(hubitat.zwave.commands.sensorbinaryv1.SensorBinaryReport cmd) {
// ignore since BasicSet is also sent
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd) {
	logDebug "In motionEvent cmd = $cmd"
    // Sensor sends value 0xFF on motion, 0x00 on no motion (after expiry interval)
    def map = [name: "motion"]
    if (cmd.value) {
        map.value = "active"
        map.descriptionText = "$device.displayName detected motion"
    } else {
        map.value = "inactive"
        map.descriptionText = "$device.displayName motion has stopped"
    }
	log.info map.descriptionText
    createEvent(map)
}

def zwaveEvent(hubitat.zwave.commands.notificationv3.NotificationReport cmd) {
	logDebug "In NotificationReport cmd = $cmd"
	
    def result = []
    if (cmd.notificationType == 7) {
        switch (cmd.event) {
            case 0:
				log.info "$device.displayName acceleration is inactive"
                result << createEvent(name: "acceleration", value: "inactive", descriptionText: "$device.displayName is inactive", displayed: true)
                break
            case 3:
				log.info "$device.displayName acceleration is active"
                result << createEvent(name: "acceleration", value: "active", descriptionText: "$device.displayName is active", displayed: true)
                break
        }
    } else {
        log.warn "Need to handle this cmd.notificationType: ${cmd.notificationType}"
        result << createEvent(descriptionText: cmd.toString())
    }
    result
}

def zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
    logDebug "---CONFIGURATION REPORT V1--- ${device.displayName} parameter ${cmd.parameterNumber} with a byte size of ${cmd.size} is set to ${cmd.configurationValue}"

    def result = []
    def value
    if (cmd.parameterNumber == 9 && cmd.configurationValue[0] == 0) {
        value = "dc"
        if (!isConfigured()) {
            logDebug("ConfigurationReport: configuring device")
            result << response(configure())
        }
        result << createEvent(name: "powerSource", value: value, displayed: false)
    }
    else if (cmd.parameterNumber == 9 && cmd.configurationValue[0] == 1) {
        value = "battery"
        result << createEvent(name: "powerSource", value: value, displayed: false)
    } else if (cmd.parameterNumber == 4) {
		//received response to last command in configure() - configuration is complete
		setConfigured("true")
		logDebug("Configured!")
	}
    result
}

def zwaveEvent(hubitat.zwave.Command cmd) {
    logDebug "General zwaveEvent cmd: ${cmd}"
    createEvent(descriptionText: cmd.toString(), isStateChange: false)
}

def configure() {
    // This sensor joins as a secure device if you double-click the button to include it
    log.info "${device.displayName} is configuring its settings"

    if (motionDelayTime == null)  motionDelayTime = "1 minute"
    if (motionSensitivity == null) motionSensitivity = 3
    if (reportInterval == null) reportInterval = "5 minutes"
    if (tempChangeAmount == null) tempChangeAmount = 2
    if (humidChangeAmount == null) humidChangeAmount = 10
    if (luxChangeAmount == null) luxChangeAmount = 100
    if (tempOffset == null) tempOffset = 0
    if (humidOffset == null) humidOffset = 0
    if (luxOffset == null) luxOffset = 0
    selectiveReport = 0
    if (selectiveReporting == true) {selectiveReport = 1}

    if (motionSensitivity < 0) {
        logDebug "Motion sensitivity too low ... resetting to 0"
        motionSensitivity = 0
    } else if (motionSensitivity > 5) {
        logDebug "Motion sensitivity too high ... resetting to 5"
        motionSensitivity = 5
    }

    // fix temp offset
    if (tempOffset < -10) {
        tempOffset = -10
        logDebug "Temperature calibration too low... resetting to -10"
    } else if (tempOffset > 10) {
        tempOffset = 10
        logDebug "Temperature calibration too high ... resetting to 10"
    }

    // fix humidity offset
    if (humidOffset < -50) {
        humidOffset = -50
        logDebug "Humidity calibration too low... resetting to -50"
    } else if (humidOffset > 50) {
        humidOffset = 50
        logDebug "Humidity calibration too high ... resetting to 50"
    }

    if (luxOffset < -1000) {
        luxOffset = -1000
        logDebug "Luminance calibration too low ... resetting to -1000"
    } else if (luxOffset > 1000) {
        luxOffset = 1000
        logDebug "Luminance calibration too high ... resetting to 1000"
    }

    logDebug "In configure: Report Interval = $settings.reportInterval"
    logDebug "Motion Delay Time = $settings.motionDelayTime"
    logDebug "Motion Sensitivity = $settings.motionSensitivity"
    logDebug "Temperature adjust = $settings.tempOffset"
    logDebug "Humidity adjust = $settings.humidOffset"
    logDebug "Min Temp change for reporting = $settings.tempChangeAmount"
    logDebug "Min Humidity change for reporting = $settings.humidChangeAmount"
    logDebug "Min Lux change for reporting = $settings.luxChangeAmount"
    logDebug "LED Option = $settings.ledOptions"

    def waketime

    if (timeOptionValueMap[settings.reportInterval] > 300)
        waketime = timeOptionValueMap[settings.reportInterval]
    else waketime = 300

    logDebug "wake time reset to $waketime"

    logDebug "Current firmware: $device.currentFirmware"

    // Retrieve local temperature scale: "C" = Celsius, "F" = Fahrenheit
    // Convert to a value of 1 or 2 as used by the device to select the scale
    logDebug "Location temperature scale: ${location.getTemperatureScale()}"
    byte tempScaleByte = (location.getTemperatureScale() == "C" ? 1 : 2)

    def request = [
			
			// factory reset
			zwave.configurationV1.configurationSet(parameterNumber: 255, size: 1, configurationValue: [0]),

            // set wakeup interval to report time otherwise it doesnt report in time
            zwave.wakeUpV1.wakeUpIntervalSet(seconds:waketime, nodeid:zwaveHubNodeId),

            //1. set association groups for hub
            zwave.associationV1.associationSet(groupingIdentifier:1, nodeId:zwaveHubNodeId),
            //zwave.associationV1.associationSet(groupingIdentifier:2, nodeId:zwaveHubNodeId),

            //2. automatic report flags
            // params 101-103 [4 bytes] 128: light sensor, 64 humidity, 32 temperature sensor, 16 ultraviolet sensor, 1 battery sensor -> send command 241 to get all reports
            zwave.configurationV1.configurationSet(parameterNumber: 101, size: 4, scaledConfigurationValue: 241),   //association group 1 - all reports

            //3. no-motion report x seconds after motion stops (default 60 secs)
            zwave.configurationV1.configurationSet(parameterNumber: 3, size: 2, scaledConfigurationValue: timeOptionValueMap[motionDelayTime] ?: 60),

            //4. motion sensitivity: 0 (least sensitive) - 5 (most sensitive)
            zwave.configurationV1.configurationSet(parameterNumber: 4, size: 1, scaledConfigurationValue: motionSensitivity),

            //5. report every x minutes (threshold reports don't work on battery power, default 8 mins)
            zwave.configurationV1.configurationSet(parameterNumber: 111, size: 4, scaledConfigurationValue: timeOptionValueMap[reportInterval]), //association group 1
            zwave.configurationV1.configurationSet(parameterNumber: 112, size: 4, scaledConfigurationValue: timeOptionValueMap[reportInterval]),  //association group 2
			zwave.configurationV1.configurationSet(parameterNumber: 113, size: 4, scaledConfigurationValue: timeOptionValueMap[reportInterval]),  //association group 3
		
            //6. enable/disable selective reporting only on thresholds
            zwave.configurationV1.configurationSet(parameterNumber: 40, size: 1, scaledConfigurationValue: selectiveReport),

            // Set the temperature scale for automatic reports
            // US units default to reporting in Fahrenheit, whilst all others default to reporting in Celsius, but we can configure the preferred scale with this setting
            zwave.configurationV1.configurationSet(parameterNumber: 64, size: 1, configurationValue: [tempScaleByte]),

            // Automatically generate a report when temp changes by specified amount
            zwave.configurationV1.configurationSet(parameterNumber: 41, size: 4, configurationValue: [0, tempChangeAmount, tempScaleByte, 0]),

            // Automatically generate a report when humidity changes by specified amount
            zwave.configurationV1.configurationSet(parameterNumber: 42, size: 1, scaledConfigurationValue: humidChangeAmount),

            // Automatically generate a report when lux changes by specified amount
            zwave.configurationV1.configurationSet(parameterNumber: 43, size: 2, scaledConfigurationValue: luxChangeAmount),

            // Set temperature calibration offset
            zwave.configurationV1.configurationSet(parameterNumber: 201, size: 2, configurationValue: [tempOffset, tempScaleByte]),

            // Set humidity calibration offset
            zwave.configurationV1.configurationSet(parameterNumber: 202, size: 1, scaledConfigurationValue: humidOffset),

            // Set luminance calibration offset
            zwave.configurationV1.configurationSet(parameterNumber: 203, size: 2, scaledConfigurationValue: luxOffset),

            // Set LED Option value
            zwave.configurationV1.configurationSet(parameterNumber: 81, size: 1, scaledConfigurationValue: ledOptions),
		
			// we use this to set updated
			zwave.configurationV1.configurationGet(parameterNumber: 4),
    ]
    return commands(request)
}

def refresh() {
    logDebug "in refresh"

    return commands([
            zwave.versionV1.versionGet(),                                // Retrieve version info (includes firmware version)
            zwave.firmwareUpdateMdV2.firmwareMdGet(),                  // Command class not implemented by Hubitat yet
            zwave.configurationV1.configurationGet(parameterNumber: 9),  // Retrieve current power mode
            zwave.batteryV1.batteryGet(),
            zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType: 1), //temperature
            zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType: 3), //illuminance
            zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType: 5), //humidity
            zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType: 27) //ultravioletIndex
    ])
}

private def getTimeOptionValueMap() { [
        "20 seconds" : 20,
        "30 seconds" : 30,
        "1 minute"   : 60,
        "2 minutes"  : 2*60,
        "3 minutes"  : 3*60,
        "4 minutes"  : 4*60,
        "5 minutes"  : 5*60,
        "10 minutes" : 10*60,
        "15 minutes" : 15*60,
        "30 minutes" : 30*60,
        "1 hour"     : 1*60*60,
        "6 hours"    : 6*60*60,
        "12 hours"   : 12*60*60,
        "18 hours"   : 18*60*60,
        "24 hours"   : 24*60*60,
]}

private setConfigured(configure) {
    updateDataValue("configured", configure)
}

private isConfigured() {
    getDataValue("configured") == "true"
}

private command(hubitat.zwave.Command cmd) {
    if (state.sec) {
        logDebug "Sending secure Z-wave command: ${cmd.toString()}"
        return zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
    } else {
        logDebug "Sending Z-wave command: ${cmd.toString()}"
        return cmd.format()
    }
}

private commands(commands, delay=1000) {
    //log.info "sending commands: ${commands}"
    return delayBetween(commands.collect{ command(it) }, delay)
}


def zwaveEvent(hubitat.zwave.commands.versionv1.VersionCommandClassReport cmd) {
    logDebug "in version command class report $cmd"
}

def zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd) {
    logDebug "in version report"
    // SubVersion is in 1/100ths so that 1.01 < 1.08 < 1.10, etc.
    BigDecimal fw = cmd.applicationVersion + (cmd.applicationSubVersion / 100)
    state.firmware = fw
    logDebug "---VERSION REPORT V1--- ${device.displayName} is running firmware version: ${String.format("%.2f",fw)}, Z-Wave version: ${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}"
    if(fw < 1.10)
        log.warn "--- WARNING: Device handler expects devices to have firmware 1.10 or later"
}

def zwaveEvent(hubitat.zwave.commands.firmwareupdatemdv2.FirmwareMdReport cmd) {
    logDebug "In v2 FirmwareMdReport ${cmd}"
}

private logDebug(msg) {
	if (settings?.debugOutput || settings?.debugOutput == null) {
		log.debug "$msg"
	}
}

def clean() {
	logDebug "in clean"
	state = [:]
    state.take(1).each{ v ->
    	state.remove("${v.key}")
        log.info "removed ${v.key}"
    }
}