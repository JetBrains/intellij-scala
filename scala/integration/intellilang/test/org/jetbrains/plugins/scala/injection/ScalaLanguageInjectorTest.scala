package org.jetbrains.plugins.scala.injection

import com.intellij.openapi.module.Module
import com.intellij.patterns.compiler.PatternCompilerImpl.LazyPresentablePattern
import com.intellij.testFramework.EditorTestUtil
import org.intellij.plugins.intelliLang.inject.config.{BaseInjection, InjectionPlace}
import org.jetbrains.plugins.scala.injection.InjectionTestUtils._
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.junit.Assert._

import scala.jdk.CollectionConverters._

class ScalaLanguageInjectorTest extends ScalaLanguageInjectionTestBase {

  import EditorTestUtil.{CARET_TAG => Caret}

  private val Quotes = "\"\"\""
  private lazy val LanguageAnnotationDef = scalaInjectionTestFixture.LanguageAnnotationDef

  override def setUpLibraries(implicit module: Module): Unit = {
    super.setUpLibraries

    val settings = ScalaProjectSettings.getInstance(module.getProject)
    val interpolatorToLangId = Map("json" -> JsonLangId).asJava
    settings.setIntInjectionMapping(interpolatorToLangId)
    settings.setDisableLangInjection(false)
  }

  protected def doTestInBody(languageId: String, classBody: String, injectedFileExpectedText: String): Unit = {
    val classBodyWithIndent = classBody.replaceAll("\n", "\n  ")
    val text =
      s"""class A {
         |  $classBodyWithIndent
         |}
         |""".stripMargin
    scalaInjectionTestFixture.doTest(languageId, text, injectedFileExpectedText)
  }

  protected def doAnnotationTestInBody(languageId: String, classBody: String, injectedFileExpectedText: String): Unit = {
    val classBodyWithIndent = classBody.replaceAll("\n", "\n  ")
    val text =
      s"""$LanguageAnnotationDef
         |class A {
         |  $classBodyWithIndent
         |}
         |""".stripMargin
    scalaInjectionTestFixture.doTest(languageId, text, injectedFileExpectedText)
  }

  protected def doAnnotationTest(languageId: String, text: String, injectedFileExpectedText: String): Unit = {
    val textFinal =
      s"""$LanguageAnnotationDef
         |$text
         |""".stripMargin
    scalaInjectionTestFixture.doTest(languageId, textFinal, injectedFileExpectedText)
  }

  ////////////////////////////////////////
  // Comment injection tests
  ////////////////////////////////////////

  def testCommentInjection_SingleLine_UpperCaseLanguageId(): Unit = {
    val body =
      raw"""//language=JSON
           |"{$Caret \"a\" : 42 }"
           |""".stripMargin

    val expected =
      """{ "a" : 42 }"""

    doTestInBody(JsonLangId, body, expected)
  }

  def testCommentInjection_SingleLine_LowerCaseLanguageId(): Unit = {
    val body =
      raw"""//language=json
           |"{$Caret \"a\" : 42 }"
           |""".stripMargin

    val expected =
      """{ "a" : 42 }"""

    doTestInBody(JsonLangId, body, expected)
  }

  def testCommentInjection_MultilineOnSingleLine(): Unit = {
    val body =
      s"""//language=JSON
         |$Quotes{$Caret "a" : 42 }$Quotes
         |""".stripMargin

    val expected =
      """{ "a" : 42 }"""

    doTestInBody(JsonLangId, body, expected)
  }

  def testCommentInjection_Multiline(): Unit = {
    val body =
      s"""class A {
         |  //language=JSON
         |  $Quotes{
         |  "a" : 42$Caret
         |}$Quotes
         |}
         |""".stripMargin

    val expected =
      """{
        |  "a" : 42
        |}""".stripMargin

    scalaInjectionTestFixture.doTest(JsonLangId, body, expected)
  }

  def testCommentInjection_Multiline_WithMargins(): Unit = {
    val body =
      s"""//language=JSON
         |$Quotes{
         |  |  "a" : 42$Caret
         |  |}$Quotes.stripMargin
         |""".stripMargin

    val expected =
      """{
        |  "a" : 42
        |}""".stripMargin

    doTestInBody(JsonLangId, body, expected)
  }

  def testCommentInjection_Multiline_WithDefaultMargins_NonDefaultMarginInSettings(): Unit = {
    getScalaCodeStyleSettings.MULTILINE_STRING_MARGIN_CHAR = "%"
    val body =
      s"""//language=JSON
         |$Quotes{
         |  |  "a" : 42$Caret
         |  |}$Quotes.stripMargin
         |""".stripMargin

    val expected =
      """{
        |  "a" : 42
        |}""".stripMargin

    doTestInBody(JsonLangId, body, expected)
  }

  def testCommentInjection_Multiline_WithNonDefaultMargins(): Unit = {
    val body =
      s"""//language=JSON
         |$Quotes{
         |  #  "a" : 42$Caret
         |  #}$Quotes.stripMargin('#')
         |""".stripMargin

    val expected =
      """{
        |  "a" : 42
        |}""".stripMargin

    doTestInBody(JsonLangId, body, expected)
  }

  ////////////////////////////////////////
  // String interpolation injection tests
  ////////////////////////////////////////

  def testInterpolationInjection_SingleLine(): Unit = {
    val body =
      raw"""json"{$Caret \"a\" : 42 }""""
    val expected =
      """{ "a" : 42 }"""
    doTestInBody(JsonLangId, body, expected)
  }

  def testInterpolationInjection_MultilineOnSingleLine(): Unit = {
    val body =
      s"""json$Quotes{$Caret "a" : 42 }$Quotes"""
    val expected =
      """{ "a" : 42 }"""
    doTestInBody(JsonLangId, body, expected)
  }

  def testInterpolationInjection_Multiline(): Unit = {
    val text =
      s"""class A {
         |  json$Quotes{
         |  "a" : 42$Caret
         |}$Quotes
         |}
         |""".stripMargin

    val expected =
      """{
        |  "a" : 42
        |}""".stripMargin

    scalaInjectionTestFixture.doTest(JsonLangId, text, expected)
  }

  def testInterpolationInjection_MultilineWithMargins(): Unit = {
    val body =
      s"""json$Quotes{
         |  |  "a" : 42$Caret
         |  |}$Quotes.stripMargin
         |""".stripMargin

    val expected =
      """{
        |  "a" : 42
        |}""".stripMargin

    doTestInBody(JsonLangId, body, expected)
  }

  ////////////////////////////////////////
  // @Language annotation injection tests
  ////////////////////////////////////////

  def testAnnotationInjection_SingleLine(): Unit = {
    val body =
      raw"""def foo(@Language("JSON") param: String): Unit = ???
           |foo("{$Caret \"a\" : 42 }")
           |""".stripMargin

    val expected =
      """{ "a" : 42 }"""

    doAnnotationTestInBody(JsonLangId, body, expected)
  }

  def testAnnotationInjection_Scala2Language(): Unit = {
    val body =
      raw"""def foo(@Language("Scala") param: String): Unit = ???
           |foo("class A ${CARET}extends AnyRef")
           |""".stripMargin

    val expected =
      """class A extends AnyRef"""

    doAnnotationTestInBody(ScalaLangId, body, expected)
  }

  //todo: it doesn't work
  def testAnnotationInjection_Scala3Language(): Unit = {
    val body =
      raw"""def foo(@Language("Scala 3") param: String): Unit = ???
           |foo("enum MyEnum ${CARET}{ case A, B; case C } ; given value: String = ???")
           |""".stripMargin

    val expected =
      """enum MyEnum { case A, B; case C } ; given value: String = ???"""

    doAnnotationTestInBody(Scala3LangId, body, expected)
  }

  def testAnnotationInjection_MultilineOnSingleLine(): Unit = {
    val body =
      s"""def foo(@Language("JSON") param: String): Unit = ???
         |foo($Quotes{$Caret "a" : 42 }$Quotes)
         |""".stripMargin

    val expected =
      """{ "a" : 42 }"""

    doAnnotationTestInBody(JsonLangId, body, expected)
  }

  def testAnnotationInjection_Multiline(): Unit = {
    val text =
      s"""$LanguageAnnotationDef
         |class A {
         |  def foo(@Language("JSON") param: String): Unit = ???
         |  foo($Quotes{
         |  "a" : 42$Caret
         |}$Quotes
         |  )
         |}
         |""".stripMargin

    val expected =
      """{
        |  "a" : 42
        |}""".stripMargin

    doAnnotationTest(JsonLangId, text, expected)
  }

  def testAnnotationInjection_MultilineWithMargins(): Unit = {
    val text =
      s"""$LanguageAnnotationDef
         |class A {
         |  def foo(@Language("JSON") param: String): Unit = ???
         |  foo($Quotes{
         |    |  "a" : 42$Caret
         |    |}$Quotes.stripMargin)
         |}
         |""".stripMargin

    val expected =
      """{
        |  "a" : 42
        |}""".stripMargin

    doAnnotationTest(JsonLangId, text, expected)
  }

  ////////////////////////////////////////
  // String concatenation injection tests
  ////////////////////////////////////////

  def testCommentInjection_StringConcatOfSingleLines(): Unit = {
    val body =
      s"""//language=JSON
         |"[{$Caret \\\"a\\\" : 42 }" +
         |", { \\\"b\\\" : 23 }]"
         |""".stripMargin

    val expected =
      """[{ \"a\" : 42 }, { \"b\" : 23 }]"""

    doTestInBody(JsonLangId, body, expected)
  }

  def testCommentInjection_StringConcatOfSingleLinesWithLineBreak(): Unit = {
    val body =
      s"""//language=JSON
         |"[{$Caret \\\"a\\\" : 42 },\\n" +
         |"{ \\\"b\\\" : 23 }]"
         |""".stripMargin

    val expected =
      """[{ \"a\" : 42 },\n{ \"b\" : 23 }]""".stripMargin

    doTestInBody(JsonLangId, body, expected)
  }

  def testCommentInjection_StringConcatOfMultilineOnSingleLine(): Unit = {
    val body =
      s"""//language=JSON
         |$Quotes[{$Caret "a" : 42 }$Quotes +
         |$Quotes, { "b" : 23 }]$Quotes
         |""".stripMargin

    val expected =
      """[{ "a" : 42 }, { "b" : 23 }]"""

    doTestInBody(JsonLangId, body, expected)
  }

  def testCommentInjection_StringConcatOfMultilineOnSingleLineWithLineBreak(): Unit = {
    val body =
      s"""class A {
         |  //language=JSON
         |  $Quotes[{$Caret "a" : 42 },
         |$Quotes +
         |  $Quotes{ "b" : 23 }]$Quotes
         |}
         |""".stripMargin

    val expected =
      """[{ "a" : 42 },
        |{ "b" : 23 }]""".stripMargin

    scalaInjectionTestFixture.doTest(JsonLangId, body, expected)
  }

  def testCommentInjection_StringConcatOfMultilines(): Unit = {
    val body =
      s"""class A {
         |  //language=JSON
         |  $Quotes[{
         |  "a" : 42$Caret
         |}$Quotes + $Quotes, {
         |  "b" : 23
         |}]$Quotes
         |}
         |""".stripMargin

    val expected =
      """[{
        |  "a" : 42
        |}, {
        |  "b" : 23
        |}]""".stripMargin

    scalaInjectionTestFixture.doTest(JsonLangId, body, expected)
  }

  def testCommentInjection_StringConcatOfMultilinesWithLineBreak(): Unit = {
    val body =
      s"""class A {
         |  //language=JSON
         |  $Quotes[
         |  {
         |    "a" : 42$Caret
         |  },$Quotes +
         |  $Quotes
         |  {
         |    "b" : 23
         |  }
         |]$Quotes
         |}
         |""".stripMargin

    val expected =
      """[
        |  {
        |    "a" : 42
        |  },
        |  {
        |    "b" : 23
        |  }
        |]""".stripMargin

    scalaInjectionTestFixture.doTest(JsonLangId, body, expected)
  }

  // FIXME: string concat does not detect stripMargin for now
  //
  //  can't ignore the test with @Ignore annotation
  //  def testCommentInjection_StringConcatOfMultilineWithMargins(): Unit = {
  //    val body =
  //      s"""//language=JSON
  //         |$Quotes[{
  //         |  |  "a" : 42$Caret
  //         |  |}$Quotes.stripMargin +
  //         |  $Quotes, {
  //         |  |  "b" : 23$Caret
  //         |  |}]$Quotes.stripMargin
  //         |""".stripMargin
  //
  //    val expected =
  //      """[{
  //        |  "a" : 42
  //        |}, {
  //        |  "b" : 23
  //        |}]""".stripMargin
  //
  //    doTestInBody(JsonLangId, body, expected)
  //  }

  ////////////////////////////////////////
  // other
  ////////////////////////////////////////

  def testThatAllInjectionPatternsAreCompiled(): Unit = {
    val injections: Seq[BaseInjection] = scalaInjectionTestFixture.intelliLangConfig.getInjections("scala").asScala.toSeq
    for {
      injection <- injections
      place: InjectionPlace <- injection.getInjectionPlaces
    } {
      // for now if pattern compilation fails IntelliJ only generates warning in logs but continue to work properly
      // we would like to detect compilation failure in tests
      val pattern = place.getElementPattern match {
        case laz: LazyPresentablePattern[_] =>
          // in case of failure `PatternCompilerImpl.onCompilationFailed` will be called and test will fail
          laz.getCompiledPattern
        case p => p
      }
      if (pattern.getClass.getName.contains("False")) {
        fail(s"injection `${injection.getDisplayName}` has non-compiled pattern `${place.getText}`")
      }
    }
  }

  ///////////////////////////////////
  // Injections via patterns defined in `scalaInjections.xml`
  ///////////////////////////////////


  def testPatternInjection_Regexp_MultilineOnSingleLine(): Unit = {
    val body =
      s"""$Quotes hello world$Quotes.r""".stripMargin

    val expected =
      """ hello world"""

    doTestInBody(RegexpLangId, body, expected)
  }

  def testPatternInjection_Regexp_Multiline(): Unit = {
    val body =
      s"""class A {
         |  ${Quotes}hello
         |  world
         |!$Quotes.r
         |}
         |""".stripMargin

    val expected =
      """hello
        |  world
        |!""".stripMargin

    scalaInjectionTestFixture.doTest(RegexpLangId, body, expected)
  }

  def testPatternInjection_JavaMethodsPattern_String_matches(): Unit = {
    val body = s""""42".matches("[0-9]+\\\\d+$CARET")""".stripMargin
    val expected = """[0-9]+\d+""".stripMargin
    scalaInjectionTestFixture.doTest(RegexpLangId, body, expected)
  }

  def testPatternInjection_JavaMethodsPattern_String_replaceAll(): Unit = {
    val body = s""""42".replaceAll("[0-9]+\\\\d+$CARET", "23")""".stripMargin
    val expected = """[0-9]+\d+""".stripMargin
    scalaInjectionTestFixture.doTest(RegexpLangId, body, expected)
  }

  def testPatternInjection_JavaMethodsPattern_Pattern_compile(): Unit = {
    val body = """java.util.regex.Pattern.compile("[0-9]+\\d+")""".stripMargin
    val expected = """[0-9]+\d+""".stripMargin
    scalaInjectionTestFixture.doTest(RegexpLangId, body, expected)
  }

  //TODO: s trip margin + pattern not supported yet
//  def test PatternInjection_Multiline_WithMargins(): Unit = {
//    val body =
//      s"""${Quotes}hello
//         |  |  world
//         |  |!$Quotes.stripMargin.r
//         |""".stripMargin
//
//    val expected =
//      """hello
//        |  world
//        |!""".stripMargin
//
//    doTestInBody(RegexpLangId, body, expected)
//  }
}
