package org.jetbrains.plugins.scala.editor.importOptimizer

import org.jetbrains.plugins.scala.project.ScalaFeatures

case class ImportTextGenerationOptions(isUnicodeArrow: Boolean,
                                       spacesInImports: Boolean,
                                       scalaFeatures: ScalaFeatures,
                                       nameOrdering: Option[Ordering[String]])

object ImportTextGenerationOptions {
  def from(settings: OptimizeImportSettings): ImportTextGenerationOptions = {
    val ordering =
      if (settings.scalastyleOrder) Some(ScalastyleSettings.nameOrdering)
      else if (settings.sortImports) Some(Ordering.String)
      else None
    ImportTextGenerationOptions(settings.isUnicodeArrow, settings.spacesInImports, settings.scalaFeatures, ordering)
  }
}