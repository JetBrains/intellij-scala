package org.jetbrains.plugins.scala
package codeInspection

import com.intellij.codeInspection._
import com.intellij.psi.{PsiElement, PsiElementVisitor}

/**
  * Pavel Fatin
  */
abstract class AbstractInspection protected(override final val getDisplayName: String = AbstractInspection.formatName(getClass.getSimpleName))
  extends LocalInspectionTool {

  /**
    * use {@link AbstractInspection.PureFunctionVisitorVisitor#problemDescriptor(PsiElement, Option[LocalQuickFix], String, ProblemHighlightType)} instead
    */
  @Deprecated
  protected def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Any]

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = new PartialFunctionVisitor(holder)

  protected final class PureFunctionVisitorVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) extends PsiElementVisitor {

    protected def problemDescriptor(element: PsiElement,
                                    maybeQuickFix: Option[LocalQuickFix] = None,
                                    descriptionTemplate: String = getDisplayName,
                                    highlightType: ProblemHighlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING): Option[ProblemDescriptor] = {
      val fixes = maybeQuickFix match {
        case Some(quickFix) => Array(quickFix)
        case _ => LocalQuickFix.EMPTY_ARRAY
      }
      val descriptor = holder.getManager.createProblemDescriptor(element, descriptionTemplate, isOnTheFly, fixes, highlightType)
      Some(descriptor)
    }

    override def visitElement(element: PsiElement): Unit = problemDescriptor(element) match {
      case Some(descriptor) => holder.registerProblem(descriptor)
      case _ =>
    }
  }

  /**
    * use {@link AbstractInspection.PureFunctionVisitorVisitor} instead
    */
  @Deprecated
  protected final class PartialFunctionVisitor(holder: ProblemsHolder) extends PsiElementVisitor {

    override def visitElement(element: PsiElement): Unit = actionFor(holder) match {
      case action if action.isDefinedAt(element) => action(element)
      case _ =>
    }
  }

}

object AbstractInspection {

  private[this] val CapitalLetterPattern = "(?<!=.)\\p{Lu}".r
  private[this] val InspectionSuffix = "Inspection"

  private def formatName(simpleName: String): String = {
    val id = simpleName.stripSuffix(InspectionSuffix)
    CapitalLetterPattern.replaceAllIn(id, it => s" ${it.group(0).toLowerCase}")
  }
}