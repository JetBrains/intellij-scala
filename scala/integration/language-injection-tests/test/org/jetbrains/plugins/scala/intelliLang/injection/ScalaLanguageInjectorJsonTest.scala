package org.jetbrains.plugins.scala.intelliLang.injection

import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.intelliLang.injection.InjectionTestUtils.JsonLangId

class ScalaLanguageInjectorJsonTest extends InjectionInBodyTestBase:

  import EditorTestUtil.CARET_TAG as Caret

  ////////////////////////////////////////
  // Comment injection tests
  ////////////////////////////////////////

  def testCommentInjection_SingleLine_UpperCaseLanguageId(): Unit =
    val body =
      raw"""//language=JSON
           |"{$Caret \"a\" : 42 }"
           |""".stripMargin
    val expected =
      """{ "a" : 42 }"""
    doTestInBody(JsonLangId, body, expected)

  def testCommentInjection_SingleLine_LowerCaseLanguageId(): Unit =
    val body =
      raw"""//language=json
           |"{$Caret \"a\" : 42 }"
           |""".stripMargin
    val expected =
      """{ "a" : 42 }"""
    doTestInBody(JsonLangId, body, expected)

  def testCommentInjection_MultilineOnSingleLine(): Unit =
    val body =
      s"""//language=JSON
         |$Quotes{$Caret "a" : 42 }$Quotes
         |""".stripMargin
    val expected =
      """{ "a" : 42 }"""
    doTestInBody(JsonLangId, body, expected)

  def testCommentInjection_Multiline(): Unit =
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

  def testCommentInjection_Multiline_WithMargins(): Unit =
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

  def testCommentInjection_Multiline_WithDefaultMargins_NonDefaultMarginInSettings(): Unit =
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

  def testCommentInjection_Multiline_WithNonDefaultMargins(): Unit =
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

  ////////////////////////////////////////
  // String interpolation injection tests
  ////////////////////////////////////////

  def testInterpolationInjection_SingleLine(): Unit =
    val body =
      raw"""json"{$Caret \"a\" : 42 }""""
    val expected =
      """{ "a" : 42 }"""
    doTestInBody(JsonLangId, body, expected)

  def testInterpolationInjection_MultilineOnSingleLine(): Unit =
    val body =
      s"""json$Quotes{$Caret "a" : 42 }$Quotes"""
    val expected =
      """{ "a" : 42 }"""
    doTestInBody(JsonLangId, body, expected)

  def testInterpolationInjection_Multiline(): Unit =
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

  def testInterpolationInjection_MultilineWithMargins(): Unit =
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

  ////////////////////////////////////////
  // @Language annotation injection tests
  ////////////////////////////////////////

  def testAnnotationInjection_SingleLine(): Unit =
    val body =
      raw"""def foo(@Language("JSON") param: String): Unit = ???
           |foo("{$Caret \"a\" : 42 }")
           |""".stripMargin
    val expected =
      """{ "a" : 42 }"""
    doAnnotationTestInBody(JsonLangId, body, expected)

  def testAnnotationInjection_MultilineOnSingleLine(): Unit =
    val body =
      s"""def foo(@Language("JSON") param: String): Unit = ???
         |foo($Quotes{$Caret "a" : 42 }$Quotes)
         |""".stripMargin
    val expected =
      """{ "a" : 42 }"""
    doAnnotationTestInBody(JsonLangId, body, expected)

  def testAnnotationInjection_Multiline(): Unit =
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

  def testAnnotationInjection_MultilineWithMargins(): Unit =
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

  ////////////////////////////////////////
  // String concatenation injection tests
  ////////////////////////////////////////

  def testCommentInjection_StringConcatOfSingleLines(): Unit =
    val body =
      s"""//language=JSON
         |"[{$Caret \\\"a\\\" : 42 }" +
         |", { \\\"b\\\" : 23 }]"
         |""".stripMargin
    val expected =
      """[{ \"a\" : 42 }, { \"b\" : 23 }]"""
    doTestInBody(JsonLangId, body, expected)

  def testCommentInjection_StringConcatOfSingleLinesWithLineBreak(): Unit =
    val body =
      s"""//language=JSON
         |"[{$Caret \\\"a\\\" : 42 },\\n" +
         |"{ \\\"b\\\" : 23 }]"
         |""".stripMargin
    val expected =
      """[{ \"a\" : 42 },\n{ \"b\" : 23 }]""".stripMargin
    doTestInBody(JsonLangId, body, expected)

  def testCommentInjection_StringConcatOfMultilineOnSingleLine(): Unit =
    val body =
      s"""//language=JSON
         |$Quotes[{$Caret "a" : 42 }$Quotes +
         |$Quotes, { "b" : 23 }]$Quotes
         |""".stripMargin
    val expected =
      """[{ "a" : 42 }, { "b" : 23 }]"""
    doTestInBody(JsonLangId, body, expected)

  def testCommentInjection_StringConcatOfMultilineOnSingleLineWithLineBreak(): Unit =
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

  def testCommentInjection_StringConcatOfMultilines(): Unit =
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

  def testCommentInjection_StringConcatOfMultilinesWithLineBreak(): Unit =
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

// FIXME: string concat does not detect stripMargin for now
//
//  can't ignore the test with @Ignore annotation
//  def testCommentInjection_StringConcatOfMultilineWithMargins(): Unit =
//    val body =
//      s"""//language=JSON
//         |$Quotes[{
//         |  |  "a" : 42$Caret
//         |  |}$Quotes.stripMargin +
//         |  $Quotes, {
//         |  |  "b" : 23$Caret
//         |  |}]$Quotes.stripMargin
//         |""".stripMargin
//    val expected =
//      """[{
//        |  "a" : 42
//        |}, {
//        |  "b" : 23
//        |}]""".stripMargin
//    doTestInBody(JsonLangId, body, expected)
