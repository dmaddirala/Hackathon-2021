#include "BluetoothSerial.h"
#include <WiFi.h>
#include <WiFiClient.h>
#include <WebServer.h>
#include <ESPmDNS.h>
#include <FirebaseESP32.h>

#define ledPin2 25
#define ledPin1 26

#define WIFI_SSID "DRM2"
#define WIFI_PASSWORD "Madirala123!@"

#define FIREBASE_HOST "home-automation-test-4a21a.firebaseio.com"
#define FIREBASE_AUTH "8OOwZ8OguWr8cwER8RT6dYOmRicdEfvGzjyUZEL"

FirebaseData fbdo;
FirebaseData fbdo1;
FirebaseJson json;
String path = "/test";

WebServer server(80);
String IP = "192.168.31.201", IP1 = "";

void printResult(FirebaseData &data);
void printResult(StreamData &data);

void streamCallback(StreamData data)
{
  int flag = 5;
  String event = data.eventType();
  String eventPath = data.dataPath();
  String dataType = data.dataType();
  Serial.print("Event Type: ");
  Serial.println(event);
  Serial.print("Data Type: ");
  Serial.println(dataType);
  if(event == "put"){
    if(dataType == "int"){
      flag = data.intData();
      Serial.print("Data : ");
      Serial.println(flag);
    }else if((dataType == "string") || (dataType == "null")){
      flag = data.stringData().toInt();
      Serial.print("Data : ");
      Serial.println(flag);
    }
    else if (data.dataType() == "json"){
      FirebaseJson *json = data.jsonObjectPtr();
      String jsonStr;
      json->toString(jsonStr, true);
      Serial.print("json");
      Serial.println(jsonStr);
    }
    
  }

  if (flag!=5){
    if(eventPath == "/Fan"){
      digitalWrite(ledPin1, !flag);
      Serial.print("FAN: ");
      Serial.println(flag);
    }
    else if(eventPath == "/Light"){
      digitalWrite(ledPin2, !flag);
      Serial.print("LIGHT: ");
      Serial.println(flag);
    }
    else if(eventPath == "/Everything"){
      if(flag == 1){
        digitalWrite(ledPin1, LOW);
        digitalWrite(ledPin2, LOW);
      }else{
        digitalWrite(ledPin1, HIGH);
        digitalWrite(ledPin2, HIGH);
      }
    }
  }
}

void streamTimeoutCallback(bool timeout)
{
  if (timeout)
  {
    Serial.println();
    Serial.println("Stream timeout, resume streaming...");
    Serial.println();
  }
}

void setup(void) {
  pinMode(ledPin1, OUTPUT);
  pinMode(ledPin2, OUTPUT);
  digitalWrite(ledPin1, HIGH);
  digitalWrite(ledPin2, HIGH);

  Serial.begin(115200);
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  // Wait for connection
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("");
  Serial.print("Connected to ");
  Serial.println(WIFI_SSID);
  Serial.print("IP address: ");
  Serial.println(WiFi.localIP());
  IP = WiFi.localIP().toString();

  Firebase.begin(FIREBASE_HOST, FIREBASE_AUTH);
  Firebase.reconnectWiFi(true);

  Firebase.setReadTimeout(fbdo, 1000 * 60);
  Firebase.setFloatDigits(3);
  Firebase.setDoubleDigits(6);

  Firebase.setString(fbdo, path + "/IP", IP);

  if (!Firebase.beginStream(fbdo1, path))
  {
    Serial.println("------------------------------------");
    Serial.println("Can't begin stream connection...");
    Serial.println("REASON: " + fbdo1.errorReason());
    Serial.println("------------------------------------");
    Serial.println();
  }
  Firebase.setStreamCallback(fbdo1, streamCallback, streamTimeoutCallback, 8192);

  if (MDNS.begin("esp32")) {
    Serial.println("MDNS responder started");
  }

  server.on("/", handleRoot);
  server.on("/LightOn", LightOn);
  server.on("/LightOff", LightOff);
  server.on("/FanOn", FanOn);
  server.on("/FanOff", FanOff);
  server.on("/EverythingOn", EverythingOn);
  server.on("/EverythingOff", EverythingOff);

//  server.on("/", []() {
//    server.send(200, "text/plain", "this works as well");
//  });

  server.onNotFound(handleNotFound);

  server.begin();
  Serial.println("HTTP server started");
}

void loop(void) {
  server.handleClient();
  IP1 = WiFi.localIP().toString();
  if (IP != IP1 ){
    if (Firebase.setString(fbdo, path + "/IP", IP1 ) ){
      Serial.println("PASSED");
      Serial.print("NEW IP: ");
      Serial.println(IP);
    }else{
      Serial.println("FIREBASE FAILED: " + fbdo.errorReason());
      Serial.println();
    }
  }
}

//LIGHT
void LightOn() {
  digitalWrite(ledPin2, LOW);
  server.send(200, "text/plain", "LIGHT ON");
  Serial.println("LIGHT ON");

}
void LightOff() {
  digitalWrite(ledPin2, HIGH);
  server.send(200, "text/plain", "LIGHT OFF");
  Serial.println("LIGHT OFF");
}
void Switch2() {
  digitalWrite(ledPin2, !digitalRead(ledPin2));
  server.send(200, "text/plain", "FAN");
  Serial.println("LIGHT");

}

//FAN
void FanOn() {

  digitalWrite(ledPin1, LOW);
  server.send(200, "text/plain", "FAN ON");
  Serial.println("FAN ON");
}
void FanOff() {

  digitalWrite(ledPin1, HIGH);
  server.send(200, "text/plain", "FAN OFF");
  Serial.println("FAN OFF");
}
void Switch1() {

  digitalWrite(ledPin1, !digitalRead(ledPin1));
  server.send(200, "text/plain", "LIGHT");
  Serial.println("FAN");
}

//Everything

void EverythingOn() {

  digitalWrite(ledPin1, LOW);
  digitalWrite(ledPin2, LOW);
  server.send(200, "text/plain", "Everything ON");
  Serial.println("Everything ON");
}

void EverythingOff() {

  digitalWrite(ledPin1, HIGH);
  digitalWrite(ledPin2, HIGH);
  server.send(200, "text/plain", "Everything OFF");
  Serial.println("Everything OFF");
}


void handleRoot() {

  server.send(200, "text/plain", "Hello World!!");

}

void handleNotFound() {

  String message = "File Not Found\n\n";
  message += "URI: ";
  message += server.uri();
  message += "\nMethod: ";
  message += (server.method() == HTTP_GET) ? "GET" : "POST";
  message += "\nArguments: ";
  message += server.args();
  message += "\n";
  for (uint8_t i = 0; i < server.args(); i++) {
    message += " " + server.argName(i) + ": " + server.arg(i) + "\n";
  }
  server.send(404, "text/plain", message);

}
