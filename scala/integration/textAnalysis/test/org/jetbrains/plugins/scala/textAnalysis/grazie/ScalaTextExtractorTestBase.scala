package org.jetbrains.plugins.scala.textAnalysis.grazie

import com.intellij.grazie.text.{TextContent, TextExtractor}
import com.intellij.lexer.LayeredLexer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.tree.{IElementType, TokenSet}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile, PsiFileFactory}
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt
import org.jetbrains.plugins.scala.highlighter.ScalaSyntaxHighlighterFactory
import org.jetbrains.plugins.scala.project.ScalaFeatures
import org.jetbrains.plugins.scala.textAnalysis.grazie.ScalaTextExtractorTestBase.{assertNoSyntaxHighlightingInvalidStringTokens, buildTextWithSpecialMarkers}
import org.jetbrains.plugins.scala.util.assertions.PsiAssertions
import org.junit.Assert.*

import scala.collection.mutable
import scala.jdk.CollectionConverters.ListHasAsScala

/**
 * Originally inspired by tests in `com.intellij.grazie.text.TextExtractionTest` in IntelliJ repo
 */
abstract class ScalaTextExtractorTestBase extends BasePlatformTestCase {

  protected val TripleQuote = "\"\"\""

  protected def scalaFeatures: ScalaFeatures

  protected def extractSingleTextContent(fileText: String, offset: Int): TextContent = {
    val file = createScalaFile(fileText)
    val textContent = TextExtractor.findTextAt(file, offset, TextContent.TextDomain.ALL)
    assertNotNull(textContent)
    textContent
  }

  protected def extractAllTextContents(fileText: String, offset: Int, psi: Class[? <: PsiElement]): Seq[TextContent] = {
    val file = createScalaFile(fileText)
    val element = PsiTreeUtil.findElementOfClassAtOffset(file, offset, psi, false)
    TextExtractor.findTextsAt(element, TextContent.TextDomain.ALL).asScala.toSeq
  }

  //
  // Text content extraction
  //

  protected def extractSingleTextContentAtOffsetAndPresent(fileText: String, offset: Int): String = {
    val file = createScalaFile(fileText)
    val textContent = TextExtractor.findTextAt(file, offset, TextContent.TextDomain.ALL)
    assertNotNull(textContent)
    buildTextWithSpecialMarkers(textContent)
  }

  protected def createScalaFile(fileText: String): PsiFile =
    createScalaFile("Dummy.scala", fileText)

  protected def createScalaFile(
    fileName: String,
    fileText: String,
    createPhysicalFile: Boolean = false
  ): PsiFile = {
    val fileType = ScalaFileType.INSTANCE
    val project = getProject
    val scalaFeatures = this.scalaFeatures
    // NOTE: we can't use ScalaPsiElementFactory.createScalaFileFromText as it doesn't allow us to create a physical file (it's required for some tests)
    val file = PsiFileFactory.getInstance(project).createFileFromText(fileName, fileType, fileText, 0, createPhysicalFile)
    ScalaFeatures.setAttachedScalaFeatures(file, scalaFeatures)
    file
  }

  protected def doTestSingleTextContent(
    fileText: String,
    expectedTextContentString: String,
    failOnInvalidEscapeSequencesInTestData: Boolean = true
  ): Unit = {
    val actualTextContentString = extractSingleTextContentAndPresent(fileText, failOnInvalidEscapeSequencesInTestData = failOnInvalidEscapeSequencesInTestData)
    assertEquals(expectedTextContentString, actualTextContentString)
  }

  //
  // Text content extraction & presentation
  //
  protected def extractSingleTextContentAndPresent(
    fileText: String,
    failOnInvalidEscapeSequencesInTestData: Boolean = true
  ): String = {
    val file = createScalaFile(fileText)

    // These assertions are added mostly to ensure that hte test data is valid
    PsiAssertions.assertNoParserErrors(file)
    if (failOnInvalidEscapeSequencesInTestData) {
      assertNoSyntaxHighlightingInvalidStringTokens(file)
    }

    val textContent = extractMaxOneTextContentInFile(file)
    assertNotNull(textContent)
    buildTextWithSpecialMarkers(textContent)
  }

  @Nullable
  protected def extractMaxOneTextContentInFile(psiFile: PsiFile): TextContent = {
    val testContents = TextExtractor.findAllTextContents(psiFile.getViewProvider, TextContent.TextDomain.ALL).stream().toList
    val testContent = if (testContents.size() == 1)
      testContents.get(0)
    else if (testContents.isEmpty)
      null
    else
      throw new AssertionError(s"Expected one text content in file `${psiFile.name}`, but got ${testContents.size()}")
    testContent
  }
}

object ScalaTextExtractorTestBase {

  private val UnknownMarker = '?'
  private val MarkupMarker = '~'
  private val InvalidStringEscapeTokens = TokenSet.create(
    com.intellij.psi.StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN,
    com.intellij.psi.StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN,
  )

  private[grazie] def buildTextWithSpecialMarkers(content: TextContent): String = {
    val contentWithMarkup = content.replaceMarkupWith(MarkupMarker)
    val builder = new java.lang.StringBuilder(contentWithMarkup)
    for (i <- contentWithMarkup.length to 0 by -1) {
      if (content.hasUnknownFragmentsIn(TextRange.from(contentWithMarkup.offsetToOriginal(i), 0))) {
        builder.insert(i, UnknownMarker)
      }
    }
    builder.toString
  }

  private def assertNoSyntaxHighlightingInvalidStringTokens(file: PsiFile): Unit = {
    val tokens = parsWithHighlightingLexer(file)
    if (tokens.exists(t => InvalidStringEscapeTokens.contains(t._2))) {
      fail(s"File `${file.getName} contains invalid string escape tokens:\n${tokens.mkString("\n")}")
    }
  }

  private def parsWithHighlightingLexer(file: PsiFile): Seq[(String, IElementType)] = {
    val highlightingLexer = createHighlightingLexer(file)
    parse(file.getText, highlightingLexer)
  }

  private def createHighlightingLexer(file: PsiFile): LayeredLexer = {
    val virtualFile = file.getViewProvider.getVirtualFile
    val language = file.getLanguage
    val syntaxHighlighter = ScalaSyntaxHighlighterFactory.createScalaSyntaxHighlighter(file.getProject, virtualFile, language)
    syntaxHighlighter.getHighlightingLexer
  }

  private def parse(fileText: String, lexer: com.intellij.lexer.Lexer): Seq[(String, IElementType)] = {
    lexer.start(fileText)

    val result = new mutable.ArrayBuffer[(String, IElementType)]

    while (lexer.getTokenType != null) {
      val tokenText = lexer.getTokenText
      result += (tokenText -> lexer.getTokenType)
      lexer.advance()
    }

    result.toSeq
  }
}
