package org.jetbrains.plugins.scala.lang.completion

import com.intellij.codeInsight.completion.JavaCompletionFeatures
import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.psi.{PsiClass, PsiNamedElement}
import org.jetbrains.plugins.scala.extensions.{ClassQualifiedName, ContainingClass, OptionExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.completion.handlers.ScalaImportingInsertHandler
import org.jetbrains.plugins.scala.lang.psi.ScImportsHolder
import org.jetbrains.plugins.scala.lang.psi.api.ScPackageLike
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

package object global {

  private[global] type NameAvailability = PsiNamedElement => NameAvailabilityState

  private[global] object ThisOrCompanionObject {

    def unapply(definition: ScTypeDefinition): Option[ScObject] = definition match {
      case targetObject: ScObject => Some(targetObject)
      case _ => definition.baseCompanion.filterByType[ScObject] // todo ScalaPsiUtil.getCompanionModule / fakeCompanionModule
    }
  }

  private[global] def createGlobalTopLevelMemberInsertHandler(containingPackage: ScPackageLike) =
    new ScalaImportingInsertHandler(null) {
      override protected def qualifyAndImport(reference: ScReferenceExpression): Unit = {
        triggerGlobalMemberCompletionFeature()
        ScImportsHolder(reference).addImportForPath(containingPackage.fqn)
      }
    }

  private[global] def createGlobalTopLevelMemberInsertHandler(elementToImport: PsiNamedElement,
                                                              containingPackage: ScPackageLike) =
    new ScalaImportingInsertHandler(null) {
      override protected def qualifyAndImport(reference: ScReferenceExpression): Unit = {
        triggerGlobalMemberCompletionFeature()
        val maybePath = Option(containingPackage.fqn)
          .filter(_.nonEmpty)
          .map(_ + "." + elementToImport.name)
          .orElse(ScalaNamesUtil.qualifiedName(elementToImport))

        maybePath.foreach(ScImportsHolder(reference).addImportForPath(_))
      }
    }

  private[global] def createGlobalMemberInsertHandler(containingClass: PsiClass) =
    new ScalaImportingInsertHandler(containingClass) {

      override protected def qualifyAndImport(reference: ScReferenceExpression): Unit = {
        triggerGlobalMemberCompletionFeature()
        qualifyOnly(reference)
      }
    }

  private[global] def createGlobalMemberInsertHandler(elementToImport: PsiNamedElement,
                                                      containingClass: PsiClass): ScalaImportingInsertHandler =
    new ScalaImportingInsertHandler(containingClass) {

      override protected def qualifyAndImport(reference: ScReferenceExpression): Unit = for {
        ContainingClass(ClassQualifiedName(_)) <- Option(elementToImport.nameContext)
        holder = ScImportsHolder(reference)
      } holder.addImportForPsiNamedElement(
        elementToImport,
        null,
        Some(this.containingClass)
      )

      override protected def qualifyOnly(reference: ScReferenceExpression): Unit = {
        triggerGlobalMemberCompletionFeature()
        qualifyAndImport(reference)
      }
    }

  private[this] def triggerGlobalMemberCompletionFeature(): Unit =
    FeatureUsageTracker
      .getInstance
      .triggerFeatureUsed(JavaCompletionFeatures.GLOBAL_MEMBER_NAME)
}
