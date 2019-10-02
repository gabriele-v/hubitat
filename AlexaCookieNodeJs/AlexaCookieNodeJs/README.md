# Alexa Cookie NodeJS wrapper for Hubitat
This webservice is a wrapper of orginal [Apollon77 alexa-cookie library](https://github.com/Apollon77/alexa-cookie) for use with Hubitat [Alexa TTS App](https://github.com/ogiewon/Hubitat/tree/master/Alexa%20TTS).

# How to install
(Tested on Ubuntu 16.04, commands may be a little different on other platforms)
- Deploy a NodeJS server on your preferred platform (plenty of instuctions on web if needed)
- It can be deployed on a cloud service as well, in that case it's suggested to configure authentication and don't publish the service but use it behind a HTTPS reverse proxy
- Download latest version as zip and then unzip it on the server
- Go to folder of AlexaCookieNodeJs
- Make ```AlexaCookie.js``` executable (needed on Unix platforms only) => ```chmod +x ./AlexaCookie.js```
- Execute the command ```npm install``` to install all required npm packages

# Configuration
In case default ports (wrapper on 81 and proxy on 82) have to be changed or to add authentication, modify the file ```config.json```
Empty username and empty password means no authentication required
```
{
	"port":"81",
	"proxyPort":"82",
	"username": "",
	"password": "",
	"consoleLogging": false
}
```

# How to automatically start the wrapper
- Execute command ```npm install -g pm2``` to install Node Process Manager 2 and allow autostart
- Start the application with ```pm2 start AlexaCookie.js``` command
- Execute command ```pm2 startup```
- Execute command ```pm2 save```
- Due to an issue still under investigation, looks like AlexaCookie.js must be restarted after getting succesfully 1 cookie, otherwise the second refresh won't work. To workaround it, schedule ```pm2 restart AlexaCookie.js``` with cron every day

# Usage
- Go to ```http://[serverip]:81```
- Fill the form wih your Amazon username, password and country
- At this point, depending on Amazon security, cookie refresh options could be immediately generated or a message will ask to go to webpage ```http://[serverip]:82``` (proxy) that will be opened in a new tab
- **IMPORTANT!** do not close the original (let's call it :81) tab
- Login again on second page (with 2FA if enabled)
- **IMPORTANT!** After succesfull login on second webpage, a message will be prompted to close the browser: don't close the browser, but instead close only that tab (:82) and go back to the original tab (:81)
- Copy the RefreshURL and RefreshOptions in the relative fields on Hubitat Alexa TTS App
- Refresh will be now handled automatically between Hubitat and wrapper every 6 days, without human intervention requirement
