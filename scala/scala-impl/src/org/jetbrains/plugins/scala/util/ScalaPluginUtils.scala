package org.jetbrains.plugins.scala.util

import com.intellij.openapi.application.PathManager
import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

object ScalaPluginUtils {

  private val ScalaPluginHomeFolders = Set(
    ".ScalaPluginIU",
    ".ScalaPluginIC"
  )

  /** analogue of [[com.intellij.ide.plugins.PluginManagerCore#isRunningFromSources]] */
  val isRunningFromSources: Boolean = try {
    val home = Path.of(PathManager.getHomePath)
    val parent2 = home.parents.drop(1).nextOption()
    parent2.exists(path => path.isDirectory && ScalaPluginHomeFolders.contains(path.getFileName.toString))
  } catch {
    case _: Throwable => false
  }
}
