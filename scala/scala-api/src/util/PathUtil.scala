package org.jetbrains.plugins.scala
package util

import com.intellij.openapi.application.PathManager
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.utils.EelPathUtils

import java.nio.file.Path

object PathUtil {
  def getSystemDirectory(eelDescriptor: EelDescriptor): Path =
    eelDescriptor match {
      case LocalEelDescriptor.INSTANCE =>
        // For filesystem paths which match the machine where IDEA is running on, we call
        // `PathManager.getSystemDir`, which respects the `-Didea.system.path` VM option.
        PathManager.getSystemDir
      case remote =>
        EelPathUtils.getSystemFolder(remote)
    }
}
