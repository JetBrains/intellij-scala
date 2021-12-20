package org.jetbrains.plugins.scala.annotator.usageTracker

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.editor.importOptimizer.ImportInfoProvider
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiElementExt, PsiFileExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.AuxiliaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAssignment, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages._
import org.jetbrains.plugins.scala.lang.psi.{ScImportsHolder, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import scala.collection.immutable.ArraySeq
import scala.collection.mutable

object UsageTracker {

  def registerUsedElementsAndImports(element: PsiElement, results: Iterable[ScalaResolveResult], checkWrite: Boolean): Unit = {
    for (resolveResult <- results if resolveResult != null) {
      registerUsedImports(element, resolveResult)
      registerUsedElement(element, resolveResult, checkWrite)
    }
  }

  def registerUsedImports(elem: PsiElement, imports: Set[ImportUsed]): Unit = {
    if (!elem.isValid) return
    if (imports.isEmpty)
      return

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
    val redundantBuilder = ArraySeq.newBuilder[ImportUsed]

    val potentiallyRedundantImports = RedundantImportUtils.collectPotentiallyRedundantImports(file)

    val importExprToUsedImports: mutable.Buffer[(ScImportExpr, Iterable[ImportUsed])] =
      mutable.ArrayBuffer.empty

    val importHolders: Iterator[ScImportsHolder] =
      file.depthFirst().filterByType[ScImportsHolder]

    for {
      importHolder <- importHolders
      importStmt   <- importHolder.getImportStatements
      importExprs  = importStmt.importExprs
      importExpr   <- importExprs
    } {
      val importsUsed = ImportUsed.buildAllFor(importExpr)
      importExprToUsedImports += ((importExpr, importsUsed))
    }

    val refHolder = ScalaRefCountHolder.getInstance(file)

    refHolder.runIfUnusedReferencesInfoIsAlreadyRetrievedOrSkip { () =>
      def isRedundant(importUsed: ImportUsed): Boolean =
        potentiallyRedundantImports.contains(importUsed) &&
          RedundantImportUtils.isActuallyRedundant(importUsed, file.getProject, file.isScala3File)

      def treatAsUnused(importUsed: ImportUsed): Boolean =
        refHolder.usageFound(importUsed) && !isRedundant(importUsed) ||
          importUsed.isAlwaysUsed

      importExprToUsedImports.foreach { case (expr, importsUsed) =>
        val toHighlight: Iterable[ImportUsed] = importsUsed.filterNot(treatAsUnused)

        val wholeImportExprIsUnused = toHighlight.size == importsUsed.size
        if (wholeImportExprIsUnused)
          redundantBuilder += new ImportExprUsed(expr)
        else
          redundantBuilder ++= toHighlight
      }
    }

    val result0 = redundantBuilder.result()
    val result1 = ImportInfoProvider.filterOutUsedImports(file, result0)
    result1
  }

  private def registerUsedElement(element: PsiElement,
                                  resolveResult: ScalaResolveResult,
                                  checkWrite: Boolean): Unit = {
    val named = resolveResult.getActualElement
    val file = element.getContainingFile
    if (named.isValid && named.getContainingFile == file &&
      !PsiTreeUtil.isAncestor(named, element, true)) { //to filter recursive usages

      val holder = ScalaRefCountHolder.getInstance(file)
      val value: ValueUsed = element match {
        case ref: ScReferenceExpression if checkWrite && ScalaPsiUtil.isPossiblyAssignment(ref) =>
          ref.getContext match {
            case ScAssignment.resolvesTo(target) if target != named =>
              holder.registerValueUsed(WriteValueUsed(target))
            case _ =>
          }
          WriteValueUsed(named)
        case _ => ReadValueUsed(named)
      }
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
