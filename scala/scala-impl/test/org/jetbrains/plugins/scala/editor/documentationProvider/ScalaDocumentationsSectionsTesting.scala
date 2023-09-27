package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiFile
import org.junit.Assert._

trait ScalaDocumentationsSectionsTesting {
  self: DocumentationTesting =>

  protected def DocHtmlHead(file: PsiFile): String =
    s"""<head>
       |<style>${ScalaDocCss.value}</style>
       |<base href="${VfsUtilCore.convertToURL(file.getVirtualFile.getUrl)}">
       |</head>""".stripMargin

  protected val BodyStart = s"<body>"
  protected val BodyEnd   = "</body>"

  protected val DefinitionStart: String = DocumentationMarkup.DEFINITION_START
  protected val DefinitionEnd  : String = DocumentationMarkup.DEFINITION_END
  protected val ContentStart   : String = DocumentationMarkup.CONTENT_START
  protected val ContentEnd     : String = DocumentationMarkup.CONTENT_END
  protected val SectionsStart  : String = DocumentationMarkup.SECTIONS_START
  protected val SectionsEnd    : String = DocumentationMarkup.SECTIONS_END

  protected def doGenerateDocBodyTest(
    fileContent: String,
    expectedBody: String,
    whitespacesMode: HtmlSpacesComparisonMode = HtmlSpacesComparisonMode.IgnoreNewLinesAndCollapseSpaces
  ): Unit = {
    val actualDoc = generateDoc(fileContent)
    val actualPart = extractSectionInner(actualDoc, "body", BodyStart, BodyEnd)
    assertDocHtml(expectedBody, actualPart, whitespacesMode)
  }

  protected def doGenerateDocDefinitionTest(fileContent: String, expectedDefinition: String): Unit = {
    val actualDoc = generateDoc(fileContent)
    val actualPart = extractSectionInner(actualDoc, "definition", DefinitionStart, DefinitionEnd)
    //don't ignore new lines, definition section is wrapped with `<pre>` tag
    assertDocHtml(expectedDefinition, actualPart, HtmlSpacesComparisonMode.DontIgnore)
  }

  /** NOTE: doesn't support inner <div> tags, see [[extractSectionInner]] */
  protected def doGenerateDocContentTest(
    fileContent: String,
    expectedContent: String,
    whitespacesMode: HtmlSpacesComparisonMode = HtmlSpacesComparisonMode.IgnoreNewLinesAndCollapseSpaces
  ): Unit = {
    val actualDoc = generateDoc(fileContent)
    val actualPart = extractSectionInner(actualDoc, "content", ContentStart, ContentEnd)
    assertDocHtml(expectedContent, actualPart, whitespacesMode)
  }

  /** The same as [[doGenerateDocContentTest]] but accepts a comment without any definition */
  protected def doGenerateDocContentDanglingTest(
    commentContent: String,
    expectedContent: String,
    whitespacesMode: HtmlSpacesComparisonMode = HtmlSpacesComparisonMode.IgnoreNewLinesAndCollapseSpaces
  ): Unit =
    doGenerateDocContentTest(
      s"""$commentContent
         |class A""".stripMargin,
      expectedContent,
      whitespacesMode
    )

  protected def doGenerateDocSectionsTest(
    fileContent: String,
    expectedDoSections: String,
    whitespacesMode: HtmlSpacesComparisonMode = HtmlSpacesComparisonMode.IgnoreNewLinesAndCollapseSpaces
  ): Unit = {
    val actualDoc = generateDoc(fileContent)
    val actualPart = extractSectionInner(actualDoc, "sections", SectionsStart, SectionsEnd)
    assertDocHtml(expectedDoSections, actualPart, whitespacesMode)
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


