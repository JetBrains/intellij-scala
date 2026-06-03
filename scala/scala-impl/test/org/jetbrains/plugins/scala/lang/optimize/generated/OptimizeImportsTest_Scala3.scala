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

  def testUnqualifiedUsedRenamedScala3ImportsRearranged(): Unit = {
    myFixture.addFileToProject("cat/package.scala",
      """
        |package object cat {
        |  def text(s: String): Unit = println(s)
        |}
        |""".stripMargin
    )

    myFixture.addFileToProject("dog/package.scala",
      """
        |package object dog {
        |  def text(s: String): Unit = println(s)
        |}
        |""".stripMargin
    )

    myFixture.addFileToProject("whale/package.scala",
      """
        |package object whale {
        |  def text(s: String): Unit = println(s)
        |}
        |""".stripMargin
    )

    myFixture.addFileToProject("com/example/package.scala",
      """
        |package com
        |package object example {
        |  class Foo
        |}
        |""".stripMargin
    )

    val before =
      """
        |import cat as foo
        |import whale as foo2
        |import scala.collection.mutable
        |import dog as foo1
        |import java.util.function.Consumer
        |import com.example.Foo
        |
        |@main
        |def main(): Unit = {
        |  val consumer: Consumer[Int] = ???
        |  val map = mutable.LinkedHashMap.empty[String, Int]
        |  foo.text("cat")
        |  foo1.text("dog")
        |  foo2.text("whale")
        |  val foo3: Foo = ???
        |}
        |""".stripMargin

    val after =
      """
        |import cat as foo
        |import dog as foo1
        |import whale as foo2
        |
        |import com.example.Foo
        |
        |import java.util.function.Consumer
        |import scala.collection.mutable
        |
        |@main
        |def main(): Unit = {
        |  val consumer: Consumer[Int] = ???
        |  val map = mutable.LinkedHashMap.empty[String, Int]
        |  foo.text("cat")
        |  foo1.text("dog")
        |  foo2.text("whale")
        |  val foo3: Foo = ???
        |}
        |""".stripMargin

    doTest(before = before, after = after, expectedNotificationText = "Rearranged imports")
  }

  def testUnqualifiedUsedRenamedScala3ImportsNotChanged(): Unit = {
    myFixture.addFileToProject("cat/package.scala",
      """
        |package object cat {
        |  def text(s: String): Unit = println(s)
        |}
        |""".stripMargin
    )

    myFixture.addFileToProject("dog/package.scala",
      """
        |package object dog {
        |  def text(s: String): Unit = println(s)
        |}
        |""".stripMargin
    )

    doTest(
      """
        |import cat as foo
        |import dog as foo1
        |
        |@main
        |def main(): Unit = {
        |  foo.text("cat")
        |  foo1.text("dog")
        |}
        |""".stripMargin)
  }
}
