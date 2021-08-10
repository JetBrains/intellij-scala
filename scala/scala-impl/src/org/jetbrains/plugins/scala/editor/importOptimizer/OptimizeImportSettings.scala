package org.jetbrains.plugins.scala.editor.importOptimizer

import java.util.regex.Pattern
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.codeInspection.scalastyle.ScalastyleCodeInspection
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.{ProjectPsiElementExt, ScalaFeatures}
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

/**
  * @author Nikolay.Tropin
  */
case class OptimizeImportSettings(addFullQualifiedImports: Boolean,
                                  basePackage: Option[String],
                                  isLocalImportsCanBeRelative: Boolean,
                                  sortImports: Boolean,
                                  collectImports: Boolean,
                                  isUnicodeArrow: Boolean,
                                  spacesInImports: Boolean,
                                  scalaFeatures: ScalaFeatures,
                                  forceScala2SyntaxInSource3: Boolean,
                                  classCountToUseImportOnDemand: Int,
                                  importLayout: Array[String],
                                  isAlwaysUsedImport: String => Boolean,
                                  scalastyleSettings: ScalastyleSettings) {

  def scalastyleGroups: Option[Seq[Pattern]] = scalastyleSettings.groups
  def scalastyleOrder: Boolean = scalastyleSettings.scalastyleOrder

  private def this(s: ScalaCodeStyleSettings, scalastyleSettings: ScalastyleSettings, basePackage: Option[String], scalaFeatures: ScalaFeatures) = {

    this(
      addFullQualifiedImports = s.isAddFullQualifiedImports,
      basePackage = basePackage,
      isLocalImportsCanBeRelative = s.isDoNotChangeLocalImportsOnOptimize,
      sortImports = s.isSortImports,
      collectImports = s.isCollectImports,
      isUnicodeArrow = s.REPLACE_CASE_ARROW_WITH_UNICODE_CHAR,
      spacesInImports = s.SPACES_IN_IMPORTS,
      scalaFeatures = scalaFeatures,
      forceScala2SyntaxInSource3 = s.forceScala2ImportSyntaxInSource3,
      classCountToUseImportOnDemand = s.getClassCountToUseImportOnDemand,
      importLayout = s.getImportLayout,
      isAlwaysUsedImport = s.isAlwaysUsedImport,
      scalastyleSettings = scalastyleSettings
    )
  }
}

object OptimizeImportSettings {
  def apply(file: PsiFile): OptimizeImportSettings = {
    val project = file.getProject
    val codeStyleSettings = ScalaCodeStyleSettings.getInstance(project)
    val scalastyleSettings =
      if (codeStyleSettings.isSortAsScalastyle) {
        val scalastyleConfig = ScalastyleCodeInspection.configurationFor(file)
        val scalastyleChecker = scalastyleConfig.flatMap(_.checks.find(_.className == ScalastyleSettings.importOrderChecker))
        val groups = scalastyleChecker.filter(_.enabled).flatMap(ScalastyleSettings.groups)
        ScalastyleSettings(scalastyleOrder = true, groups)
      }
      else ScalastyleSettings(scalastyleOrder = false, None)

    val basePackage: Option[String] =
      if (codeStyleSettings.isAddImportsRelativeToBasePackage) {
        val configuredBasePackage = file.module.map(ScalaProjectSettings.getInstance(project).getBasePackageFor).filter(_.nonEmpty)
        (file, configuredBasePackage) match {
          case (file: ScalaFile, Some(basePackage)) if file.firstPackaging.exists(_.packageName == basePackage) => Some(basePackage)
          case _ => None
        }
      }
      else None

    new OptimizeImportSettings(codeStyleSettings, scalastyleSettings, basePackage, file.features)
  }
}