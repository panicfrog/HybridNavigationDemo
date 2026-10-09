module.exports = {
  dependency: {
    platforms: {
      android: {
        // HybridNavigationPackage 在 fabric 子包，不在库包根，需显式指定
        packageImportPath: 'import com.hybridnavigation.fabric.HybridNavigationPackage;',
        packageInstance: 'new HybridNavigationPackage()',
      },
    },
  },
};
