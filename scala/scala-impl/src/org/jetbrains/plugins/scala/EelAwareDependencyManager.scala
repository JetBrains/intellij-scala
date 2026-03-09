//noinspection ApiStatus,UnstableApiUsage
package org.jetbrains.plugins.scala

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import com.intellij.platform.eel.provider.{EelProviderUtil, LocalEelDescriptor}
import com.intellij.platform.eel.provider.utils.EelPathUtils
import java.nio.file.Path

@ApiStatus.Experimental
class EelAwareDependencyManager(project: Project) extends DependencyManagerBase {
  override protected val ivyHomeDirectory: Path = EelProviderUtil.getEelDescriptor(project) match {
    case LocalEelDescriptor.INSTANCE => super.ivyHomeDirectory
    case remote => EelPathUtils.getHomePath(remote).resolve(".ivy2")
  }
}
