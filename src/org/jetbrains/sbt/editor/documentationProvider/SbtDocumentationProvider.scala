package org.jetbrains.sbt
package editor.documentationProvider

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocumentationProvider
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScInfixExpr, ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScLiteralImpl
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScReferenceExpressionImpl

/**
 * @author Nikolay Obedin
 * @since 7/30/14.
 */
class SbtDocumentationProvider extends AbstractDocumentationProvider {

  private val scalaDocProvider = new ScalaDocumentationProvider

  override def getQuickNavigateInfo(element: PsiElement, originalElement: PsiElement): String = {
    val scalaDoc = Option(scalaDocProvider.getQuickNavigateInfo(element, originalElement))
    scalaDoc.map { d => extractDoc(element, originalElement, d) }.orNull
  }

  override def generateDoc(element: PsiElement, originalElement: PsiElement): String = {
    val scalaDoc = Option(scalaDocProvider.generateDoc(element, originalElement)).map(_.replace("</body></html>", ""))
    scalaDoc.map { d => extractDoc(element, originalElement, d) + "</body></html>" }.orNull
  }

  private def extractDoc(element: PsiElement, originalElement: PsiElement, scalaDoc: String): String = {
    if (originalElement.getContainingFile.getFileType.getName != Sbt.Name) return scalaDoc

    element match {
      case value: ScNamedElement =>
        val keyDefinition = Option(element.getNavigationElement)
                            .safeMap { _.getParent }
                            .safeMap { _.getParent }
                            .collect { case s: ScPatternDefinition => s }

        val keyArgs = keyDefinition map { _.getLastChild } match {
          case Some(call: ScMethodCall) => call.argumentExpressions
          case _ => Seq.empty
        }

        def extractDocString(expr: ScExpression): Option[String] = expr match {
          case ScLiteralImpl.string(str) => Some(str)
          case ScInfixExpr(lOp, _, rOp) =>
            val str = extractDocString(lOp).getOrElse("") ++
                      extractDocString(rOp).getOrElse("")
            if (str.nonEmpty) Some(str) else None
          case refExpr: ScReferenceExpression => Some(refExpr.getText)
          case _ => None
        }

        val docs = keyArgs flatMap extractDocString

        scalaDoc + (keyArgs.headOption match {
          case Some(_: ScLiteral) => // new key definition
            docs lift 1 map { "<br/><b>" + _ + "</b>" }
          case Some(_: ScReferenceExpressionImpl) => // reference to another key
            docs lift 0 map { "</br><b><i>" + _ + "</i></b>" }
          case _ => None
        }).getOrElse("")
      case _ => scalaDoc
    }
  }
}
