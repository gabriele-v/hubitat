/**
 *
 *  Copyright 2018 gabriele-v
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
 *  Bitron 902010/32 Thermostat
 *
 *  Version: 0.4b
 *  0.1b (2018-12-21) => First release
 *  0.2b (2018-12-23) => Add system mode support and cooling temperature
 *  0.3b (2018-12-23) => Skip wrong messages
 *  0.4b (2018-12-29) => Fix last change source reporting
 *
 *  Author: gabriele-v
 *
 *  Date: 2018-12-29
 *
 * Sources:
 * Bitron 902010/32 Zigbee manual => https://images-eu.ssl-images-amazon.com/images/I/91ZbuTU-duS.pdf
 *
 */
 

metadata {
	definition (name: "Bitron 902010/32 Thermostat", namespace: "gabriele-v", author: "gabriele-v") {
		capability "Thermostat"
		capability "Temperature Measurement"
		capability "Battery"
		capability "Actuator"
		capability "Refresh"
		capability "Sensor"
		capability "Configuration"

		attribute "lastCheckin", "String"
		attribute "hwVer", "String"
		attribute "swVer", "String"
		attribute "lockMode", "String"
		attribute "thermostatUnitFormat", "String"
		attribute "thermostatLastChangeSource", "String"
		
		command "increaseHeatSetpoint"
		command "decreaseHeatSetpoint"

		fingerprint profileId: "0104", endpointId: "01", inClusters: " 0000,0001,0003,000A,0020,0201,0204,0B05", outClusters: "0402", manufacturer: "Bitron", model: "902010/32"
	}

	preferences {
		input("unitformat", "enum", title: "What unit format do you want to display temperature on thermostat and in Hubitat?", options: ["C", "F"], defaultValue: "C", required: true, displayDuringSetup: false)
		input("lock", "enum", title: "Display Lock?", options: ["No", "Mode locked", "Mode and SetPoint locked"], defaultValue: "No", required: false, displayDuringSetup: false)
		input("tempcal", "enum", title: "Temperature adjustment.", options: ["+2.5", "+2.0", "+1.5", "+1.0", "+0.5", "0", "-0.5", "-1.0", "-1.5", "-2.0", "-2.5"], defaultValue: "0", required: false, displayDuringSetup: false)
	    //Battery Voltage Range
 		input name: "voltsmin", title: "Min Volts (0% battery = ___ volts, range 2.0 to 2.7). Default = 2.5 Volts", description: "", type: "decimal", range: "2..2.7"
 		input name: "voltsmax", title: "Max Volts (100% battery = ___ volts, range 2.8 to 3.4). Default = 3.0 Volts", description: "", type: "decimal", range: "2.8..3.4"
		//Logging Message Config
 		input name: "infoLogging", type: "bool", title: "Enable info message logging", description: ""
 		input name: "debugLogging", type: "bool", title: "Enable debug message logging", description: ""
	}
}

// parse events into attributes
def parse(String description) {
	displayDebugLog("Parsing message: ${description}")
	Map map = [:]
	
	// lastCheckin can be used with webCoRE
	// sendEvent(name: "lastCheckin", value: now())
	
	if (description?.startsWith("read attr -")) {
		def descMap = parseDescriptionAsMap(description)
		displayDebugLog("Desc Map: ${descMap}")
		if (descMap.cluster == "0201" && descMap.attrId == "0000")
		{
			displayDebugLog("RAW TEMPERATURE: ${descMap.value}")
			temp = parseTemperature(descMap.value)
			map = [
				name: "temperature",
				value: temp,
				unit: "${settings.unitformat}",
				isStateChange: true,
				descriptionText: "Temperature is ${temp}°${settings.unitformat}",
				translatable:true
			]
		}
		else if (descMap.cluster == "0001" && descMap.attrId == "0020")
		{
			displayDebugLog("RAW BATTERY: ${descMap.value}")
			map = parseBattery(descMap.value)
		}
		else if (descMap.cluster == "0000" && descMap.attrId == "4000")
		{
			displayDebugLog("RAW SW BUILD: ${descMap.value}")
			map = [
				name: 'swVer',
				value: new String(descMap.value.decodeHex())
			]
		}
		else if (descMap.cluster == "0000" && descMap.attrId == "0003")
		{
			displayDebugLog("RAW HW VER: ${descMap.value}")
			map = [
				name: 'hwVer',
				value: descMap.value
			]
		}
		else if (descMap.cluster == "0201" && descMap.attrId == "0010")
		{
			displayDebugLog("RAW TEMP CALIBRATION: ${descMap.value}")
		}
		else if (descMap.cluster == "0201" && descMap.attrId == "0011")
		{
			displayDebugLog("RAW COOLING SETPOINT: ${descMap.value}")
			temp = parseTemperature(descMap.value)
			map = [
				name: "coolingSetpoint",
				value: temp,
				unit: "${settings.unitformat}",
				isStateChange: true,
				descriptionText: "Cooling set point is ${temp}°${settings.unitformat}",
				translatable:true
			]
		}
		else if (descMap.cluster == "0201" && descMap.attrId == "0012")
		{
			displayDebugLog("RAW HEATING SETPOINT: ${descMap.value}")
			temp = parseTemperature(descMap.value)
			map = [
				name: "heatingSetpoint",
				value: temp,
				unit: "${settings.unitformat}",
				isStateChange: true,
				descriptionText: "Heating set point is ${temp}°${settings.unitformat}",
				translatable:true
			]
		}
		//After configure, some 0x0029 events with value 07122900000000 and size 14 are sent, skip them filtering on size
        else if (descMap.cluster == "0201" && descMap.attrId == "0029" && descMap.size != "14")
		{
			displayDebugLog("RAW THERMOSTAT RELAY STATE: ${descMap.value}")		
			def retValue = (descMap.value == "0000") ? "idle" : "heating"
			map = [
				name: "thermostatOperatingState",
				value: retValue,
				isStateChange: true,
				descriptionText: "Thermostat is ${retValue}",
				translatable:true
			]
		}
		else if (descMap.cluster == "0201" && descMap.attrId == "001C")
		{
			displayDebugLog("RAW THERMOSTAT MODE: ${descMap.value}")
			
			def retValue = ""
			switch(descMap.value) {
				case "00":
					retValue = "off"
					break
				case "03":
					retValue = "cool"
					break
				case "04":
					retValue = "heat"
					break
				default:
					break
			}
			
			map = [
				name: "thermostatMode",
				value: retValue,
				isStateChange: true,
				descriptionText: "Thermostat mode is ${retValue}",
				translatable:true
			]
		}
		else if (descMap.cluster == "0204" && descMap.attrId == "0001")
		{
			displayDebugLog("RAW LOCK DISPLAY: ${descMap.value}")
			
			def retValue = ""
			switch(descMap.value) {
				case "00":
					retValue = "unlocked"
					break
				case "01":
					retValue = "ModeLock"
					break
				case "02":
					retValue = "ModeSetPointLocked"
					break
				default:
					break
			}
			
			map = [
				name: "lockMode",
				value: retValue,
				isStateChange: true,
				descriptionText: "Thermostat display lock status is ${retValue}",
				translatable:true
			]
		}
		else if (descMap.cluster == "0204" && descMap.attrId == "0000")
		{		
			displayDebugLog("RAW THERMOSTAT UNIT FORMAT: ${descMap.value}")		
			def retValue = (descMap.value == "00") ? "C" : "F"
			map = [
				name: "thermostatUnitFormat",
				value: retValue,
				isStateChange: true,
				descriptionText: "Thermostat is ${retValue}",
				translatable:true
			]
		}
		else if (descMap.cluster == "0201" && descMap.attrId == "0030")
		{
			displayDebugLog("RAW THERMOSTAT LAST CHANGE: ${descMap.value}")
			
			def retValue = ""
			switch(descMap.value) {
				case "00":
					retValue = "Manual"
					break
				case "01":
					retValue = "Internal Scheduler"
					break
				case "02":
					retValue = "External"
					break
				default:
					break
			}
			
			map = [
				name: "thermostatLastChangeSource",
				value: retValue,
				isStateChange: true,
				descriptionText: "Thermostat last change has been made ${retValue}",
				translatable:true
			]
		}
		
		// TO BE DONE, SET DATE IT'S NEEDED
		//else if (descMap.cluster == "0201" && descMap.attrId == "0032")
		//{
		//	log.debug "THERMOSTAT LAST CHANGE DATE: $descMap.value"
		//	map.name = "thermostatUnitFormat"
		//	map.value = $descMap.value
		//}
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

// Return temperature
def parseTemperature(value) {
	float temp = Integer.parseInt(value, 16)/100
	temp = (settings.unitformat == "F") ? ((temp * 1.8) + 32) : temp
	temp = temp.round(1)
}

// Return battery status
private parseBattery(value) {
	def rawVolts = Math.round(Integer.parseInt(value, 16)) / 10
	def minVolts = voltsmin ? voltsmin : 2.5
	def maxVolts = voltsmax ? voltsmax : 3.0
	def pct = (rawVolts - minVolts) / (maxVolts - minVolts)
	def roundedPct = Math.min(100, Math.round(pct * 100))
	def descText = "Battery level is ${roundedPct}% (${rawVolts} Volts)"
	def result = [
		name: 'battery',
		value: roundedPct,
		unit: "%",
		isStateChange: true,
		descriptionText: descText
	]
	return result
}

def setHeatingSetpoint(degrees) {
	if (degrees != null) {
		float fTemp = degrees; 
		displayInfoLog("setHeatingSetpoint(${fTemp} ${temperatureScale})")
		float celsius = (settings.unitformat == "Fahrenheit") ? (fahrenheitToCelsius(fTemp)).round(1) : fTemp.round(1)
		sendEvent("name":"heatingSetpoint", "value":celsius)
		int zigbeeTemp = celsius * 100;
		def cmds =
			zigbee.writeAttribute(0x201, 0x12, 0x29, zigbeeTemp) +
			zigbee.readAttribute(0x201, 0x12)	//Read Heat Setpoint
		return cmds
	}
}

def setCoolingSetpoint(degrees) {
	if (degrees != null) {
		float fTemp = degrees; 
		displayInfoLog("setCoolingSetpoint(${fTemp} ${temperatureScale})")
		float celsius = (settings.unitformat == "Fahrenheit") ? (fahrenheitToCelsius(fTemp)).round(1) : fTemp.round(1)
		sendEvent("name":"coolingSetpoint", "value":celsius)
		int zigbeeTemp = celsius * 100;
		def cmds =
			zigbee.writeAttribute(0x201, 0x11, 0x29, zigbeeTemp) +
			zigbee.readAttribute(0x201, 0x11)	//Read Cooling Setpoint
		return cmds
	}
}

def increaseHeatSetpoint()
{
	float currentSetpoint = device.currentValue("heatingSetpoint")
   	float maxSetpoint
   	float step

   	if (settings.unitformat == "Fahrenheit")
   	{
		maxSetpoint = 86
		step = 0.5
   	}
   	else
   	{
		maxSetpoint = 30
		step = 0.5
   	}

	if (currentSetpoint < maxSetpoint)
	{
		currentSetpoint = currentSetpoint + step
		setHeatingSetpoint(currentSetpoint)
	}
}

def decreaseHeatSetpoint()
{
	float currentSetpoint = device.currentValue("heatingSetpoint")
	float minSetpoint
	float step

	if (settings.unitformat == "Fahrenheit")
	{
		minSetpoint = 41
		step = 0.5
	}
	else
	{
		minSetpoint = 5
		step = 0.5
	}

   	if (currentSetpoint > minSetpoint)
   	{
		currentSetpoint = currentSetpoint - step
		setHeatingSetpoint(currentSetpoint)
   	}
}

//# thermostat.setThermostatMode capability
void setThermostatMode(mode) {
	if (mode != null) {
		displayInfoLog("Set thermostat mode to ${mode}")
		def Value
		switch(mode) {
			case "off":
				Value = 0x00
				break
			case "cool":
				Value = 0x03
				break
			case "heat":
				Value = 0x04
				break
			default:
				break
		}

		if($Value)
		{
			sendEvent(name: 'thermostatMode', value: mode)
			zigbee.writeAttribute(0x201, 0x001C, 0x30, Value)
			zigbee.readAttribute(0x201, 0x001C)	//Read Mode
		}
		else
		{
			log.error("Mode ${mode} not supported")
		}
	}
}

void off() {
	setThermostatMode('off')
}

void heat() {
	setThermostatMode('heat')
}

void emergencyHeat() {
	setThermostatMode('heat')
}

void cool() {
	setThermostatMode('cool')
}

def configure() {
	def cmds =
		//Cluster ID (0x0201 = Thermostat Cluster), Attribute ID, Data Type, Payload (Min report, Max report, On change trigger)
		zigbee.configureReporting(0x0201, 0x0000, 0x29, 30, 60, 50) +    //Attribute ID 0x0000 = local temperature, Data Type: S16BIT
		zigbee.configureReporting(0x0201, 0x0011, 0x29, 30, 120, 50) +   //Attribute ID 0x0011 = cooling temperature, Data Type: S16BIT
		zigbee.configureReporting(0x0201, 0x0012, 0x29, 30, 120, 50) +   //Attribute ID 0x0012 = heating temperature, Data Type: S16BIT
		zigbee.configureReporting(0x0201, 0x001C, 0x30, 600, 21600, 1) + //Attribute ID 0x001C = system mode, Data Type: ENUM-8
        zigbee.configureReporting(0x0201, 0x0029, 0x19, 30, 60, 1) + 	 //Attribute ID 0x0029 = relay status, Data Type: BIT16
		zigbee.configureReporting(0x0201, 0x0030, 0x30, 600, 120, 1)   //Attribute ID 0x0030 = last change source, Data Type: ENUM-8
		
		//Cluster ID (0x0001 = Power)
		zigbee.configureReporting(0x0001, 0x0020, 0x20, 600, 21600, 1) 	//Attribute ID 0x0020 = battery voltage, Data Type: U8BIT
	
	displayInfoLog("configure() --- cmds: ${cmd}")
	return refresh() + cmds
}

def refresh() {
	def cmds =
		//Read the configured variables
		zigbee.readAttribute(0x000, 0x0003) +	//Read HW Version
		zigbee.readAttribute(0x000, 0x4000) +	//Read Application Version
		zigbee.readAttribute(0x001, 0x0020) +	//Read BatteryVoltage
		zigbee.readAttribute(0x201, 0x0000) +	//Read LocalTemperature
		zigbee.readAttribute(0x201, 0x0010) +	//Read LocalTemperatureCalibration
		zigbee.readAttribute(0x201, 0x0011) +	//Read CoolingTemperature
		zigbee.readAttribute(0x201, 0x0012) +	//Read HeatingTemperature
		zigbee.readAttribute(0x201, 0x001C) +	//Read SystemMode
	    zigbee.readAttribute(0x201, 0x0029)	+	//Read Relay Status
		zigbee.readAttribute(0x201, 0x0030) +   //Read latest change source
		zigbee.readAttribute(0x201, 0x0032)     //Read latest change date
		zigbee.readAttribute(0x204, 0x0001)	+	//Read KeypadLockout
		zigbee.readAttribute(0x204, 0x0000) +   //Read unit format
	
	displayInfoLog("refresh() --- cmds: $cmds")
	return cmds
}

def updated() {
	displayDebugLog("updated called")
	def unitformat = 0x00
	def lockmode = 0x00
	def tempadjust = 0x00

	displayInfoLog("lock : $settings.lock")
	if (settings.lock == "Mode locked")
	{
		lockmode = 0x01
	}
	else if (settings.lock == "Mode and SetPoint locked")
	{
		lockmode = 0x02
	}
	else
	{
		lockmode = 0x00
		settings.lock = "No"
	}

	displayInfoLog("unitformat : $settings.unitformat")
	if (settings.unitformat == "Fahrenheit")
	{
		unitformat = 0x01
	}
	else
	{
		settings.unitformat = "Celsius"
		unitformat = 0x00
	}

	displayInfoLog("tempcal : $settings.tempcal")
	if (settings.tempcal == "+2.5")
	{
		tempadjust = 0x19
	}
	else if (settings.tempcal == "+2.0")
	{
		tempadjust = 0x14
	}
	else if (settings.tempcal == "+1.5")
	{
		tempadjust = 0x0f
	}
	else if (settings.tempcal == "+1.0")
	{
		tempadjust = 0x0a
	}
	else if (settings.tempcal == "+0.5")
	{
		tempadjust = 0x05
	}
	else if (settings.tempcal == "-0.5")
	{
		tempadjust = 0xd3
	}
	else if (settings.tempcal == "-1.0")
	{
		tempadjust = 0xd8
	}
	else if (settings.tempcal == "-1.5")
	{
		tempadjust = 0xed
	}
	else if (settings.tempcal == "-2.0")
	{
		tempadjust = 0xe2
	}
	else if (settings.tempcal == "-2.5")
	{
		tempadjust = 0xe7
	}
	else
	{
		tempadjust = 0x00
		settings.tempcal = "0"
	}
	def cmds = 
		zigbee.writeAttribute(0x204, 0x0001, 0x30, lockmode) +
		zigbee.readAttribute(0x204, 0x0001) +
		zigbee.writeAttribute(0x201, 0x0010, 0x28, tempadjust) +
		zigbee.readAttribute(0x201, 0x0010) +
		zigbee.writeAttribute(0x204, 0x0000, 0x30, unitformat) +
		zigbee.readAttribute(0x204, 0x0000)

	displayInfoLog("updated() --- cmds: $cmds")
//	fireCommand(cmds)
    return cmds
}

private fireCommand(List commands) {
	if (commands != null && commands.size() > 0)
	{
		log.trace("Executing commands:" + commands)
		for (String value : commands)
		{
			sendHubCommand([value].collect {new hubitat.device.HubAction(it)})
		}
	}
}