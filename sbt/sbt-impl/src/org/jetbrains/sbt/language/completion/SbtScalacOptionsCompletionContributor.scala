package org.jetbrains.sbt.language.completion

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementBuilder}
import com.intellij.codeInsight.template._
import com.intellij.codeInsight.template.impl.ConstantNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.annotator.TemplateUtils
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.completion.{CaptureExt, PsiElementPatternExt, positionFromParameters}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel
import org.jetbrains.sbt.language.completion.SbtScalacOptionsCompletionContributor._
import org.jetbrains.sbt.language.psi.SbtScalacOptionDocHolder
import org.jetbrains.sbt.language.utils.SbtScalacOptionInfo.ArgType
import org.jetbrains.sbt.language.utils.{SbtScalacOptionInfo, SbtScalacOptionUtils}

import scala.jdk.CollectionConverters._

class SbtScalacOptionsCompletionContributor extends CompletionContributor {
  extend(CompletionType.BASIC, PATTERN, completionProvider)
}

object SbtScalacOptionsCompletionContributor {
  private val PATTERN = (SbtPsiElementPatterns.sbtFilePattern || SbtPsiElementPatterns.scalaFilePattern) &&
    (psiElement.inside(SbtPsiElementPatterns.scalacOptionsReferencePattern).notAfterLeafSkippingWhitespaceComment(ScalaTokenTypes.tDOT) ||
      psiElement.inside(SbtPsiElementPatterns.scalacOptionsStringLiteralPattern))

  private val completionProvider = new CompletionProvider[CompletionParameters] {
    override def addCompletions(parameters: CompletionParameters, context: ProcessingContext, resultSet: CompletionResultSet): Unit = {
      val place = positionFromParameters(parameters)
      implicit val project: Project = place.getProject

      val cleanPrefix = place.getText
        .stripPrefix("\"")
        .dropWhile(_ == '-') // remove '-', '--', etc. from search
        .stripSuffix("\"")
        .replace(CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED, "")

      val newResultSet = resultSet.withPrefixMatcher(cleanPrefix)

      val scalaVersions = SbtScalacOptionUtils
        .projectVersionsSorted(place.getProject, reverse = true)

      val elements = SbtScalacOptionUtils
        .getScalacOptions
        .flatMap(lookupElementMatchingVersions(_, scalaVersions))
        .asJava

      newResultSet.addAllElements(elements)
    }
  }

  private def lookupElementMatchingVersions(option: SbtScalacOptionInfo, scalaVersions: Seq[ScalaLanguageLevel])(implicit project: Project): Option[LookupElement] = {
    val matchingVersions = scalaVersions.filter(option.scalaVersions)

    Option.when(matchingVersions.nonEmpty) {
      LookupElementBuilder.create(option, option.flag)
        .withPresentableText(option.getText)
        .withTailText(matchingVersions.map(_.getVersion).mkString(" (", ", ", ")"))
        .withInsertHandler(new ScalacOptionInsertHandler(option))
        .withPsiElement(new SbtScalacOptionDocHolder(option))
        .withCaseSensitivity(false)
        .bold()
    }
  }

  private class ScalacOptionInsertHandler(option: SbtScalacOptionInfo) extends InsertHandler[LookupElement] {
    override def handleInsert(ctx: InsertionContext, item: LookupElement): Unit = {
      implicit val context: InsertionContext = ctx
      context.commitDocument()

      val elem = context.getFile.findElementAt(context.getStartOffset)

      elem.getContext match {
        case _: ScReferenceExpression if option.flag.startsWith("-") =>
          // rewrite `-flag`, `--flag` to "-flag" and "--flag" respectively
          // handle `-foo-bar-baz` and `--foo-bar-baz` cases as well
          doHandleInsert(context.getStartOffset, context.getTailOffset)
        case str: ScStringLiteral =>
          // handle cases when string literal is invalid. E.g.: `"-flag` -> `"-flag"`
          doHandleInsert(str.startOffset, str.endOffset)
        case _ =>
      }
    }

    private def doHandleInsert(startOffset: Int, endOffset: Int)(implicit context: InsertionContext): Unit = {
      val newStartOffset = insertOption(startOffset, endOffset)

      (option.argType, option.defaultValue) match {
        case (ArgType.No, _) =>
        case (ArgType.OneAfterPrefix(prefix), _) =>
          runPrefixedOptionArgumentsTemplate(newStartOffset, prefix)
        case (_, Some(defaultValue)) =>
          runOptionArgumentsTemplate(newStartOffset, defaultValue)
        case _ =>
          context.getEditor.getCaretModel.moveToOffset(newStartOffset + option.getText.length - 1)
      }
    }

    private def insertOption(startOffset: Int, endOffset: Int)(implicit context: InsertionContext): Int = {
      context.getDocument.replaceString(startOffset, endOffset, option.getText)

      option.argType match {
        case ArgType.OneSeparate =>
          context.commitDocument()

          val element = context.getFile.findElementAt(startOffset)
          val parent = SbtScalacOptionUtils.getScalacOptionsSbtSettingParent(element)

          parent match {
            // simple case `scalacOptions +=/-= "option", "argument"`
            // rewrite to `scalacOptions ++=/--= Seq("option", "argument")`
            case Some(expr) if SbtScalacOptionUtils.SINGLE_OPS(expr.operation.refName) && expr.right.startOffset == element.startOffset =>
              context.getDocument.replaceString(startOffset, startOffset + option.getText.length, s"Seq(${option.getText})")
              context.commitDocument()
              val op = expr.operation.refName
              expr.operation.replace(ScalaPsiElementFactory.createElementFromText(op.prepended(op.head), expr)(expr.projectContext))
              PsiDocumentManager.getInstance(context.getProject).doPostponedOperationsAndUnblockDocument(context.getDocument)
              startOffset + 5 // '+' or '-' in operation + 'Seq('
            case _ =>
              startOffset
          }

        case _ =>
          startOffset
      }
    }

    private def runPrefixedOptionArgumentsTemplate(offset: Int, prefix: String)(implicit context: InsertionContext): Unit =
      runOptionArgumentsTemplate(offset) { (builder, _) =>
        val argumentText = option.flag.substring(prefix.length)

        builder.replaceRange(
          TextRange.from(offset + prefix.length + 1, argumentText.length),
          new ConstantNode(argumentText)
        )
      }

    private def runOptionArgumentsTemplate(offset: Int, defaultValue: String)(implicit context: InsertionContext): Unit =
      runOptionArgumentsTemplate(offset) { (builder, offsetBeforeClosingQuote) =>

        builder.replaceRange(
          TextRange.from(offsetBeforeClosingQuote, 0),
          new ConstantNode(defaultValue)
        )
      }

    private def runOptionArgumentsTemplate(offset: Int)(replaceRange: (TemplateBuilderImpl, Int) => Unit)(implicit context: InsertionContext): Unit = {
      context.commitDocument()

      val offsetBeforeClosingQuote = offset + option.getText.length - 1
      val templateContainerElement = context.getFile.findElementAt(offsetBeforeClosingQuote)

      val builder = TemplateBuilderFactory.getInstance()
        .createTemplateBuilder(templateContainerElement)
        .asInstanceOf[TemplateBuilderImpl]

      replaceRange(builder, offsetBeforeClosingQuote)

      val template = builder.buildTemplate()
      TemplateUtils.startTemplateAtElement(context.getEditor, template, templateContainerElement)
    }
  }

}
