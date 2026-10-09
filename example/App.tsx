import React from 'react';
import { HybridNavigationRoot } from '@yyp/hybrid-navigation';
import { screens } from './src/screens';

export default function App() {
  return <HybridNavigationRoot screens={screens} />;
}
