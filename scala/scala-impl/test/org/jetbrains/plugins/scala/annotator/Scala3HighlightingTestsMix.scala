package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.ScalaVersion

/**
 * Contains highlighting tests, for which no better test class was found
 */
class Scala3HighlightingTestsMix extends ScalaHighlightingTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_3

  //SCL-21604
  def testAnonymousUsingParameterWithTypeWithSameNameAsObject(): Unit = {
    assertNoErrors(
      s"""type MyClass = Int
         |object MyClass:
         |  def test(): String = ""
         |
         |def foo(using MyClass): Unit = {
         |  summon[MyClass]
         |  MyClass.test()
         |}
         |""".stripMargin
    )
  }
}
