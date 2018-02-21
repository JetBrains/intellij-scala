package org.jetbrains.plugins.scala.annotator.usageTracker

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages._
import org.jetbrains.plugins.scala.lang.psi.light.scala.isLightScNamedElement
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.worksheet.ScalaScriptImportsUtil

import scala.collection.Set
import scala.collection.mutable.ArrayBuffer

/**
  * @author Alexander Podkhalyuzin
  */
object UsageTracker {

  def registerUsedElementsAndImports(element: PsiElement, results: Seq[ScalaResolveResult], checkWrite: Boolean): Unit = {
    for (resolveResult <- results if resolveResult != null) {
      registerUsedImports(element, resolveResult)
      registerUsedElement(element, resolveResult, checkWrite)
    }
  }

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

  private def registerUsedElement(element: PsiElement,
                                  resolveResult: ScalaResolveResult,
                                  checkWrite: Boolean) {
    val named = resolveResult.getActualElement match {
      case isLightScNamedElement(e) => e
      case e => e
    }
    val file = element.getContainingFile
    if (named.isValid && named.getContainingFile == file &&
      !PsiTreeUtil.isAncestor(named, element, true)) { //to filter recursive usages
      val value: ValueUsed = element match {
        case ref: ScReferenceExpression if checkWrite &&
          ScalaPsiUtil.isPossiblyAssignment(ref) => WriteValueUsed(named)
        case _ => ReadValueUsed(named)
      }
      val holder = ScalaRefCountHolder.getInstance(file)
      holder.registerValueUsed(value)
      // For use of unapply method, see SCL-3463
      resolveResult.parentElement.foreach(parent => holder.registerValueUsed(ReadValueUsed(parent)))
    }
  }

}
