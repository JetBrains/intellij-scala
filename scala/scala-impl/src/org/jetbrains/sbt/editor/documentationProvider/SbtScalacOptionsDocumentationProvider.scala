package org.jetbrains.sbt.editor.documentationProvider

import com.intellij.lang.documentation.{AbstractDocumentationProvider, DocumentationMarkup}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel
import org.jetbrains.sbt.editor.documentationProvider.SbtScalacOptionsDocumentationProvider._
import org.jetbrains.sbt.language.psi.SbtScalacOptionDocHolder
import org.jetbrains.sbt.language.utils.SbtScalacOptionUtils.{getScalacOptionsForLiteralValue, withScalacOption}
import org.jetbrains.sbt.language.utils.{SbtDependencyUtils, SbtScalacOptionInfo}

class SbtScalacOptionsDocumentationProvider extends AbstractDocumentationProvider {
  override def generateDoc(element: PsiElement, originalElement: PsiElement): String =
    element match {
      case docHolder: SbtScalacOptionDocHolder =>
        generateScalacOptionDoc(docHolder)
      case _ => null
    }

  /**
   * If contextElement is a string corresponding to a scalac option, wrap this option in [[SbtScalacOptionDocHolder]],
   * otherwise return null
   */
  override def getCustomDocumentationElement(editor: Editor, file: PsiFile, contextElement: PsiElement,
                                             targetOffset: Int): PsiElement =
    contextElement match {
      case null => null
      case _ => withScalacOption(contextElement)(onMismatch = null, onMatch = wrapInDocHolder)
    }

  private def wrapInDocHolder(str: ScStringLiteral): PsiElement =
    getScalacOption(str)
      .map(SbtScalacOptionDocHolder(_)(str.getProject))
      .orNull

  private def generateScalacOptionDoc(docHolder: SbtScalacOptionDocHolder): String = {
    val descriptions = getDescriptions(docHolder)
    if (descriptions.isEmpty) return null

    val builder = new StringBuilder

    descriptions
      .sortBy { case (_, versions) => versions.max }(implicitly[Ordering[ScalaLanguageLevel]].reverse)
      .foreach { case (description, versions) =>
        builder.append(DocumentationMarkup.CONTENT_START)
          .append(versions.map(_.getVersion).mkString(", "))
          .append("<br>")
          .append(StringUtil.escapeXmlEntities(description))
          .append(DocumentationMarkup.CONTENT_END)
      }

    builder.result()
  }
}

object SbtScalacOptionsDocumentationProvider {
  private def mergeDescriptions(options: Seq[SbtScalacOptionInfo]): Option[SbtScalacOptionInfo] = {
    def descriptions: Map[String, Set[ScalaLanguageLevel]] =
      options.foldLeft(Map.empty[String, Set[ScalaLanguageLevel]]) { case (acc, option) =>
        (acc.keySet ++ option.descriptions.keys).map { key =>
          val values = acc.getOrElse(key, Set.empty) | option.descriptions.getOrElse(key, Set.empty)
          key -> values
        }.toMap
      }

    options.headOption.map(_.copy(descriptions = descriptions))
  }

  private def getScalacOption(str: ScStringLiteral): Option[SbtScalacOptionInfo] =
    mergeDescriptions(getScalacOptionsForLiteralValue(str))

  private def getDescriptions(docHolder: SbtScalacOptionDocHolder) = {
    val projectVersions = SbtDependencyUtils.getAllScalaVersionsOrDefault(docHolder, majorOnly = true).toSet

    docHolder.option.descriptions.toList.flatMap {
      case (description, versions) =>
        val matchingVersions = versions.filter(version => projectVersions(version.getVersion))

        Option.when(matchingVersions.nonEmpty) {
          (description, matchingVersions)
        }
    }
  }
}
