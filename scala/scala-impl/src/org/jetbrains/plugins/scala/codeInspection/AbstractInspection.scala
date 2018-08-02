package org.jetbrains.plugins.scala
package codeInspection

import com.intellij.codeInspection._
import com.intellij.psi.{PsiElement, PsiElementVisitor}

/**
  * Pavel Fatin
  */
abstract class AbstractInspection protected(displayName: String = AbstractInspection.formatName(getClass))
  extends LocalInspectionTool {

  override def getDisplayName: String = displayName

  protected final def defaultDisplayName: String = super.getDisplayName

  /**
    * use [[org.jetbrains.plugins.scala.codeInspection.AbstractInspection#problemDescriptor]] instead
    */
  @Deprecated
  protected def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Any]

  protected def problemDescriptor(element: PsiElement,
                                  maybeQuickFix: Option[LocalQuickFix] = None,
                                  descriptionTemplate: String = getDisplayName,
                                  highlightType: ProblemHighlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
                                 (implicit manager: InspectionManager, isOnTheFly: Boolean): Option[ProblemDescriptor] = {
    val fixes = maybeQuickFix match {
      case Some(quickFix) => Array(quickFix)
      case _ => LocalQuickFix.EMPTY_ARRAY
    }
    val descriptor = manager.createProblemDescriptor(element, descriptionTemplate, isOnTheFly, fixes, highlightType)
    Some(descriptor)
  }

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = new PartialFunctionVisitor(holder)

  protected final class PureFunctionVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) extends PsiElementVisitor {

    override def visitElement(element: PsiElement): Unit =
      problemDescriptor(element)(holder.getManager, isOnTheFly) match {
        case Some(descriptor) => holder.registerProblem(descriptor)
        case _ =>
      }
  }

  /**
    * use [[org.jetbrains.plugins.scala.codeInspection.AbstractInspection.PureFunctionVisitor]] instead
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

  private def formatName(clazz: Class[_]): String = {
    val id = clazz.getSimpleName.stripSuffix(InspectionSuffix)
    CapitalLetterPattern.replaceAllIn(id, it => s" ${it.group(0).toLowerCase}")
  }
}