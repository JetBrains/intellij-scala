package org.jetbrains.plugins.scala.editor.importOptimizer

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings

/**
  * @author Nikolay.Tropin
  */
case class OptimizeImportSettings(addFullQualifiedImports: Boolean,
                                  isLocalImportsCanBeRelative: Boolean,
                                  sortImports: Boolean,
                                  collectImports: Boolean,
                                  isUnicodeArrow: Boolean,
                                  spacesInImports: Boolean,
                                  classCountToUseImportOnDemand: Int,
                                  importLayout: Array[String],
                                  isAlwaysUsedImport: String => Boolean) {

  private def this(s: ScalaCodeStyleSettings) {
    this(
      s.isAddFullQualifiedImports,
      s.isDoNotChangeLocalImportsOnOptimize,
      s.isSortImports,
      s.isCollectImports,
      s.REPLACE_CASE_ARROW_WITH_UNICODE_CHAR,
      s.SPACES_IN_IMPORTS,
      s.getClassCountToUseImportOnDemand,
      s.getImportLayout,
      s.isAlwaysUsedImport
    )
  }
}

object OptimizeImportSettings {
  def apply(project: Project) = new OptimizeImportSettings(ScalaCodeStyleSettings.getInstance(project))
}
