/***********************************************************************************************************************
 * Import URL: https://raw.githubusercontent.com/HubitatCommunity/wx-ApiXU/master/wx-ApiXU-Driver.groovy
 *  Copyright 2018 CSteele
 *
 *   CLONED from Bangali's ApiXU Weather Driver (v5.4.1)
 *
 ***********************************************************************************************************************/

public static String version()      {  return "v1.4.5"  }

/***********************************************************************************************************************
 *
 * Version: 1.4.5
 *                Improved updateCheck() with Switch/Case.
 *
 * Version: 1.4.4
 *			Increased Lux 'slices of a day' to include the next day
 *                Increased pollSunRiseSet to every 8 hours (3 times a day)
 *                Used "cityName" override option everywhere obs.location.name is used.
 *
 * Version: 1.4.3
 *                Change "float" values to BigDecimal or Integer.
 *			'Name' and 'City' are duplicates, but made them display the same text.
 *
 * Version: 1.4.2
 *                Skip forecastPrecip and mytile data calculations if the user hasn't enabled them.
 *
 * Version: 1.4.1
 *                Corrected typo for forecastIcon (in getWUIconName)
 *
 * Version: 1.4.0
 *                Moved the schedule() statements to initialize() so they run on reboot too.
 *                Distributed forecastPrecip() into sunRiseSetHandler, for the once a day portion; 
 *                 and into calcTime and doPoll for the display of data.
 *                Reworked updateLux() to use a schedule() vs chained runIn for robustness.
 *                Moved sunRiseSet map from 'state' to 'data' storage to declutter State Variables.
 *                Removed display: true from all sendEvent lines. Hubitat doesn't use it.
 *
 * Version: 1.3.1
 *                Corrected typos on twilight (astro vs civil)
 *
 * Version: 1.3.0
 *                Added attribute betwixt for Dashboard.
 *                Rewrote Lux calculation using Milliseconds vs Date Object
 *                 to be half the number of conversions.
 *
 * Version: 1.2.1
 *                Made repeating updateLux() run slower at night with lowLuxEvery.
 *                converted Cobra's updateCheck to Async.
 *
 * Version: 1.2.0 
 *                Update Attributes to send correct type (Number, String) 
 *                 - potential to break user's automation.
 *
 * Version: 1.1.9
 *                Update Attributes for the defined Capabilities (no longer selectable).
 *
 * Version: 1.1.8
 * Version: 1.1.7
 *                Correction for "java.lang.ClassCastException" found by halfrican.ak.
 *
 * Version: 1.1.6
 *                Semaphore protecting sunrise/set poll if Apixu poll is incomplete.
 *                Reworked log.warn messages for uniformity.
 *                Merged Latitude/Longitude into a group selector.
 *
 * Version: 1.1.5
 *                Merged imgNames & conditionFactor Maps into: imgCondMap
 * 
 * Version: 1.1.4
 *                rewrote handler for sunrise-sunset.org to fill state.sunRiseSet, which gets used 
 *                 throughout the day. 
 *                Added "wipe" commands to ease upgrading.
 *
 * Version: 1.1.3   Thanks @ codahq
 *                removed the child devices because they aren't needed anymore. 
 *                changed precipitation map variables from "in" (reserved groovy word) to "inches".
 *                put log.info behind a preference 
 *
 * Version: 1.1.2
 *                prevent calculating sunrise, sunset, twilight, noon, etc. a hundred times a day.
 *                added 1 and 3 hour options on Poll() - allowing RM for poll-on-demand.
 *                converted SunriseAndSet to asynchttp call
 *
 * Version: 1.1.1
 *                removed 'configure' as a command, refresh & poll are adequate.
 *                reorganized attributes into relationship groups with a single selector.
 * 
 * Version: 1.0.0
 *                renamed wx-ApiXU-Driver.
 *                reworked Poll and UpdateLux to use common code.
 *                reworked metadata to build the attributes needed.
 *                converted Poll to asynchttp call.
 *                duplicated attributes for OpenWX compatibility with Dashboard Weather Template.
 *
/***********************************************************************************************************************



/***********************************************************************************************************************
 *  Copyright 2018 bangali
 *
 *  Contributors:
 *       https://github.com/jebbett      code for new weather icons based on weather condition data
 *       https://www.deviantart.com/vclouds/art/VClouds-Weather-Icons-179152045     new weather icons courtesy of VClouds
 *	  https://github.com/arnbme		code for mytile
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  ApiXU Weather Driver
 *
 *  Author: bangali
 *
 *  Date: 2018-05-27
 *
 *  attribution: weather data courtesy: https://www.apixu.com/
 *
 *  attribution: sunrise and sunset courtesy: https://sunrise-sunset.org/
 *
 * for use with HUBITAT so no tiles
 *
 * features:
 * - supports global weather data with free api key from apixu.com
 * - provides calculated illuminance data based on time of day and weather condition code.
 * - no local server setup needed
 * - no personal weather station needed
 *
 *
 * record of Bangali's version history prior to the Clone moved to the end of file.
*/

import groovy.transform.Field

metadata    {
 	definition (name: "wx-ApiXU-Driver", namespace: "csteele", author: "bangali, csteele", importUrl: "https://raw.githubusercontent.com/HubitatCommunity/wx-ApiXU/master/wx-ApiXU-Driver.groovy")  {
 		capability "Actuator"
 		capability "Sensor"
 		capability "Polling"
 		capability "Illuminance Measurement"
 		capability "Temperature Measurement"
 		capability "Relative Humidity Measurement"
 		capability "Pressure Measurement"
 		capability "Ultraviolet Index"
	
		attributesMap.each
		{
			k, v -> if (v.typeof) attribute "${k}", "${v.typeof}"
		}

	// some attributes are 'doubled' due to spelling differences, such as wind_dir & windDirection
	//  the additional doubled attributes are added here:
		attribute "windDirection", "number"		// open_weatherPublish  related
		attribute "windSpeed", "number"		// open_weatherPublish    |
		attribute "weatherIcons", "string"		// open_weatherPublish    |

	// some attributes are in a 'group' of similar, under a single selector
		attribute "precipDayMinus2", "number"	// precipExtended related
		attribute "precipDayMinus1", "number"	// precipExtended   |
		attribute "precipDay0",      "number"	// precipExtended   |
		attribute "precipDayPlus1",  "number"	// precipExtended   |
		attribute "precipDayPlus2",  "number"	// precipExtended   |
		
		attribute "local_sunrise",   "string"	// localSunrisePublish related
		attribute "local_sunset",    "string"	// localSunrisePublish   |
		attribute "localSunrise",    "string"	// localSunrisePublish   |
		attribute "localSunset",     "string"	// localSunrisePublish   |

		attribute "lat",             "number"	// latPublish related
		attribute "lon",             "number"	// latPublish   |

		attribute "temperatureHighDayPlus1", "number"  // tempHiLowPublish related
		attribute "temperatureLowDayPlus1",  "number"  // tempHiLowPublish   |

		attribute "betwixt",       "string"
	
		command "refresh"
//		command "updateLux"			// **---** delete for Release
//		command "pollSunRiseSet"		// **---** delete for Release
//		command "updateCheck"			// **---** delete for Release
 	}

	def settingDescr = settingEnable ? "<br><i>Hide many of the Preferences to reduce the clutter, if needed, by turning OFF this toggle.</i><br>" : "<br><i>Many Preferences are available to you, if needed, by turning ON this toggle.</i><br>"

	preferences {
		input "zipCode",       "text", title:"Zip code or city name or latitude,longitude?", required:true
		input "apixuKey",      "text", title:"ApiXU key?", required:true, defaultValue:null
		input "cityName",      "text", title: "Override default city name?", required:false, defaultValue:null
		input "isFahrenheit",  "bool", title:"Use Imperial units?", required:true, defaultValue:true
		input "pollEvery",     "enum", title:"Poll ApiXU how frequently?\nrecommended setting 30 minutes.\nilluminance updating defaults to every 5 minutes.", required:false, defaultValue: 30, options:[5:"5 minutes",10:"10 minutes",15:"15 minutes",30:"30 minutes",60:"1 hour",180:"3 hours"]
		input "luxEvery",      "enum", title:"Publish illuminance how frequently?", required:false, defaultValue: 5, options:[5:"5 minutes",10:"10 minutes",15:"15 minutes",30:"30 minutes"]
		input "lowLuxEvery",   "enum", title:"When illuminance is minimum, how frequently is it published?", required:false, defaultValue: 999, options:[999: "don't change", 5:"5 minutes",10:"10 minutes",15:"15 minutes",30:"30 minutes"]
		input "settingEnable", "bool", title: "<b>Display All Preferences</b>", description: "$settingDescr", defaultValue: true
		input "debugOutput",   "bool", title: "<b>Enable debug logging?</b>", defaultValue: true
		input "descTextEnable","bool", title: "<b>Enable descriptionText logging?</b>", defaultValue: true
	// build a Selector for each mapped Attribute or group of attributes
		attributesMap.each
		{
			keyname, attribute ->
			if (settingEnable) input "${keyname}Publish", "bool", title: "${attribute.title}", required: true, defaultValue: "${attribute.default}", description: "<br>${attribute.descr}<br>"
		}
    }
}


// helpers
def refresh()	{ poll() }


/*
	updated

	Purpose: runs when save is clicked in the preferences section

*/
def updated()   {
	initialize()  // includes an unsubscribe()
	state.clockSeconds = true
	if (debugOutput) runIn(1800,logsOff)        // disable debug logs after 30 min
	if (settingEnable) runIn(2100,settingsOff)  // "roll up" (hide) the condition selectors after 35 min
	if (pollEvery == "180") { "runEvery3Hours"(poll) }
	   else if (pollEvery == "60") { "runEvery1Hour"(poll) }
	   else { "runEvery${pollEvery}Minutes"(poll) }
	if (dashClock)  updateClock();
	poll()
	if (descTextEnable) log.info "Updated with settings: ${settings}, $state.sunRiseSet"
}


/*
	doPoll

	Purpose: build out the Attributes and add to Hub DB if selected

*/
def doPoll(obs) {
	if (descTextEnable) log.info "wx-ApiXU poll for: $zipCode"
	calcTime(obs)		// calculate all the time variables
	sendEvent(name: "lastXUupdate", value: now)

	// Update Attributes for the defined Capabilities
	sendEvent(name: "humidity", value: obs.current.humidity.toInteger(), unit: "%")
	sendEvent(name: "pressure", value: (isFahrenheit ? obs.current.pressure_in.toInteger() : obs.current.pressure_mb.toInteger()), unit: "${(isFahrenheit ? 'IN' : 'MBAR')}")
	sendEvent(name: "temperature", value: (isFahrenheit ? obs.current.temp_f.toBigDecimal() : obs.current.temp_c.toBigDecimal()), unit: "${(isFahrenheit ? 'F' : 'C')}")
	sendEvent(name: "ultravioletIndex", value: obs.current.uv.toInteger())

	if (localSunrisePublish) {
		if (debugOutput) log.debug "localSunrise Group"
		sendEvent(name: "local_sunrise", value: state.localSunrise, descriptionText: "Sunrise today is at $state.localSunrise")
		sendEvent(name: "local_sunset", value: state.localSunset, descriptionText: "Sunset today at is $state.localSunset")
		sendEvent(name: "localSunrise", value: state.localSunrise)
		sendEvent(name: "localSunset", value: state.localSunset)
	}
	if (open_weatherPublish) {
		if (debugOutput) log.debug "open_weather Group"
		sendEvent(name: "weatherIcons", value: getOWIconName(obs.current.condition.code, obs.current.is_day))
		sendEvent(name: "windSpeed", value: (isFahrenheit ? obs.current.wind_mph.toInteger() : obs.current.wind_kph.toInteger()))
		sendEvent(name: "windDirection", value: obs.current.wind_degree.toInteger())
	}
	if (tempHiLowPublish) {
		if (debugOutput) log.debug "temp+1 Hi/Lo Group"
		sendEvent(name: "temperatureHighDayPlus1", value: (isFahrenheit ? obs.forecast.forecastday[0].day.maxtemp_f.toInteger() :
	                        obs.forecast.forecastday[0].day.maxtemp_c.toInteger()), unit: "${(isFahrenheit ? 'F' : 'C')}")
		sendEvent(name: "temperatureLowDayPlus1", value: (isFahrenheit ? obs.forecast.forecastday[0].day.mintemp_f.toInteger() :
                            obs.forecast.forecastday[0].day.mintemp_c.toInteger()), unit: "${(isFahrenheit ? 'F' : 'C')}")
	}
	if (latPublish) { // latitude and longitude group
		if (debugOutput) log.debug "Lat/Long Group"
		sendEvent(name: "lat", value: obs.location.lat.toBigDecimal())
		sendEvent(name: "lon", value: obs.location.lon.toBigDecimal())
	}

	sendEventPublish(name: "city", value: (cityName ?: (obs.location.name ?: "n/a")))
	sendEventPublish(name: "cloud", value: obs.current.cloud.toInteger(), unit: "%")
	sendEventPublish(name: "condition_code", value: obs.current.condition.code.toInteger())
	sendEventPublish(name: "condition_codeDayPlus1", value: obs.forecast.forecastday[0].day.condition.code.toInteger())
	sendEventPublish(name: "condition_icon_only", value: obs.current.condition.icon.split("/")[-1])
	sendEventPublish(name: "condition_icon_url", value: 'https:' + obs.current.condition.icon)
	sendEventPublish(name: "condition_icon", value: '<img src=https:' + obs.current.condition.icon + '>')
	sendEventPublish(name: "condition_text", value: obs.current.condition.text)
	sendEventPublish(name: "country", value: (obs.location.country ?: " "))
	sendEventPublish(name: "feelsLike", value: (isFahrenheit ? obs.current.feelslike_f.toBigDecimal() : obs.current.feelslike_c.toBigDecimal()), unit: "${(isFahrenheit ? 'F' : 'C')}")
	sendEventPublish(name: "forecastIcon", value: getWUIconName(obs.current.condition.code, obs.current.is_day))
	sendEventPublish(name: "is_day", value: obs.current.is_day.toInteger())
	sendEventPublish(name: "last_updated_epoch", value: obs.current.last_updated_epoch.toInteger())
	sendEventPublish(name: "last_updated", value: obs.current.last_updated)
	sendEventPublish(name: "local_date", value: state.thisDate)
	sendEventPublish(name: "local_time", value: state.thisTime)
	sendEventPublish(name: "localtime_epoch", value: obs.location.localtime_epoch.toInteger())
	sendEventPublish(name: "location", value: ((obs.location.name ?: "n/a") + ', ' + (obs.location.region ?: " ")))
	sendEventPublish(name: "name", value: (cityName ?: (obs.location.name ?: "n/a")))
	sendEventPublish(name: "percentPrecip", value: (isFahrenheit ? obs.current.precip_in.toBigDecimal() : obs.current.precip_mm.toBigDecimal()), unit: "${(isFahrenheit ? 'IN' : 'MM')}")
	sendEventPublish(name: "region", value: (obs.location.region ?: " "))
	sendEventPublish(name: "twilight_begin", value: state.twiBegin, descriptionText: "Twilight begins today at $state.twiBegin")
	sendEventPublish(name: "twilight_end", value: state.twiEnd, descriptionText: "Twilight ends today at $state.twiEnd")	
	sendEventPublish(name: "tz_id", value: obs.location.tz_id)
	sendEventPublish(name: "visual", value: '<img src=' + imgName + '>')
	sendEventPublish(name: "visualDayPlus1", value: '<img src=' + imgNamePlus1 + '>')
	sendEventPublish(name: "visualDayPlus1WithText", value: '<img src=' + imgNamePlus1 + '><br>' + obs.forecast.forecastday[0].day.condition.text)
	sendEventPublish(name: "visualWithText", value: '<img src=' + imgName + '><br>' + obs.current.condition.text)
	sendEventPublish(name: "weather", value: obs.current.condition.text)
	sendEventPublish(name: "wind_degree", value: obs.current.wind_degree.toInteger(), unit: "DEGREE")
	sendEventPublish(name: "wind_dir", value: obs.current.wind_dir)
	sendEventPublish(name: "wind_mytile", value: wind_mytile)
	sendEventPublish(name: "wind", value: (isFahrenheit ? obs.current.wind_mph.toInteger() : obs.current.wind_kph.toInteger()), unit: "${(isFahrenheit ? 'MPH' : 'KPH')}")

	if (isFahrenheit)	{
		sendEventPublish(name: "wind_mph", value: obs.current.wind_mph.toBigDecimal(), unit: "MPH")
		sendEventPublish(name: "precip_in", value: obs.current.precip_in.toBigDecimal(), unit: "IN")
		sendEventPublish(name: "feelslike_f", value: obs.current.feelslike_f.toBigDecimal(), unit: "F")
		sendEventPublish(name: "vis_miles", value: obs.current.vis_miles.toBigDecimal(), unit: "MILES")
	}
	else {
		sendEventPublish(name: "wind_kph", value: obs.current.wind_kph.toBigDecimal(), unit: "KPH")
		sendEventPublish(name: "wind_mps", value: ((obs.current.wind_kph / 3.6f).round(1).toBigDecimal()), unit: "MPS")
		sendEventPublish(name: "precip_mm", value: obs.current.precip_mm.toBigDecimal(), unit: "MM")
		sendEventPublish(name: "feelsLike_c", value: obs.current.feelslike_c.toBigDecimal(), unit: "C")
		sendEventPublish(name: "vis_km", value: obs.current.vis_km.toInteger(), unit: "KM")
	}
	if (precipExtendedPublish) {
		if (debugOutput) log.debug "Extended Precip Group"
		sendEvent(name: "precipDayMinus2", value: (isFahrenheit ? state.forecastPrecip.precipDayMinus2.inch.toBigDecimal() : state.forecastPrecip.precipDayMinus2.mm.toBigDecimal()), unit: "${(isFahrenheit ? 'IN' : 'MM')}")
		sendEvent(name: "precipDayMinus1", value: (isFahrenheit ? state.forecastPrecip.precipDayMinus1.inch.toBigDecimal() : state.forecastPrecip.precipDayMinus1.mm.toBigDecimal()), unit: "${(isFahrenheit ? 'IN' : 'MM')}")
		sendEvent(name: "precipDay0", value: (isFahrenheit ? state.forecastPrecip.precipDay0.inch.toBigDecimal() : state.forecastPrecip.precipDay0.mm.toBigDecimal()), unit: "${(isFahrenheit ? 'IN' : 'MM')}")
		sendEvent(name: "precipDayPlus1", value: (isFahrenheit ? state.forecastPrecip.precipDayPlus1.inch.toBigDecimal() : state.forecastPrecip.precipDayPlus1.mm.toBigDecimal()), unit: "${(isFahrenheit ? 'IN' : 'MM')}")
		sendEvent(name: "precipDayPlus2", value: (isFahrenheit ? state.forecastPrecip.precipDayPlus2.inch.toBigDecimal() : state.forecastPrecip.precipDayPlus2.mm.toBigDecimal()), unit: "${(isFahrenheit ? 'IN' : 'MM')}")
	}

	sendEventPublish(name: "mytile", value: mytext)

	return
}


/*
	poll

	Purpose: initiate the asynchtttpGet() call each poll cycle.

	Notes: very, very simple, all the action is in the handler.
*/
def poll() {
	def requestParams = [ uri: "https://api.apixu.com/v1/forecast.json?key=$apixuKey&q=$zipCode&days=3" ]
	// log.debug "Poll ApiXU: $requestParams"
	asynchttpGet("pollHandler", requestParams)
}

/*
	pollHandler

	Purpose: the APIXU website response

	Notes: a good response will be processed by doPoll()
*/
def pollHandler(resp, data) {
	if(resp.getStatus() == 200 || resp.getStatus() == 207) {
		obs = parseJson(resp.data)
    //    	if (debugOutput) log.debug "wx-ApiXU returned: $obs"
		doPoll(obs)		// parse the data returned by ApiXU
	} else {
		log.error "wx-ApiXU weather api did not return data"
	}
}



/*
	Sun Rise Set

	Purpose: Run just after midnight to establish the Astronomical times needed all day long when polling APIXU.

*/
def pollSunRiseSet() {
	if (state.loc_lat) {
		def requestParams = [ uri: "https://api.sunrise-sunset.org/json?lat=$state.loc_lat&lng=$state.loc_lon&formatted=0" ]
		if (state.thisDate) {requestParams = [ uri: "https://api.sunrise-sunset.org/json?lat=$state.loc_lat&lng=$state.loc_lon&formatted=0&date=$state.thisDate" ]}
		if (descTextEnable) log.info "SunRiseSet poll for $state.loc_lat  $state.loc_lon" //$requestParams"
		asynchttpGet("sunRiseSetHandler", requestParams)
	} else {
		state.sunRiseSet.init = false
		log.warn "wx-ApiXU no sunrise-sunset without Lat/Long."
	}
}


def sunRiseSetHandler(resp, data) {
	if(resp.getStatus() == 200 || resp.getStatus() == 207) {
		sunRiseSet = resp.getJson().results
		updateDataValue("sunRiseSet", resp.data)
		state.sunRiseSet.init = true
		state.localSunrise = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXXX", sunRiseSet.sunrise).format("HH:mm")
		state.localSunset  = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXXX", sunRiseSet.sunset).format("HH:mm")
		state.twiBegin     = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXXX", sunRiseSet.civil_twilight_begin).format("HH:mm")
		state.twiEnd       = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXXX", sunRiseSet.civil_twilight_end).format("HH:mm")

		state.forecastPrecip.precipDayMinus2 = state?.forecastPrecip?.precipDayMinus1
		state.forecastPrecip.precipDayMinus1 = state?.forecastPrecip?.precipDay0
	} else {
		log.warn "wx-ApiXU sunrise-sunset api did not return data"
		state.sunRiseSet.init = false
	}
}

private isConfigured() {
    getDataValue("configured") == "true"
}


/*
	updateLux

	Purpose: calculate Lux / Illuminance / Illuminated offset by time of day
	
	Notes: minimum Lux is a value of 5 after dark.
*/
def updateLux() {
	if (state?.sunRiseSet?.init) { 
		if (descTextEnable) log.info "wx-ApiXU lux calc for: $zipCode" // ", $state.loc_lat, $state.localSunset"	
		def (lux, bwn) = estimateLux(state.condition_code, state.cloud)
		state.luxNext = (lux > 6) ? true : false 
		state.luxNext ? { schedule("0 0/${luxEvery} * * * ?", updateLux) } : {if (lowLuxEvery != 999) { schedule("0 0/${lowLuxEvery} * * * ?", updateLux) } }
        	//if (debugOutput) log.debug "Lux: $lux, $state.luxNext, $bwn"
      
		sendEvent(name: "illuminance", value: lux.toInteger(), unit: "lux")
		sendEventPublish(name: "illuminated", value: String.format("%,d lux", lux))
		sendEventPublish(name: "betwixt", value: bwn)
	} else {
		if (descTextEnable) log.warn "no wx-ApiXU lux without sunRiseSet value."
		runIn(2, pollSunRiseSet) 
	}
}


def estimateLux(condition_code, cloud)     {	
	def lux = 0l
	def aFCC = true
	def l
	def bwn

	def sunRiseSet           = parseJson(getDataValue("sunRiseSet")).results
	def tZ                   = TimeZone.getTimeZone(state.tz_id)
	def lT                   = new Date().format("yyyy-MM-dd'T'HH:mm:ssXXX", tZ)
	def localeMillis         = getEpoch(lT)
	def twilight_beginMillis = getEpoch(sunRiseSet.civil_twilight_begin)
	def sunriseTimeMillis    = getEpoch(sunRiseSet.sunrise)
	def noonTimeMillis       = getEpoch(sunRiseSet.solar_noon)
	def sunsetTimeMillis     = getEpoch(sunRiseSet.sunset)
	def twilight_endMillis   = getEpoch(sunRiseSet.civil_twilight_end)
	def twiStartNextMillis   = twilight_beginMillis + 86400000 // = 24*60*60*1000 --> one day in milliseconds
	def sunriseNextMillis    = sunriseTimeMillis + 86400000 
	def noonTimeNextMillis   = noonTimeMillis + 86400000 
	def sunsetNextMillis     = sunsetTimeMillis + 86400000
	def twiEndNextMillis     = twilight_endMillis + 86400000

	switch(localeMillis) { 
		case { it < twilight_beginMillis}: 
			bwn = "Fully Night Time" 
			lux = 5l
			break
		case { it < sunriseTimeMillis}:
			bwn = "between twilight and sunrise" 
			l = (((localeMillis - twilight_beginMillis) * 50f) / (sunriseTimeMillis - twilight_beginMillis))
			lux = (l < 10f ? 10l : l.trunc(0) as long)
			break
		case { it < noonTimeMillis}:
			bwn = "between sunrise and noon" 
			l = (((localeMillis - sunriseTimeMillis) * 10000f) / (noonTimeMillis - sunriseTimeMillis))
			lux = (l < 50f ? 50l : l.trunc(0) as long)
			break
		case { it < sunsetTimeMillis}:
			bwn = "between noon and sunset" 
			l = (((sunsetTimeMillis - localeMillis) * 10000f) / (sunsetTimeMillis - noonTimeMillis))
			lux = (l < 50f ? 50l : l.trunc(0) as long)
			break
		case { it < twilight_endMillis}:
			bwn = "between sunset and twilight" 
			l = (((twilight_endMillis - localeMillis) * 50f) / (twilight_endMillis - sunsetTimeMillis))
			lux = (l < 10f ? 10l : l.trunc(0) as long)
			break
		case { it < twiStartNextMillis}:
			bwn = "Fully Night Time" 
			lux = 5l
			break
		case { it < sunriseNextMillis}:
			bwn = "between twilight and sunrise" 
			l = (((localeMillis - twiStartNextMillis) * 50f) / (sunriseNextMillis - twiStartNextMillis))
			lux = (l < 10f ? 10l : l.trunc(0) as long)
			break
		case { it < noonTimeNextMillis}:
			bwn = "between sunrise and noon" 
			l = (((localeMillis - sunriseNextMillis) * 10000f) / (noonTimeNextMillis - sunriseNextMillis))
			lux = (l < 50f ? 50l : l.trunc(0) as long)
			break
		case { it < sunsetNextMillis}:
			bwn = "between noon and sunset" 
			l = (((sunsetNextMillis - localeMillis) * 10000f) / (sunsetNextMillis - noonTimeNextMillis))
			lux = (l < 50f ? 50l : l.trunc(0) as long)
			break
		case { it < twiEndNextMillis}:
			bwn = "between sunset and twilight" 
			l = (((twiEndNextMillis - localeMillis) * 50f) / (twiEndNextMillis - sunsetNextMillis))
			lux = (l < 10f ? 10l : l.trunc(0) as long)
			break
		default:
			bwn = "Fully Night Time" 
			lux = 5l
			aFCC = false
			break
	}

	def cC = condition_code.toInteger()
	def cCF
	if (aFCC)
	    if (imgCondMap[cC].condCode)
	    {
	      cCF = imgCondMap[cC].condCode[1]
	    }
	    else
	    {
		// factor in cloud cover if available
		cCF = state.apixu.init ? ((100 - (cloud.toInteger() / 3d)) / 100) : 0.998d
   		// log.debug "zzz: $l $cloud $cCF, $lux, $bwn, $state.apixu.init"
	    }
	else
	{
	    cCF = 1.0
	}
	
	lux = (lux * cCF) as long
	if (debugOutput) log.debug "condition: $cC | condition factor: $cCF | lux: $lux"
	sendEventPublish(name: "cCF", value: cCF)
	
	return [lux, bwn]
}


/*
	calcTime

	Purpose: calculate display data from each observation (aka: obs/wxData).
	
*/
def calcTime(wxData) {
	state.condition_code = wxData.current.condition.code
	state.cloud = wxData.current.cloud
	// with sun rise/set being async and once a day, 'obs' (wxdata) won't be available
	// to that method. Lat + long needs to be saved.
	state.loc_lat   = wxData.location.lat ?: location.latitude
	state.loc_lon   = wxData.location.lon ?: location.longitude
	state.tz_id     = wxData.location.tz_id ?: TimeZone.getDefault().getID()
	state.thisDate  = Date.parse("yyyy-MM-dd HH:mm", wxData.location.localtime).format("yyyy-MM-dd")
	state.thisTime  = Date.parse("yyyy-MM-dd HH:mm", wxData.location.localtime).format("HH:mm")
	
	imgName = getImgName(wxData.current.condition.code, wxData.current.is_day)
	imgNamePlus1 = getImgName(wxData.forecast.forecastday[0].day.condition.code, 1)
	wind_mytile=(isFahrenheit ? "${Math.round(wxData.current.wind_mph)}" + " mph " : "${Math.round(wxData.current.wind_kph)}" + " kph ")
	

	if (mytilePublish) { // don't bother setting these values if it's not enabled
		// build the myTile text
		mytext = (cityName ?: (wxData.location.name ?: "n/a")) + ', ' + (wxData.location.region ?: " ")
		mytext += '<br>' + (isFahrenheit ? "${Math.round(wxData.current.temp_f)}" + '&deg;F ' : wxData.current.temp_c + '&deg;C ') + wxData.current.humidity + '%'
		mytext += '<br>' + state?.localSunrise + ' <img style="height:2em" src=' + imgName + '> ' + state?.localSunset
		mytext += (wind_mytile == (isFahrenheit ? "0 mph " : "0 kph ") ? '<br> Wind is calm' : '<br>' + wxData.current.wind_dir + ' ' + wind_mytile)
		mytext += '<br>' + wxData.current.condition.text
		//if (debugOutput) log.debug "mytext: $mytext"
	}

	if (precipExtendedPublish) { // don't bother setting these values if it's not enabled
		state.forecastPrecip.precipDay0.mm = wxData.forecast.forecastday[0].day.totalprecip_mm
		state.forecastPrecip.precipDay0.inch = wxData.forecast.forecastday[0].day.totalprecip_in
		state.forecastPrecip.precipDayPlus1.mm = wxData.forecast.forecastday[1].day.totalprecip_mm
		state.forecastPrecip.precipDayPlus1.inch = wxData.forecast.forecastday[1].day.totalprecip_in
		state.forecastPrecip.precipDayPlus2.mm = wxData.forecast.forecastday[2].day.totalprecip_mm
		state.forecastPrecip.precipDayPlus2.inch = wxData.forecast.forecastday[2].day.totalprecip_in
	}
}


def logsOff(){
	log.warn "debug logging disabled..."
	device.updateSetting("debugOutput",[value:"false",type:"bool"])
}


def settingsOff(){
	log.warn "Settings disabled..."
	device.updateSetting("settingEnable",[value:"false",type:"bool"])
}



/*
	sendEventPublish

	Purpose: Attribute sent to DB if selected
	
*/
def sendEventPublish(evt)	{
	if (this[evt.name + "Publish"]) {
		sendEvent(name: evt.name, value: evt.value, descriptionText: evt.descriptionText, unit: evt.unit, displayed: evt.displayed);
		if (debugOutput) log.debug "$evt.name" //: $evt.name, $evt.value $evt.unit"
	}
}


/*
	getEpoch

	Purpose: take a Date object and return Milliseconds (Epoch)

	Notes:
*/
def getEpoch (aTime) {
	def tZZ = TimeZone.getTimeZone(state.tz_id)
	def localeTime = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXXX", aTime, tZZ)
	long localeMillis = localeTime.getTime()
	return (localeMillis)
}


/*
	updateClock

	Purpose: implements a blinking : in a dashboard clock
	
*/
def updateClock()       {
	runIn(2, updateClock)
	if (!state.tz_id)       return;
	def nowTime = new Date()
	sendEventPublish(name: "local_time", value: nowTime.format((state.clockSeconds ? "HH:mm" : "HH mm"), location.timeZone))
	def localDate = nowTime.format("yyyy-MM-dd", location.timeZone)
	if (localDate != state.localDate) {
		state.localDate = localDate
		sendEventPublish(name: "local_date", value: localDate)
	}
	state.clockSeconds = (state.clockSeconds ? false : true)
}


/*
	getWUIconName

	Purpose: get the WeatherUnderground image value.

*/
def getWUIconName(condition_code, is_day)     {
   def wuIcon = imgCondMap[condition_code].condCode[0] ? imgCondMap[condition_code].condCode[2] : ''
    if (is_day != 1 && wuIcon) wuIcon = 'nt_' + wuIcon;
    return wuIcon
}


/*
	getOWIconName

	Purpose: Hubitat's Weather template for Dashboard is OpenWeather icon friendly ONLY.

*/
def getOWIconName(condition_code, is_day)     {
    def wIcon = imgCondMap[condition_code].condCode[0] ? imgCondMap[condition_code].condCode[3] : ''
    return is_day ? wIcon + 'd' : wIcon + 'n'
}


/*
	getImgName

	Purpose: get our image id.

*/
def getImgName(wCode, is_day)       {
	def url = "https://raw.githubusercontent.com/csteele-PD/ApiXU/master/docs/weather/"
	def imgItem = isDay ? imgCondMap[wCode].imgNames.night_img : imgCondMap[wCode].imgNames.day_img
	// log.debug "getImgName: $wCode, $imgItem"
	return (url + imgItem)
}


@Field static imgCondMap = [
	1000: [imgNames: [day_img: '32.png', night_img: '31.png'], condCode: ['Sunny', 1, 'sunny', '01'] ],                                   	//  Sunny 
	1003: [imgNames: [day_img: '30.png', night_img: '29.png'], condCode: ['Partly cloudy', 0.8, 'partlycloudy', '03'] ],				//  Partly cloudy
	1006: [imgNames: [day_img: '28.png', night_img: '27.png'], condCode: ['Cloudy', 0.6, 'cloudy', '02'] ],                               	//  Cloudy   
	1009: [imgNames: [day_img: '26.png', night_img: '26.png'], condCode: ['Overcast', 0.5, 'cloudy', '13'] ],						//  Overcast
	1030: [imgNames: [day_img: '20.png', night_img: '20.png'], condCode: ['Mist', 0.5, 'fog', '13'] ],                                    	//  Mist   
	1063: [imgNames: [day_img: '39.png', night_img: '45.png'], condCode: ['Patchy rain possible', 0.8, 'chancerain', '04'] ],			//  Patchy rain possible
	1066: [imgNames: [day_img: '41.png', night_img: '46.png'], condCode: ['Patchy snow possible', 0.6, 'chancesnow', '13'] ],             	//  Patchy snow possible   
	1069: [imgNames: [day_img: '41.png', night_img: '46.png'], condCode: ['Patchy sleet possible', 0.6, 'chancesleet', '13'] ],			//  Patchy sleet possible
	1072: [imgNames: [day_img: '39.png', night_img: '45.png'], condCode: ['Patchy freezing drizzle possible', 0.4, 'chancesleet', '13'] ],	//  Patchy freezing drizzle possible   
	1087: [imgNames: [day_img: '38.png', night_img: '47.png'], condCode: ['Thundery outbreaks possible', 0.2, 'chancetstorms', '11'] ],	//  Thundery outbreaks possible
	1114: [imgNames: [day_img: '15.png', night_img: '15.png'], condCode: ['Blowing snow', 0.3, 'snow', '13'] ],                           	//  Blowing snow   
	1117: [imgNames: [day_img: '16.png', night_img: '16.png'], condCode: ['Blizzard', 0.1, 'snow', '13'] ],						//  Blizzard
	1135: [imgNames: [day_img: '21.png', night_img: '21.png'], condCode: ['Fog', 0.2, 'fog', '50'] ],                                     	//  Fog   
	1147: [imgNames: [day_img: '21.png', night_img: '21.png'], condCode: ['Freezing fog', 0.1, 'fog', '13'] ],						//  Freezing fog
	1150: [imgNames: [day_img: '39.png', night_img: '45.png'], condCode: ['Patchy light drizzle', 0.8, 'rain', '10'] ],                   	//  Patchy light drizzle   
	1153: [imgNames: [day_img: '11.png', night_img: '11.png'], condCode: ['Light drizzle', 0.7, 'rain', '09'] ],					//  Light drizzle
	1168: [imgNames: [day_img:  '8.png', night_img:  '8.png'], condCode: ['Freezing drizzle', 0.5, 'sleet', '13'] ],                      	//  Freezing drizzle   
	1171: [imgNames: [day_img: '10.png', night_img: '10.png'], condCode: ['Heavy freezing drizzle', 0.2, 'sleet', '13'] ],				//  Heavy freezing drizzle
	1180: [imgNames: [day_img: '39.png', night_img: '45.png'], condCode: ['Patchy light rain', 0.8, 'rain', '09'] ],                      	//  Patchy light rain   
	1183: [imgNames: [day_img: '11.png', night_img: '11.png'], condCode: ['Light rain', 0.7, 'rain', '09'] ],						//  Light rain
	1186: [imgNames: [day_img: '39.png', night_img: '45.png'], condCode: ['Moderate rain at times', 0.5, 'rain', '09'] ],                 	//  Moderate rain at times   
	1189: [imgNames: [day_img: '12.png', night_img: '12.png'], condCode: ['Moderate rain', 0.4, 'rain', '09'] ],					//  Moderate rain
	1192: [imgNames: [day_img: '39.png', night_img: '45.png'], condCode: ['Heavy rain at times', 0.3, 'rain', '09'] ],                    	//  Heavy rain at times   
	1195: [imgNames: [day_img: '12.png', night_img: '12.png'], condCode: ['Heavy rain', 0.2, 'rain', '09'] ],						//  Heavy rain
	1198: [imgNames: [day_img:  '8.png', night_img:  '8.png'], condCode: ['Light freezing rain', 0.7, 'sleet', '13'] ],                   	//  Light freezing rain   
	1201: [imgNames: [day_img: '10.png', night_img: '10.png'], condCode: ['Moderate or heavy freezing rain', 0.3, 'sleet', '13'] ],		//  Moderate or heavy freezing rain
	1204: [imgNames: [day_img:  '5.png', night_img:  '5.png'], condCode: ['Light sleet', 0.5, 'sleet', '13'] ],                           	//  Light sleet   
	1207: [imgNames: [day_img:  '6.png', night_img:  '6.png'], condCode: ['Moderate or heavy sleet', 0.3, 'sleet', '13'] ],			//  Moderate or heavy sleet
	1210: [imgNames: [day_img: '41.png', night_img: '41.png'], condCode: ['Patchy light snow', 0.8, 'flurries', '13'] ],                  	//  Patchy light snow   
	1213: [imgNames: [day_img: '18.png', night_img: '18.png'], condCode: ['Light snow', 0.7, 'snow', '13'] ],						//  Light snow
	1216: [imgNames: [day_img: '41.png', night_img: '41.png'], condCode: ['Patchy moderate snow', 0.6, 'snow', '13'] ],                   	//  Patchy moderate snow   
	1219: [imgNames: [day_img: '16.png', night_img: '16.png'], condCode: ['Moderate snow', 0.5, 'snow', '13'] ],					//  Moderate snow
	1222: [imgNames: [day_img: '41.png', night_img: '41.png'], condCode: ['Patchy heavy snow', 0.4, 'snow', '13'] ],                      	//  Patchy heavy snow   
	1225: [imgNames: [day_img: '16.png', night_img: '16.png'], condCode: ['Heavy snow', 0.3, 'snow', '13'] ],						//  Heavy snow
	1237: [imgNames: [day_img: '18.png', night_img: '18.png'], condCode: ['Ice pellets', 0.5, 'sleet', '13'] ],                           	//  Ice pellets   
	1240: [imgNames: [day_img: '11.png', night_img: '11.png'], condCode: ['Light rain shower', 0.8, 'rain', '10'] ],					//  Light rain shower
	1243: [imgNames: [day_img: '12.png', night_img: '12.png'], condCode: ['Moderate or heavy rain shower', 0.3, 'rain', '10'] ],          	//  Moderate or heavy rain shower   
	1246: [imgNames: [day_img: '12.png', night_img: '12.png'], condCode: ['Torrential rain shower', 0.1, 'rain', '10'] ],				//  Torrential rain shower
	1249: [imgNames: [day_img:  '5.png', night_img:  '5.png'], condCode: ['Light sleet showers', 0.7, 'sleet', '10'] ],                   	//  Light sleet showers   
	1252: [imgNames: [day_img:  '6.png', night_img:  '6.png'], condCode: ['Moderate or heavy sleet showers', 0.5, 'sleet', '10'] ],		//  Moderate or heavy sleet showers
	1255: [imgNames: [day_img: '16.png', night_img: '16.png'], condCode: ['Light snow showers', 0.7, 'snow', '13'] ],                     	//  Light snow showers   
	1258: [imgNames: [day_img: '16.png', night_img: '16.png'], condCode: ['Moderate or heavy snow showers', 0.5, 'snow', '13'] ],		//  Moderate or heavy snow showers
	1261: [imgNames: [day_img:  '8.png', night_img:  '8.png'], condCode: ['Light showers of ice pellets', 0.7, 'sleet', '13'] ],          	//  Light showers of ice pellets   
	1264: [imgNames: [day_img: '10.png', night_img: '10.png'], condCode: ['Moderate or heavy showers of ice pellets',0.3, 'sleet', '13'] ],	//  Moderate or heavy showers of ice pellets
	1273: [imgNames: [day_img: '38.png', night_img: '47.png'], condCode: ['Patchy light rain with thunder', 0.5, 'tstorms', '11'] ],      	//  Patchy light rain with thunder   
	1276: [imgNames: [day_img: '35.png', night_img: '35.png'], condCode: ['Moderate or heavy rain with thunder', 0.3, 'tstorms', '11'] ],	//  Moderate or heavy rain with thunder
	1279: [imgNames: [day_img: '41.png', night_img: '46.png'], condCode: ['Patchy light snow with thunder', 0.5, 'tstorms', '11'] ],		//  Patchy light snow with thunder
	1282: [imgNames: [day_img: '18.png', night_img: '18.png'], condCode: ['Moderate or heavy snow with thunder', 0.3, 'tstorms', '11'] ]	//  Moderate or heavy snow with thunder
]


@Field static attributesMap = [
	"betwixt":				[title: "Betwixt", descr: "Display the 'slice-of-day'?", typeof: "string", default: "false"],
	"cCF":				[title: "Cloud cover factor", descr: "", typeof: "number", default: "false"],
	"city":				[title: "City", descr: "Display your City's name?", typeof: "string", default: "true"],
	"cloud":				[title: "Cloud", descr: "", typeof: "number", default: "false"],
	"condition_code":			[title: "Condition code", descr: "", typeof: "number", default: "false"],
	"condition_icon_only":		[title: "Condition icon only", descr: "", typeof: "string", default: "false"],
	"condition_icon_url":		[title: "Condition icon URL", descr: "", typeof: "string", default: "false"],
	"condition_icon":			[title: "Condition icon", descr: "", typeof: "string", default: "false"],
	"condition_text":			[title: "Condition text", descr: "", typeof: "string", default: "false"],
	"country":				[title: "Country", descr: "", typeof: "string", default: "false"],
	"dashClock":			[title: "Clock", descr: "Flash time ':' every 2 seconds?", typeof: "string", default: "false"],
	"feelslike_c":			[title: "Feels like °C", descr: "Select to display the 'feels like' temperature in C:", typeof: "number", default: "true"],
	"feelslike_f":			[title: "Feels like °F", descr: "Select to display the 'feels like' temperature in F:", typeof: "number", default: "true"],
	"feelslike":			[title: "Feels like (in default unit)", descr: "Select to display the 'feels like' temperature:", typeof: "number", default: "true"],
	"forecastIcon":			[title: "Forecast icon", descr: "Select to display an Icon of the Forecast Weather:", typeof: "string", default: "true"],
	"illuminated":			[title: "Illuminated", descr: "Illuminance with 'lux' added for use on a Dashboard", typeof: "string", default: "true"],
	"is_day":				[title: "Is daytime", descr: "", typeof: "number", default: "false"],
	"last_updated_epoch":		[title: "Last updated epoch", descr: "", typeof: "number", default: "false"],
	"last_updated":			[title: "Last updated", descr: "", typeof: "string", default: "false"],
	"lat":				[title: "Latitude and Longitude", descr: "Select to display both Latitude and Longitude", typeof: "number", default: "false"],
	"local_date":			[title: "Local date", descr: "", typeof: "string", default: "false"],
	"localSunrise":			[title: "Local Sun Rise and Set", descr: "Select to display the Group of 'Time of Local Sunrise and Sunset,' with and without Dashboard text", typeof: "string", default: "true"],
	"local_time":			[title: "Local time", descr: "", typeof: "string", default: "false"],
	"localtime_epoch":		[title: "Localtime epoch", descr: "", typeof: "number", default: "false"],
	"location":				[title: "Location name with region", descr: "", typeof: "string", default: "false"],
	"mytile":				[title: "Mytile for dashboard", descr: "", typeof: "string", default: "false"],
	"name":				[title: "Location name", descr: "Name of Location (duplicates 'City')", typeof: "string", default: "false"],
	"open_weather":			[title: "OpenWeather attributes", descr: "Select duplicate wind attributes that are specific to Dashboard's Weather template", typeof: false, default: "true"],
	"percentPrecip":			[title: "Percent precipitation", descr: "Select to display the Chance of Rain, in percent", typeof: "number", default: "true"],
	"precipExtended":			[title: "Extended Precipitation", descr: "Select to display precipitation over a period of +- 2 days", typeof: false, default: "false"],
	"precip_in":			[title: "Precipitation Inches", descr: "", typeof: "number", default: "false"],
	"precip_mm":			[title: "Precipitation MM", descr: "", typeof: "number", default: "false"],
	"region":				[title: "Region", descr: "", typeof: "string", default: "false"],
	"tempHiLow":			[title: "Temperature high & low day +1", descr: "Select to display tomorrow's Forecast High and Low Temperatures", typeof: false, default: "true"],
	"twilight_begin":			[title: "Twilight begin", descr: "", typeof: "string", default: "false"],
	"twilight_end":			[title: "Twilight end", descr: "", typeof: "string", default: "false"],
	"tz_id":				[title: "Timezone ID", descr: "", typeof: "string", default: "false"],
	"vis_km":				[title: "Visibility KM", descr: "", typeof: "number", default: "false"],
	"vis_miles":			[title: "Visibility miles", descr: "", typeof: "number", default: "false"],
	"visual":				[title: "Visual weather", descr: "Select to display the Image of the Weather", typeof: "string", default: "true"],
	"visualDayPlus1":			[title: "Visual weather day +1", descr: "Select to display tomorrow's visual of the Weather", typeof: "string", default: "true"],
	"visualDayPlus1WithText":	[title: "Visual weather day +1 with text", descr: "", typeof: "string", default: "false"],
	"visualWithText":			[title: "Visual weather with text", descr: "", typeof: "string", default: "false"],
	"weather":				[title: "Weather", descr: "Current Conditions", typeof: "string", default: "false"],
	"wind_degree":			[title: "Wind Degree", descr: "Select to display the Wind Direction (number)", typeof: "number", default: "false"],
	"wind_dir":				[title: "Wind direction", descr: "Select to display the Wind Direction (letters)", typeof: "string", default: "true"],
	"wind_kph":				[title: "Wind KPH", descr: "", typeof: "number", default: "false"],
	"wind_mph":				[title: "Wind MPH", descr: "", typeof: "number", default: "false"],
	"wind_mps":				[title: "Wind MPS", descr: "Wind in Meters per Second", typeof: "number", default: "false"],
	"wind_mytile":			[title: "Wind mytile", descr: "", typeof: "string", default: "false"],
	"wind":				[title: "Wind (in default unit)", descr: "Select to display the Wind Speed", typeof: "number", default: "true"]
]


/*

	generic driver stuff
  -  -  -  -  -  -  -  -  -  -  -  -  -  -  -  -  -  -  -  -  -  -  -  -  -  -  -  -  -  -
*/


/*
	installed

	Doesn't do much other than call initialize().
*/
def installed()
{
	if (descTextEnable) log.info "${state.InternalName ?: device} is Installed"
	state.driverInstalled = true
	initialize()
}



/*
	initialize

	called by a reboot and by methods internal to this driver: updated() and installed()
*/
def initialize()
{
	unschedule()
	migrateTo()		// an effort to migrate from pre v1.4.0 to v1.4.0
	schedule("11 20 0/8 ? * * *", pollSunRiseSet)
	schedule("0 0 8 ? * FRI *", updateCheck)
	schedule("11 0/${luxEvery} * * * ?", updateLux)
	state.tz_id = TimeZone.getDefault().getID()
	if (state?.sunRiseSet?.init == null) state.sunRiseSet = [init:false]
	if (state?.apixu?.init      == null) state.apixu      = [init:false]
	if (state?.forecastPrecip?.init == null) {
	    state.forecastPrecip = [
    	    	precipDayMinus2: [inch: 999.9, mm: 999.9],
    	    	precipDayMinus1: [inch: 999.9, mm: 999.9],
    	    	precipDay0:	     [inch: 999.9, mm: 999.9],
    	    	precipDayPlus1:  [inch: 999.9, mm: 999.9],
    	    	precipDayPlus2:  [inch: 999.9, mm: 999.9],
    	    	init: true
    	   ]
	}
	log.trace "${state.InternalName ?: device} was initialized"
	runIn(4, updateLux)      // give sunrise/set time to complete.
	runIn(20, updateCheck)  // verify version once
}


private migrateTo() {
	if (state?.sunRiseSet?.init) {
		pollSunRiseSet()
//		state.remove("sunRiseSet") // converted to 'data' storage, no longer need 'state' storage.
		state.remove("lowLuxRepeat") // using schedule(), no longer need 'state' storage.
	}
}


// Check Version   ***** with great thanks and acknowledgment to Cobra (CobraVmax) for his original code ****
def updateCheck()
{    
	def paramsUD = [uri: "https://hubitatcommunity.github.io/wx-ApiXU/version2.json"]
	
 	asynchttpGet("updateCheckHandler", paramsUD) 
}


def updateCheckHandler(resp, data) {

	state.InternalName = "wx-ApiXU-Driver"
	
	if (resp.getStatus() == 200 || resp.getStatus() == 207) {
		respUD = parseJson(resp.data)
		//log.warn " Version Checking - Response Data: $respUD"   // Troubleshooting Debug Code - Uncommenting this line should show the JSON response from your webserver 
		state.Copyright = "${thisCopyright}"
		// uses reformattted 'version2.json' 
		def newVerRaw = (respUD.driver.(state.InternalName).ver)
		def newVer = (respUD.driver.(state.InternalName).ver.replaceAll("[.vV]", ""))
		def currentVer = version().replaceAll("[.vV]", "")                
		state.UpdateInfo = (respUD.driver.(state.InternalName).updated)
		def author = (respUD.author)
            // log.debug "updateCheck: $newVerRaw, $state.UpdateInfo, $author"
	
		switch(newVer) {
			case { it == "NLS"}:
			      state.Status = "<b>** This Driver is no longer supported by ${respUD.author}  **</b>"       
			      log.warn "** This Driver is no longer supported by ${respUD.author} **"      
				break
			case { it > currentVer}:
			      state.Status = "<b>New Version Available (Version: ${respUD.driver.(state.InternalName).ver})</b>"
			      log.warn "** There is a newer version of this Driver available  (Version: ${respUD.driver.(state.InternalName).ver}) **"
			      log.warn "** $state.UpdateInfo **"
				break
			case { it < currentVer}:
			      state.Status = "<b>You are using a Test version of this Driver (Expecting: ${respUD.driver.(state.InternalName).ver})</b>"
				break
			default:
				state.Status = "Current"
				if (descTextEnable) log.info "You are using the current version of this driver"
				break
		}
	
	      sendEvent(name: "chkUpdate", value: state.UpdateInfo)
	      sendEvent(name: "chkStatus", value: state.Status)
      }
      else
      {
           log.warn "Something went wrong: CHECK THE JSON FILE AND IT'S URI"
      }
}

def getThisCopyright(){"&copy; 2019 C Steele "}
//**********************************************************************************************************************

/*
* record of Bangali's version history prior to the Clone:
*
* Version: 5.1.4
*                added precipication forecast data from day - 2 to day + 2
*                removed selector for duplicate sunrise/sunset (localSunrise == local_sunrise)
*
* Version: 5.1.3
*                alternating description for settingEnabled input
*
* Version: 5.1.2
*                merged codahq's child device code -- with switch.
*
* Version: 5.1.1
*                merged Bangali's v5.0.2 - 5.0.5
*
* Version: 5.1.0
*	4/20/2019: extend attributesMap to contain keyname, title, descr and default
*                add debug logging and auto disable
*                add settings visibility and auto disable
*
** Version: 5.0.5
*	5/4/2019: fixed typos for feelsLike* and added condition code for day plus 1 forecasted data.
*
* Version: 5.0.2
*	4/20/2019: allow selection for publishing feelsLike and wind attributes
*
* Version: 5.0.1
*	3/24/2019: revert typo
*
* Version: 5.0.0
*	3/10/2019: allow selection of which attributes to publish
*	3/10/2019: restore localSunrise and localSunset attributes
*   3/10/2019: added option for lux polling interval
*   3/10/2019: added expanded weather polling interval
*
* Version: 4.3.1
*   1/20/2019: change icon size for mytile attribute
*
* Version: 4.3.0
*   12/30/2018: removed isStateChange:true based on testing done by @nh.schottfam on hubitat format
*
* Version: 4.2.0
*   12/30/2018: deprecated localSunrise and localSunset attributes instead use local_sunrise and local_sunset respectively
*
* Version: 4.1.0
*   12/29/2018: merged mytile code
*
* Version: 4.0.3
*   12/09/2018: added wind speed in MPS (meters per second)
*
* Version: 4.0.2
*   10/28/2018: continue publishing lux even if apixu api call fails.
*
* Version: 4.0.1
*   10/14/2018: removed logging of weather data.
*
* Version: 4.0.0
*   8/16/2018: added optional weather undergroud mappings.
*   8/16/2018: added forecast icon, high and low temperature for next day.
*
* Version: 3.5.0
*   8/10/2018: added temperature, pressure and humidity capabilities.
*
* Version: 3.0.0
*   7/25/2018: added code contribution from https://github.com/jebbett for new cooler weather icons with icons courtesy
*                 of https://www.deviantart.com/vclouds/art/VClouds-Weather-Icons-179152045.
*
* Version: 2.5.0
*   5/23/2018: update condition_icon to contain image for use on dashboard and moved icon url to condition_icon_url.
*
* Version: 2.0.0
*   5/29/2018: updated lux calculation with factor from condition code.
*
* Version: 1.0.0
*   5/27/2018: initial release.
*
*/
