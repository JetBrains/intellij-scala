package org.jetbrains.plugins.scala.annotator

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.ScalaVersion

/**
 * Contains highlighting tests, for which no better test class was found
 */
class Scala3HighlightingTestsMix extends ScalaHighlightingTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_3

  override def assertNoErrors(@Language("Scala 3") code: String): Unit =
    assertErrors(code, Nil: _*)

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

  //SCL-21834
  def testMultipleAnonymousParameters(): Unit = {
    assertNoErrors(
      """case class Company(name: String)
        |case class SalesRep(name: String)
        |
        |case class Invoice(customer: String)(using Company, SalesRep):
        |  override def toString = s"${summon[Company].name} / ${summon[SalesRep].name} - Customer: $customer"
        |
        |@main def test(): Unit =
        |  given Company = Company("Big Corp")
        |  given SalesRep = SalesRep("John")
        |  println(Invoice("Peter LTD"))
        |""".stripMargin
    )
  }
}
