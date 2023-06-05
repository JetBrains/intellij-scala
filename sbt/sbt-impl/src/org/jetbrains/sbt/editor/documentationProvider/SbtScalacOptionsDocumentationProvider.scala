package org.jetbrains.sbt.editor.documentationProvider

import com.intellij.lang.documentation.{AbstractDocumentationProvider, DocumentationMarkup}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.text.{HtmlBuilder, HtmlChunk}
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel
import org.jetbrains.sbt.language.psi.SbtScalacOptionDocHolder
import org.jetbrains.sbt.language.utils.SbtScalacOptionUtils
import org.jetbrains.sbt.language.utils.SbtScalacOptionUtils.{getScalacOptionsForLiteralValue, withScalacOption}

import scala.annotation.nowarn
import scala.collection.mutable

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
    val builder = new HtmlBuilder
    appendContent(builder, docHolder)

    builder.toString
  }

  private def appendContent(builder: HtmlBuilder, docHolder: SbtScalacOptionDocHolder): Unit = {
    val options = docHolder.options

    val descriptions = options.map(_.descriptions).reduce(_ ++ _)
    val choices = options.map(_.choices).reduce(_ ++ _)
    val defaultValues = (for {
      option <- options
      defaultValue <- option.defaultValue.toSeq
      version <- option.scalaVersions
    } yield version -> defaultValue).toMap

    // generate details sections for each version, merge duplicates keeping insertion order
    val detailsSectionsWithVersions = new LinkedHashMultiMap[HtmlBuilderWrapper, ScalaLanguageLevel]
    SbtScalacOptionUtils
      .projectVersionsSorted(docHolder.getProject, reverse = true)
      .foreach { version =>
        generateDetailsSections(descriptions.get(version), choices.get(version), defaultValues.get(version)).foreach { details =>
          // HtmlBuilder doesn't implement equals/hashcode so it cannot be used as a map key
          detailsSectionsWithVersions.add(new HtmlBuilderWrapper(details), version)
        }
      }

    val contentBuilder = new HtmlBuilder
    detailsSectionsWithVersions.foreach { case (details, versions) =>
      contentBuilder
        .append(HtmlChunk.text(versions.map(_.getVersion).mkString(", ")).bold())
        .append(details.builder)
        .br()
    }

    val content = contentBuilder.wrapWith(DocumentationMarkup.CONTENT_ELEMENT)
    builder.append(content)
  }

  private def generateDetailsSections(description: Option[String],
                                      choices: Option[Set[String]],
                                      defaultValue: Option[String]): Option[HtmlBuilder] =
    (description, choices, defaultValue) match {
      case (None, None, None) => None
      case _ =>
        val sectionsBuilder = new HtmlBuilder

        description.foreach(appendSection(sectionsBuilder, None, _))
        choices.filter(_.nonEmpty).map(_.toList.sorted.mkString(", "))
          .foreach(appendSection(sectionsBuilder, "Arguments".toOption, _))
        defaultValue.foreach(appendSection(sectionsBuilder, "Default value".toOption, _))

        Some(sectionsBuilder)
    }

  private def appendSection(builder: HtmlBuilder, sectionName: Option[String], sectionContent: String): Unit = {
    sectionName.foreach { name =>
      val headerCell = DocumentationMarkup.SECTION_HEADER_CELL.child(HtmlChunk.text(name).wrapWith("p"))
      builder.append(HtmlChunk.tag("tr").child(headerCell))
    }

    val contentCell = DocumentationMarkup.SECTION_CONTENT_CELL.addText(sectionContent)
    builder.append(HtmlChunk.tag("tr").child(contentCell))
  }
}

@nowarn("msg=inheritance from class LinkedHashMap in package mutable is deprecated")
private class LinkedHashMultiMap[K, V] extends mutable.LinkedHashMap[K, Vector[V]] {
  def add(key: K, value: V): this.type = {
    this(key) = get(key) match {
      case Some(values) => values :+ value
      case None => Vector(value)
    }
    this
  }
}

private class HtmlBuilderWrapper(val builder: HtmlBuilder) {
  private val asString = builder.toString

  override def hashCode(): Int = asString.hashCode()

  override def equals(obj: Any): Boolean = obj match {
    case wrapper: HtmlBuilderWrapper => asString == wrapper.asString
    case _ => false
  }
}
