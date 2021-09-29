package org.jetbrains.sbt.editor.documentationProvider

import com.intellij.lang.documentation.{AbstractDocumentationProvider, DocumentationMarkup}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.text.{HtmlBuilder, HtmlChunk}
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel
import org.jetbrains.sbt.language.psi.SbtScalacOptionDocHolder
import org.jetbrains.sbt.language.utils.SbtScalacOptionUtils
import org.jetbrains.sbt.language.utils.SbtScalacOptionUtils.{getScalacOptionsForLiteralValue, withScalacOption}

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

  private def wrapInDocHolder(str: ScStringLiteral): PsiElement = {
    val options = getScalacOptionsForLiteralValue(str)

    if (options.isEmpty) null
    else new SbtScalacOptionDocHolder(options)(str.getProject)
  }

  private def generateScalacOptionDoc(docHolder: SbtScalacOptionDocHolder): String = {
    val projectVersions = SbtScalacOptionUtils.projectVersionsSorted(docHolder.getProject, reverse = true)
    val options = docHolder.options

    val descriptions = options.map(_.descriptions).reduce(_ ++ _)
    val choices = options.map(_.choices).reduce(_ ++ _)
    val defaultValues = (for {
      option <- options
      defaultValue <- option.defaultValue.toSeq
      version <- option.scalaVersions
    } yield version -> defaultValue).toMap

    val builder = new HtmlBuilder
    appendDefinition(builder, docHolder.getText)

    val contentBuilder = new HtmlBuilder
    projectVersions.foreach { version =>
      appendVersionSpecificSections(contentBuilder, version, descriptions, choices, defaultValues)
    }
    val content = contentBuilder.wrapWith(DocumentationMarkup.CONTENT_ELEMENT)
    builder.append(content)

    builder.toString
  }

  private def appendDefinition(builder: HtmlBuilder, definitionContent: String): Unit = {
    val definition = new HtmlBuilder()
      .append(HtmlChunk.text(definitionContent).bold())
      .wrapWith("pre")
      .wrapWith(DocumentationMarkup.DEFINITION_ELEMENT)

    builder.append(definition)
  }

  private def appendVersionSpecificSections(builder: HtmlBuilder, version: ScalaLanguageLevel,
                                            descriptions: Map[ScalaLanguageLevel, String],
                                            choices: Map[ScalaLanguageLevel, Set[String]],
                                            defaultValues: Map[ScalaLanguageLevel, String]): Unit =
    (descriptions.get(version), choices.get(version), defaultValues.get(version)) match {
      case (None, None, None) =>
      case (maybeDescription, maybeChoices, maybeDefaultValue) =>
        builder.append(version.getVersion)

        val sectionsBuilder = new HtmlBuilder

        maybeDescription.foreach(appendSection(sectionsBuilder, "Description", _))
        maybeChoices.filter(_.nonEmpty).map(_.toList.sorted.mkString("[", ", ", "]"))
          .foreach(appendSection(sectionsBuilder, "Choices", _))
        maybeDefaultValue.foreach(appendSection(sectionsBuilder, "Default value", _))

        val sections = sectionsBuilder.wrapWith(DocumentationMarkup.SECTIONS_TABLE)

        builder
          .append(sections)
          .br()
    }

  private def appendSection(builder: HtmlBuilder, sectionName: String, sectionContent: String): Unit = {
    val headerCell = DocumentationMarkup.SECTION_HEADER_CELL.child(HtmlChunk.text(sectionName).wrapWith("p"))
    val contentCell = DocumentationMarkup.SECTION_CONTENT_CELL.addText(sectionContent)
    builder.append(HtmlChunk.tag("tr").children(headerCell, contentCell))
  }
}
