package org.jetbrains.sbt
package editor.documentationProvider

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocumentationProvider
import org.jetbrains.plugins.scala.extensions.PsiElementExt
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
    scalaDoc.map { doc => appendToScalaDoc(doc, extractDoc(element))}.orNull
  }

  override def generateDoc(element: PsiElement, originalElement: PsiElement): String = {
    val scalaDoc = Option(scalaDocProvider.generateDoc(element, originalElement))
    scalaDoc.map { doc => appendToScalaDoc(doc, extractDoc(element))}.orNull
  }


  private def appendToScalaDoc(scalaDoc: String, sbtDoc: String): String =
    (scalaDoc.replace("</body></html>", "") + sbtDoc) + "</body></html>"

  private def extractDoc(element: PsiElement): String = element match {
    case settingKey: ScNamedElement if isElementInSbtFile(element) =>
      extractDocFromSettingKey(settingKey)
    case _ =>
      ""
  }

  private def isElementInSbtFile(element: PsiElement): Boolean =
    Option(element).safeMap(_.getContainingFile).fold(false)(_.getFileType.getName != Sbt.Name)

  private def extractDocFromSettingKey(settingKey: ScNamedElement): String = {
    val keyDefinition = findSettingKeyDefinition(settingKey)
    val keyDefinitionArgs = keyDefinition.fold(Seq.empty[ScExpression])(getKeyDefinitionArgs)
    val argStrings = keyDefinitionArgs.flatMap(argToString)

    val doc = keyDefinitionArgs.headOption match {
      case Some(_: ScLiteral) => getDocForNewKeyDefinition(argStrings)
      case Some(_: ScReferenceExpressionImpl) => getDocForKeyReference(argStrings)
      case _ => None
    }

    doc.getOrElse("")
  }

  private def findSettingKeyDefinition(settingKey: ScNamedElement): Option[ScPatternDefinition] =
    Option(settingKey.getNavigationElement)
      .safeMap(_.getParent)
      .safeMap(_.getParent)
      .collect { case s: ScPatternDefinition => s }

  private def getKeyDefinitionArgs(keyDefinition: ScPatternDefinition): Seq[ScExpression] =
    keyDefinition.lastChild match {
      case Some(call: ScMethodCall) => call.argumentExpressions
      case _ => Seq.empty
    }

  private def argToString(arg: ScExpression): Option[String] = arg match {
    case ScLiteralImpl.string(str) =>
      Some(str)
    case ScInfixExpr(lOp, _, rOp) =>
      val str = argToString(lOp).getOrElse("") ++
        argToString(rOp).getOrElse("")
      if (str.nonEmpty) Some(str) else None
    case refExpr: ScReferenceExpression =>
      Some(refExpr.getText)
    case _ =>
      None
  }

  private def getDocForNewKeyDefinition(docs: Seq[String]): Option[String] =
    // val someKey = SettingKey[Unit]("some-key", "Here are docs for some-key")
    docs.lift(1).map("<br/><b>" + _ + "</b>")

  private def getDocForKeyReference(docs: Seq[String]): Option[String] =
    // val someKey = SettingKey[Unit](someOtherKey)
    docs.headOption.map("<br/><b><i>" + _ + "</i></b>")
}
