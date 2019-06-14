package org.jetbrains.plugins.scala.lang.completion

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion.{JavaCompletionFeatures, JavaCompletionUtil}
import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.psi.{PsiClass, PsiFile, PsiNamedElement}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiClassExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

abstract class GlobalMembersFinder {

  FeatureUsageTracker.getInstance.triggerFeatureUsed(JavaCompletionFeatures.GLOBAL_MEMBER_NAME)

  final def lookupItems(originalFile: PsiFile, reference: ScReferenceExpression): Seq[ScalaLookupItem] =
    candidates.filterNot(c => isInExcludedPackage(c.classToImport)).toSeq match {
      case Seq() => Seq.empty
      case globalCandidates =>
        val simpleElements = reference.completionVariants().toSet[ScalaLookupItem].map(_.element)
        globalCandidates.flatMap(_.createLookupItem(originalFile, simpleElements))
    }

  protected def candidates: Iterable[GlobalMemberResult]

  protected abstract class GlobalMemberResult {

    val classToImport: PsiClass

    protected val resolveResult: ScalaResolveResult
    protected val isOverloadedForClassName: Boolean
    protected val containingClass: PsiClass
    protected val elementToImport: PsiNamedElement

    def createLookupItem(originalFile: PsiFile,
                         elements: Set[PsiNamedElement]): Option[ScalaLookupItem] =
      resolveResult.getLookupElement(
        isClassName = true,
        isOverloadedForClassName = isOverloadedForClassName,
        shouldImport = shouldImport(resolveResult.element, originalFile, elements),
        containingClass = Option(containingClass)
      ).headOption.map { lookupItem =>
        lookupItem.classToImport = Some(classToImport)
        lookupItem.elementToImport = Some(elementToImport)
        lookupItem.putUserData(JavaCompletionUtil.FORCE_SHOW_SIGNATURE_ATTR, Boolean.box(true))
        lookupItem
      }

    private def shouldImport(element: PsiNamedElement,
                             originalFile: PsiFile,
                             elements: Set[PsiNamedElement]): Boolean = element.getContainingFile match {
      case `originalFile` =>
        def contextContainingClassName(element: PsiNamedElement): Option[String] =
          element.containingClassOfNameContext.flatMap(_.qualifiedName.toOption)

        //complex logic to detect static methods in same file, which we shouldn't import
        val name = element.name
        val objectNames = for {
          e <- elements
          if e.getContainingFile == originalFile && e.name == name
          className <- contextContainingClassName(e)
        } yield className

        contextContainingClassName(element).forall(!objectNames.contains(_))
      case _ => !elements.contains(element)
    }
  }

  private def isInExcludedPackage(c: PsiClass): Boolean = {
    val qName = c.qualifiedName
    if (qName == null) false
    else {
      CodeInsightSettings.getInstance.EXCLUDED_PACKAGES.exists { excludedPackage =>
        qName == excludedPackage ||  qName.startsWith(excludedPackage + ".")
      }
    }
  }
}

