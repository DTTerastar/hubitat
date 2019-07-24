/**
 * WeMo Maker driver
 *
 * Author: Jason Cheatham
 * Last updated: 2019-07-23, 23:33:17-0400
 *
 * Inspired by Chris Kitch's WeMo Maker driver
 * at https://github.com/Kriskit/SmartThingsPublic/blob/master/devicetypes/kriskit/wemo/wemo-maker.groovy
 *
 * Licensed under the Apache License, Version 2.0 (the 'License'); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at:
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

metadata {
    definition(
        name: 'Wemo Maker',
        namespace: 'jason0x43',
        author: 'Jason Cheatham'
    ) {
        capability 'Actuator'
        capability 'Switch'
        capability 'Contact Sensor'
        capability 'Momentary'
        capability 'Polling'
        capability 'Refresh'
        capability 'Sensor'

        attribute 'switchMode', 'string'
        attribute 'sensorPresent', 'string'

        command 'subscribe'
        command 'unsubscribe'
        command 'resubscribe'
    }

    preferences {
        section {
            input(
                'invertSensor',
                type: 'bool',
                title: 'Invert Sensor',
                description: 'Inverts the sensor input',
                required: true
            )
        }
    }
}

def getDriverVersion() {
    2
}

def on() {
    log.info('Turning on')
    parent.childSetBinaryState(device, '1')
}

def off() {
    log.info('Turning off')
    parent.childSetBinaryState(device, '0')
}

def parse(description) {
    debugLog('parse: received message')

    // A message was received, so the device isn't offline
    unschedule('setOffline')

    def msg = parseLanMessage(description)
    parent.childUpdateSubscription(msg, device)

    def result = []
    def bodyString = msg.body
    if (bodyString) {
        def body = new XmlSlurper().parseText(bodyString)

        if (body?.property?.TimeSyncRequest?.text()) {
            debugLog('parse: Got TimeSyncRequest')
            result << syncTime()
        } else if (body?.Body?.SetBinaryStateResponse?.BinaryState?.text()) {
            def rawValue = body.Body.SetBinaryStateResponse.BinaryState.text()
            debugLog("parse: Got SetBinaryStateResponse = ${rawValue}")
            result << createBinaryStateEvent(rawValue)
        } else if (body?.property?.BinaryState?.text()) {
            def rawValue = body.property.BinaryState.text()
            debugLog("parse: Notify: BinaryState = ${rawValue}")
            result << createBinaryStateEvent(rawValue)
        } else if (body?.property?.TimeZoneNotification?.text()) {
            debugLog("parse: Notify: TimeZoneNotification = ${body.property.TimeZoneNotification.text()}")
        } else if (body?.Body?.GetBinaryStateResponse?.BinaryState?.text()) {
            def rawValue = body.Body.GetBinaryStateResponse.BinaryState.text()
            debugLog("parse: GetBinaryResponse: BinaryState = ${rawValue}")
            result << createBinaryStateEvent(rawValue)
        } else if (body?.property?.attributeList?.text()) {
            def rawValue = body.property.attributeList.text()
            debugLog("parse: Got PropertySet = ${rawValue}")
            result << createPropertySetEvent(rawValue)
        }
    }

    result
}

def poll() {
    log.info('Polling')

    // Schedule a call to flag the device offline if no new message is received
    if (device.currentValue('switch') != 'offline') {
        runIn(10, setOffline)
    }

    parent.childGetBinaryState(device)
}

def push() {
    log.info('Pushing')
    parent.childSetBinaryState(device, '1')
}

def refresh() {
    log.info('Refreshing')
    [
        resubscribe(),
        syncTime(),
        poll()
    ]
}

def resubscribe() {
    log.info('Resubscribing')

    // Schedule a subscribe check that will run after the resubscription should
    // have completed
    runIn(10, subscribeIfNecessary)

    parent.childResubscribe(device)
}

def setOffline() {
    sendEvent(
        name: 'switch',
        value: 'offline',
        descriptionText: 'The device is offline'
    )
}

def subscribe() {
    log.info('Subscribing')
    parent.childSubscribe(device)
}

def subscribeIfNecessary() {
    parent.childSubscribeIfNecessary(device)
}

def unsubscribe() {
    log.info('Unsubscribing')
    parent.childUnsubscribe(device)
}

def updated() {
    log.info('Updated')
    refresh()
}

private createBinaryStateEvent(rawValue) {
    updateSwitch(rawValue == '0' ? 'off' : 'on')
}

private createPropertySetEvent(rawValue) {
    def attrList = new XmlSlurper().parseText('<attributeList>' + listString + '</attributeList>')
    processAttributeList(attrList, result)
}

private debugLog(message) {
    if (parent.debugLogging) {
        log.debug(message)
    }
}

private processAttributeList(list, result) {
    def values = [:]

    list?.attribute.findAll {
        it.name?.text()
    }.each {
        values[it.name.text()] = it.value.text()
    }

    def sensorPresent = device.currentValue('sensorPresent') == 'on'

    if (values['SensorPresent']) {
        log.debug "SensorPresent = ${values['SensorPresent']}"
        def newSensorPresent = values['SensorPresent'] == '1'

        if (sensorPresent != newSensorPresent && newSensorPresent && !values['Sensor']) {
            values['Sensor'] = '0'
        }

        result << updateSensorPresent(newSensorPresent ? 'on' : 'off')
        sensorPresent = newSensorPresent
    }

    if (!sensorPresent) {
        result << updateSensor('disabled')
    } else if (values['Sensor']) {
        log.debug "Sensor = ${values['Sensor']}"
        def checkValue = sensorInvert ? '1' : '0'
        result << updateSensor(values['Sensor'] == checkValue ? 'closed' : 'open')
    }

    def switchMode = device.currentValue('switchMode')

    if (values['SwitchMode']) {
        log.debug "SwitchMode = ${values['SwitchMode']}"
        switchMode = values['SwitchMode'] == '0' ? 'toggle' : 'momentary'

        if (switchMode == 'momentary' && device.currentValue('switch') != 'momentary') {
            result << updateSwitch('momentary')
        } else if (!values['Switch'] && switchMode == 'toggle' && device.currentValue('switch') != 'toggle') {
            values['Switch'] = '0'
        }

        result << updateSwitchMode(switchMode)
    }

    if (values['Switch']) {
        log.debug "Switch = ${values['Switch']}"
        if (switchMode == 'toggle') {
            result << updateSwitch(values['Switch'] == '0' ? 'off' : 'on')
        } else if (values['Switch'] == '0') {
            result << updateSwitch('momentary')
        }
    }
}

private syncTime() {
    parent.childSyncTime(device)
}

private updateSensor(value) {
    createEvent(
        name: 'contact',
        value: value,
        descriptionText: "Contact is ${value}"
    )
}

private updateSensorPresent(value) {
    def sensorState = value ? 'enabled' : 'disabled'
    createEvent(
        name: "sensorPresent",
        value: value,
        descriptionText: "Sensor is ${sensorState}"
    )
}

private updateSwitch(value) {
    createEvent(
        name: 'switch',
        value: value,
        descriptionText: "Switch is ${value}"
    )
}

private updateSwitchMode(value) {
    createEvent(
        name: 'switchMode',
        value: value,
        descriptionText: "Switch mode is ${value}"
    )
}
