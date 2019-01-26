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
 *  Heiman HS2WD-E Siren
 *
 *  Version: 0.1b
 *  0.1b (2019-01-26) => First release
 *
 *  Author: gabriele-v
 *
 *  Date: 2019-01-26
 *
 *  Sources:
 *  ST Siren => https://github.com/SmartThingsCommunity/SmartThingsPublic/blob/master/devicetypes/smartthings/ozom-smart-siren.src/ozom-smart-siren.groovy
 */

metadata {
	definition (name: "Heiman HS2WD-E Siren", namespace: "gabriele-v", author: "gabriele-v") {
		capability "Alarm"
		capability "Switch"
		capability "Actuator"
		capability "Battery"
		capability "Refresh"
		capability "Configuration"

		attribute "maxDuration", "Integer"
		attribute "hwVer", "String"
		
		fingerprint profileId: "0104", endpointId: "01", inClusters: "0000,0001,0003,0004,0009,0500,0502", outClusters: "0003,0019", manufacturer: "Heiman", model: "WarningDevice"
	}

	preferences {
		input name: "maxDuration", type: "number", title: "Max duration of strobe/siren",  range: "1..1800", defaultValue: "240", required: true
		//Logging Message Config
		input name: "infoLogging", type: "bool", title: "Enable info message logging", description: ""
		input name: "debugLogging", type: "bool", title: "Enable debug message logging", description: ""
	}
}

// parse events into attributes
def parse(String description) {
	displayDebugLog("Parsing message: ${description}")
	Map map = [:]
	
	if (description?.startsWith("read attr -")) {
		def descMap = parseDescriptionAsMap(description)
		displayDebugLog("Desc Map: ${descMap}")
		if (descMap.cluster == "0000" && descMap.attrId == "0003")
		{
			displayDebugLog("RAW HW VER: ${descMap.value}")
			map = [
				name: 'hwVer',
				value: descMap.value
			]
		}
		else if (descMap.cluster == "0001" && descMap.attrId == "0021")
		{
			displayDebugLog("RAW BATTERY PERCENTAGE: ${descMap.value}")
			retValue = Integer.parseInt(descMap.value, 16) - 100
			map = [
				name: 'battery',
				value: retValue,
				unit: "%",
				descriptionText: "Battery level is ${retValue}%"
			]
		}
		else if (descMap.cluster == "0502" && descMap.attrId == "0000")
		{
			displayDebugLog("RAW MAX DURATION: ${descMap.value}")
			retValue = Integer.parseInt(descMap.value, 16)
			map = [
				name: "maxDuration",
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

def parseDescriptionAsMap(description) {
	(description - "read attr - ").split(",").inject([:]) { map, param ->
		def nameAndValue = param.split(":")
		map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
	}
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
	displayInfoLog("maxDuration : ${settings.maxDuration}")
	
	def cmds = 
	zigbee.writeAttribute(0x502, 0x0000, DataType.UINT16, (int)settings.maxDuration) +
	zigbee.readAttribute(0x502, 0x0000)

	displayInfoLog("updated() --- cmds: $cmds")
    return cmds
}

def refresh() {
	def cmds =
		//Read the configured variables
		zigbee.readAttribute(0x000, 0x0003) +	//Read HW Version
		zigbee.readAttribute(0x001, 0x0021) +   //Battery percentage
		zigbee.readAttribute(0x502, 0x0000) //Read Max Duration
	
	displayInfoLog("refresh() --- cmds: $cmds")
	return cmds
}

def off() {
	zigbee.command(0x0502, 0x00, "00", "0000", "00", "00")
}

def on() {
	both()
}

def both() {
	zigbee.command(0x502, 0x00, "17", DataType.pack(1, DataType.UINT16), "00", "01")
}

def strobe() {
	zigbee.command(0x502, 0x00, "04", DataType.pack(1, DataType.UINT16), "00", "01")
}

def siren() {
	zigbee.command(0x502, 0x00, "13", DataType.pack(warningDuration, DataType.UINT16), "00", "00")
}
