package org.jetbrains.plugins.scala.textAnalysis.grazie

import com.intellij.grazie.text.{TextContent, TextExtractor}
import com.intellij.lang.Language
import com.intellij.lang.injection.{InjectedLanguageManager, MultiHostInjector, MultiHostRegistrar}
import com.intellij.psi.PsiElement
import com.intellij.psi.javadoc.PsiDocComment
import org.intellij.lang.regexp.RegExpLanguage
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.junit.Assert.*

import java.util

/**
 * Originally inspired by tests in `com.intellij.grazie.text.TextExtractionTest` in IntelliJ repo
 */
//noinspection SpellCheckingInspection (The class is supposed to have ton's of spell check errors)
abstract class ScalaTextExtractorTest_CommonTests extends ScalaTextExtractorTestBase {

  protected val CommonStringInnerContent_WithEscapes1_Extracted_AsText = "example \\n text \\t \\r with ? escapes ? haha"
  // NOTE: keep the sorounding leading and trailing spaces in the content.
  // It also tests part of the logic deep inside the spell checker
  // (it trims the spaces somewhere, and we need to make sure that we don't fail)
  private val CommonStringInnerContent_WithInjections = """  aaa ${2 + 2} bbb $value ccc  """
  private val CommonStringInnerContent_WithInjections_Extracted_InjectionsAsText = "aaa ${2 + 2} bbb $value ccc"
  private val CommonStringInnerContent_WithInjections_Extracted_InjectionsExcluded = "aaa ? bbb ? ccc"
  private val CommonStringInnerContent_WithEscapes1 = "example \\n text \\t \\r with \\u0024 escapes \\uuuuu0024 haha"
  private val CommonStringInnerContent_WithEscapes1_Extracted_AsEscapes = "example \n text \t \r with ? escapes ? haha"
  //TODO: after SCL-25152 is fixed also add `\"`
  private val CommonStringInnerContent_WithEscapes2 = """aaa \b bbb \f ccc \n ddd \r eee \t fff \' ggg \\ hhh \n eee"""
  private val CommonStringInnerContent_WithEscapes2_Extracted_AsEscapes = "aaa ? bbb ? ccc \n ddd \r eee \t fff ? ggg ? hhh \n eee"
  private val CommonStringInnerContent_WithEscapes2_Extracted_AsText = "aaa \\b bbb \\f ccc \\n ddd \\r eee \\t fff \\' ggg \\\\ hhh \\n eee"
  //TODO: after SCL-25152 is fixed also add `\"`
  private val CommonStringInnerContent_WithEscapes_AndInjections = """aaa \b bbb ${2 + 2} ccc \n ddd $value eee \t fff \' ggg \\ hhh \n eee"""
  private val CommonStringInnerContent_WithEscapes_AndInjections_Extracted_AsEscapes_InjectionsAsText = "aaa ? bbb ${2 + 2} ccc \n ddd $value eee \t fff ? ggg ? hhh \n eee"
  private val CommonStringInnerContent_WithEscapes_AndInjections_Extracted_AsEscapes_InjectionsExcluded = "aaa ? bbb ? ccc \n ddd ? eee \t fff ? ggg ? hhh \n eee"
  private val CommonStringInnerContent_WithEscapes_AndInjections_Extracted_AsText_InjectionsAsText = "aaa \\b bbb ${2 + 2} ccc \\n ddd $value eee \\t fff \\' ggg \\\\ hhh \\n eee"
  private val CommonStringInnerContent_WithEscapes_AndInjections_Extracted_AsText_InjectionsExcluded = "aaa \\b bbb ? ccc \\n ddd ? eee \\t fff \\' ggg \\\\ hhh \\n eee"
  private val CommonFileTextForInjejctionTest = s"""val s1 = raw\"\"\"  This is example \\s  \"\"\""""

  def testMergeAdjacentLineComments_1(): Unit = {
    assertEquals(
      """Hello. I are a very humble
        |persons.""".stripMargin,
      extractSingleTextContentAndPresent(
        """//Hello. I are a very humble
          |//persons.
          |
          |class C {}""".stripMargin
      )
    )
  }

  def testMergeAdjacentLineComments_2(): Unit = {
    assertEquals(
      """First line.
        |Third line.""".stripMargin,
      extractSingleTextContentAndPresent(
        s"""// First line.
           |//   ${""}
           |//   Third line.
           |""".stripMargin
      )
    )
  }

  def testMergeAdjacentLineComments_3(): Unit = {
    val text =
      """//1
        |//2
        |//3
        |//4""".stripMargin

    val file = createScalaFile("Dummy.scala", text)
    val textContent1 = TextExtractor.findTextAt(file, text.indexOf("1"), TextContent.TextDomain.ALL)
    val textContent2 = TextExtractor.findTextAt(file, text.indexOf("3"), TextContent.TextDomain.ALL)

    assertEquals(
      """1
        |2
        |3
        |4""".stripMargin,
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
        |//4""".stripMargin

    val textContent1 = extractSingleTextContent(text, text.indexOf("1"))
    val textContent2 = extractSingleTextContent(text, text.indexOf("3"))
    assertEquals(
      """1
        |2""".stripMargin,
      textContent1.toString
    )

    assertEquals(
      """3
        |4""".stripMargin,
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
        | * Link 4 [[https://www.scala-lang.org description of http link]]
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
        |""".stripMargin

    val textContents = extractAllTextContents(docText, offset = 10, classOf[PsiDocComment])
    val actual = textContents.map(ScalaTextExtractorTestBase.buildTextWithSpecialMarkers)
    val expected = List(
      """Plain text line 1
        |Plain text line 2
        |Here's an asterisk: *
        |
        |Text ~ with ~ html""".stripMargin,
      "tags",
      """inside.
        |Unknown tags1: ?
        |Unknown tags2: tags2 ?one? two""".stripMargin,
      """three? four?
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
        |Link 4 ? description of http link?
        |
        |Deprecated Java-style inline elements:
        |?
        |?
        |
        |List:
        |?list item 1
        |?list item 2 line 1 ? description?
        |list item 2 line 2 ~text in span~
        |?list item 3""".stripMargin
    )
    assertEquals(expected, actual)

    val text1 = extractSingleTextContentAtOffsetAndPresent(docText, docText.indexOf("macro text"))
    assertEquals("macro text content (macro key name is not included)", text1)

    val text2 = extractSingleTextContentAtOffsetAndPresent(docText, docText.indexOf("parameter description"))
    assertEquals("parameter description bold (parameter name is not included)", text2)

    val text3 = extractSingleTextContentAtOffsetAndPresent(docText, docText.indexOf("description of exception"))
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
        |""".stripMargin

    assertEquals("Before snippet\n\n\n\nAfter snippet", extractSingleTextContentAndPresent(docText))
  }

  def testStringLiteral_Multiline_Plain(): Unit = {
    val text =
      s"""  $TripleQuote  first line
         |   second line
         |   third line
         |  $TripleQuote  """.stripMargin
    doTestSingleTextContent(text, "first line\n   second line\n   third line")
  }

  def testStringLiteral_Multiline_Plain2(): Unit = {
    val text =
      s"""val _ =
         |  \"\"\"hello
         |world
         |\"\"\"""".stripMargin
    doTestSingleTextContent(text, "hello\nworld")
  }

  def testStringLiteral_Multiline_Plain3(): Unit = {
    val text =
      s"""val _ =
         |  \"\"\"   hello
         |world
         |\"\"\"""".stripMargin
    doTestSingleTextContent(text, "hello\nworld")
  }

  def testStringLiteral_Multiline_Plain_WithStripMargin(): Unit = {
    val text =
      s"""${TripleQuote}first line
         *  |second line
         *  |third line
         *  |$TripleQuote.stripMargin""".stripMargin('*')
    doTestSingleTextContent(text, "first line\nsecond line\nthird line\n")
  }

  def testStringLiteral_Multiline_Plain_WithStripMargin_WithCustomMargin(): Unit = {
    val text =
      s"""${TripleQuote}first line
         |  #second line
         |  #third line
         |  #$TripleQuote.stripMargin('#')""".stripMargin
    doTestSingleTextContent(text, "first line\nsecond line\nthird line\n")
  }

  def testStringLiteral_Multiline_Plain_WithStripMargin_WithCustomMargin_1(): Unit = {
    val text =
      s"""${TripleQuote}first line
         *  |second line
         *  |third line
         *  |$TripleQuote.stripMargin('#')""".stripMargin('*')
    doTestSingleTextContent(text, "first line\n  |second line\n  |third line\n  |")
  }

  def testStringLiteral_Multiline_WithStripMargin_FirstLineBlank(): Unit = {
    val text =
      s"""val value =
         *
         *$TripleQuote
         *  |second line
         *  |third line
         *  |   $TripleQuote.stripMargin""".stripMargin('*')
    doTestSingleTextContent(text, "second line\nthird line\n")
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
         *   |$TripleQuote.stripMargin""".stripMargin('*')
    doTestSingleTextContent(text, "this is\nexample\n\nthis is\n ?\nexample\n\nthis is\n?\nexample\n\nthis is ? example\n")
  }

  def testStringLiteral_Multiline_Interpolated_Raw_WithStripMargin_WithCustomMargin(): Unit = {
    val text =
      s"""raw$TripleQuote  first \\ line
         |     #second $${2 + 2} line
         |     #third $$value line
         |     #  $TripleQuote.stripMargin('#')""".stripMargin
    doTestSingleTextContent(text, "first \\ line\nsecond ? line\nthird ? line\n")
  }

  def testStringLiteral_WithInjections_SingleLine_Plain(): Unit = {
    val text = s"""  "$CommonStringInnerContent_WithInjections"  """
    doTestSingleTextContent(text, CommonStringInnerContent_WithInjections_Extracted_InjectionsAsText)
  }

  def testStringLiteral_WithInjections_SingleLine_Interpolated_S(): Unit = {
    val text = s"""  s"$CommonStringInnerContent_WithInjections"  """
    doTestSingleTextContent(text, CommonStringInnerContent_WithInjections_Extracted_InjectionsExcluded)
  }

  def testStringLiteral_WithInjections_SingleLine_Interpolated_Raw(): Unit = {
    val text = s"""  raw"$CommonStringInnerContent_WithInjections"  """
    doTestSingleTextContent(text, CommonStringInnerContent_WithInjections_Extracted_InjectionsExcluded)
  }

  def testStringLiteral_WithInjections_Multiline_OneLine_Plain(): Unit = {
    val text = s"""  $TripleQuote$CommonStringInnerContent_WithInjections$TripleQuote  """
    doTestSingleTextContent(text, CommonStringInnerContent_WithInjections_Extracted_InjectionsAsText)
  }

  def testStringLiteral_WithInjections_Multiline_OneLine_Interpolated_S(): Unit = {
    val text = s"""  s$TripleQuote$CommonStringInnerContent_WithInjections$TripleQuote  """
    doTestSingleTextContent(text, CommonStringInnerContent_WithInjections_Extracted_InjectionsExcluded)
  }

  def testStringLiteral_WithInjections_Multiline_OneLine_Interpolated_Raw(): Unit = {
    val text = s"""  raw$TripleQuote$CommonStringInnerContent_WithInjections$TripleQuote  """
    doTestSingleTextContent(text, CommonStringInnerContent_WithInjections_Extracted_InjectionsExcluded)
  }

  def testStringLiteral_WithEscapes1_SingleLine_Plain(): Unit = {
    val text = s"""val value = "$CommonStringInnerContent_WithEscapes1""""
    doTestSingleTextContent(text, CommonStringInnerContent_WithEscapes1_Extracted_AsEscapes)
  }

  def testStringLiteral_WithEscapes1_SingleLine_Interpolated_S(): Unit = {
    val text = s"""val value = s"$CommonStringInnerContent_WithEscapes1""""
    doTestSingleTextContent(text, CommonStringInnerContent_WithEscapes1_Extracted_AsEscapes)
  }

  def testStringLiteral_WithEscapes1_SingleLine_Interpolated_Raw(): Unit = {
    val text = s"""val value = raw"$CommonStringInnerContent_WithEscapes1""""
    doTestSingleTextContent(text, CommonStringInnerContent_WithEscapes1_Extracted_AsText)
  }

  def testStringLiteral_WithEscapes1_Multiline_OneLine_Plain(): Unit = {
    val text = s"""val value = $TripleQuote$CommonStringInnerContent_WithEscapes1$TripleQuote"""
    doTestSingleTextContent(text, CommonStringInnerContent_WithEscapes1_Extracted_AsText)
  }

  def testStringLiteral_WithEscapes1_Multiline_OneLine_Interpolated_S(): Unit = {
    val text = s"""val value = s$TripleQuote$CommonStringInnerContent_WithEscapes1$TripleQuote"""
    doTestSingleTextContent(text, CommonStringInnerContent_WithEscapes1_Extracted_AsEscapes)
  }

  def testStringLiteral_WithEscapes1_Multiline_OneLine_Interpolated_Raw(): Unit = {
    val text = s"""val value = raw$TripleQuote$CommonStringInnerContent_WithEscapes1$TripleQuote"""
    doTestSingleTextContent(text, CommonStringInnerContent_WithEscapes1_Extracted_AsText)
  }

  def testStringLiteral_WithEscapes2_SingleLine_Plain(): Unit = {
    val text = s"""val value = "$CommonStringInnerContent_WithEscapes2""""
    doTestSingleTextContent(text, CommonStringInnerContent_WithEscapes2_Extracted_AsEscapes)
  }

  def testStringLiteral_WithEscapes2_SingleLine_Interpolated_S(): Unit = {
    val text = s"""val value = s"$CommonStringInnerContent_WithEscapes2""""
    doTestSingleTextContent(text, CommonStringInnerContent_WithEscapes2_Extracted_AsEscapes)
  }

  def testStringLiteral_WithEscapes2_SingleLine_Interpolated_Raw(): Unit = {
    val text = s"""val value = raw"$CommonStringInnerContent_WithEscapes2""""
    doTestSingleTextContent(text, CommonStringInnerContent_WithEscapes2_Extracted_AsText)
  }

  def testStringLiteral_WithEscapes2_Multiline_OneLine_Plain(): Unit = {
    val text = s"""val value = $TripleQuote$CommonStringInnerContent_WithEscapes2$TripleQuote"""
    doTestSingleTextContent(text, CommonStringInnerContent_WithEscapes2_Extracted_AsText)
  }

  def testStringLiteral_WithEscapes2_Multiline_OneLine_Interpolated_S(): Unit = {
    val text = s"""val value = s$TripleQuote$CommonStringInnerContent_WithEscapes2$TripleQuote"""
    doTestSingleTextContent(text, CommonStringInnerContent_WithEscapes2_Extracted_AsEscapes)
  }

  def testStringLiteral_WithEscapes2_Multiline_OneLine_Interpolated_Raw(): Unit = {
    val text = s"""raw$TripleQuote$CommonStringInnerContent_WithEscapes2$TripleQuote"""
    doTestSingleTextContent(text, CommonStringInnerContent_WithEscapes2_Extracted_AsText)
  }

  def testStringLiteral_WithEscapes_AndInjections_SingleLine_Plain(): Unit = {
    val text = s"""val value = "$CommonStringInnerContent_WithEscapes_AndInjections""""
    doTestSingleTextContent(text, CommonStringInnerContent_WithEscapes_AndInjections_Extracted_AsEscapes_InjectionsAsText)
  }

  def testStringLiteral_WithEscapes_AndInjections_SingleLine_Interpolated_S(): Unit = {
    val text = s"""val value = s"$CommonStringInnerContent_WithEscapes_AndInjections""""
    doTestSingleTextContent(text, CommonStringInnerContent_WithEscapes_AndInjections_Extracted_AsEscapes_InjectionsExcluded)
  }

  def testStringLiteral_WithEscapes_AndInjections_SingleLine_Interpolated_Raw(): Unit = {
    val text = s"""val value = raw"$CommonStringInnerContent_WithEscapes_AndInjections""""
    doTestSingleTextContent(text, CommonStringInnerContent_WithEscapes_AndInjections_Extracted_AsText_InjectionsExcluded)
  }

  def testStringLiteral_WithEscapes_AndInjections_Multiline_OneLine_Plain(): Unit = {
    val text = s"""val value = $TripleQuote$CommonStringInnerContent_WithEscapes_AndInjections$TripleQuote"""
    doTestSingleTextContent(text, CommonStringInnerContent_WithEscapes_AndInjections_Extracted_AsText_InjectionsAsText)
  }

  def testStringLiteral_WithEscapes_AndInjections_Multiline_OneLine_Interpolated_S(): Unit = {
    val text = s"""val value = s$TripleQuote$CommonStringInnerContent_WithEscapes_AndInjections$TripleQuote"""
    doTestSingleTextContent(text, CommonStringInnerContent_WithEscapes_AndInjections_Extracted_AsEscapes_InjectionsExcluded)
  }

  def testStringLiteral_WithEscapes_AndInjections_Multiline_OneLine_Interpolated_Raw(): Unit = {
    val text = s"""raw$TripleQuote$CommonStringInnerContent_WithEscapes_AndInjections$TripleQuote"""
    doTestSingleTextContent(text, CommonStringInnerContent_WithEscapes_AndInjections_Extracted_AsText_InjectionsExcluded)
  }

  def testStringLiteral_IgnoreInvalidEscapes(): Unit = {
    val text = s"val value1 = s\"helllo1 \\n \\\\ helllo2 \\ helllo3 \\x helllo4 \\u0024 helllo5 \\u002 helllo6 \\u002helllo7\""
    doTestSingleTextContent(text, "helllo1 \n ? helllo2 ?helllo3 ? helllo4 ? helllo5 ?002 helllo6 ?002helllo7", failOnInvalidEscapeSequencesInTestData = false)
  }

  def testStringLiteral_IgnoreInvalidEscapes2(): Unit = {
    val text = s"val value1 = s\"helllo1\\n\\\\helllo2\\helllo3\\xhelllo4\\u0024helllo5\\u002helllo6\\u002helllo7\""
    doTestSingleTextContent(text, "helllo1\n?helllo2?elllo3?helllo4?helllo5?002helllo6?002helllo7", failOnInvalidEscapeSequencesInTestData = false)
  }

  def testInjectedFragments_HasExtractionWithoutInjection(): Unit = {
    doTestTextContentInPhysicalFile(CommonFileTextForInjejctionTest, "This is example \\s")
  }

  private def doTestTextContentInPhysicalFile(
    fileText: String,
    @Nullable expectedTextContentString: String,
  ): Unit = {
    val createPhysicalFile = true //injection works only on physical files
    val psiFile = createScalaFile("Dummy.scala", fileText, createPhysicalFile = createPhysicalFile)
    val testContent = extractMaxOneTextContentInFile(psiFile)
    val actualTextContentString = if (testContent != null) testContent.toString else null
    assertEquals(expectedTextContentString, actualTextContentString)
  }

  def testInjectedFragments_NoExtractionWithInjection(): Unit = {
    registerLanguageInjectorForAllStringLiterals(RegExpLanguage.INSTANCE)
    doTestTextContentInPhysicalFile(CommonFileTextForInjejctionTest, null)
  }

  private def registerLanguageInjectorForAllStringLiterals(language: Language): Unit = {
    val injector: MultiHostInjector = new MultiHostInjector() {
      override def elementsToInjectIn: util.List[? <: Class[? <: PsiElement]] = util.List.of(classOf[ScStringLiteral])

      override def getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement): Unit = {
        context match {
          case stringLiteral: ScStringLiteral =>
            val host = stringLiteral
            val rangeInHost = stringLiteral.contentRangeInParent
            registrar.startInjecting(language).addPlace(null, null, host, rangeInHost)
            registrar.doneInjecting()
          case _ =>
        }
      }
    }
    InjectedLanguageManager.getInstance(getProject).registerMultiHostInjector(injector, getTestRootDisposable)
  }
}