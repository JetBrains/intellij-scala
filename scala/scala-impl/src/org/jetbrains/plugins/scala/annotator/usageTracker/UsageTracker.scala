package org.jetbrains.plugins.scala.annotator.usageTracker

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiNamedElement}
import org.jetbrains.plugins.scala.editor.importOptimizer.ImportInfoProvider
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiElementExt, PsiFileExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAssignment, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScEnum, ScObject}
import org.jetbrains.plugins.scala.lang.psi.{ScImportsHolder, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.util.ScalaUsageNamesUtil

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

    val importExprToUsedImports: mutable.Buffer[(ScImportExpr, Set[ImportUsed])] =
      mutable.ArrayBuffer.empty

    val importHolders: Iterator[ScImportsHolder] =
      file.depthFirst().filterByType[ScImportsHolder]

    val isSource3 = file.isSource3Enabled

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

      def containsGivenWildcardInSource3(importsUsed: Set[ImportUsed]): Boolean = isSource3 && {
        importsUsed.exists {
          case ImportWildcardSelectorUsed(importExpr) if importExpr.hasGivenSelector => true
          case _ => false
        }
      }

      importExprToUsedImports.foreach { case (expr, importsUsed) =>
        val toHighlight: Iterable[ImportUsed] = importsUsed.filterNot(treatAsUnused)

        val wholeImportExprIsUnused = toHighlight.size == importsUsed.size
        if (wholeImportExprIsUnused)
          redundantBuilder += new ImportExprUsed(expr)
        else if (!containsGivenWildcardInSource3(importsUsed))
          redundantBuilder ++= toHighlight
      }
    }

    val result0 = redundantBuilder.result()
    val result1 = ImportInfoProvider.filterOutUsedImports(file, result0)
    result1
  }

  private def collectAllNamedElementTargets(resolveResult: ScalaResolveResult): Seq[PsiNamedElement] = {
    val originalsFromSynthetics = resolveResult.element match {
      case ScEnum.FromObject(enum) => Seq(enum)
      case ScEnum.FromSyntheticMethod(enum) => enum.cases
      case o: ScObject if o.isSyntheticObject => Seq(o.syntheticNavigationElement).collect {
        case n: ScNamedElement => n
      }
      case f: ScFunctionDefinition if f.isSynthetic => Seq(f.syntheticNavigationElement).collect {
        case n: ScNamedElement => n
      }
      case _ => Seq.empty
    }
    originalsFromSynthetics ++ resolveResult.parentElement.toSeq :+ resolveResult.element
  }

  private def registerTargetElement(sourceElement: PsiElement, targetElement: PsiNamedElement, checkWrite: Boolean): Unit =
    if (targetElement.isValid && targetElement.getContainingFile == sourceElement.getContainingFile &&
      !PsiTreeUtil.isAncestor(targetElement, sourceElement, true)) { //to filter recursive usages

      val valueUseds = sourceElement match {
        case ref: ScReferenceExpression if checkWrite && ScalaPsiUtil.isPossiblyAssignment(ref) =>

          val additionalWrite = ref.getContext match {
            case ScAssignment.resolvesTo(assignmentTarget) if assignmentTarget != targetElement =>
              Seq(WriteValueUsed(assignmentTarget, ref))
            case _ => Seq.empty
          }

          WriteValueUsed(targetElement, ref) +: additionalWrite
        case _ => Seq(ReadValueUsed(targetElement, sourceElement))
      }

      val holder = ScalaRefCountHolder.getInstance(sourceElement.getContainingFile)
      valueUseds.foreach(holder.registerValueUsed)
    }


  private def registerUsedElement(sourceElement: PsiElement,
                                  resolveResult: ScalaResolveResult,
                                  checkWrite: Boolean): Unit =
    collectAllNamedElementTargets(resolveResult)
      .foreach(registerTargetElement(sourceElement, _, checkWrite))

  private object ScEnum {
    object FromObject {
      def unapply(obj: ScObject): Option[ScEnum] = obj.syntheticNavigationElement match {
        case cls: ScEnum => Some(cls)
        case _ => None
      }
    }

    object FromSyntheticMethod {
      def unapply(functionDefinition: ScFunctionDefinition): Option[ScEnum] =
        if (ScalaUsageNamesUtil.enumSyntheticMethodNames.contains(functionDefinition.name))
          functionDefinition.context match {
            case cls: ScClass if cls.syntheticNavigationElement != null =>
              cls.syntheticNavigationElement match {
                case enum: ScEnum => Some(enum)
                case _ => None
              }
            case _ => None
          }
        else None
    }
  }
}
