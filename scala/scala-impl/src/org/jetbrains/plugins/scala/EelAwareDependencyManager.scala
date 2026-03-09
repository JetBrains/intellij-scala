//noinspection ApiStatus,UnstableApiUsage
package org.jetbrains.plugins.scala

import com.intellij.openapi.project.Project
import com.intellij.platform.eel.EelDescriptor
import org.jetbrains.annotations.ApiStatus
import com.intellij.platform.eel.provider.{EelProviderUtil, LocalEelDescriptor}
import com.intellij.platform.eel.provider.utils.EelPathUtils
import java.nio.file.Path

@ApiStatus.Experimental
class EelAwareDependencyManager(eelDescriptor: EelDescriptor) extends DependencyManagerBase {
  def this(project: Project) = this(EelProviderUtil.getEelDescriptor(project))

  override protected val ivyHomeDirectory: Path = eelDescriptor match {
    case LocalEelDescriptor.INSTANCE => super.ivyHomeDirectory
    case remote => EelPathUtils.getHomePath(remote).resolve(".ivy2")
  }
}
