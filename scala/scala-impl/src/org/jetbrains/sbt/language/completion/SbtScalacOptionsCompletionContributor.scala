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
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.completion.{CaptureExt, positionFromParameters}
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.sbt.language.completion.SbtScalacOptionsCompletionContributor._
import org.jetbrains.sbt.language.psi.SbtScalacOptionDocHolder
import org.jetbrains.sbt.language.utils.SbtScalacOptionInfo.ArgType
import org.jetbrains.sbt.language.utils.{SbtDependencyUtils, SbtScalacOptionInfo, SbtScalacOptionUtils}

import scala.jdk.CollectionConverters._

class SbtScalacOptionsCompletionContributor extends CompletionContributor {
  extend(CompletionType.BASIC, PATTERN, completionProvider)
}

object SbtScalacOptionsCompletionContributor {
  private val PATTERN = (SbtPsiElementPatterns.sbtFilePattern || SbtPsiElementPatterns.scalaFilePattern) &&
    (psiElement.inside(SbtPsiElementPatterns.scalacOptionsReferencePattern) || psiElement.inside(SbtPsiElementPatterns.scalacOptionsStringLiteralPattern))

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

      val scalaVersions = SbtDependencyUtils.getAllScalaVersionsOrDefault(place, majorOnly = true)

      val elements = SbtScalacOptionUtils
        .getScalacOptions
        .flatMap(lookupElementMatchingVersions(_, scalaVersions))
        .asJava

      newResultSet.addAllElements(elements)
      newResultSet.stopHere()
    }
  }

  private def lookupElementMatchingVersions(option: SbtScalacOptionInfo, scalaVersions: List[String])(implicit project: Project): Option[LookupElement] = {
    val matchingVersions = scalaVersions.filter(version => option.scalaVersions.exists(_.getVersion == version))

    Option.when(matchingVersions.nonEmpty) {
      LookupElementBuilder.create(option, option.flag)
        .withPresentableText(option.getText)
        .withTailText(matchingVersions.mkString(" (", ", ", ")"))
        .withInsertHandler(new ScalacOptionInsertHandler(option, scalaVersions))
        .withPsiElement(SbtScalacOptionDocHolder(option))
        .withCaseSensitivity(false)
        .bold()
    }
  }

  private class ScalacOptionInsertHandler(option: SbtScalacOptionInfo, scalaVersions: List[String]) extends InsertHandler[LookupElement] {
    override def handleInsert(ctx: InsertionContext, item: LookupElement): Unit = {
      implicit val context: InsertionContext = ctx
      context.commitDocument()

      val elem = context.getFile.findElementAt(context.getStartOffset)

      elem.getContext match {
        case ref: ScReferenceExpression if option.flag.startsWith("-") =>
          // rewrite `-flag`, `--flag` to "-flag" and "--flag" respectively
          // handle `-foo-bar-baz` and `--foo-bar-baz` cases as well
          val startOffset = ref.startOffset
          val endOffset = ref.endOffset + option.flag.dropWhile(_ == '-').length
          doHandleInsert(startOffset, endOffset)
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

    private def insertOption(startOffset: Int, endOffset: Int)(implicit context: InsertionContext): Int =
      option.argType match {
        case ArgType.OneSeparate =>
          val element = context.getFile.findElementAt(startOffset)
          val parent = SbtScalacOptionUtils.getScalacOptionsSbtSettingParent(element)

          parent match {
            // simple case `scalacOptions += "option", "argument"`
            // rewrite to `scalacOptions ++= Seq("option", "argument")`
            case Some(expr) if expr.operation.refName == "+=" && expr.right.startOffset == element.startOffset =>
              context.getDocument.replaceString(startOffset, endOffset, s"Seq(${option.getText})")
              context.commitDocument()
              expr.operation.replace(ScalaPsiElementFactory.createElementFromText("++=")(expr.projectContext))
              PsiDocumentManager.getInstance(context.getProject).doPostponedOperationsAndUnblockDocument(context.getDocument)
              startOffset + 5 // '+' in operation + 'Seq('
            case _ =>
              context.getDocument.replaceString(startOffset, endOffset, option.getText)
              startOffset
          }

        case _ =>
          context.getDocument.replaceString(startOffset, endOffset, option.getText)
          startOffset
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
      context.getDocument.replaceString(templateContainerElement.startOffset, templateContainerElement.endOffset, "")
      context.getEditor.getCaretModel.moveToOffset(templateContainerElement.startOffset)

      TemplateManager.getInstance(context.getProject).startTemplate(context.getEditor, template)
    }
  }

}
