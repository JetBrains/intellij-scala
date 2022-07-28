package org.jetbrains.plugins.scala
package codeInspection
package methodSignature
package quickfix

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

final class RemoveParentheses(function: ScFunction)
  extends AbstractFixOnPsiElement(ScalaInspectionBundle.message("redundant.parentheses"), function) {

  override protected def doApplyFix(function: ScFunction)
                                   (implicit project: Project): Unit = {
    val paramClauses = function.paramClauses
    val clauses = paramClauses.clauses

    for {
      from <- clauses.headOption
      to <- clauses.lastOption
    } paramClauses.deleteChildRange(from, to)
  }
}
