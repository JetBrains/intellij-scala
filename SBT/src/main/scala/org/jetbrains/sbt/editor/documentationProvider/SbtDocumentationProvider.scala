package org.jetbrains.sbt
package editor.documentationProvider

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocumentationProvider
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScReferenceExpression, ScExpression, ScInfixExpr, ScMethodCall}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScReferenceExpressionImpl

/**
 * @author Nikolay Obedin
 * @since 7/30/14.
 */
class SbtDocumentationProvider extends AbstractDocumentationProvider {

  private val scalaDocProvider = new ScalaDocumentationProvider

  override def getQuickNavigateInfo(element: PsiElement, originalElement: PsiElement): String = {
    if (originalElement.getContainingFile.getFileType.getName != Sbt.Name) return null

    element match {
      case value: ScNamedElement =>
        val scalaDoc = scalaDocProvider.getQuickNavigateInfo(element, originalElement)
        if (scalaDoc == null) return null

        val keyDefinition = Option(element.getNavigationElement)
                            .?>> { _.getParent }
                            .?>> { _.getParent }
                            .collect { case s: ScPatternDefinition => s }

        val keyArgs = keyDefinition map { _.getLastChild } match {
          case Some(call: ScMethodCall) => call.argumentExpressions
          case _ => Seq.empty
        }

        def extractDocString(expr: ScExpression): Option[String] = expr match {
          case lit: ScLiteral => lit.getValue match {
            case value: String => Some(value)
            case _ => None
          }
          case infExpr: ScInfixExpr =>
            val str = extractDocString(infExpr.lOp).getOrElse("") ++
                      extractDocString(infExpr.rOp).getOrElse("")
            if (str.nonEmpty) Some(str) else None
          case refExpr: ScReferenceExpression => Some(refExpr.getText)
          case _ => None
        }

        val docs = keyArgs flatMap extractDocString

        scalaDoc + (keyArgs.headOption match {
          case Some(_: ScLiteral) => // new key definition
            docs lift 1 map { "\n<b>" + _ + "</b>" }
          case Some(_: ScReferenceExpressionImpl) => // reference to another key
            docs lift 0 map { "\n<b><i>" + _ + "</i></b>" }
          case _ => None
        }).getOrElse("")
      case _ => null
    }
  }
}
