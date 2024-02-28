package org.jetbrains.plugins.scala.editor.documentationProvider.util

import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocCss
import org.jetbrains.plugins.scala.editor.documentationProvider.base.DocumentationProviderTestBase
import org.jetbrains.plugins.scala.editor.documentationProvider.util.ScalaDocumentationsSectionsTestingBase.skipAllNestedDivs
import org.junit.Assert._

trait ScalaDocumentationsSectionsTestingBase {
  self: DocumentationProviderTestBase =>

  protected def DocHtmlHead(file: PsiFile): String =
    s"""<head>
       |<style>${ScalaDocCss.value}</style>
       |<base href="${VfsUtilCore.convertToURL(file.getVirtualFile.getUrl)}">
       |</head>""".stripMargin

  protected val BodyStart = s"<body>"
  protected val BodyEnd = "</body>"

  protected val DefinitionStart: String = DocumentationMarkup.DEFINITION_START
  protected val DefinitionEnd: String = DocumentationMarkup.DEFINITION_END
  protected val ContentStart: String = DocumentationMarkup.CONTENT_START
  protected val ContentEnd: String = DocumentationMarkup.CONTENT_END
  protected val SectionsStart: String = DocumentationMarkup.SECTIONS_START
  protected val SectionsEnd: String = DocumentationMarkup.SECTIONS_END


  // NOTE: doesn't support nested tags,
  // so will not detect "<div>a <div> b </div></div>"
  // will find first closing tag: "<div>a <div> b </div>"
  protected def extractSectionInner(doc: String, sectionName: String, tagStart: String, tagEnd: String): String = {
    doc.indexOf(tagStart) match {
      case -1 =>
        fail(s"no '$sectionName' section found\n$doc").asInstanceOf[Nothing]
      case startIdx =>
        val lastDivIdx = skipAllNestedDivs(doc, startIdx)
        val endIndex = doc.indexOf(tagEnd, if (lastDivIdx != -1) lastDivIdx else startIdx)
        doc.substring(startIdx + tagStart.length, endIndex)
    }
  }
}

object ScalaDocumentationsSectionsTestingBase {
  private val DivOpenRegex = "<div[^>]*>".r
  private val DivCloseRegex = "</div>".r
  private val DivOpenOrCloseRegex = s"($DivOpenRegex|$DivCloseRegex)".r

  /**
   * @return -1 - if no `<div></div>` sections were found<br>
   *          index - of first character after last `</div>`
   */
  private def skipAllNestedDivs(html: String, start: Int): Int = {
    var openedDivs = 0

    var idx = start + 1
    var continue = true
    var hasAtLeastOneNestedDiv = false

    val matcher = DivOpenOrCloseRegex.pattern.matcher(html)
    while (continue && matcher.find(idx)) {
      val matchingString = matcher.group()

      val isDivOpening = DivOpenRegex.matches(matchingString)
      hasAtLeastOneNestedDiv |= isDivOpening

      openedDivs += (if (isDivOpening) 1 else -1)
      if (openedDivs >= 0) {
        idx = matcher.end()
      }
      continue = idx < html.length && openedDivs >= 0
    }

    if (hasAtLeastOneNestedDiv)
      idx
    else
      -1
  }
}

trait ScalaDocumentationsDefinitionSectionTesting extends ScalaDocumentationsSectionsTestingBase {
  self: DocumentationProviderTestBase =>

  protected def doGenerateDocDefinitionTest(fileContent: String, expectedDefinition: String): Unit = {
    val actualDoc = generateDoc(fileContent)
    val actualPart = extractSectionInner(actualDoc, "definition", DefinitionStart, DefinitionEnd)
    assertDocHtml(
      expectedDefinition,
      actualPart,
      //don't ignore new lines, definition section is wrapped with `<pre>` tag
      HtmlSpacesComparisonMode.DontIgnore
    )
  }
}

trait ScalaDocumentationsBodySectionTesting extends ScalaDocumentationsSectionsTestingBase {
  self: DocumentationProviderTestBase =>

  protected def doGenerateDocBodyTest(
    fileContent: String,
    expectedBody: String,
    whitespacesMode: HtmlSpacesComparisonMode = HtmlSpacesComparisonMode.IgnoreNewLinesAndCollapseSpaces
  ): Unit = {
    val actualDoc = generateDoc(fileContent)
    val actualPart = extractSectionInner(actualDoc, "body", BodyStart, BodyEnd)
    assertDocHtml(expectedBody, actualPart, whitespacesMode)
  }

  protected def doGenerateRenderedDocBodyTest(
    fileContent: String,
    expectedBody: String,
    whitespacesMode: HtmlSpacesComparisonMode = HtmlSpacesComparisonMode.IgnoreNewLinesAndCollapseSpaces
  ): Unit = {
    val actualDoc = generateRenderedDoc(fileContent)
    val actualPart = extractSectionInner(actualDoc, "body", BodyStart, BodyEnd)
    assertDocHtml(expectedBody, actualPart, whitespacesMode)
  }
}

trait ScalaDocumentationsScalaDocContentTesting extends ScalaDocumentationsSectionsTestingBase {
  self: DocumentationProviderTestBase =>

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
}


