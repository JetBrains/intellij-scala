package org.jetbrains.plugins.scala.intelliLang.injection

import org.jetbrains.plugins.scala.intelliLang.injection.InjectionTestUtils.JsonLangId
import org.jetbrains.plugins.scala.intelliLang.injection.ScalaInjectionTestFixture.{ExpectedInjection, ShredInfo}
import org.jetbrains.plugins.scala.util.TextRangeUtils.ImplicitConversions.tupleToTextRange

class InterpolatedStringWithInjectionsJsonTest extends ScalaLanguageInjectionTestBase:

  //
  //Different ways of injections
  //

  def testInjectionViaComment(): Unit =
    scalaInjectionTestFixture.doTest(
      """//language=JSON
        |s"hello ${foo} world $foo !"""".stripMargin,
      ExpectedInjection(
        "hello InjectionPlaceholder world InjectionPlaceholder !",
        JsonLangId,
        Some(Seq(
          ShredInfo((0, 6), (2, 8)),
          ShredInfo((6, 33), (14, 21), "InjectionPlaceholder"),
          ShredInfo((33, 55), (25, 27), "InjectionPlaceholder"),
        ))
      )
    )

  def testInjectionViaAnnotation(): Unit =
    scalaInjectionTestFixture.doTest(
      s"""class Language(val value: String) extends scala.annotation.StaticAnnotation
         |
         |class A {
         |  def foo(@Language("JSON") param: String): Unit = ???
         |  foo(s"hello $${foo} ${CARET}world $$foo !")
         |}
         |""".stripMargin,
      ExpectedInjection(
        "hello InjectionPlaceholder world InjectionPlaceholder !",
        JsonLangId,
        Some(Seq(
          ShredInfo((0, 6), (2, 8)),
          ShredInfo((6, 33), (14, 21), "InjectionPlaceholder"),
          ShredInfo((33, 55), (25, 27), "InjectionPlaceholder"),
        ))
      )
    )

  def testInjectionViaStringInterpolator(): Unit =
    scalaInjectionTestFixture.doTest(
      s"""json"hello $${foo} ${CARET}world $$foo !"
         |
         |implicit class StringContextOps(val sc: StringContext) {
         |  def json(args: Any*): String = ???
         |}
         |s""".stripMargin,
      ExpectedInjection(
        "hello InjectionPlaceholder world InjectionPlaceholder !",
        JsonLangId,
        Some(Seq(
          ShredInfo((0, 6), (5, 11)),
          ShredInfo((6, 33), (17, 24), "InjectionPlaceholder"),
          ShredInfo((33, 55), (28, 30), "InjectionPlaceholder"),
        ))
      )
    )
