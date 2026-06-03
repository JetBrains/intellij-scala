package org.jetbrains.plugins.scala.worksheet.ammonite

import org.jetbrains.plugins.scala.editor.importOptimizer.ImportInfoProvider
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr

final class AmmoniteImportInfoProvider extends ImportInfoProvider {

  override def acceptsFile(file: ScalaFile): Boolean =
    AmmoniteUtil.isAmmoniteFile(file)

  override def isImportUsed(imp: ScImportExpr): Boolean =
    AmmoniteUtil.isAmmoniteSpecificTextImport(imp)
}
