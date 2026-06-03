//noinspection ApiStatus,UnstableApiUsage
package org.jetbrains.sbt

import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.{EelNioBridgeServiceKt, EelProviderUtil, LocalEelDescriptor}

import java.nio.file.Path

extension (path: Path)
  def eelDescriptor: EelDescriptor = EelProviderUtil.getEelDescriptor(path)

  def normalizedLocalPath: String =
    FileUtil.toSystemIndependentName(path.asLocalPath)

  /**
   * A machine-specific local path translated via the eel API.
   */
  def asLocalPath: String =
    val eelPath = EelNioBridgeServiceKt.asEelPath(path)
    if eelPath.getDescriptor == LocalEelDescriptor.INSTANCE then
      path.toString
    else
      eelPath.toString
