package org.jetbrains.plugins.scala.grazie

import com.intellij.grazie.text.{TextContent, TextContentBuilder, TextExtractor}
import com.intellij.lang.injection.{InjectedLanguageManager, MultiHostInjector, MultiHostRegistrar}
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.javadoc.{PsiDocComment, PsiDocTag}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiComment, PsiElement, PsiFileFactory, PsiLanguageInjectionHost}
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase.assertInstanceOf
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.lang.regexp.RegExpLanguage
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.grazie.ScalaTextExtractorTest.{TripleQuote, buildTextWithSpecialMarkers}
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.junit.Assert.*

import java.util

/**
 * Inspired by tests in `com.intellij.grazie.text.TextExtractionTest` in IntelliJ repo
 */
class ScalaTextExtractorTest extends BasePlatformTestCase {

  private def extractTextContent(fileText: String, offset: Int): TextContent =
    ScalaTextExtractorTest.extractTextContent("Dummy.scala", fileText, offset, getProject)

  private def extractTextWithUnknownFragments(fileText: String, offset: Int): String = {
    val textContent = extractTextContent(fileText, offset)
    assertNotNull(textContent)
    buildTextWithSpecialMarkers(textContent)
  }

  def testMergeAdjacentLineComments_1(): Unit = {
    assertEquals(
      """Hello. I are a very humble
        |persons.""".stripMargin.withNormalizedSeparator,
      extractTextWithUnknownFragments(
        """//Hello. I are a very humble
          |//persons.
          |
          |class C {}""".stripMargin.withNormalizedSeparator, 4
      )
    )
  }

  def testMergeAdjacentLineComments_2(): Unit = {
    assertEquals(
      """First line.
        |Third line.""".stripMargin.withNormalizedSeparator,
      extractTextWithUnknownFragments(
        s"""// First line.
           |//   ${""}
           |//   Third line.
           |""".stripMargin.withNormalizedSeparator, 4
      )
    )
  }

  def testMergeAdjacentLineComments_3(): Unit = {
    val text =
      """//1
        |//2
        |//3
        |//4""".stripMargin.withNormalizedSeparator

    val file = PsiFileFactory.getInstance(getProject).createFileFromText("Dummy.scala", ScalaFileType.INSTANCE, text)
    val textContent1 = TextExtractor.findTextAt(file, text.indexOf("1"), TextContent.TextDomain.ALL)
    val textContent2 = TextExtractor.findTextAt(file, text.indexOf("3"), TextContent.TextDomain.ALL)

    assertEquals(
      """1
        |2
        |3
        |4""".stripMargin.withNormalizedSeparator,
      textContent1.toString
    )
    assertEquals(
      textContent1,
      textContent2,
    )
  }

  def testDontMergeNonAdjacentLineComments(): Unit = {
    val text =
      """//1
        |//2
        |
        |//3
        |//4""".stripMargin.withNormalizedSeparator

    val textContent1 = extractTextContent(text, text.indexOf("1"))
    val textContent2 = extractTextContent(text, text.indexOf("3"))
    assertEquals(
      """1
        |2""".stripMargin.withNormalizedSeparator,
      textContent1.toString
    )

    assertEquals(
      """3
        |4""".stripMargin.withNormalizedSeparator,
      textContent2.toString
    )
  }

  def testScalaDoc(): Unit = {
    val docText =
      """/**
        | * Plain text line 1
        | * Plain text line 2
        | * Here's an asterisk: *
        | *
        | * Text <span> with </span> html <br> tags <br/> inside.
        | * Unknown tags1: <unknownTag>this<unknownTag>is</unknownTag>unknown</unknownTag >
        | * Unknown tags2: tags2 <unknown1>one<unknown2>unknown<unknown1>unknown</unknown2> two<p/> three<unknown1/> four</unknown1>
        | *
        | * Bold: '''bold text'''
        | * Italic: ''italic text''
        | * Monospace: `monospace text`
        | * Underline: __underlined text__
        | * Lower index: ,,lower index text,,
        | * Upper index: ^upper index text^
        | *
        | * =Header1=
        | * ===Header3===
        | *
        | * Links:
        | * Link 1 [[scala.Option]]
        | * Link 2 [[scala.Option description of class reference]]
        | * Link 3 [[https://www.scala-lang.org]]
        | * Link 4 [[https://www.scala-lang.org description o http link]]
        | *
        | * Deprecated Java-style inline elements:
        | * {@link scala.Option}
        | * {@literal literal text}
        | *
        | * List:
        | *  - list item 1
        | *  - list item 2 line 1 [[scala.Option description]]
        | *    list item 2 line 2 <span>text in span</span>
        | *  - list item 3
        | *
        | * @define macroKey macro text content (macro key name is not included)
        | * @param paramName parameter description '''bold''' (parameter name is not included)
        | * @throws Exception description of exception [[scala.Option]] (exception is not included)
        | */
        |class A
        |""".stripMargin.withNormalizedSeparator

    val text0 = extractTextContent(docText, 10)
    assertEquals(
      """Plain text line 1
        |Plain text line 2
        |Here's an asterisk: *
        |
        |Text ? html ? tags ? inside.
        |Unknown tags1: ?
        |Unknown tags2: tags2 ?
        |
        |Bold: bold text
        |Italic: italic text
        |Monospace: monospace text
        |Underline: underlined text
        |Lower index: lower index text
        |Upper index: upper index text
        |
        |Header1
        |Header3
        |
        |Links:
        |Link 1?
        |Link 2 ? description of class reference?
        |Link 3?
        |Link 4 ? description o http link?
        |
        |Deprecated Java-style inline elements:
        |?
        |?
        |
        |List:
        |?list item 1
        |?list item 2 line 1 ? description?
        |list item 2 line 2 ?
        |?list item 3""".stripMargin.withNormalizedSeparator,
      buildTextWithSpecialMarkers(text0)
    )

    val text1 = extractTextWithUnknownFragments(docText, docText.indexOf("macro text"))
    assertEquals("macro text content (macro key name is not included)", text1)

    val text2 = extractTextWithUnknownFragments(docText, docText.indexOf("parameter description"))
    assertEquals("parameter description bold (parameter name is not included)", text2)

    val text3 = extractTextWithUnknownFragments(docText, docText.indexOf("description of exception"))
    assertEquals("description of exception ? (exception is not included)", text3)
  }

  def testScalaDoc_CodeSnippet(): Unit = {
    val docText =
      """/**
        | * Before snippet
        | * {{{
        | *   val value = "This is example shouldn't checked"
        | * }}}
        | * After snippet
        | */
        |""".stripMargin.withNormalizedSeparator

    assertEquals("Before snippet\n\n\n\nAfter snippet", extractTextWithUnknownFragments(docText, docText.indexOf("snippet")))
  }

  def testStringLiteral_Plain(): Unit = {
    val text = """  "  aaa ${2 + 2} bbb \\ \t \n ccc $value ddd \\u0041 eee  "  """
    assertEquals("aaa ${2 + 2} bbb \\\\ \\t \\n ccc $value ddd \\\\u0041 eee", extractTextWithUnknownFragments(text, text.indexOf("aaa")))
  }

  def testStringLiteral_Interpolated_S(): Unit = {
    val text = """  s"  aaa ${2 + 2} bbb \\ \t \n ccc $value ddd \\u0041 eee  "  """
    assertEquals("aaa ? bbb \\\\ \\t \\n ccc ? ddd \\\\u0041 eee", extractTextWithUnknownFragments(text, text.indexOf("aaa")))
  }

  def testStringLiteral_Interpolated_Raw(): Unit = {
    val text = """  raw"  aaa ${2 + 2} bbb \\ \t \n ccc $value ddd \\u0041 eee  "  """
    assertEquals("aaa ? bbb \\\\ \\t \\n ccc ? ddd \\\\u0041 eee", extractTextWithUnknownFragments(text, text.indexOf("aaa")))
  }

  def testStringLiteral_Multiline_OneLine_Plain(): Unit = {
    val text = """  '''  aaa ${2 + 2} bbb \\ \t \n ccc $value ddd \\u0041 eee  '''  """.replace("'''", "\"\"\"")
    assertEquals("aaa ${2 + 2} bbb \\\\ \\t \\n ccc $value ddd \\\\u0041 eee", extractTextWithUnknownFragments(text, text.indexOf("aaa")))
  }

  def testStringLiteral_Multiline_OneLine_Interpolated_S(): Unit = {
    val text = """  s'''  aaa ${2 + 2} bbb \\ \t \n ccc $value ddd \\u0041 eee  '''  """.replace("'''", "\"\"\"")
    assertEquals("aaa ? bbb \\\\ \\t \\n ccc ? ddd \\\\u0041 eee", extractTextWithUnknownFragments(text, text.indexOf("aaa")))
  }

  def testStringLiteral_Multiline_OneLine_Interpolated_Raw(): Unit = {
    val text = """  raw'''  aaa ${2 + 2} bbb \\ \t \n ccc $value ddd \\u0041 eee  '''  """.replace("'''", "\"\"\"")
    assertEquals("aaa ? bbb \\\\ \\t \\n ccc ? ddd \\\\u0041 eee", extractTextWithUnknownFragments(text, text.indexOf("aaa")))
  }

  def testStringLiteral_Multiline_Plain(): Unit = {
    val text =
      s"""  $TripleQuote  first line
         |   second line
         |   third line
         |  $TripleQuote  """.stripMargin.withNormalizedSeparator
    assertEquals("first line\n   second line\n   third line", extractTextWithUnknownFragments(text, text.indexOf("first line")))
  }

  def testStringLiteral_Multiline_Plain_WithStripMargin(): Unit = {
    val text =
      s"""${TripleQuote}first line
         *  |second line
         *  |third line
         *  |$TripleQuote.stripMargin""".stripMargin('*').withNormalizedSeparator
    assertEquals("first line\nsecond line\nthird line\n", extractTextWithUnknownFragments(text, text.indexOf("first line")))
  }

  def testStringLiteral_Multiline_Plain_WithStripMargin_WithCustomMargin(): Unit = {
    val text =
      s"""${TripleQuote}first line
         |  #second line
         |  #third line
         |  #$TripleQuote.stripMargin('#')""".stripMargin.withNormalizedSeparator
    assertEquals("first line\nsecond line\nthird line\n", extractTextWithUnknownFragments(text, text.indexOf("first line")))
  }

  def testStringLiteral_Multiline_Plain_WithStripMargin_WithCustomMargin_1(): Unit = {
    val text =
      s"""${TripleQuote}first line
         *  |second line
         *  |third line
         *  |$TripleQuote.stripMargin('#')""".stripMargin('*').withNormalizedSeparator
    assertEquals("first line\n  |second line\n  |third line\n  |", extractTextWithUnknownFragments(text, text.indexOf("first line")))
  }

  def testStringLiteral_Multiline_WithStripMargin_FirstLineBlank(): Unit = {
    val text =
      s"""val value =
         *
         *$TripleQuote
         *  |second line
         *  |third line
         *  |   $TripleQuote.stripMargin""".stripMargin('*').withNormalizedSeparator
    assertEquals("second line\nthird line\n", extractTextWithUnknownFragments(text, text.indexOf("second")))
  }

  def testStringLiteral_Multiline_Interpolated_S(): Unit = {
    val text =
      s"""s$TripleQuote
         *   |this is
         *   |example
         *   |
         *   |this is
         *   | $${42}
         *   |example
         *   |
         *   |this is
         *   |$${42}
         *   |example
         *   |
         *   |this is $${42} example
         *   |$TripleQuote.stripMargin""".stripMargin('*').withNormalizedSeparator
    assertEquals("this is\nexample\n\nthis is\n ?\nexample\n\nthis is\n?\nexample\n\nthis is ? example\n", extractTextWithUnknownFragments(text, text.indexOf("this")))
  }

  def testStringLiteral_Multiline_Interpolated_Raw_WithStripMargin_WithCustomMargin(): Unit = {
    val text =
      s"""raw$TripleQuote  first \\ line
         |     #second $${2 + 2} line
         |     #third $$value line
         |     #  $TripleQuote.stripMargin('#')""".stripMargin.withNormalizedSeparator
    assertEquals("first \\ line\nsecond ? line\nthird ? line\n", extractTextWithUnknownFragments(text, text.indexOf("first")))
  }

  def testNoExtractionInInjectedFragments(): Unit = {
    val createPhysicalFile = true //injection works only on physical files

    val text = s"""val s1 = raw\"\"\"  This is example \\s  \"\"\""""

    val offset = text.indexOf("example")

    //First run test without injection, later run with injection (we manually add a special injector for that)
    val file1 = PsiFileFactory.getInstance(getProject).createFileFromText("Dummy1.scala", ScalaFileType.INSTANCE, text, 0, createPhysicalFile)
    assertEquals("This is example \\s", TextExtractor.findTextAt(file1, offset, TextContent.TextDomain.ALL).toString)

    InjectedLanguageManager.getInstance(getProject).registerMultiHostInjector(new MultiHostInjector() {
      override def elementsToInjectIn: util.List[_ <: Class[_ <: PsiElement]] = util.List.of(classOf[ScStringLiteral])

      override def getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement): Unit = {
        if (context.getText.contains("example")) {
          registrar.startInjecting(RegExpLanguage.INSTANCE).addPlace(
            null, null,
            context.asInstanceOf[PsiLanguageInjectionHost],
            context.asInstanceOf[ScStringLiteral].contentRangeInParent
          ).doneInjecting()
        }
      }
    }, getTestRootDisposable)

    val file2 = PsiFileFactory.getInstance(getProject).createFileFromText("Dummy1.scala", ScalaFileType.INSTANCE, text, 0, createPhysicalFile)
    assertNull(TextExtractor.findTextAt(file2, offset, TextContent.TextDomain.ALL))
  }

  def testBuildingPerformance_removingIndents(): Unit = {
    val text = "  b\n".repeat(10_000)
    val expected = "b\n".repeat(10_000).trim

    val file = myFixture.configureByText("dummy.scala", s"/*\n$text*/")

    val comment = assertInstanceOf(file.findElementAt(10), classOf[PsiComment])
    val builder = TextContentBuilder.FromPsi.removingIndents(" ")

    PlatformTestUtil.newBenchmark("TextContent building with indent removing", () => {
      assertEquals(expected, builder.build(comment, TextContent.TextDomain.COMMENTS).toString)
    }).start()
  }

  def testBuildingPerformance_removingHtml(): Unit = {
    val text = "b<unknownTag>x</unknownTag>".repeat(10_000)
    val expected = "b".repeat(10_000)
    val file = myFixture.configureByText("dummy.scala", "/**\n" + text + "*/")
    val comment = PsiTreeUtil.findElementOfClassAtOffset(file, 10, classOf[PsiDocComment], false)
    val extractor = new ScalaTextExtractor
    PlatformTestUtil.newBenchmark("TextContent building with HTML removal", () => {
      assertEquals(expected, extractor.buildTextContent(comment, TextContent.TextDomain.ALL).toString)
    }).start()
  }

  def testBuildingPerformance_longTextFragment(): Unit = {
    val line = "here's some relative long text that helps make this text fragment a bit longer than it could have been otherwise"
    val text = ("\n\n\n" + line).repeat(10_000)
    val expected = (line + "\n\n\n").repeat(10_000).trim
    val file = myFixture.configureByText("dummy.scala", "class C { String s = \"\"\"\n" + text + "\"\"\"; }")
    val literal = PsiTreeUtil.findElementOfClassAtOffset(file, 100, classOf[ScStringLiteral], false)
    val extractor = new ScalaTextExtractor
    PlatformTestUtil.newBenchmark("TextContent building from a long text fragment", () => {
      assertEquals(expected, extractor.buildTextContent(literal, TextContent.TextDomain.ALL).toString)
    }).start()
  }

  def testBuildingPerformance_ComplexScalaDocPsi(): Unit = {
    val link = "[[foo.bar.goo1.goo2.goo3.goo4.goo5.goo6.goo7]]"
    val text = "/** @return something if " + link.repeat(10_000) + " is not too expensive */"
    val file = myFixture.configureByText("dummy.scala", text)
    val extractor = new ScalaTextExtractor
    val tag = PsiTreeUtil.findElementOfClassAtOffset(file, text.indexOf("something"), classOf[PsiDocTag], false)
    PlatformTestUtil.newBenchmark("TextContent building from complex PSI", () => {
      for (_ <- 0 until 10) {
        val content = extractor.buildTextContent(tag, TextContent.TextDomain.ALL)
        assertEquals("something if  is not too expensive", content.toString)
      }
    }).start()
  }
}

object ScalaTextExtractorTest {

  private def extractTextContent(fileName: String, fileText: String, offset: Int, project: Project): TextContent = {
    val fileType = FileTypeManager.getInstance.getFileTypeByFileName(fileName)
    val file = PsiFileFactory.getInstance(project).createFileFromText(fileName, fileType, fileText)
    TextExtractor.findTextAt(file, offset, TextContent.TextDomain.ALL)
  }

  private val UnknownMarker = '?'
  private val MarkupMarker = '~'
  private val TripleQuote = "\"\"\""

  private def buildTextWithSpecialMarkers(content: TextContent): String = {
    val builder = new java.lang.StringBuilder(content.replaceMarkupWith(MarkupMarker))
    for (i <- content.length to 0 by -1) {
      if (content.hasUnknownFragmentsIn(TextRange.from(i, 0))) {
        builder.insert(i, UnknownMarker)
      }
    }
    builder.toString
  }
}
