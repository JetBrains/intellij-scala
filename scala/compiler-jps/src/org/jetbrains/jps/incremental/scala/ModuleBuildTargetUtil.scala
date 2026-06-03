package org.jetbrains.jps.incremental.scala

import org.jetbrains.annotations.Nullable
import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.jps.model.java.JpsJavaExtensionService

import java.nio.file.Path

object ModuleBuildTargetUtil {
  @Nullable
  def outputDir(target: ModuleBuildTarget): Path =
    JpsJavaExtensionService.getInstance().getOutputDirectoryPath(target.getModule, target.isTests)
}
