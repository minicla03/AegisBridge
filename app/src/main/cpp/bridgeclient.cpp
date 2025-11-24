#include <ArduinoBLE.h>

// Main service
BLEService AegisService("6e400001-b5a3-f393-e0a9-e50e24dcca9e");

// Manufacturer Data
const uint8_t manufacturerData[] = {0x01, 0x02};
const uint16_t companyId = 0xFFFF;

// specific services
BLEService heartService("0001");
BLECharacteristic heartChar("1001", BLERead | BLENotify, 2);

BLEService spo2Service("0002");
BLECharacteristic spo2Char("2002", BLERead | BLENotify, 2);

BLEService tempService("0003");
BLECharacteristic tempChar("3003", BLERead | BLENotify, 4);

// simulatori (casuali per ora)
int readHeartRate() {
    return random(60, 100);
}

int readSpO2() {
    return random(95, 100);
}

float readTemperature() {
    return random(360, 380) / 10.0;
}


void setup() {
    Serial.begin(115200);
    while (!Serial);

    if (!BLE.begin()) {
        Serial.println("Starting BLE failed!");
        while (1);
    }

    // Main name e advertising
    BLE.setLocalName("AegisBracelet");
    BLE.setAdvertisedService(AegisService);
    BLE.addService(AegisService);

    // Add secondary services
    heartService.addCharacteristic(heartChar);
    BLE.addService(heartService);
    // TODO: vedere descriptor

    spo2Service.addCharacteristic(spo2Char);
    BLE.addService(spo2Service);

    tempService.addCharacteristic(tempChar);
    BLE.addService(tempService);

    // Manufacturer Data
    BLE.setManufacturerData(companyId, manufacturerData, sizeof(manufacturerData));

    // Avvia advertising
    BLE.advertise();
    Serial.println("BLE device ready and advertising!");
}

void loop()
{
    BLE.poll(); // start BLE communication and flow

    static unsigned long lastUpdate = 0;
    if (millis() - lastUpdate > 1000) {
        lastUpdate = millis();

        int bpm = readHeartRate();
        heartChar.setValue(bpm);

        int spo2 = readSpO2();
        spo2Char.setValue(spo2);

        float temp = readTemperature();
        tempChar.setValue((byte*)&temp, sizeof(temp)); // float -->byte array

        Serial.print("Heart: "); Serial.print(bpm);
        Serial.print(" SpO2: "); Serial.print(spo2);
        Serial.print(" Temp: "); Serial.println(temp);
    }
}
