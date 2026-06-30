import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { pushRoute } from './HybridNavigation';

interface ScreenBProps {
  params?: { title?: string };
}

export default function ScreenB({ params }: ScreenBProps) {
  return (
    <View style={styles.container}>
      <Text style={styles.title}>{params?.title ?? 'RN Screen B'}</Text>
      <Text style={styles.subtitle}>React Native 页面</Text>
      <TouchableOpacity
        style={styles.button}
        onPress={() => pushRoute('C', { message: 'Hi from B' })}>
        <Text style={styles.buttonText}>Push Native Screen C →</Text>
      </TouchableOpacity>
      <TouchableOpacity
        style={[styles.button, styles.badButton]}
        onPress={() => pushRoute('C', { wrongField: 123 })}>
        <Text style={styles.buttonText}>Push C with bad params (会被拦截)</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#FFE0B2',
  },
  title: { fontSize: 28, fontWeight: 'bold', color: '#E65100', marginBottom: 8 },
  subtitle: { fontSize: 16, color: '#BF360C', marginBottom: 40 },
  button: {
    backgroundColor: '#E65100',
    paddingHorizontal: 24,
    paddingVertical: 14,
    borderRadius: 10,
  },
  badButton: {
    marginTop: 16,
    backgroundColor: '#BF360C',
  },
  buttonText: { color: 'white', fontSize: 17, fontWeight: '600' },
});
