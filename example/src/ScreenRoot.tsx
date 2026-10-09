import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { pushRoute } from '@yyp/hybrid-navigation';

// 验证用 Root RN 页:作为新 nav 的 rootViewController,通过启动期主动 mount 进来。
// 这里只验证 push B 落到新 nav 栈顶(active=default 的 Root nav)。
export default function ScreenRoot() {
  return (
    <View style={styles.container}>
      <Text style={styles.title}>RN Root (__index__)</Text>
      <Text style={styles.subtitle}>该页是 window.rootViewController 的 root</Text>
      <TouchableOpacity
        style={styles.button}
        onPress={async () => {
          try {
            await pushRoute('B', { title: 'from Root' });
            console.log('[ScreenRoot] B push 完成');
          } catch (e) {
            console.error('[ScreenRoot] pushRoute B failed:', e);
          }
        }}>
        <Text style={styles.buttonText}>Push B (title=from Root) →</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#C8E6C9',
  },
  title: { fontSize: 28, fontWeight: 'bold', color: '#1B5E20', marginBottom: 8 },
  subtitle: { fontSize: 16, color: '#2E7D32', marginBottom: 40 },
  button: {
    backgroundColor: '#1B5E20',
    paddingHorizontal: 24,
    paddingVertical: 14,
    borderRadius: 10,
    marginTop: 12,
  },
  buttonText: { color: 'white', fontSize: 17, fontWeight: '600' },
});
