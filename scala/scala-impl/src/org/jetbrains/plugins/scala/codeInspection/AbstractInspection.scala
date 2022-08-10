package org.jetbrains.plugins.scala
package codeInspection

import com.intellij.codeInspection._
import com.intellij.psi.{PsiElement, PsiElementVisitor}

@deprecated(AbstractInspection.DeprecationText)
abstract class AbstractInspection protected() extends LocalInspectionTool {

  protected def actionFor(implicit holder: ProblemsHolder, isOnTheFly: Boolean): PartialFunction[PsiElement, Any]

  override final def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = new PsiElementVisitor {

    override def visitElement(element: PsiElement): Unit = actionFor(holder, isOnTheFly) match {
      case action if action.isDefinedAt(element) => action(element)
      case _ =>
    }
  }

}

object AbstractInspection {
  final val DeprecationText = "use org.jetbrains.plugins.scala.codeInspection.AbstractRegisteredInspection instead"
}