import React, { useEffect } from 'react';
import { PermissionsAndroid, Button, View, Platform, Text } from 'react-native';
import { NativeModules } from 'react-native';

interface SensorServiceType {
  startService: () => void;
}

const { SensorServiceModule } = NativeModules as {
  SensorServiceModule: SensorServiceType;
};

const App = () => {
  useEffect(() => {
    requestPermissions();
  }, []);

  const requestPermissions = async () => {
    if (Platform.OS === 'android') {
      try {
        await PermissionsAndroid.requestMultiple([
          PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION,
        ]);
      } catch (error) {
        console.warn("Permission error", error);
      }
    }
  };

  const startNativeService = () => {
    if (SensorServiceModule) {
      SensorServiceModule.startService();
    } else {
      console.warn("SensorServiceModule is undefined");
    }
  };

  return (
    <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: 'white' }}>
      <Text style={{ marginBottom: 20, fontSize: 18 }}>Native Sensor Tracker</Text>
      <Button title="Start Background Service" onPress={startNativeService} />
    </View>
  );
};

export default App;
