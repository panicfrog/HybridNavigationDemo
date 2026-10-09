import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { pushRoute } from '@yyp/hybrid-navigation';

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
        onPress={async () => {
          try {
            await pushRoute('B', { title: 'From B' });
            console.log('[ScreenB] 另一个 B push 完成');
          } catch (e) {
            console.error('[ScreenB] pushRoute B failed:', e);
          }
        }}>
        <Text style={styles.buttonText}>Push Another B (title=From B) →</Text>
      </TouchableOpacity>
      <TouchableOpacity
        style={styles.button}
        onPress={async () => {
          try {
            await pushRoute('C', { message: 'Hi from B' });
            console.log('[ScreenB] C push 完成');
          } catch (e) {
            console.error('[ScreenB] pushRoute C failed:', e);
          }
        }}>
        <Text style={styles.buttonText}>Push Native Screen C →</Text>
      </TouchableOpacity>
      <TouchableOpacity
        style={[styles.button, styles.badButton]}
        onPress={async () => {
          try {
            await pushRoute('C', { wrongField: 123 });
            console.log('[ScreenB] bad params push 完成（不应该到这里）');
          } catch (e) {
            console.error('[ScreenB] bad params push 被拦截:', e);
          }
        }}>
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
    marginTop: 12,
  },
  badButton: {
    backgroundColor: '#BF360C',
  },
  buttonText: { color: 'white', fontSize: 17, fontWeight: '600' },
});

