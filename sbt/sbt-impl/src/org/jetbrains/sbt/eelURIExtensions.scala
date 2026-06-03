//noinspection ApiStatus,UnstableApiUsage
package org.jetbrains.sbt

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.utils.EelPathUtilsKt
import com.intellij.platform.eel.provider.LocalEelDescriptor

import java.net.URI
import java.nio.file.Path

extension (uri: URI)

  /**
   * Convert [[URI]] to [[Path]] in the environment described by the [[EelDescriptor]].
   *
   * For local descriptors, returns a local path.
   * For remote descriptors, returns a path with the remote prefix (e.g., `\\wsl.localhost\Ubuntu\...`).
   */
  def asPath(using eelDescriptor: EelDescriptor): Path =
    if eelDescriptor == LocalEelDescriptor.INSTANCE then
      Path.of(uri)
    else
      EelPathUtilsKt.Path(uri.getPath, eelDescriptor)
