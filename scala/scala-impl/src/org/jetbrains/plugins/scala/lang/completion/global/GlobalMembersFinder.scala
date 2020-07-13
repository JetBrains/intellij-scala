package org.jetbrains.plugins.scala
package lang
package completion
package global

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion.{InsertHandler, JavaCompletionFeatures, JavaCompletionUtil}
import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.psi.{PsiClass, PsiFile, PsiMember, PsiNamedElement}
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

abstract class GlobalMembersFinder protected(protected val place: ScalaPsiElement,
                                             protected val accessAll: Boolean) {

  import GlobalMembersFinder._

  protected trait GlobalMemberInsertHandler {
    this: InsertHandler[ScalaLookupItem] =>

    def triggerGlobalMemberCompletionFeature(): Unit =
      FeatureUsageTracker
        .getInstance
        .triggerFeatureUsed(JavaCompletionFeatures.GLOBAL_MEMBER_NAME)
  }

  protected final def isAccessible(member: PsiMember): Boolean =
    accessAll ||
      completion.isAccessible(member)(place)

  final def lookupItems(reference: ScReferenceExpression,
                        originalFile: PsiFile): Iterable[ScalaLookupItem] = {
    val shouldImport = new ShouldImportPredicate(reference, originalFile)

    candidates.flatMap(_.createLookupItem(shouldImport))
  }

  protected def candidates: Iterable[GlobalMemberResult]

  protected abstract class GlobalMemberResult(protected val resolveResult: ScalaResolveResult,
                                              protected val classToImport: PsiClass,
                                              containingClass: Option[PsiClass] = None) {

    final def createLookupItem(shouldImport: PsiNamedElement => Boolean): Option[ScalaLookupItem] =
      if (isApplicable)
        resolveResult.getLookupElement(
          isClassName = true,
          containingClass = containingClass
        ).flatMap { lookupItem =>
          buildItem(
            lookupItem,
            shouldImport(lookupItem.getPsiElement)
          )
        }
      else
        None

    protected def buildItem(lookupItem: ScalaLookupItem,
                            shouldImport: Boolean): Option[ScalaLookupItem] = {
      lookupItem.shouldImport = shouldImport
      lookupItem.setInsertHandler(createInsertHandler)
      lookupItem.withBooleanUserData(JavaCompletionUtil.FORCE_SHOW_SIGNATURE_ATTR)
      Some(lookupItem)
    }

    protected def createInsertHandler: InsertHandler[ScalaLookupItem]

    private def isApplicable: Boolean = Option(classToImport.qualifiedName).forall(isNotExcluded)
  }
}

object GlobalMembersFinder {

  private final class ShouldImportPredicate(reference: ScReferenceExpression,
                                            originalFile: PsiFile) extends (PsiNamedElement => Boolean) {

    private lazy val elements = reference
      .completionVariants()
      .toSet[ScalaLookupItem]
      .map(_.getPsiElement)

    override def apply(element: PsiNamedElement): Boolean = element.getContainingFile match {
      case `originalFile` =>
        element.containingClassOfNameContext.forall { containingClass =>
          //complex logic to detect static methods in the same file, which we shouldn't import
          val name = element.name
          !elements
            .filter(_.getContainingFile == originalFile)
            .filter(_.name == name)
            .flatMap(_.containingClassOfNameContext)
            .contains(containingClass)
        }
      case _ => !elements.contains(element)
    }
  }

  private def isNotExcluded(qualifiedName: String): Boolean = {
    CodeInsightSettings.getInstance.EXCLUDED_PACKAGES.forall { excludedPackage =>
      qualifiedName != excludedPackage && !qualifiedName.startsWith(excludedPackage + ".")
    }
  }
}