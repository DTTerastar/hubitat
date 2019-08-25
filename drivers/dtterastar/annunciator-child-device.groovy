metadata {
	definition (name: "Annunciator Child Device", namespace: "DTTerastar", author: "Darrell Turner") {
		capability "Speech Synthesis"
		capability "Notification"
		capability "Actuator"
        capability "Refresh"
	}
}

def deviceNotification(message) {
    speak(message)
}

void speak(phrase) {
	parent.speak(phrase)
}