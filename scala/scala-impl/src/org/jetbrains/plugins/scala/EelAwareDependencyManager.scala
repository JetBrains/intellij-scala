//noinspection ApiStatus,UnstableApiUsage
package org.jetbrains.plugins.scala

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.utils.EelPathUtils
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.DependencyManagerBase.{DependencyDescription, ivyHome}
import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.{Files, Path}

@ApiStatus.Internal
private[scala] class EelAwareDependencyManager extends DependencyManagerBase {
  @ApiStatus.Internal
  def resolveSafeAndTransferToRemoteEel(eelDescriptor: EelDescriptor, dependencies: DependencyDescription*): Seq[Path] = {
    val resolvedPaths = resolveSafe(dependencies: _*).toOption.getOrElse(Seq.empty).map(_.file)
    eelDescriptor match {
      case LocalEelDescriptor.INSTANCE =>
        resolvedPaths
      case remote =>
        val localIvyHome = ivyHome
        val remoteIvyHome = EelPathUtils.getHomePath(remote) / ".ivy2"
        Files.createDirectories(remoteIvyHome)
        resolvedPaths.map { path =>
          val relativePath = localIvyHome.relativize(path)
          val targetPath = remoteIvyHome / relativePath
          Files.createDirectories(targetPath.getParent)
          EelPathUtils.transferLocalContentToRemote(path, new EelPathUtils.TransferTarget.Explicit(remoteIvyHome / relativePath))
        }
    }
  }
}
