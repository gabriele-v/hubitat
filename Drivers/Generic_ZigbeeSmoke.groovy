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
 *  Generic Zigbee Smoke detector
 *
 *  Version: 0.1b
 *  0.1b (2019-02-22) => First release
 *
 *  Author: gabriele-v
 *
 *  Date: 2019-02-22
 *
 *  Source => https://github.com/SmartThingsCommunity/SmartThingsPublic/blob/master/devicetypes/smartthings/zigbee-smoke-sensor.src/zigbee-smoke-sensor.groovy
 */

import hubitat.zigbee.clusters.iaszone.ZoneStatus

metadata {
	definition (name: "Generic Zigbee Smoke detector", namespace: "gabriele-v", author: "gabriele-v",) {
		capability "Smoke Detector"
		capability "Sensor"
		capability "Battery"
		capability "Configuration"
		capability "Refresh"
	}
	
	preferences {
		//Logging Message Config
 		input name: "infoLogging", type: "bool", title: "Enable info message logging", description: ""
 		input name: "debugLogging", type: "bool", title: "Enable debug message logging", description: ""
	}
}

def installed(){
	displayDebugLog("installed")
	refresh()
}

def parse(String description) {
	displayDebugLog("description(): $description")
	def map = zigbee.getEvent(description)
	if (!map) {
		if (description?.startsWith('zone status')) {
			map = parseIasMessage(description)
		} else {
			map = parseAttrMessage(description)
		}
	}
	displayDebugLog("Parse returned $map")
	def result = map ? createEvent(map) : [:]
	if (description?.startsWith('enroll request')) {
		List cmds = zigbee.enrollResponse()
		displayDebugLog("enroll response: ${cmds}")
		result = cmds?.collect { new hubitat.device.HubAction(it)}
	}
	return result
}

def parseAttrMessage(String description){
	displayDebugLog("Received message => ${description}")
	def descMap = zigbee.parseDescriptionAsMap(description)
	def map = [:]
	if (descMap?.clusterInt == zigbee.POWER_CONFIGURATION_CLUSTER && descMap.commandInt != 0x07 && descMap.value) {
		map = getBatteryPercentageResult(Integer.parseInt(descMap.value, 16))
	} else if (descMap?.clusterInt == 0x0500 && descMap.attrInt == 0x0002) {
		def zs = new ZoneStatus(zigbee.convertToInt(descMap.value, 16))
		map = translateZoneStatus(zs)
	}
	return map;
}

def parseIasMessage(String description) {
	ZoneStatus zs = zigbee.parseZoneStatus(description)
	return getDetectedResult(zs.isAlarm1Set() || zs.isAlarm2Set())
}

private Map translateZoneStatus(ZoneStatus zs) {
	return getDetectedResult(zs.isAlarm1Set() || zs.isAlarm2Set())
}

private Map getBatteryPercentageResult(rawValue) {
	displayDebugLog("Battery Percentage rawValue = ${rawValue} -> ${rawValue / 2}%")
	def result = [:]

	if (0 <= rawValue && rawValue <= 200) {
		result.name = 'battery'
		result.value = Math.round(rawValue / 2)
		result.descriptionText = "${device.displayName} battery was ${result.value}%"
	}

	return result
}
def getDetectedResult(value) {
	def detected = value ? 'detected': 'clear'
	String descriptionText = "${device.displayName} smoke ${detected}"
	return [name:'smoke',
			value: detected,
			descriptionText:descriptionText
		   ]
}

def refresh() {
	displayDebugLog("Refreshing Values")
	def refreshCmds = []
	refreshCmds += zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, 0x0021) +
					zigbee.readAttribute(zigbee.IAS_ZONE_CLUSTER, 0x0003)
	return refreshCmds
}

private def displayDebugLog(message) {
    if (debugLogging) log.debug "${device.displayName}: ${message}"
}

private def displayInfoLog(message) {
    if (infoLogging || state.prefsSetCount != 1)
        log.info "${device.displayName}: ${message}"
}