/***********************************************************************************************************************
*  Copyright 2018 bangali
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
* for use with HUBITAT so no tiles <<<<<
*
***********************************************************************************************************************/

public static String version()      {  return "v2.0.0"  }

/***********************************************************************************************************************
*
* Version: 2.0.0
*   5/29/2018: updated lux calculation with factor from condition code.
*
* Version: 1.0.0
*   5/27/2018: initial release.
*
*/

import groovy.transform.Field

metadata    {
    definition (name: "ApiXU Weather Driver", namespace: "bangali", author: "bangali")  {
        capability "Illuminance Measurement"
        capability "Temperature Measurement"
        capability "Relative Humidity Measurement"
        capability "Sensor"
        capability "Polling"

        attribute "name", "string"
        attribute "region", "string"
        attribute "country", "string"
        attribute "lat", "string"
        attribute "lon", "string"
        attribute "tz_id", "string"
        attribute "localtime_epoch", "string"
        attribute "local_time", "string"
        attribute "local_date", "string"
        attribute "last_updated_epoch", "string"
        attribute "last_updated", "string"
        attribute "temp_c", "string"
        attribute "temp_f", "string"
        attribute "is_day", "string"
        attribute "condition_text", "string"
        attribute "condition_icon", "string"
        attribute "condition_code", "string"
        attribute "wind_mph", "string"
        attribute "wind_kph", "string"
        attribute "wind_degree", "string"
        attribute "wind_dir", "string"
        attribute "pressure_mb", "string"
        attribute "pressure_in", "string"
        attribute "precip_mm", "string"
        attribute "precip_in", "string"
        attribute "cloud", "string"
        attribute "feelslike_c", "string"
        attribute "feelslike_f", "string"
        attribute "vis_km", "string"
        attribute "vis_miles", "string"

        attribute "location", "string"
        attribute "local_sunrise", "string"
        attribute "local_sunset", "string"
        attribute "twilight_begin", "string"
        attribute "twilight_end", "string"
        attribute "illuminated", "string"
        attribute "cCF", "string"
        attribute "lastXUupdate", "string"
        attribute "lastXUupdate", "string"

        command "refresh"
    }

    preferences {
        input "zipCode", "text", title: "Zip code or city name or latitude,longitude?", required: true
        input "apixuKey", "text", title: "ApiXU key?", required: true
        input "isFahrenheit", "bool", title: "Temperature in fahrenheit?", required: true, defaultValue: true
        input "dashClock", "bool", title: "Flash time ':' every 2 seconds?", required: true, defaultValue: false
        input "pollEvery", "enum", title: "Poll ApiXU how frequently?\nrecommended setting 30 minutes.\nilluminance is always updated every 5 minutes.", required: true, defaultValue: 30,
                            options: [5:"5 minutes",10:"10 minutes",15:"15 minutes",30:"30 minutes"]
		
        input "debugOutput", "bool", title:"Enable debug logging?"
    }

}

def updated()   {
	unschedule()
    state.tz_id = null
    state.clockSeconds = true
    poll()
    "runEvery${pollEvery}Minutes"(poll)
    runEvery5Minutes(updateLux)
//    schedule("0 * * * * ?", updateClock)
//    schedule("0/2 0 0 ? * * *", updateClock)
    if (dashClock)  updateClock();
}

def poll()      {
    logDebug ">>>>> apixu: Executing 'poll', location: $zipCode"

    def obs = getXUdata()
    if (!obs)   {
        log.warn "No response from ApiXU API"
        return
    }

    def now = new Date().format('yyyy-MM-dd HH:mm', location.timeZone)
    sendEvent(name: "lastXUupdate", value: now)

    def tZ = TimeZone.getTimeZone(obs.location.tz_id)
    state.tz_id = obs.location.tz_id

    def localTime = new Date().parse("yyyy-MM-dd HH:mm", obs.location.localtime, tZ)
    def localDate = localTime.format("yyyy-MM-dd", tZ)
    def localTimeOnly = localTime.format("HH:mm", tZ)

    def sunriseAndSunset = getSunriseAndSunset(obs.location.lat, obs.location.lon, localDate)
    def sunriseTime = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXXX", sunriseAndSunset.results.sunrise, tZ)
    def sunsetTime = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXXX", sunriseAndSunset.results.sunset, tZ)
    def noonTime = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXXX", sunriseAndSunset.results.solar_noon, tZ)
    def twilight_begin = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXXX", sunriseAndSunset.results.civil_twilight_begin, tZ)
    def twilight_end = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXXX", sunriseAndSunset.results.civil_twilight_end, tZ)

    def localSunrise = sunriseTime.format("HH:mm", tZ)
    sendEvent(name: "local_sunrise", value: localSunrise, descriptionText: "Sunrise today is at $localSunrise")
    def localSunset = sunsetTime.format("HH:mm", tZ)
    sendEvent(name: "local_sunset", value: localSunset, descriptionText: "Sunset today at is $localSunset")
    def tB = twilight_begin.format("HH:mm", tZ)
    sendEvent(name: "twilight_begin", value: tB, descriptionText: "Twilight begins today at $tB")
    def tE = twilight_end.format("HH:mm", tZ)
    sendEvent(name: "twilight_end", value: tE, descriptionText: "Twilight ends today at $tE")

    state.sunriseTime = sunriseTime.format("yyyy-MM-dd'T'HH:mm:ssXXX", tZ)
    state.sunsetTime = sunsetTime.format("yyyy-MM-dd'T'HH:mm:ssXXX", tZ)
    state.noonTime = noonTime.format("yyyy-MM-dd'T'HH:mm:ssXXX", tZ)
    state.twilight_begin = twilight_begin.format("yyyy-MM-dd'T'HH:mm:ssXXX", tZ)
    state.twilight_end = twilight_end.format("yyyy-MM-dd'T'HH:mm:ssXXX", tZ)

    sendEvent(name: "name", value: obs.location.name)
    sendEvent(name: "region", value: obs.location.region)
    sendEvent(name: "country", value: obs.location.country)
    sendEvent(name: "lat", value: obs.location.lat)
    sendEvent(name: "lon", value: obs.location.lon)
    sendEvent(name: "tz_id", value: obs.location.tz_id)
    sendEvent(name: "localtime_epoch", value: obs.location.localtime_epoch)
    sendEvent(name: "local_time", value: localTimeOnly)
    sendEvent(name: "local_date", value: localDate)
    sendEvent(name: "last_updated_epoch", value: obs.current.last_updated_epoch)
    sendEvent(name: "last_updated", value: obs.current.last_updated)
    sendEvent(name: "temp_c", value: obs.current.temp_c, unit: "C")
    sendEvent(name: "temp_f", value: obs.current.temp_f, unit: "F")
    sendEvent(name: "temperature", value: (isFahrenheit ? obs.current.temp_f : obs.current.temp_c), unit: "${(isFahrenheit ? 'F' : 'C')}")
    sendEvent(name: "is_day", value: obs.current.is_day)
    sendEvent(name: "condition_text", value: obs.current.condition.text)
    sendEvent(name: "condition_icon", value: 'http:' + obs.current.condition.icon)
    sendEvent(name: "condition_code", value: obs.current.condition.code)
    sendEvent(name: "wind_mph", value: obs.current.wind_mph, unit: "MPH")
    sendEvent(name: "wind_kph", value: obs.current.wind_kph, unit: "KPH")
    sendEvent(name: "wind_degree", value: obs.current.wind_degree, unit: "DEGREE")
    sendEvent(name: "wind_dir", value: obs.current.wind_dir)
    sendEvent(name: "pressure_mb", value: obs.current.pressure_mb, unit: "MBAR")
    sendEvent(name: "pressure_in", value: obs.current.pressure_in, unit: "IN")
    sendEvent(name: "precip_mm", value: obs.current.precip_mm, unit: "MM")
    sendEvent(name: "precip_in", value: obs.current.precip_in, unit: "IN")
    sendEvent(name: "humidity", value: obs.current.humidity, unit: "%")
    sendEvent(name: "cloud", value: obs.current.cloud, unit: "%")
    sendEvent(name: "feelslike_c", value: obs.current.feelslike_c, unit: "C")
    sendEvent(name: "feelslike_f", value: obs.current.feelslike_f, unit: "F")
    sendEvent(name: "vis_km", value: obs.current.vis_km, unit: "KM")
    sendEvent(name: "vis_miles", value: obs.current.vis_miles, unit: "MILES")

    sendEvent(name: "condition_icon_only", value: obs.current.condition.icon.split("/")[-1])
    sendEvent(name: "location", value: obs.location.name + ', ' + obs.location.region)
    state.condition_code = obs.current.condition.code
    state.cloud = obs.current.cloud
    updateLux()

    return
}

def refresh()       { poll() }

def configure()     { poll() }

private getXUdata()   {
    def obs = [:]
    def params = [ uri: "https://api.apixu.com/v1/forecast.json?key=$apixuKey&q=$zipCode&days=3" ]
    try {
        httpGet(params)		{ resp ->
            if (resp?.data)     obs << resp.data;
            else                log.error "http call for ApiXU weather api did not return data: $resp";
        }
    } catch (e) { log.error "http call failed for ApiXU weather api: $e" }

    return obs
}

private getSunriseAndSunset(latitude, longitude, forDate)	{
    def params = [ uri: "https://api.sunrise-sunset.org/json?lat=$latitude&lng=$longitude&date=$forDate&formatted=0" ]
    def sunRiseAndSet = [:]
    try {
        httpGet(params)		{ resp -> sunRiseAndSet = resp.data }
    } catch (e) { log.error "http call failed for sunrise and sunset api: $e" }

    return sunRiseAndSet
}

def updateLux()     {
    if (!state.sunriseTime || !state.sunsetTime || !state.noonTime ||
        !state.twilight_begin || !state.twilight_end || !state.condition_code || !state.tz_id)
        return

    def tZ = TimeZone.getTimeZone(state.tz_id)
    def lT = new Date().format("yyyy-MM-dd'T'HH:mm:ssXXX", tZ)
    def localTime = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXXX", lT, tZ)
    def sunriseTime = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXXX", state.sunriseTime, tZ)
    def sunsetTime = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXXX", state.sunsetTime, tZ)
    def noonTime = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXXX", state.noonTime, tZ)
    def twilight_begin = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXXX", state.twilight_begin, tZ)
    def twilight_end = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXXX", state.twilight_end, tZ)
    def lux = estimateLux(localTime, sunriseTime, sunsetTime, noonTime, twilight_begin, twilight_end, state.condition_code, state.cloud, state.tz_id)
    sendEvent(name: "illuminance", value: lux, unit: "lux")
    sendEvent(name: "illuminated", value: String.format("%,d lux", lux))
}

private estimateLux(localTime, sunriseTime, sunsetTime, noonTime, twilight_begin, twilight_end, condition_code, cloud, tz_id)     {
//    logDebug "condition_code: $condition_code | cloud: $cloud"
//    logDebug "twilight_begin: $twilight_begin | twilight_end: $twilight_end | tz_id: $tz_id"
//    logDebug "localTime: $localTime | sunriseTime: $sunriseTime | noonTime: $noonTime | sunsetTime: $sunsetTime"

    def tZ = TimeZone.getTimeZone(tz_id)
    def lux = 0l
    def aFCC = true
    def l

    if (timeOfDayIsBetween(sunriseTime, noonTime, localTime, tZ))      {
        logDebug "between sunrise and noon"
        l = (((localTime.getTime() - sunriseTime.getTime()) * 10000f) / (noonTime.getTime() - sunriseTime.getTime()))
        lux = (l < 50f ? 50l : l.trunc(0) as long)
    }
    else if (timeOfDayIsBetween(noonTime, sunsetTime, localTime, tZ))      {
        logDebug "between noon and sunset"
        l = (((sunsetTime.getTime() - localTime.getTime()) * 10000f) / (sunsetTime.getTime() - noonTime.getTime()))
        lux = (l < 50f ? 50l : l.trunc(0) as long)
    }
    else if (timeOfDayIsBetween(twilight_begin, sunriseTime, localTime, tZ))      {
        logDebug "between sunrise and twilight"
        l = (((localTime.getTime() - twilight_begin.getTime()) * 50f) / (sunriseTime.getTime() - twilight_begin.getTime()))
        lux = (l < 10f ? 10l : l.trunc(0) as long)
    }
    else if (timeOfDayIsBetween(sunsetTime, twilight_end, localTime, tZ))      {
        logDebug "between sunset and twilight"
        l = (((twilight_end.getTime() - localTime.getTime()) * 50f) / (twilight_end.getTime() - sunsetTime.getTime()))
        lux = (l < 10f ? 10l : l.trunc(0) as long)
    }
    else if (!timeOfDayIsBetween(twilight_begin, twilight_end, localTime, tZ))      {
        logDebug "between non-twilight"
        lux = 5l
        aFCC = false
    }

    def cC = condition_code.toInteger()
    def cCT = ''
    def cCF
    if (aFCC)
        if (conditionFactor[cC])    {
            cCF = conditionFactor[cC][1]
            cCT = conditionFactor[cC][0]
        }
        else    {
            cCF = ((100 - (cloud.toInteger() / 3d)) / 100).round(1)
            cCT = 'using cloud cover'
        }
    else    {
        cCF = 1.0
        cCT = 'night time now'
    }

    lux = (lux * cCF) as long
    logDebug "condition: $cC | condition text: $cCT | condition factor: $cCF | lux: $lux"
    sendEvent(name: "cCF", value: cCF)

    return lux
}

private timeOfDayIsBetween(fromDate, toDate, checkDate, timeZone)     {
    return (!checkDate.before(fromDate) && !checkDate.after(toDate))
}

def updateClock()       {
    runIn(2, updateClock)
    if (!state.tz_id)       return;
    if (!tz_id)       return;
    def nowTime = new Date()
    def tZ = TimeZone.getTimeZone(state.tz_id)
    sendEvent(name: "local_time", value: nowTime.format((state.clockSeconds ? "HH:mm" : "HH mm"), tZ))
    def localDate = nowTime.format("yyyy-MM-dd", tZ)
    if (localDate != state.localDate)
    {   state.localDate = localDate
        sendEvent(name: "local_date", value: localDate)
    }
    state.clockSeconds = (state.clockSeconds ? false : true)
}

@Field final Map    conditionFactor = [
        1000: ['Sunny', 1],                                     1003: ['Partly cloudy', 0.8],
        1006: ['Cloudy', 0.6],                                  1009: ['Overcast', 0.5],
        1030: ['Mist', 0.5],                                    1063: ['Patchy rain possible', 0.8],
        1066: ['Patchy snow possible', 0.6],                    1069: ['Patchy sleet possible', 0.6],
        1072: ['Patchy freezing drizzle possible', 0.4],        1087: ['Thundery outbreaks possible', 0.2],
        1114: ['Blowing snow', 0.3],                            1117: ['Blizzard', 0.1],
        1135: ['Fog', 0.2],                                     1147: ['Freezing fog', 0.1],
        1150: ['Patchy light drizzle', 0.8],                    1153: ['Light drizzle', 0.7],
        1168: ['Freezing drizzle', 0.5],                        1171: ['Heavy freezing drizzle', 0.2],
        1180: ['Patchy light rain', 0.8],                       1183: ['Light rain', 0.7],
        1186: ['Moderate rain at times', 0.5],                  1189: ['Moderate rain', 0.4],
        1192: ['Heavy rain at times', 0.3],                     1195: ['Heavy rain', 0.2],
        1198: ['Light freezing rain', 0.7],                     1201: ['Moderate or heavy freezing rain', 0.3],
        1204: ['Light sleet', 0.5],                             1207: ['Moderate or heavy sleet', 0.3],
        1210: ['Patchy light snow', 0.8],                       1213: ['Light snow', 0.7],
        1216: ['Patchy moderate snow', 0.6],                    1219: ['Moderate snow', 0.5],
        1222: ['Patchy heavy snow', 0.4],                       1225: ['Heavy snow', 0.3],
        1237: ['Ice pellets', 0.5],                             1240: ['Light rain shower', 0.8],
        1243: ['Moderate or heavy rain shower', 0.3],           1246: ['Torrential rain shower', 0.1],
        1249: ['Light sleet showers', 0.7],                     1252: ['Moderate or heavy sleet showers', 0.5],
        1255: ['Light snow showers', 0.7],                      1258: ['Moderate or heavy snow showers', 0.5],
        1261: ['Light showers of ice pellets', 0.7],            1264: ['Moderate or heavy showers of ice pellets',0.3],
        1273: ['Patchy light rain with thunder', 0.5],          1276: ['Moderate or heavy rain with thunder', 0.3],
        1279: ['Patchy light snow with thunder', 0.5],          1282: ['Moderate or heavy snow with thunder', 0.3]]

//**********************************************************************************************************************

private getDebugOutputSetting() {
	return (settings?.debugOutput || settings?.debugOutput == null)
}

private logDebug(msg) {
	if (debugOutputSetting) {
		log.debug msg
	}
}