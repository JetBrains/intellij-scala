package org.jetbrains.plugins.scala.format

import com.intellij.psi.{PsiClass, PsiMethod, PsiElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScExpression}
import org.jetbrains.plugins.scala.extensions.{ContainingClass, &&, PsiReferenceEx}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject

/**
 * Pavel Fatin
 */

object PrintStringParser extends StringParser {
  def parse(element: PsiElement) = {
    extractPrintCall(element).map(p => FormattedStringParser.parseFormatCall(p._1, p._2))
  }

  private def extractPrintCall(element: PsiElement): Option[(ScLiteral, Seq[ScExpression])] = Some(element) collect {
    // printf("%d", 1)
    case MethodInvocation(PsiReferenceEx.resolve((f: ScFunction) &&
            ContainingClass(owner: ScObject)), Seq(literal: ScLiteral, args @  _*))
      if literal.isString && isPrintfMethod(owner.qualifiedName, f.name) =>
      (literal, args)

    // System.out.printf("%d", 1)
    case MethodInvocation(PsiReferenceEx.resolve((f: PsiMethod) &&
            ContainingClass(owner: PsiClass)), Seq(literal: ScLiteral, args @  _*))
      if literal.isString && isPrintStreamPrintfMethod(owner.getQualifiedName, f.getName) =>
      (literal, args)
  }

  private def isPrintStreamPrintfMethod(holder: String, method: String) =
    holder == "java.io.PrintStream" && method == "printf"

  private def isPrintfMethod(holder: String, method: String) =
    holder == "scala.Predef" && method == "printf"
}
