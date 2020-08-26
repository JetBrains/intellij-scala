package org.jetbrains.plugins.scala.annotator.usageTracker

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.AuxiliaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.worksheet.ScalaScriptImportsUtil

import scala.collection.Set
import scala.collection.mutable.ArrayBuffer

/**
  * @author Alexander Podkhalyuzin
  */
object UsageTracker {

  def registerUsedElementsAndImports(element: PsiElement, results: Iterable[ScalaResolveResult], checkWrite: Boolean): Unit = {
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

  def getUnusedImports(file: ScalaFile): collection.Seq[ImportUsed] = {
    val redundant = ArrayBuffer.empty[ImportUsed]
    val imports = file.getAllImportUsed
    val refHolder = ScalaRefCountHolder.getInstance(file)

    refHolder.retrieveUnusedReferencesInfo { () =>
      imports.groupBy(_.importExpr).foreach {
        case (expr, importsUsed) if expr.nonEmpty =>
          val toHighlight =
            importsUsed.filterNot(imp => refHolder.usageFound(imp) || imp.isAlwaysUsed)

          if (toHighlight.size == importsUsed.size)
            redundant += ImportExprUsed(expr.get)
          else
            redundant ++= toHighlight
        case _ =>
      }
    }
    ScalaScriptImportsUtil.filterScriptImportsInUnused(file, redundant)
  }

  private def registerUsedElement(element: PsiElement,
                                  resolveResult: ScalaResolveResult,
                                  checkWrite: Boolean): Unit = {
    val named = resolveResult.getActualElement
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

      // For use of secondary constructors, see SCL-17662
      resolveResult.element match {
        case AuxiliaryConstructor(constr) => holder.registerValueUsed(ReadValueUsed(constr))
        case _ =>
      }
    }
  }

}
