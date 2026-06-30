import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { pop } from './HybridNavigation';

export default function ScreenD() {
  return (
    <View style={styles.container}>
      <Text style={styles.title}>RN Screen D</Text>
      <Text style={styles.subtitle}>终点页面（React Native）</Text>
      <TouchableOpacity style={styles.button} onPress={pop}>
        <Text style={styles.buttonText}>← Pop Back</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#E8F5E9',
  },
  title: { fontSize: 28, fontWeight: 'bold', color: '#1B5E20', marginBottom: 8 },
  subtitle: { fontSize: 16, color: '#2E7D32', marginBottom: 40 },
  button: {
    backgroundColor: '#2E7D32',
    paddingHorizontal: 24,
    paddingVertical: 14,
    borderRadius: 10,
  },
  buttonText: { color: 'white', fontSize: 17, fontWeight: '600' },
});
