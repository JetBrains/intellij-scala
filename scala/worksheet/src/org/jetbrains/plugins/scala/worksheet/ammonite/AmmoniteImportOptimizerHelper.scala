package org.jetbrains.plugins.scala.worksheet.ammonite

import org.jetbrains.plugins.scala.editor.importOptimizer.{ImportInfo, ScalaImportOptimizerHelper}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed

final class AmmoniteImportOptimizerHelper extends ScalaImportOptimizerHelper {

  override def isImportUsed(used: ImportUsed): Boolean = AmmoniteUtil.isAmmoniteSpecificImport(used)

  override def cannotShadowName(info: ImportInfo): Boolean = AmmoniteUtil.isAmmoniteSpecificImport(info)

  override def preventMerging(info: ImportInfo): Boolean = AmmoniteUtil.isAmmoniteSpecificImport(info)
}
