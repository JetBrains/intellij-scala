package org.jetbrains.sbt.language.completion

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementBuilder}
import com.intellij.codeInsight.template._
import com.intellij.codeInsight.template.impl.ConstantNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, inWriteAction}
import org.jetbrains.plugins.scala.lang.completion.{CaptureExt, positionFromParameters}
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
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
    psiElement.inside(SbtPsiElementPatterns.scalacOptionsPattern)

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
    override def handleInsert(context: InsertionContext, item: LookupElement): Unit = {
      context.commitDocument()

      val elem = context.getFile.findElementAt(context.getStartOffset)

      elem.getContext match {
        case ref: ScReferenceExpression if option.flag.startsWith("-") =>
          // rewrite `-flag`, `--flag` to "-flag" and "--flag" respectively
          // handle `-foo-bar-baz` and `--foo-bar-baz` cases as well
          val startOffset = ref.startOffset
          val endOffset = ref.endOffset + option.flag.dropWhile(_ == '-').length
          doHandleInsert(context)(startOffset, endOffset)
        case str: ScStringLiteral =>
          // handle cases when string literal is invalid. E.g.: `"-flag` -> `"-flag"`
          doHandleInsert(context)(str.startOffset, str.endOffset)
        case _ =>
      }
    }

    private def doHandleInsert(context: InsertionContext)(startOffset: Int, endOffset: Int): Unit = {
      inWriteAction {
        context.getDocument.replaceString(startOffset, endOffset, option.getText)
      }

      if (option.argType == ArgType.No) return

      // if option has arguments, build and run interactive template
      context.commitDocument()

      val offsetBeforeClosingQuote = startOffset + option.getText.length - 1

      val templateContainerElement = context.getFile.findElementAt(offsetBeforeClosingQuote)

      val builder = TemplateBuilderFactory.getInstance()
        .createTemplateBuilder(templateContainerElement)
        .asInstanceOf[TemplateBuilderImpl]

      def replaceRange(offset: Int, len: Int, expr: Expression = scalacOptionArgumentExpression(option, scalaVersions)): Unit =
        builder.replaceRange(TextRange.from(offset, len), expr)

      option.argType match {
        case ArgType.OneAfterPrefix(prefix) =>
          val argument = option.flag.substring(prefix.length)

          // for options like -J<flag> create one template variable: -J`<flag>`
          // for options like -Dproperty=value create multiple variables: -D`property`=`value`
          argument.split('=').foldLeft(startOffset + 1 + prefix.length) {
            case (offset, variableText) =>
              replaceRange(offset, variableText.length, new ConstantNode(variableText))
              // next variable offset skipping `=`
              offset + variableText.length + 1
          }
        case ArgType.Multiple =>
          // remove `"` to be able to add variables BEFORE quote
          replaceRange(offsetBeforeClosingQuote, len = 1)
        case _ =>
          replaceRange(offsetBeforeClosingQuote, len = 0)
      }

      val template = builder.buildTemplate()
      context.getDocument.replaceString(templateContainerElement.startOffset, templateContainerElement.endOffset, "")
      context.getEditor.getCaretModel.moveToOffset(templateContainerElement.startOffset)

      // TODO: is there a better way of doing "vararg" style insertion?
      if (option.argType == ArgType.Multiple) {
        option.choices.tail.foreach { case (choice, _) =>
          template.addVariable(choice, scalacOptionArgumentExpression(option, scalaVersions, isFirst = false), null, false)
        }
        template.addTextSegment("\"")
      }

      TemplateManager.getInstance(context.getProject).startTemplate(context.getEditor, template)
    }
  }

  /** Expression used in [[com.intellij.codeInsight.template.Template]] */
  private def scalacOptionArgumentExpression(option: SbtScalacOptionInfo, projectScalaVersions: List[String],
                                             isFirst: Boolean = true): Expression = {
    val text = if (isFirst) option.defaultValue.getOrElse("???") else ""

    val lookupItems = option.choices.toList.flatMap { case (choice, scalaVersions) =>
      val matchingVersions = projectScalaVersions.filter(version => scalaVersions.exists(_.getVersion == version))

      Option.when(matchingVersions.nonEmpty) {
        LookupElementBuilder.create(choice)
          .withTailText(matchingVersions.mkString(" (", ", ", ")"))
          .withInsertHandler { (context, _) =>
            context.commitDocument()
            if (!isFirst) inWriteAction(context.getDocument.insertString(context.getStartOffset, ","))
          }
          .bold()
      }
    }

    new ConstantNode(text).withLookupItems(lookupItems.asJava)
  }

}
