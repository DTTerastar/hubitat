/**
 *  Ring Manager
 *
 *  Copyright 2019 Dominick Meglio
 *
 */
metadata {
	definition (name: "Ring Manager", namespace: "ring", author: "Dominick Meglio") {
		capability "SwitchLevel"
	}

}

def setLevel(level) {
	parent.trigger(level)
	sendEvent(name: "switchLevel", value: "0")
}
