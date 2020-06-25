package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.lang.documentation.DocumentationMarkup
import org.junit.Assert._

trait ScalaDocumentationsSectionsTesting {
  self: DocumentationTesting =>

  protected val DocHtmlHeader = s"<head><style>${ScalaDocCss.value}</style></head>"
  protected val DocStart      = s"<html>$DocHtmlHeader<body>"
  protected val DocEnd        = "</body></html>"

  protected val DefinitionStart: String = DocumentationMarkup.DEFINITION_START
  protected val DefinitionEnd  : String = DocumentationMarkup.DEFINITION_END
  protected val ContentStart   : String = DocumentationMarkup.CONTENT_START
  protected val ContentEnd     : String = DocumentationMarkup.CONTENT_END
  protected val SectionsStart  : String = DocumentationMarkup.SECTIONS_START
  protected val SectionsEnd    : String = DocumentationMarkup.SECTIONS_END

  protected def doGenerateDocBodyTest(fileContent: String, expectedBody: String): Unit = {
    val actualDoc = generateDoc(fileContent)
    val actualPart = extractSectionInner(actualDoc, "body", DocStart, DocEnd)
    assertDocHtml(expectedBody, actualPart)
  }

  protected def doGenerateDocDefinitionTest(fileContent: String, expectedDefinition: String): Unit = {
    val actualDoc = generateDoc(fileContent)
    val actualPart = extractSectionInner(actualDoc, "definition", DefinitionStart, DefinitionEnd)
    assertDocHtml(expectedDefinition, actualPart)
  }

  /** NOTE: doesn't support inner <div> tags, see [[extractSectionInner]] */
  protected def doGenerateDocContentTest(fileContent: String, expectedContent: String): Unit = {
    val actualDoc = generateDoc(fileContent)
    val actualPart = extractSectionInner(actualDoc, "content", ContentStart, ContentEnd)
    assertDocHtml(expectedContent, actualPart)
  }

  /** The same as [[doGenerateDocContentTest]] but accepts a comment without any definition */
  protected def doGenerateDocContentDanglingTest(commentContent: String, expectedContent: String): Unit =
    doGenerateDocContentTest(
      s"""$commentContent
         |class A""".stripMargin,
      expectedContent
    )

  protected def doGenerateDocSectionsTest(fileContent: String, expectedDoSections: String): Unit = {
    val actualDoc = generateDoc(fileContent)
    val actualPart = extractSectionInner(actualDoc, "sections", SectionsStart, SectionsEnd)
    assertDocHtml(expectedDoSections, actualPart)
  }

  // NOTE: doesn't support nested tags,
  // so will not detect "<div>a <div> b </div></div>"
  // will find first closing tag: "<div>a <div> b </div>"
  private def extractSectionInner(doc: String, sectionName: String, tagStart: String, tagEnd: String): String =
    doc.indexOf(tagStart) match {
      case -1 => fail(s"no '$sectionName' section found\n$doc").asInstanceOf[Nothing]
      case idx  => doc.substring(idx + tagStart.length, doc.indexOf(tagEnd, idx))
    }
}


