package org.jetbrains.plugins.scala.worksheet

import org.jetbrains.plugins.scala.editor.importOptimizer.ImportInfo
import org.jetbrains.plugins.scala.extensions.implementation.iterator.ContextsIterator
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.{ImportExprUsed, ImportUsed}
import org.jetbrains.plugins.scala.worksheet.ammonite.AmmoniteUtil

/**
  * This class is needed to abstract import optimizer from specific impls (now only for Ammonite) 
  * 
  * User: Dmitry.Naydanov
  * Date: 20.10.17.
  */
object ScalaScriptImportsUtil {
  def filterScriptImportsInUnused(file: ScalaFile, unused: collection.Seq[ImportUsed]): collection.Seq[ImportUsed] = {
    file match {
      case ammoniteFile if AmmoniteUtil.isAmmoniteFile(ammoniteFile) =>
        unused.filterNot {
          case ImportExprUsed(ex) => AmmoniteUtil.isAmmoniteSpecificTextImport(ex)
          case _ => false
        }
      case _ => unused
    }
  }

  /**
    * 
    * @return if true, expr shouldn't be merged
    */
  def preventMerging(info: ImportInfo): Boolean = AmmoniteUtil.isAmmoniteSpecificImport(info)

  def cannotShadowName(info: ImportInfo): Boolean = AmmoniteUtil.isAmmoniteSpecificImport(info)
  
  def isImportUsed(used: ImportUsed): Boolean = AmmoniteUtil.isAmmoniteSpecificImport(used)
  
  def isScriptRef(ref: ScStableCodeReference): Boolean =
    new ContextsIterator(ref).find(_.isInstanceOf[ScImportExpr]).exists(
      expr => AmmoniteUtil.isAmmoniteSpecificImport(expr.asInstanceOf[ScImportExpr])
    )
}
