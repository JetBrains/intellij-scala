package org.jetbrains.plugins.scala
package util

import com.intellij.openapi.application.PathManager
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.utils.EelSystemFolderUtils

import java.nio.file.Path

//noinspection ApiStatus,UnstableApiUsage
object PathUtil {
  def getSystemDirectory(eelDescriptor: EelDescriptor): Path =
    eelDescriptor match {
      case LocalEelDescriptor.INSTANCE =>
        // For filesystem paths which match the machine where IDEA is running on, we call
        // `PathManager.getSystemDir`, which respects the `-Didea.system.path` VM option.
        PathManager.getSystemDir
      case remote =>
        EelSystemFolderUtils.getSystemFolder(remote)
    }
}
