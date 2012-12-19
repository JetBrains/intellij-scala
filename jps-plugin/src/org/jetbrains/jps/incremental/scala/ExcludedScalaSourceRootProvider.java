package org.jetbrains.jps.incremental.scala;

import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.service.JpsServiceManager;

/**
 * User: Dmitry Naydanov
 * Date: 12/5/12
 */
public abstract class ExcludedScalaSourceRootProvider {
  public abstract boolean isExcludedFromCompilation(JpsModule module);
  
  public static boolean isExcludedInSomeProvider(JpsModule module) {
    Iterable<ExcludedScalaSourceRootProvider> excludedRootProviders = 
        JpsServiceManager.getInstance().getExtensions(ExcludedScalaSourceRootProvider.class);
    for (ExcludedScalaSourceRootProvider provider : excludedRootProviders) {
      if (provider.isExcludedFromCompilation(module)) return true;
    }
    
    return false;
  }
}
