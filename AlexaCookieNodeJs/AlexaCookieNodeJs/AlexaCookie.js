//
//  Alexa Cookie NodeJS wrapper for Hubitat
//
//  Copyright 2018 Gabriele - Special thanks to Apollon77 for the original Alexia Cookie generator
//
//  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
//  in compliance with the License. You may obtain a copy of the License at:
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
//  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
//  for the specific language governing permissions and limitations under the License.
//
//  Change History:
//
//    Version   Date        Who             What
//    -------   ----        ---             ----
//     v0.1.0   2019-01-02  Gabriele        Initial Version
//     v0.1.1   2019-01-20  Gabriele        Update Alexa-Cookie to v2.0.1
//     v0.1.2   2019-06-24  Gabriele        Update Alexa-Cookie to v2.1.0


'use strict';
// Define libraries
const app = require('express')();
const server = require('http').Server(app);
const io = require('socket.io')(server);
const fs = require('fs');

const bodyParser = require('body-parser');
const linkify = require('linkifyjs');
require('linkifyjs/plugins/hashtag')(linkify);
const linkifyHtml = require('linkifyjs/html');
const uuidv4 = require('uuid/v4');

const alexaCookie = require('./alexa-cookie/alexa-cookie');
const _DictRefOptions = {}

// Load config
var config = JSON.parse(fs.readFileSync('config.json', 'utf8'));
var port = process.env.PORT || config.port;
var consoleLogging = config.consoleLogging;

// Autenthication
var auth = function (req, res, next) {
    // Skip authentication if username is empty in config file
    if (!config.username || config.username == '') {
        return next();
    }

    // If authentication not provided, ask again
    if (!req.headers.authorization || req.headers.authorization.indexOf('Basic ') === -1) {
        res.statusCode = 401;
        res.setHeader('WWW-Authenticate', 'Basic realm="AlexaCookieNodeJs"');
        res.end('Unauthorized');
        return;
    }

    // Verify autenthication
    const base64Credentials = req.headers.authorization.split(' ')[1];
    const credentials = Buffer.from(base64Credentials, 'base64').toString('ascii');
    const [username, password] = credentials.split(':');
    if (username === config.username && password === config.password) {
        next();
    } else {
        res.statusCode = 401;
        res.setHeader('WWW-Authenticate', 'Basic realm="AlexaCookieNodeJs"');
        res.end('Unauthorized');
        return;
    }
}

// Body parser
app.use(bodyParser.urlencoded({
    extended: true
}));
app.use(bodyParser.json());

// Socket.io
io.sockets.on('connection', (socket) => {
logToConsole("A user is connected to socket.io => " + socket);
})
app.use(function (req, res, next) {
    req.io = io;
    next();
})

// Index get
app.get('/', auth, function (req, res) {
    res.send(`
        <!DOCTYPE html>
        <html>
        <head>
            <title>AlexaCookieNodeJs</title>
        </head>
        <body> 
            <form action="/" method="post">
                <fieldset>
                    <legend>Amazon information:</legend>
                    Username: <br>
                    <input type="email" name="username" required><br>
                    <br>
                    Password:<br>
                    <input type="password" name="password" required><br>
                    <br>
                    Country:<br>
                    <input type="radio" name="amazonSite" value="alexa.amazon.com|en-US" checked> United States<br>
                    <input type="radio" name="amazonSite" value="alexa.amazon.ca|en-US"> Canada<br>
                    <input type="radio" name="amazonSite" value="amazon.co.uk|en-GB"> United Kingdom<br>
                    <input type="radio" name="amazonSite" value="alexa.amazon.ca|en-US"> Italy<br>
                    <br>
                    <input type="submit" value="Submit">
                </fieldset>
            </form>
            </body>
            </html>
    `);
});

// Index post
app.post('/', auth, function (req, res) {
    if (req.body.username == '' || req.body.password == '') {
        res.send(`
            Username and password are required!<br>
            <br>
            <button onclick="goBack()">Go Back</button>
            <script> function goBack() { window.history.back(); }</script>
        `);
    }

    res.writeHead(200, { "Content-Type": "text/html" });
    res.end(`
        <!DOCTYPE html>
        <html>
        <head>
            <title>AlexaCookieNodeJs</title>
            <script src="/socket.io/socket.io.js"></script>
        </head>
        <body>  
            <div id="text">Please wait...</div>
            <script>
                var socket = io();
                socket.on('text', addText)
                function addText(text){
                    document.getElementById("text").innerHTML = text;
                    addListners();
                }
                function addListners(){
                    const list = document.getElementsByTagName("a");
                    for (i = 0; i < list.length; ++i) {
                        console.log(list[i]);
                        list[i].onclick = function() {
                            document.getElementById("text").innerHTML = "Please wait...";
                        };
                    }
                }
            </script>
        </body>
        </html>
    `);

    setTimeout(function () {
        const options = {
            logger: consoleLogging ? console.log : '',
            amazonPage: req.body.amazonSite.split('|')[0],                   // optional: possible to use with different countries, default is 'amazon.de'
            acceptLanguage: req.body.amazonSite.split('|')[1],               // optional: webpage language, should match to amazon-Page, default is 'de-DE'
            userAgent: req.headers['user-agent'],                            // optional: own userAgent to use for all request, overwrites default one, should not be needed
            proxyOnly: true,                                                 // optional: should only the proxy method be used? WHen no email/passwort are provided this will set to true automatically, default: false
            setupProxy: true,                                                // optional: should the library setup a proxy to get cookie when automatic way did not worked? Default false!
            proxyOwnIp: req.headers.host.split(':')[0],                      // required if proxy enabled: provide own IP or hostname to later access the proxy. needed to setup all rewriting and proxy stuff internally
            proxyPort: config.proxyPort,                                     // optional: use this port for the proxy, default is 0 means random port is selected
            proxyListenBind: '',                                             // optional: set this to bind the proxy to a special IP, default is '0.0.0.0'
            proxyLogLevel: 'warn',                                           // optional: Loglevel of Proxy, default 'warn'
            amazonPageProxyLanguage: req.body.amazonSite.split('|')[1],      // optional: language to be used for the Amazon Signin page the proxy calls. default is "de_DE")
            formerRegistrationData: {}                                       // option/preferred: provide the result object from subsequent proxy usages here and some generated data will be reused for next proxy call too
        };


        alexaCookie.generateAlexaCookie(req.body.username, req.body.password, options, (err, result) => {
            if (err) {
                err = 'ERROR: ' + linkifyHtml(err.message) + '<br><br><br>If you already have inserted your credential in Amazon webpage, please wait...';
                req.io.emit('text', err);
            }
            else if (result && result.csrf) {
                alexaCookie.stopProxyServer();
                var regData = JSON.stringify(result);
                var text = `
                    OAuth generated successfully!<br>
                    Copy this data into Hubitat Alexa App<br>
                    <br>
                    <b>RefreshURL:</b><br>
                    <textarea readonly rows="1" style="width:100%">http://${req.headers.host}/refreshCookie</textarea><br>
                    <br>
                    <b>RefreshOptions:</b><br>
                    <textarea readonly rows="25" style="width:100%">${regData}</textarea>
                `;
                req.io.emit('text', text);
            }
        });
    }, 2000);
});

app.post('/refreshCookie', auth, function (req, res) {
    var refOptions = req.body;
    if (!refOptions || refOptions == '' || Object.keys(refOptions).length == 0) {
        res.status(400).send('Cookie options empty!');
    }
    else {
        var guid = uuidv4()
        logToConsole(`Refreshing cookie for guid ${guid} with Options: ${JSON.stringify(refOptions)}`);
        res.send(guid);

        const options = {
            logger: consoleLogging ? console.log : '',
            formerRegistrationData: refOptions // required: provide the result object from subsequent proxy usages here and some generated data will be reused for next proxy call too
        };

        alexaCookie.refreshAlexaCookie(options, (err, result) => {
            if (err) {
                logToConsole(`Cookie refresh error for guid ${guid} => Error: ${err.message}`);
                _DictRefOptions[guid] = "ERROR: " + err.message;
            }
            else {
                logToConsole(`Cookie refreshed succesfully for guid ${guid} => Result: ${JSON.stringify(result)}`);
                _DictRefOptions[guid] = result;
            }

            // Remove it after 5 minutes, Hubitat download it's scheduled 2 minutes after refresh
            setTimeout(function () {
                if (_DictRefOptions[guid]) {
                    delete _DictRefOptions[guid]
                }
            }, 1000 * 60 * 5);
        });
    }
});

app.get('/refreshCookie', auth, function (req, res) {
    var guid = req.query.guid;
    if (!guid || guid == '') {
        logToConsole(`Guid empty for request ${req}!`);
        res.status(400).send('Guid empty!');
    }
    else if (!_DictRefOptions[guid] || _DictRefOptions[guid] == '') {
        logToConsole(`No cookie found for guid ${guid}!`);
        res.status(500).send('No cookie or error for specified guid, maybe timeout of 5 mintues it\'s expired?');
    }
    else if (JSON.stringify(_DictRefOptions[guid]).includes("ERROR:")) {
        logToConsole(`Cookie refresh error for guid ${guid}!`);
        res.status(500).send(`Error refreshing cookie => ${_DictRefOptions[guid]}`);
    }
    else {
        logToConsole(`Cookie sent succesfully for guid ${guid}!`);
        res.setHeader('Content-Type', 'application/json');
        res.send(_DictRefOptions[guid])
    }
});

server.listen(port, function () {
    console.log(`AlexaCookieNodeJs listening on port ${port}!`);
});

function logToConsole(text) {
    if (consoleLogging) {
        console.log(text);
    }
}