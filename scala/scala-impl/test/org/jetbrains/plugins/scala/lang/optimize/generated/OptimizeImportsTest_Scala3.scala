package org.jetbrains.plugins.scala.lang.optimize.generated

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.lang.optimize.OptimizeImportsTestBase

class OptimizeImportsTest_Scala3 extends OptimizeImportsTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= ScalaVersion.Latest.Scala_3_0

  def testExportClausesAreNotModified(): Unit = {
    doTest(
      """class A {
        |  def foo = ???
        |}
        |
        |export AWrapper.*
        |export AWrapper.given
        |export AWrapper.{given}
        |export AWrapper.{given String}
        |
        |object AWrapper {
        |  val b = new A
        |  export b.*
        |}""".stripMargin
    )
  }

  def testUnqualifiedUsedRenamedScala3ImportIsNotRemoved(): Unit = {
    myFixture.addFileToProject("Foo.scala",
      """
        |package definitions
        |
        |case class Foo(s: String)
        |""".stripMargin
    )

    doTest(
      """
        |package tests
        |
        |import definitions as defs
        |
        |object Test:
        |  def test(): Unit =
        |    println(defs.Foo("bar"))
        |""".stripMargin
    )
  }

  def testUnqualifiedUnusedRenamedScala3ImportIsRemoved(): Unit = {
    myFixture.addFileToProject("Foo.scala",
      """
        |package definitions
        |
        |case class Foo(s: String)
        |""".stripMargin
    )

    doTest(
      before =
        """
          |package tests
          |
          |import definitions as defs
          |
          |object Test:
          |  def test(): Unit =
          |    println("bar")
          |""".stripMargin,
      after =
        """
          |package tests
          |
          |object Test:
          |  def test(): Unit =
          |    println("bar")
          |""".stripMargin,
      expectedNotificationText = "Removed 1 import"
    )
  }

  def testUnqualifiedUnusedRenamedScala3ImportIsRemoved2(): Unit = {
    myFixture.addFileToProject("Foo.scala",
      """
        |package definitions
        |
        |case class Foo(s: String)
        |""".stripMargin
    )

    doTest(
      before =
        """
          |package tests
          |
          |import definitions as defs
          |
          |object Test:
          |  def test(): Unit =
          |    println(definitions.Foo("bar"))
          |""".stripMargin,
      after =
        """
          |package tests
          |
          |object Test:
          |  def test(): Unit =
          |    println(definitions.Foo("bar"))
          |""".stripMargin,
      expectedNotificationText = "Removed 1 import"
    )
  }
}
