package org.jetbrains.plugins.scala.format

import com.intellij.psi.{PsiClass, PsiElement, PsiMethod}
import org.jetbrains.plugins.scala.extensions.{&&, ContainingClass, PsiClassExt, PsiReferenceEx}
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject

object FormattedPrintStringParser extends StringParser {
  override def parse(element: PsiElement): Option[Seq[StringPart]] = {
    extractPrintCall(element).map(p => FormattedStringParser.parseFormatCall(p._1, p._2))
  }

  def extractPrintCall(element: PsiElement): Option[(ScStringLiteral, Seq[ScExpression])] = Some(element) collect {
    // printf("%d", 1)
    case MethodInvocation(PsiReferenceEx.resolve((f: ScFunction) &&
      ContainingClass(owner: ScObject)), Seq(literal: ScStringLiteral, args @  _*))
      if isPrintfMethod(owner.qualifiedName, f.name) =>
      (literal, args)

    // System.out.printf("%d", 1)
    case MethodInvocation(PsiReferenceEx.resolve((f: PsiMethod) &&
      ContainingClass(owner: PsiClass)), Seq(literal: ScStringLiteral, args @  _*))
      if isPrintStreamPrintfMethod(owner.qualifiedName, f.getName) =>
      (literal, args)
  }

  private def isPrintStreamPrintfMethod(holder: String, method: String) =
    holder == "java.io.PrintStream" && method == "printf"

  private def isPrintfMethod(holder: String, method: String) =
    holder == "scala.Predef" && method == "printf"
}
