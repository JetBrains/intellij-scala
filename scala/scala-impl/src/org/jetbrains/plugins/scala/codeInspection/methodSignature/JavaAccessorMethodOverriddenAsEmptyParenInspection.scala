package org.jetbrains.plugins.scala
package codeInspection
package methodSignature

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

/**
 * Pavel Fatin
 */
final class JavaAccessorMethodOverriddenAsEmptyParenInspection extends AbstractInspection("Java accessor method overridden as empty-paren") {

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Unit] = {
    case f: ScFunction if f.isEmptyParen =>
      f.superMethods.headOption match {  // f.superMethod returns None for some reason
        case Some(_: ScalaPsiElement) => // do nothing
        case Some(method) if method.isAccessor =>
          holder.registerProblem(f.nameId, getDisplayName, new quickfix.RemoveParentheses(f))
        case _ =>
      }
  }
}