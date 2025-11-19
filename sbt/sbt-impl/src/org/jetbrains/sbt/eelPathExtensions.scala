//noinspection ApiStatus,UnstableApiUsage
package org.jetbrains.sbt

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.{EelNioBridgeServiceKt, EelProviderUtil, LocalEelDescriptor}

import java.nio.file.Path

extension (path: Path)
  def eelDescriptor: EelDescriptor = EelProviderUtil.getEelDescriptor(path)

  /**
   * A machine-specific local path translated via the eel API.
   */
  def asLocalPath: String =
    val eelPath = EelNioBridgeServiceKt.asEelPath(path)
    if eelPath.getDescriptor == LocalEelDescriptor.INSTANCE then
      path.toString
    else
      eelPath.toString
