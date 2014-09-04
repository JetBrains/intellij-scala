package org.jetbrains.plugins.scala
package codeInspection.parameters

import com.intellij.codeInspection.{LocalQuickFix, ProblemDescriptor}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall
import org.jetbrains.plugins.scala.util.IntentionUtils

/**
 * @author Ksenia.Sautina
 * @since 5/10/12
 */

class NameBooleanParametersQuickFix(expr: ScMethodCall, element: ScLiteral) extends LocalQuickFix {
  def getName = "Name boolean parameters"

  def getFamilyName = "Name boolean parameters"

  def applyFix(project: Project, descriptor: ProblemDescriptor) {
    if (!element.isValid) return

    IntentionUtils.check(element, true) match {
      case Some(x) => x()
      case None =>
    }
  }
}
