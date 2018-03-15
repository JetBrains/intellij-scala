package org.jetbrains.plugins.scala
package annotator
package importsTracker


import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.{ImportExprUsed, ImportUsed}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.worksheet.ScalaScriptImportsUtil

import scala.collection.Set
import scala.collection.mutable.ArrayBuffer

/**
 * @author Alexander Podkhalyuzin
 */
object ImportTracker {

  def registerUsedImports(elem: PsiElement, imports: Set[ImportUsed]): Unit = {
    if (!elem.isValid) return

    elem.getContainingFile match {
      case scalaFile: ScalaFile =>
        val refHolder = ScalaRefCountHolder.getInstance(scalaFile)
        imports.foreach(refHolder.registerImportUsed)
      case _ =>
    }
  }

  def registerUsedImports(element: PsiElement, resolveResult: ScalaResolveResult): Unit = {
    registerUsedImports(element, resolveResult.importsUsed)
  }

  def getUnusedImports(file: ScalaFile): Seq[ImportUsed] = {
    val redundant = ArrayBuffer.empty[ImportUsed]
    val imports = file.getAllImportUsed
    val refHolder = ScalaRefCountHolder.getInstance(file)

    refHolder.retrieveUnusedReferencesInfo { () =>
      imports.groupBy(_.importExpr).foreach {
        case (expr, importsUsed) =>
          val toHighlight =
            importsUsed.filter(imp => refHolder.noUsagesFound(imp) && !imp.isAlwaysUsed)

          if (toHighlight.size == importsUsed.size)
            redundant += ImportExprUsed(expr)
          else
            redundant ++= toHighlight
      }
    }
    ScalaScriptImportsUtil.filterScriptImportsInUnused(file, redundant)
  }
}
