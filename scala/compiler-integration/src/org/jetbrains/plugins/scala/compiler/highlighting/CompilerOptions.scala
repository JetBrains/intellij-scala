package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.openapi.module.Module
import org.jetbrains.plugins.scala.project.ModuleExt

private object CompilerOptions {
  def scalacOptions(module: Module): Seq[String] =
    module.scalaCompilerSettings.getOptionsAsStrings(module.hasScala3)

  def containsUnusedImports(scalacOptions: Seq[String]): Boolean = {
    val unusedCategories = scalacOptions.collect {
      case s"-Wunused:$rest" => rest.split(",")
    }.flatten.toSet
    unusedCategories.contains("all") || unusedCategories.contains("imports")
  }

  def containsFatalWarnings(scalacOptions: Seq[String]): Boolean =
    scalacOptions.contains("-Xfatal-warnings") || scalacOptions.contains("-Werror")
}
