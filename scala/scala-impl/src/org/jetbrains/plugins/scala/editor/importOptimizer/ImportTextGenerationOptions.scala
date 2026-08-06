package org.jetbrains.plugins.scala.editor.importOptimizer

import org.jetbrains.plugins.scala.project.ScalaFeatures

case class ImportTextGenerationOptions(isUnicodeArrow: Boolean,
                                       spacesInImports: Boolean,
                                       scalaFeatures: ScalaFeatures,
                                       nameOrdering: Option[Ordering[String]],
                                       forceScala2SyntaxInSource3: Boolean)

object ImportTextGenerationOptions {
  val default: ImportTextGenerationOptions = ImportTextGenerationOptions(
    isUnicodeArrow = false,
    spacesInImports = false,
    scalaFeatures = ScalaFeatures.default,
    nameOrdering = None,
    forceScala2SyntaxInSource3 = false
  )

  def from(settings: OptimizeImportSettings): ImportTextGenerationOptions = {
    val ordering =
      if (settings.scalastyleOrder) Some(ScalastyleImportsUtil.nameOrdering)
      else if (settings.sortImports) Some(Ordering.String)
      else None
    ImportTextGenerationOptions(
      isUnicodeArrow = settings.isUnicodeArrow,
      spacesInImports = settings.spacesInImports,
      scalaFeatures = settings.scalaFeatures,
      nameOrdering = ordering,
      forceScala2SyntaxInSource3 = settings.forceScala2SyntaxInSource3,
    )
  }
}