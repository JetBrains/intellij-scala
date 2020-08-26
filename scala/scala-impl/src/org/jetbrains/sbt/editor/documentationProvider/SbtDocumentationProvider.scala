package org.jetbrains.sbt.editor.documentationProvider

import com.intellij.lang.documentation.{AbstractDocumentationProvider, DocumentationMarkup}
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.{Nls, NonNls}
import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocumentationProvider
import org.jetbrains.plugins.scala.extensions.OptionExt
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.sbt.editor.documentationProvider.SbtDocumentationProvider._
import org.jetbrains.sbt.language.SbtFileType

import scala.jdk.CollectionConverters._

/**
 * Generates documentation from sbt key description.<br>
 * There are three types of key: SettingKey, TaskKey, InputKey<br>
 * [[https://www.scala-sbt.org/1.x/docs/Basic-Def.html#Keys]]<br><br>
 *
 * For sbt '''0.13.18''' see sbt.Structure.scala: {{{
 *   object SettingKey {
 *     def apply[T: Manifest](label: String, description: String, ...): SettingKey[T] = ...
 *     def apply[T](akey: AttributeKey[T]): SettingKey[T] = ...
 *   }
 * }}}
 * example: {{{
 *   val libraryDependencies = SettingKey[Seq[ModuleID]]("library-dependencies", "Declares managed dependencies.", APlusSetting)
 * }}}
 *
 * For sbt '''1.2.8''' see sbt.BuildSyntax.scala:  {{{
 *   def settingKey[T](description: String): SettingKey[T] = macro std.KeyMacro.settingKeyImpl[T]
 * }}}
 * example: {{{
 *   val libraryDependencies = settingKey[Seq[ModuleID]]("Declares managed dependencies.").withRank(APlusSetting)
 * }}}
 */
class SbtDocumentationProvider extends AbstractDocumentationProvider {

  private val scalaDocProvider = new ScalaDocumentationProvider

  override def getQuickNavigateInfo(element: PsiElement, originalElement: PsiElement): String =
    if (!isInSbtFile(originalElement)) null
    else generateSbtDoc(element, originalElement, scalaDocProvider.getQuickNavigateInfo).orNull

  override def generateDoc(element: PsiElement, originalElement: PsiElement): String =
    if (!isInSbtFile(originalElement)) null
    else generateSbtDoc(element, originalElement, scalaDocProvider.generateDoc).orNull

  private def generateSbtDoc(element: PsiElement, originalElement: PsiElement,
                             generateScalaDoc: (PsiElement, PsiElement) => String): Option[String] = for {
    sbtKey   <- Option(element).filterByType[ScNamedElement]
    sbtDoc   <- generateSBtDocFromSbtKey(sbtKey)
    scalaDoc <- Option(generateScalaDoc(element, originalElement))
  } yield appendToScalaDoc(scalaDoc, sbtDoc)

  private def isInSbtFile(element: PsiElement): Boolean =
    Option(element).safeMap(_.getContainingFile).exists(_.getFileType == SbtFileType)

  private def generateSBtDocFromSbtKey(key: ScNamedElement): Option[String] =
    for {
      keyDefinition      <- keyDefinition(key)
      applyMethodCall    <- keyApplyMethodCall(keyDefinition)
      args                = applyMethodCall.argumentExpressions
      descriptionElement <- descriptionArgument(args)
      description        <- descriptionText(descriptionElement)
      if description.nonEmpty
    } yield wrapIntoHtml(description)

  private def keyDefinition(key: ScNamedElement): Option[ScPatternDefinition] =
    Option(key.getNavigationElement)
      .safeMap(_.getParent)
      .safeMap(_.getParent)
      .collect { case s: ScPatternDefinition => s }

  private def keyApplyMethodCall(keyDefinition: ScPatternDefinition): Option[ScMethodCall] = {
    // last found method child will be the left-most method call in chain
    val methodCalls: Iterable[ScMethodCall] = PsiTreeUtil.findChildrenOfType(keyDefinition, classOf[ScMethodCall]).asScala
    methodCalls.lastOption.filter(isSbtKeyApplyMethodCall)
  }

  private def isSbtKeyApplyMethodCall(call: ScMethodCall): Boolean =
    Option(call.getInvokedExpr)
      .filterByType[ScGenericCall]
      .map(_.referencedExpr.getText.toLowerCase)
      .exists(SbtKeyTypes.contains)

  private def descriptionArgument(args: Iterable[ScExpression]): Option[ScExpression] =
    Some(args.toList).collect {
      case (_: ScLiteral) :: description :: _ => description //e.g. SettingKey[Unit]("some-key", "Here goes description for some-key", ...)
      case (ref: ScReferenceExpression) :: _  => ref // e.g. SettingKey(BasicKeys.watch)
      case description :: Nil                 => description //e.g. settingKey[Seq[ModuleID]]("Some description").withRank(BSetting)
    }

  @NonNls private def descriptionText(element: ScExpression): Option[String] = Some(element).collect {
    case ScInfixExpr(left, _, right) => Seq(left, right).map(descriptionText).mkString
    case ScStringLiteral(string)     => string
    case ref: ScReferenceExpression  => s"<i>${ref.getText}</i>"
  }

  private def wrapIntoHtml(@Nls description: String): String =
    DocumentationMarkup.CONTENT_START + description + DocumentationMarkup.CONTENT_END

  private def appendToScalaDoc(@Nls scalaDoc: String, @Nls sbtDoc: String): String = {
    @NonNls val closingTags = "</body></html>"
    val withoutClosingTags = scalaDoc.replace(closingTags, "")
    s"$withoutClosingTags$sbtDoc$closingTags"
  }
}

private object SbtDocumentationProvider {

  @NonNls private val SbtKeyTypes: Set[String] = Set("SettingKey", "TaskKey", "InputKey", "AttributeKey").map(_.toLowerCase)
}
