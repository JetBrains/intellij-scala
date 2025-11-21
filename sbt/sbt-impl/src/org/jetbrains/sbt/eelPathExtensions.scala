package org.jetbrains.sbt

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.{EelNioBridgeServiceKt, EelProviderUtil}

import java.nio.file.Path

extension (path: Path)
  //noinspection ApiStatus
  //noinspection UnstableApiUsage,ApiStatus
  def eelDescriptor: EelDescriptor = EelProviderUtil.getEelDescriptor(path)

  /**
   * A machine-specific local path translated via the eel API.
   */
  //noinspection ApiStatus
  def asLocalPath: String = EelNioBridgeServiceKt.asEelPath(path).toString
