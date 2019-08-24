/**
 *
 *  Copyright 2019 gabriele-v
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Generic Zigbee Siren
 *
 *  Version: 0.3b
 *  0.1b (2019-02-14) => First release
 *  0.2b (2019-04-05) => Fix speech parameter without ending space
 *  0.3b (2019-08-24) => Add option for last checkin
 *
 *  Author: gabriele-v
 *
 *  Date: 2019-08-24
 *
 */

metadata {
    definition (name: "Generic Zigbee Siren", namespace: "gabriele-v", author: "gabriele-v") {
        capability "Alarm"
        capability "Switch"
        capability "Actuator"
        capability "Battery"
        capability "Refresh"
        capability "Configuration"
		capability "Speech Synthesis"

        attribute "maxWarningDuration", "Integer"
        attribute "hwVer", "String"
        attribute "lastCheckinTime", "Date"
		
		command "squawkArm"
		command "squawkDisarm"
    }

    preferences {
		//Global
        input name: "maxWarningDuration", type: "number", title: "Max duration of strobe/siren",  range: "1..1800", defaultValue: "240", required: true
		//Defaults
		input name: "defaultWarningMode", type:"enum", title: "Default Warning Mode", options: [[0:"0 - None"],[1:"1 - Burglar"],[2:"2 - Fire"],[3:"3 - Emergency"],[4:"4 - Police panic"],[5:"5 - Fire panic"],[6:"6 - Emergency panic"]], defaultValue: 3, required: true
		input name: "defaultUseStrobe", type: "enum", title: "Default Strobe", options: [[0:"0 - No strobe"],[1:"1 - Use Strobe"]], defaultValue: 1, required: true
		input name: "defaultSirenLevel", type: "enum", title: "Default Siren Level", options: [[0:"0 - Low"],[1:"1 - Medium"],[2:"2 - High"],[3:"3 - Very high"]], defaultValue: 1, required: true
        input name: "defaultWarningDuration", type: "number", title: "Default duration of strobe/siren",  range: "1..1800", defaultValue: "240", required: true
		input name: "defaultStrobeDutyCycle", type: "enum", title: "Default Strobe Duty Cycle (on for x% of time)", options: [[0:"0%"],[10:"10%"],[2:"20%"],[3:"30%"],[4:"40%"],[5:"50%"],[6:"60%"],[7:"70%"],[8:"80%"],[9:"90%"],[10:"100%"]], defaultValue: 5, required: true
		input name: "defaultStrobeLevel", type: "enum", title: "Default Strobe Level", options: [[0:"0 - Low"],[1:"1 - Medium"],[2:"2 - High"],[3:"3 - Very high"]], defaultValue: 1, required: true
		input name: "defaultSquawkMode", type: "enum", title: "Default Squawk Mode", options: [[0:"0 - Armed"],[1:"1 - Disarmed"]], defaultValue: 0, required: true
		input name: "defaultSquawkUseStrobe", type: "enum", title: "Default Squawk Strobe", options: [[0:"0 - No strobe"],[1:"1 - Use Strobe"]], defaultValue: 1, required: true
		input name: "defaultSquawkLevel", type: "enum", title: "Default Squawk Level", options: [[0:"0 - Low"],[1:"1 - Medium"],[2:"2 - High"],[3:"3 - Very high"]], defaultValue: 1, required: true
        //Logging Message Config
        input name: "lastCheckinEnable", type: "bool", title: "Enable custom date/time stamp events for lastCheckin", description: ""
        input name: "infoLogging", type: "bool", title: "Enable info message logging", description: ""
        input name: "debugLogging", type: "bool", title: "Enable debug message logging", description: ""
    }
}

// parse events into attributes
def parse(String description) {
    displayDebugLog("Parsing message: ${description}")
    Map map = [:]
    
    if (lastCheckinEnable) {
		sendEvent(name: "lastCheckinTime", value: new Date().toLocaleString())
	}
    
    if (description?.startsWith("read attr -")) {
        def descMap = zigbee.parseDescriptionAsMap(description)
        displayDebugLog("Desc Map: ${descMap}")
        if (descMap.cluster == "0001" && descMap.attrId == "0020")
        {
			displayDebugLog("RAW BATTERY: ${descMap.value}")
			map = parseBattery(descMap.value)
        }
        else if (descMap.cluster == "0502" && descMap.attrId == "0000")
        {
            displayDebugLog("RAW MAX WARNING DURATION: ${descMap.value}")
            retValue = Integer.parseInt(descMap.value, 16)
            map = [
                name: "maxWarningDuration",
                value: retValue,
                descriptionText: "Max siren/strobe duration is ${retValue}"
            ]
        }
    }

    if (map != [:]) {
        displayInfoLog("${map.name} => ${map.value} (${map.descriptionText})")
        displayDebugLog("Creating event $map")
        return createEvent(map)
    } else
        return [:]
}

private def displayDebugLog(message) {
    if (debugLogging) log.debug "${device.displayName}: ${message}"
}

private def displayInfoLog(message) {
    if (infoLogging || state.prefsSetCount != 1)
        log.info "${device.displayName}: ${message}"
}

def updated() {
    displayDebugLog("updated called")
    displayInfoLog("maxWarningDuration : ${settings.maxWarningDuration}")
    
    def cmds = 
    zigbee.writeAttribute(0x502, 0x0000, DataType.UINT16, (int)settings.maxWarningDuration) +
    zigbee.readAttribute(0x502, 0x0000)

    displayInfoLog("updated() --- cmds: $cmds")
    return cmds
}

def refresh() {
    def cmds =
        //Read the configured variables
        zigbee.readAttribute(0x001, 0x0020) +   //Battery percentage
        zigbee.readAttribute(0x502, 0x0000) //Read Max Duration
    
    displayInfoLog("refresh() --- cmds: $cmds")
    return cmds
}

def configure() {
	def cmds =
		zigbee.configureReporting(0x001, 0x0021, 0x20, 30, 3600)    //Attribute ID 0x0021 = battery percentage, Data Type: S16BIT
	
	displayInfoLog("configure() --- cmds: ${cmd}")
	return refresh() + cmds
}

def off() {
    zigbee.command(0x0502, 0x00, "00", "0000", "00", "00")
}

def on() {
    sendEvent(name: "alarm", value: "on", descriptionText: "Device alarming on", type: "digital")
    both()
}

def both() {
    sendEvent(name: "alarm", value: "both", descriptionText: "Device alarming with siren and strobe", type: "digital")
    startWarning(defaultWarningMode, 1, defaultSirenLevel, defaultWarningDuration, defaultStrobeDutyCycle, defaultStrobeLevel)
}

def strobe() {
    sendEvent(name: "alarm", value: "strobe", descriptionText: "Device alarming with strobe", type: "digital")
	startWarning(0, 1, 0, defaultWarningDuration, defaultStrobeDutyCycle, defaultStrobeLevel)
}

def siren() {
    sendEvent(name: "alarm", value: "siren", descriptionText: "Device alarming with siren", type: "digital")
    startWarning(defaultWarningMode, 0, defaultSirenLevel, defaultWarningDuration, defaultStrobeDutyCycle, defaultStrobeLevel)
}

def startWarning(WarningMode, UseStrobe, SirenLevel, WarningDuration, StrobeDutyCycle, StrobeLevel) {
    displayDebugLog("startWarning => WarningMode $WarningMode - UseStrobe $UseStrobe - SirenLevel $SirenLevel - WarningDuration $WarningDuration - StrobeDutyCycle $StrobeDutyCycle - StrobeLevel $StrobeLevel")
	def map8str = IntegerToBinaryStringPaddingZero(WarningMode, 4) + IntegerToBinaryStringPaddingZero(UseStrobe, 2) + IntegerToBinaryStringPaddingZero(SirenLevel, 2)
	displayDebugLog("Warning map8str = $map8str")
	int map8int = Integer.parseInt(map8str, 2);
	def map8hex = Integer.toString(map8int, 16);
	displayDebugLog("Warning map8hex = $map8hex")
	zigbee.command(0x502, 0x00, map8hex, DataType.pack(WarningDuration, DataType.UINT16), DataType.pack(StrobeDutyCycle, DataType.UINT16), DataType.pack(StrobeLevel, DataType.UINT16))
}

def startSquawk(SquawkMode, SquawkUseStrobe, SquawkLevel) {
    displayDebugLog("startSquawk => SquawkMode $SquawkMode - SquawkUseStrobe $SquawkUseStrobe - SquawkLevel $SquawkLevel")
	def map8str = IntegerToBinaryStringPaddingZero(SquawkMode, 4) + IntegerToBinaryStringPaddingZero(SquawkUseStrobe, 1) + 0 + IntegerToBinaryStringPaddingZero(SquawkLevel, 2)
	displayDebugLog("Squawk map8str = $map8str")
	int map8int = Integer.parseInt(map8str, 2);
	def map8hex = Integer.toString(map8int, 16);
	displayDebugLog("Squawk map8hex = $map8hex")
	zigbee.command(0x502, 0x00, map8hex)
}

def IntegerToBinaryStringPaddingZero(intNr, charKeep) {
	 int iValue = Integer.parseInt(intNr.toString())
	 def str = "0000000000" + Integer.toBinaryString(iValue)
	 return str.reverse().take(charKeep).reverse()
}

def speak(message) {
    displayDebugLog("Parsing message => $message")
	if(message.startsWith("Warning")) {
		def WarningMode = findAndParseMessageValue(message, "WarningMode", defaultWarningMode)
		def UseStrobe = findAndParseMessageValue(message, "UseStrobe", defaultUseStrobe)
		def SirenLevel = findAndParseMessageValue(message, "SirenLevel", defaultSirenLevel)
		def WarningDuration = findAndParseMessageValue(message, "WarningDuration", defaultWarningDuration)
		def StrobeDutyCycle = findAndParseMessageValue(message, "StrobeDutyCycle", defaultStrobeDutyCycle)
		def StrobeLevel = findAndParseMessageValue(message, "StrobeLevel", defaultStrobeLevel)
		startWarning(WarningMode, UseStrobe, SirenLevel, WarningDuration, StrobeDutyCycle, StrobeLevel)
	}
	else if (message.startsWith("Squawk")) {
		def SquawkMode = findAndParseMessageValue(message, "SquawkMode", defaultSquawkMode)
		def SquawkUseStrobe = findAndParseMessageValue(message, "SquawkUseStrobe", defaultSquawkUseStrobe)
		def SquawkLevel = findAndParseMessageValue(message, "SquawkLevel", defaultSquawkLevel)
		startSquawk(SquawkMode, SquawkUseStrobe, SquawkLevel)
	}
	else {
		log.error("Speaking message not starting with 'Warning' or 'Squawk' commands!")
		return
	}
}

def findAndParseMessageValue(message, parameter, defValue) {
	def param = parameter + "="
	if (message == null || !message.contains(param)) {
		displayInfoLog("Parameter '$parameter' not found in message, default will be used!")
		return defValue
	}
	def txtValue = message.substring(message.lastIndexOf(param) + param.length())
	def iEndValue = (txtValue.indexOf(" ") != -1) ? txtValue.indexOf(" ") : txtValue.length()
	txtValue = txtValue.substring(0, iEndValue)
	if(txtValue.isInteger()) {
		def intValue = Integer.parseInt(txtValue)
		displayDebugLog("'$parameter' value => intValue")
		return intValue
	}
	else {
		log.warn("Value '$txtValue' for '$parameter' is not valid, default '$defValue' will be used!")
		return defValue
	}
}

def squawkArm() {
	startSquawk(0, defaultSquawkUseStrobe, defaultSquawkLevel)
}

def squawkDisarm() {
	startSquawk(1, defaultSquawkUseStrobe, defaultSquawkLevel)
}