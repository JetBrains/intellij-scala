//noinspection ApiStatus,UnstableApiUsage
package org.jetbrains.plugins.scala

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import com.intellij.platform.eel.provider.{EelProviderUtil, LocalEelDescriptor}
import com.intellij.platform.eel.provider.utils.EelPathUtils
import com.intellij.util.SystemProperties

import java.nio.file.Path

@ApiStatus.Experimental
class EelAwareDependencyManager(project: Project) extends DependencyManagerBase {
  override protected val ivyHomeDirectory: Path = {
    val eelDescriptor = EelProviderUtil.getEelDescriptor(project)
    val homeDir = eelDescriptor match {
      case LocalEelDescriptor.INSTANCE => Path.of(SystemProperties.getUserHome)
      case remote                      => EelPathUtils.getHomePath(remote)
    }
    homeDir.resolve(".ivy2")
  }
}
