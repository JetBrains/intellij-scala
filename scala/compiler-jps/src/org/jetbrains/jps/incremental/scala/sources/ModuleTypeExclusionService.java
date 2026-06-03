package org.jetbrains.jps.incremental.scala.sources;

import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.incremental.scala.ChunkExclusionService;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleType;

public class ModuleTypeExclusionService extends ChunkExclusionService {
  @Override
  public boolean isExcluded(ModuleChunk chunk) {
    for (JpsModule module : chunk.getModules()) {
      JpsModuleType<?> type = module.getModuleType();
      if (type.equals(SbtModuleType.INSTANCE) || type.equals(SharedSourcesModuleType.INSTANCE)) {
        return true;
      }
    }
    return false;
  }
}
