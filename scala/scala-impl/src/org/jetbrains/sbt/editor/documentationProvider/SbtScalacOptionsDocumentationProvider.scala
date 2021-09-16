package org.jetbrains.sbt.editor.documentationProvider

import com.intellij.lang.documentation.{AbstractDocumentationProvider, DocumentationMarkup}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.macroAnnotations.Cached
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel
import org.jetbrains.sbt.editor.documentationProvider.SbtScalacOptionsDocumentationProvider._
import org.jetbrains.sbt.language.psi.SbtScalacOptionDocHolder
import org.jetbrains.sbt.language.utils.SbtScalacOptionInfo
import org.jetbrains.sbt.language.utils.SbtScalacOptionInfo.ArgType
import org.jetbrains.sbt.language.utils.SbtScalacOptionUtils.{getScalacOptions, withScalacOption}

class SbtScalacOptionsDocumentationProvider extends AbstractDocumentationProvider {
  override def generateDoc(element: PsiElement, originalElement: PsiElement): String =
    element match {
      case SbtScalacOptionDocHolder(option) =>
        generateScalacOptionDoc(option)
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

  private def wrapInDocHolder(str: ScStringLiteral): PsiElement = {
    val value = str.getValue
    if (!value.startsWith("-")) return null

    val scalacOption = scalacOptionByFlag.get(value.split(":", 2).head).orElse {
      scalacOptionFlagsWithPrefix
        .collectFirst { case (prefix, flag) if value.startsWith(prefix) => flag }
        .flatMap(scalacOptionByFlag.get)
    }

    scalacOption
      .map(SbtScalacOptionDocHolder(_)(str.getProject))
      .orNull
  }

  private def generateScalacOptionDoc(option: SbtScalacOptionInfo): String = {
    val builder = new StringBuilder

    option.descriptions.toList
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
  @Cached(ModificationTracker.NEVER_CHANGED, null)
  private def scalacOptionFlagsWithPrefix: Seq[(String, String)] = getScalacOptions.collect {
    case SbtScalacOptionInfo(flag, _, _, ArgType.OneAfterPrefix(prefix), _, _) =>
      prefix -> flag
  }

  @Cached(ModificationTracker.NEVER_CHANGED, null)
  private def scalacOptionByFlag: Map[String, SbtScalacOptionInfo] =
    getScalacOptions.groupBy(_.flag).view.mapValues { options =>
      val descriptions = options.foldLeft(Map.empty[String, Set[ScalaLanguageLevel]]) { case (acc, option) =>
        (acc.keySet ++ option.descriptions.keys).map { key =>
          val values = acc.getOrElse(key, Set.empty) | option.descriptions.getOrElse(key, Set.empty)
          key -> values
        }.toMap
      }

      options.head.copy(descriptions = descriptions)
    }.toMap
}
