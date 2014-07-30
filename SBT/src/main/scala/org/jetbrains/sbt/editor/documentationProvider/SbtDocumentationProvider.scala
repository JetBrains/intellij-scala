package org.jetbrains.sbt
package editor.documentationProvider

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocumentationProvider
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScReferenceExpression, ScExpression, ScInfixExpr, ScMethodCall}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

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

        val navigationElement = Option(element.getNavigationElement)
        val keyDefinition = navigationElement
                .map { _.getParent }
                .map { _.getParent }
                .filter { _.isInstanceOf[ScPatternDefinition] }
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
        if (docs.length < 2) return null
        scalaDoc + "\n<b>" + docs(1) + "</b>"
      case _ => null
    }
  }
}
