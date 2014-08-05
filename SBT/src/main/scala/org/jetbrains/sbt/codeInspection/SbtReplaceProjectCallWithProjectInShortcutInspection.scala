package org.jetbrains.sbt
package codeInspection

import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.AbstractInspection
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition

/**
 * @author Nikolay Obedin
 * @since 8/5/14.
 */
class SbtReplaceProjectCallWithProjectInShortcutInspection extends AbstractInspection {

  def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case defn: ScPatternDefinition =>
      if (defn.getContainingFile.getFileType.getName != Sbt.Name) return null

      (defn.expr, defn.bindings) match {
        case (Some(call: ScMethodCall), Seq(varPat: ScReferencePattern)) =>

          val visitor = new ScalaRecursiveElementVisitor {
            override def visitMethodCallExpression(call: ScMethodCall) = call match {
              case ScMethodCall(expr, Seq(nameLit: ScLiteral, pathElt))
                  if expr.getText == "Project" && nameLit.isString && nameLit.getValue == varPat.getText =>
                // TODO: add quick fix
                // TODO: put message into bundle
                holder.registerProblem(call, "Replace Project() with project.in()",
                  ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
              case _ =>
                super.visitMethodCallExpression(call)
            }
          }

          call.accept(visitor)
        case _ => // do nothing
      }
  }
}
