package org.jetbrains.plugins.scala.worksheet

import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.{ImportExprUsed, ImportUsed}
import org.jetbrains.plugins.scala.worksheet.ammonite.AmmoniteUtil

/**
  * User: Dmitry.Naydanov
  * Date: 20.10.17.
  */
object ScalaScriptImportsUtil {
  def filterScriptImportsInUnused(file: ScalaFile, unused: Seq[ImportUsed]): Seq[ImportUsed] = {
    file match {
      case ammoniteFile if AmmoniteUtil.isAmmoniteFile(ammoniteFile) =>
        unused.filterNot {
          case ImportExprUsed(ex) => AmmoniteUtil.isAmmoniteSpecificImport(ex)
          case _ => false
        }
      case _ => unused
    }
  }
}
