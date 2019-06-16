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
 *  OpenSky Network Driver
 *
 *  Version: 0.1b
 *  0.1b  (2019-06-15) => First release
 *
 *  Author: gabriele-v
 *
 *  Date: 2019-06-15
 *
 *  Sources:
 *  OpenSky Network API => https://opensky-network.org/apidoc/rest.html
 *
 */

metadata    {
    definition (name: "OpenSky Driver", namespace: "gabriele-v", author: "gabriele-v")  {
        capability "Actuator"
        capability "Configuration"
        capability "Polling"
        capability "Refresh"
        capability "Switch"

        attribute "rawresult", "string"
        attribute "flight1", "string"
        attribute "flight1-icao24", "string"
        attribute "flight1-callsign", "string"
        attribute "flight1-origincountry", "string"
        attribute "flight1-longitude", "double"
        attribute "flight1-latitude", "double"
        attribute "flight1-baro_altitude", "double"
        attribute "flight1-velocity", "double"
        attribute "flight1-true_track", "double"
        attribute "flight1-vertical_rate", "double"
        
        attribute "flight2", "string"
        attribute "flight2-icao24", "string"
        attribute "flight2-callsign", "string"
        attribute "flight2-origincountry", "string"
        attribute "flight2-longitude", "double"
        attribute "flight2-latitude", "double"
        attribute "flight2-baro_altitude", "double"
        attribute "flight2-velocity", "double"
        attribute "flight2-true_track", "double"
        attribute "flight2-vertical_rate", "double"
        
        attribute "flight3", "string"
        attribute "flight3-icao24", "string"
        attribute "flight3-callsign", "string"
        attribute "flight3-origincountry", "string"
        attribute "flight3-longitude", "double"
        attribute "flight3-latitude", "double"
        attribute "flight3-baro_altitude", "double"
        attribute "flight3-velocity", "double"
        attribute "flight3-true_track", "double"
        attribute "flight3-vertical_rate", "double"
        
        attribute "flight4", "string"
        attribute "flight4-icao24", "string"
        attribute "flight4-callsign", "string"
        attribute "flight4-origincountry", "string"
        attribute "flight4-longitude", "double"
        attribute "flight4-latitude", "double"
        attribute "flight4-baro_altitude", "double"
        attribute "flight4-velocity", "double"
        attribute "flight4-true_track", "double"
        attribute "flight4-vertical_rate", "double"
        
        attribute "flight5", "string"
        attribute "flight5-icao24", "string"
        attribute "flight5-callsign", "string"
        attribute "flight5-origincountry", "string"
        attribute "flight5-longitude", "double"
        attribute "flight5-latitude", "double"
        attribute "flight5-baro_altitude", "double"
        attribute "flight5-velocity", "double"
        attribute "flight5-true_track", "double"
        attribute "flight5-vertical_rate", "double"
        
        attribute "message1", "string"
        attribute "message2", "string"
    }

    preferences     {
        input "pollEvery", "enum", title:"Polling time", required:true, defaultValue:30, options:[0:"Manual", 10:"10 seconds", 20:"20 seconds", 30:"30 seconds", 60:"60 seconds"]
        input "APIusername", "text", title:"API Username (not mandatory)", required:false
        input "APIpassword", "password", title:"API Password (not mandatory)", required:false
        input "LaMin", "double", title:"Lower bound for the latitude in decimal degrees", required:true
        input "LaMax", "double", title:"Upper bound for the latitude in decimal degrees", required:true
        input "LoMin", "double", title:"Lower bound for the longitude in decimal degrees", required:true
        input "LoMax", "double", title:"Upper bound for the longitude in decimal degrees", required:true
        input "FlightFormat", "text", title:"Flight text format", required:true, defaultValue:"[callsign] - [baro_altitude] m - [velocity] kph - direction [true_track]°"
        input "Message1BeginWith", "text", title:"Message1 begin with", required:false, defaultValue:"A plane is coming above your head: "
        input "Message1Format", "text", title:"Message1 text format", required:true, defaultValue:"[callsign] flying at [baro_altitude] meters with a cruise speed of [velocity] kph with direction [true_track] degrees"
        input "Message1Separator", "text", title:"Message1 separator characters", required:false, defaultValue:","
        input "Message2BeginWith", "text", title:"Message2 begin with", required:false, defaultValue:""
        input "Message2Format", "text", title:"Message2 text format", required:true, defaultValue:"[callsign] - [baro_altitude] m - [velocity] kph - direction [true_track]°"
        input "Message2Separator", "text", title:"Message2 separator characters", required:false, defaultValue:" | "
        input name: "roundNumbers", type: "bool", title: "Round numbers to integers", defaultValue:true
        input name: "infoLogging", type: "bool", title: "Enable info message logging", defaultValue:false
 		input name: "debugLogging", type: "bool", title: "Enable debug message logging", defaultValue:false
    }

}

def updated()   {
	unschedule()
    poll()
    displayDebugLog("Scheduling poll every ${pollEvery} seconds")
    if(pollEvery != "0") {
        schedule("0/${pollEvery} * * * * ? *", poll)
    }
}

def poll()      {
    displayDebugLog("OpenSky: Executing 'poll()'")

    def obs = getOpenSkyAPI("states/all?lamin=${LaMin}&lomin=${LoMin}&lamax=${LaMax}&lomax=${LoMax}")
    if (!obs)   {
        log.warn "No response from OpenSky API"
        return
    }
    
    if (!obs.states) {
        displayInfoLog("No flights found in your area")
        if(device.currentState("switch").getValue() != "off") {
            off()
        }
        return
    }
    
    on()
    displayDebugLog("${obs.states.size()} flights found in your area")
    sendEvent(name: "rawresult", value: obs)

    def Message1 = Message1BeginWith ?: ""
    def Message2 = Message2BeginWith ?: ""
    def flightNr = 0
    def flights = obs.states;
    
    for (flight in flights) {
        flightNr += 1
        displayDebugLog("Flight nr. ${flightNr} => ${flight}")
        if (flightNr != 1) {
            Message1 += Message1Separator ?: ""
            Message2 += Message2Separator ?: ""
        }
        
        //convert mps to kph
        flight[9] = flight[9] * 3.6
        
        if(roundNumbers) {
            flight[7] = Math.round(flight[7])    //baro_altitude
            flight[9] = Math.round(flight[9])    //velocity
            flight[10] = Math.round(flight[10])  //true_track
            flight[11] = Math.round(flight[11])  //vertical_rate
        }
        
        sendEvent(name: "flight${flightNr}-icao24", value: flight[0])
        sendEvent(name: "flight${flightNr}-callsign", value: flight[1])
        sendEvent(name: "flight${flightNr}-origincountry", value: flight[2])
        sendEvent(name: "flight${flightNr}-longitude", value: flight[5])
        sendEvent(name: "flight${flightNr}-latitude", value: flight[6])
        sendEvent(name: "flight${flightNr}-baro_altitude", value: flight[7])
        sendEvent(name: "flight${flightNr}-velocity", value: flight[9])
        sendEvent(name: "flight${flightNr}-true_track", value: flight[10])
        sendEvent(name: "flight${flightNr}-vertical_rate", value: flight[11])
        sendEvent(name: "flight${flightNr}", value: formatText(FlightFormat, flight))
        
        Message1 += formatText(Message1Format, flight)
        Message2 += formatText(Message2Format, flight)
    }
    
    flightNr += 1
    for (i = flightNr; i <= 5; i++) {
        clearFlight(i)
    }
    
    sendEvent(name: "message1", value: Message1)
    sendEvent(name: "message2", value: Message2)
    
    return
}

def formatText(Text, f) {
    displayDebugLog("Formatting text=> '${Text}' | Flight => ${f}")
    Text = Text.replace("[icao24]", f[0]).replace("[callsign]", f[1]).replace("[origincountry]", f[2]).replace("[longitude]", f[5].toString()).replace("[latitude]", f[6].toString())
    Text = Text.replace("[baro_altitude]", f[7].toString()).replace("[velocity]", f[9].toString()).replace("[true_track]", f[10].toString()).replace("[vertical_rate]", f[11].toString())
    return Text
}

private clearFlight(flightNr) {
    if(device.currentState("flight${flightNr}").getValue() != "empty") {
        displayDebugLog("Clearing flight${flightNr}")
        sendEvent(name: "flight${flightNr}-icao24", value: "empty")
        sendEvent(name: "flight${flightNr}-callsign", value: "empty")
        sendEvent(name: "flight${flightNr}-origincountry", value: "empty")
        sendEvent(name: "flight${flightNr}-longitude", value: 0)
        sendEvent(name: "flight${flightNr}-latitude", value: 0)
        sendEvent(name: "flight${flightNr}-baro_altitude", value: 0)
        sendEvent(name: "flight${flightNr}-velocity", value: 0)
        sendEvent(name: "flight${flightNr}-true_track", value: 0)
        sendEvent(name: "flight${flightNr}-vertical_rate", value: 0)
        sendEvent(name: "flight${flightNr}", value: "empty")
    }
    else
    {
        displayDebugLog("flight${flightNr} already cleared!")
    }
}

def off() {
    displayDebugLog("OpenSky switch off")
    sendEvent(name: "switch", value: "off")
    sendEvent(name: "rawresult", value: "empty")
    sendEvent(name: "message1", value: "empty")
    sendEvent(name: "message2", value: "empty")
    for (i = 1; i <= 5; i++) {
        clearFlight(i)
    }
}
def on() {
    displayDebugLog("OpenSky switch on")
    sendEvent(name: "switch", value: "on")
}

def refresh() {
    poll()
}

def configure() {
    poll()
}

private getOpenSkyAPI(Parameters)   {
    def obs = [:]
    def url = APIusername == "" ? "https://opensky-network.org/api/" : "https://${APIusername}:${APIpassword}@opensky-network.org/api/"
    url = url + Parameters
    def params = [ uri: url ]
    try {
        httpGet(params)		{ resp ->
            if (resp?.data)
                obs << resp.data;
            else
                log.error "HTTPS call for OpenSky API did not return data: $resp";
        }
    } catch (e) {
        log.error "HTTPS call for OpenSky API: $e"
    }
    return obs
}

private def displayDebugLog(message) {
	if (debugLogging) log.debug "${device.displayName}: ${message}"
}

private def displayInfoLog(message) {
	if (infoLogging || state.prefsSetCount != 1)
		log.info "${device.displayName}: ${message}"
}