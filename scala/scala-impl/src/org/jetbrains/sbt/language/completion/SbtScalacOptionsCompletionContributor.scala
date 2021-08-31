package org.jetbrains.sbt.language.completion

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementBuilder}
import com.intellij.openapi.project.Project
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.util.ProcessingContext
import org.apache.commons.lang3.StringUtils
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, inWriteAction}
import org.jetbrains.plugins.scala.lang.completion.{CaptureExt, positionFromParameters}
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.sbt.language.completion.SbtScalacOptionsCompletionContributor._
import org.jetbrains.sbt.language.utils.SbtScalacOptionUtils.SCALAC_OPTIONS_DOC_KEY
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

      val cleanPrefix = StringUtils.removeEnd(StringUtils.removeStart(place.getText, "\""), "\"")
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

    if (matchingVersions.isEmpty) None
    else {
      // Dummy element used to provide Quick Doc for option
      val dummyElement = ScalaPsiElementFactory.createNewLine()
      dummyElement.putUserData(SCALAC_OPTIONS_DOC_KEY, option.description)

      val elem = LookupElementBuilder.create(option.flag)
        .withPresentableText(option.quoted)
        .withTailText(matchingVersions.mkString(" (", ", ", ")"))
        .withInsertHandler(new ScalacOptionInsertHandler(option))
        .withPsiElement(dummyElement)
        .bold()

      Some(elem)
    }
  }

  private class ScalacOptionInsertHandler(option: SbtScalacOptionInfo) extends InsertHandler[LookupElement] {
    override def handleInsert(context: InsertionContext, item: LookupElement): Unit = {
      context.commitDocument()

      val elem = context.getFile.findElementAt(context.getStartOffset)

      elem.getContext match {
        case ref: ScReferenceExpression if option.flag.startsWith("-") =>
          // rewrite `-flag`, `--flag` to "-flag" and "--flag" respectively
          // handle `-foo-bar-baz` and `--foo-bar-baz` cases as well
          val startOffset = ref.startOffset
          val endOffset = ref.endOffset + option.flag.dropWhile(_ == '-').length

          inWriteAction {
            context.getDocument.replaceString(startOffset, endOffset, option.quoted)
          }
        case str: ScStringLiteral =>
          // handle cases when string literal is invalid. E.g.: `"-flag` -> `"-flag"`
          inWriteAction {
            str.replace(ScalaPsiElementFactory.createElementFromText(option.quoted)(str.projectContext))
          }
        case _ =>
      }
    }
  }

}
