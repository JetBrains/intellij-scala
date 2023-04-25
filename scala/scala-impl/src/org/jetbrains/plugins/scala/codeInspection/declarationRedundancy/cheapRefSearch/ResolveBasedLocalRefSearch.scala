package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiFile, PsiNamedElement}
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch.Search.Pipeline.ShouldProcess
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch.Search.{Method, SearchMethodResult}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAssignment, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScEnumCase, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScEnum
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

// TODO What's a good name for this class vs `TrueLocalRefSearch` ? Ultimately TrueLocalRefSearch must also use type
//  resolution to do its job. Maybe the difference is that ResolveBasedLocalRefSearch, based on .multiResolveScala,
//  doesn't find non-Scala references?

// TODO Do we still need 2 difference ref searches? Based on the above TODO, maybe we can get rid of one. Originally the
//  idea behind ScalaRefCountHolder being followed by ReferencesSearch in the context of unused declaration inspection
//  was that ScalaRefCountHolder may or may not be ready. But the class you're looking at now, is always ready. We don't
//  do any fragile annotation-phase-reference-gathering anymore. It's a normal inspection, with normal availability and
//  shouldn't have any flaky behaviour that requires a fallback mechanism, and it's made cheaper by using a cache. The
//  main question remaining is: Keep the below, or keep TrueLocalRefSearch?
//  Removing one or the other could be part of a separate ticket and effort though.

private[cheapRefSearch] final class ResolveBasedLocalRefSearch(override val shouldProcess: ShouldProcess) extends Method {

  private val cachedUsagesPerFile: java.util.Map[PsiFile, (Seq[ElementUsageWithKnownReference], Long)] =
    java.util.Collections.synchronizedMap(new java.util.WeakHashMap[PsiFile, (Seq[ElementUsageWithKnownReference], Long)])

  private def getAllUsagesInFile(file: PsiFile): Seq[ElementUsageWithKnownReference] = {

    def computeAndStore(): Seq[ElementUsageWithKnownReference] = {
      val res = computeAllUsagesInFile(file)
      cachedUsagesPerFile.put(file, (res, file.getModificationStamp))
      res
    }

    if (cachedUsagesPerFile.containsKey(file)) {
      val cachedEntry = cachedUsagesPerFile.get(file)
      if (cachedEntry._2 == file.getModificationStamp) {
        cachedUsagesPerFile.get(file)._1
      } else {
        cachedUsagesPerFile.remove(file)
        computeAndStore()
      }
    }
    else {
      computeAndStore()
    }
  }

  private def computeAllUsagesInFile(file: PsiFile): Seq[ElementUsageWithKnownReference] =
    file.depthFirst().collect { case r: ScReference => r }.toSeq
      .flatMap(ref => computeUsages(ref, ref.multiResolveScala(false)))

  def computeUsages(element: ScReference, results: Iterable[ScalaResolveResult]): Seq[ElementUsageWithKnownReference] = {

    def getUsagesForSourceAndTarget(sourceElement: ScReference, targetElement: ScNamedElement): Seq[ElementUsageWithKnownReference] =
      if (targetElement.isValid && targetElement.getContainingFile == sourceElement.getContainingFile &&
        !PsiTreeUtil.isAncestor(targetElement, sourceElement, true)) {

        sourceElement match {
          case ref: ScReferenceExpression if ScalaPsiUtil.isPossiblyAssignment(ref) =>

            val additionalWrite = ref.getContext match {
              case ScAssignment.resolvesTo(assignmentTarget) if assignmentTarget != targetElement =>
                assignmentTarget match {
                  case namedElement: ScNamedElement =>
                    Seq(ElementUsageWithKnownReference(ref, namedElement, isAssignment = true))
                  case _ => Seq.empty
                }
              case _ => Seq.empty
            }

            ElementUsageWithKnownReference(ref, targetElement, isAssignment = true) +: additionalWrite

          case _ =>
            Seq(ElementUsageWithKnownReference(sourceElement, targetElement))
        }
      } else Seq.empty

    def collectAllNamedElementTargets(element: PsiNamedElement, parentElement: Option[PsiNamedElement]): Seq[ScNamedElement] = {
      val originalsFromSynthetics = element match {
        case ScEnumCase.Original(enumCase) => Seq(enumCase)
        case ScEnum.OriginalFromObject(enum) => Seq(enum)
        case ScEnum.OriginalFromSyntheticMethod(enum) => enum.cases
        case f: ScFunctionDefinition if f.isSynthetic => Seq(f.syntheticNavigationElement).collect {
          case n: ScNamedElement => n
        }
        case _ => Seq.empty
      }
      originalsFromSynthetics ++ parentElement.toSeq :+ element
    }.collect { case n: ScNamedElement => n }

    results.filter(_ != null).toSeq.flatMap { resolveResult =>
      collectAllNamedElementTargets(resolveResult.element, resolveResult.parentElement)
        .flatMap(getUsagesForSourceAndTarget(element, _))
    }
  }

  override def searchForUsages(ctx: Search.Context): SearchMethodResult = {

    val allUsagesInFile = getAllUsagesInFile(ctx.element.getContainingFile)

    new SearchMethodResult(allUsagesInFile.filter(_.getTarget() == ctx.element), false)
  }
}
