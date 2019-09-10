package org.jetbrains.plugins.scala.injection

import com.intellij.openapi.module.Module
import com.intellij.patterns.compiler.PatternCompilerImpl.LazyPresentablePattern
import com.intellij.testFramework.EditorTestUtil
import org.intellij.plugins.intelliLang.inject.config.{BaseInjection, InjectionPlace}
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.junit.Assert._

import scala.collection.JavaConverters._

class ScalaLanguageInjectorTest extends AbstractLanguageInjectionTestCase {

  import EditorTestUtil.{CARET_TAG => Caret}

  private val Quotes = "\"\"\""
  private val JsonLangId = "JSON"

  override def setUpLibraries(implicit module: Module): Unit = {
    super.setUpLibraries

    val settings = ScalaProjectSettings.getInstance(module.getProject)
    val interpToLangId = Map("json" -> JsonLangId).asJava
    settings.setIntInjectionMapping(interpToLangId)
    settings.setDisableLangInjection(false)
  }

  ////////////////////////////////////////
  // Comment injection tests
  ////////////////////////////////////////

  def testCommentInjection_SingleLine(): Unit = {
    val body =
      s"""//language=JSON
         |"{$Caret \\\"a\\\" : 42 }"
         |""".stripMargin

    val expected =
      """{ \"a\" : 42 }"""

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

    doTest(JsonLangId, body, expected)
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
    getScalaSettings.MULTILINE_STRING_MARGIN_CHAR = "%"
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
      s"""json"{$Caret \\\"a\\\" : 42 }""""
    val expected =
      """{ \"a\" : 42 }"""
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

    doTest(JsonLangId, text, expected)
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
      s"""def foo(@Language("JSON") param: String): Unit = ???
         |foo("{$Caret \\\"a\\\" : 42 }")
         |""".stripMargin

    val expected =
      """{ \"a\" : 42 }"""

    doAnnotationTestInBody(JsonLangId, body, expected)
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

    doTest(JsonLangId, body, expected)
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

    doTest(JsonLangId, body, expected)
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

    doTest(JsonLangId, body, expected)
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
  // String concatenation injection tests
  ////////////////////////////////////////

  def testThatAllInjectionPatternsAreCompiled(): Unit = {
    val injections: Seq[BaseInjection] = intelliLangConfig.getInjections("scala").asScala
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

}
