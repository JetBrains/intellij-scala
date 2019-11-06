package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.lang.resolve.SimpleResolveTestBase.{REFSRC, REFTGT}
import org.jetbrains.plugins.scala.{ScalaVersion, Scala_2_12, Scala_2_13}

abstract class ResolvePrecedenceTest extends ScalaLightCodeInsightFixtureTestAdapter
  with SimpleResolveTestBase

class ResolvePrecedenceTest2_13 extends ResolvePrecedenceTest {

  override protected def supportedIn(version: ScalaVersion) = version >= Scala_2_13

  def testSCL16057(): Unit = doResolveTest(
    s"""
       |package foo
       |
       |import cats.Monoid
       |object Test {
       |  val a: Mon${REFSRC}oid[Int] = ???
       |}
       |""".stripMargin -> "HasRef.scala",
    s"""
       |package object cats {
       |  type Mo${REFTGT}noid[A] = A
       |}
       |""".stripMargin -> "DefinesRef.scala",
    s"""
       |package foo
       |trait Monoid[A]
       |""".stripMargin -> "PackageLocal.scala"
  )

  def testSCL16057_nonTopImport(): Unit = doResolveTest(
    s"""
       |package foo
       |
       |object Test {
       |  import cats.Monoid
       |
       |  val a: Mon${REFSRC}oid[Int] = ???
       |}
       |""".stripMargin -> "HasRef.scala",
    s"""
       |package object cats {
       |  type ${REFTGT}Monoid[A] = A
       |}
       |""".stripMargin -> "DefinesRef.scala",
    s"""
       |package foo
       |trait Monoid[A]
       |""".stripMargin -> "PackageLocal.scala"
  )

  //ScalacIssue11593
  def testWildcardImportSameUnit(): Unit = doResolveTest(
    s"""
       |package foo {
       |  class ${REFTGT}Properties
       |
       |  import java.util._
       |
       |  object X extends App {
       |    def bar(x: P${REFSRC}roperties): Unit = println(x.getClass.getName)
       |    bar(new Properties)
       |  }
       |}
       |""".stripMargin
  )

  //ScalacIssue11593
  def testWildcardImportOtherUnit(): Unit = doResolveTest(myFixture.getJavaFacade.findClass("java.util.Properties"),
    s"""
       |package foo {
       |  import java.util._
       |
       |  object X extends App {
       |    def bar(x: P${REFSRC}roperties): Unit = println(x.getClass.getName)
       |    bar(new Properties)
       |  }
       |}
       |""".stripMargin -> "HasRef.scala",
    s"""
       |package foo
       |
       |class Properties
       |
       |""".stripMargin -> "DefinesRef.scala"
  )

  //SCL-16305
  def testClassObjectNameClash(): Unit = testNoResolve(
    """
      |package hints
      |
      |case class Hint()
      |object Hint
      |""".stripMargin -> "hintsHint.scala",

    """
      |package implicits
      |
      |import hints.Hint
      |
      |object Hint {
      |  def addTo(hint: Hint) = ???
      |}
      |
      |""".stripMargin -> "implicitsHint.scala",

    s"""
      |import hints.Hint
      |
      |package object implicits {
      |  def add(hint: Hint) = ${REFSRC}Hint.addTo(hint) //ambiguous reference to object Hint
      |}
      |""".stripMargin -> "package.scala"
  )
}

class ResolvePrecedenceTest2_12 extends ResolvePrecedenceTest {

  override protected def supportedIn(version: ScalaVersion) = version <= Scala_2_12

  def testSCL16057(): Unit = doResolveTest(
    s"""
       |package foo
       |
       |import cats.Monoid
       |object Test {
       |  val a: Mon${REFSRC}oid[Int] = ???
       |}
       |""".stripMargin -> "HasRef.scala",
    s"""
       |package object cats {
       |  type Monoid[A] = A
       |}
       |""".stripMargin -> "DefinesRef.scala",
    s"""
       |package foo
       |trait M${REFTGT}onoid[A]
       |""".stripMargin -> "PackageLocal.scala"
  )

  def testSCL16057_nonTopImport(): Unit = doResolveTest(
    s"""
       |package foo
       |
       |object Test {
       |  import cats.Monoid
       |
       |  val a: Mon${REFSRC}oid[Int] = ???
       |}
       |""".stripMargin -> "HasRef.scala",
    s"""
       |package object cats {
       |  type ${REFTGT}Monoid[A] = A
       |}
       |""".stripMargin -> "DefinesRef.scala",
    s"""
       |package foo
       |trait Monoid[A]
       |""".stripMargin -> "PackageLocal.scala"
  )

  //ScalacIssue11593
  def testWildcardImportSameUnit_topLevelImport(): Unit = doResolveTest(
    s"""
       |package foo {
       |  class ${REFTGT}Properties
       |
       |  import java.util._
       |
       |  object X extends App {
       |    def bar(x: P${REFSRC}roperties): Unit = println(x.getClass.getName)
       |    bar(new Properties)
       |  }
       |}
       |""".stripMargin
  )

  def testWildcardImportSameUnit_nonTopImport(): Unit = testNoResolve(
    s"""
       |package foo {
       |  class Properties
       |
       |  object X extends App {
       |    import java.util._
       |
       |    def bar(x: P${REFSRC}roperties): Unit = println(x.getClass.getName)
       |    bar(new Properties)
       |  }
       |}
       |""".stripMargin -> "HasAmbiguity.scala"
  )

  def testWildcardImportOtherUnit_topLevelImport(): Unit =
    doResolveTest(
      s"""
         |package foo {
         |  import java.util._
         |
         |  object X extends App {
         |    def bar(x: P${REFSRC}roperties): Unit = println(x.getClass.getName)
         |    bar(new Properties)
         |  }
         |}
         |""".stripMargin -> "HasRef.scala",
      s"""
         |package foo
         |
         |class ${REFTGT}Properties
         |
         |""".stripMargin -> "DefinesRef.scala"
    )

  def testWildcardImportOtherUnit_nonTopImport(): Unit =
    doResolveTest(myFixture.getJavaFacade.findClass("java.util.Properties"),
      s"""
         |package foo {
         |  object X extends App {
         |    import java.util._
         |
         |    def bar(x: P${REFSRC}roperties): Unit = println(x.getClass.getName)
         |    bar(new Properties)
         |  }
         |}
         |""".stripMargin -> "HasRef.scala",
      s"""
         |package foo
         |
         |class Properties
         |
         |""".stripMargin -> "DefinesRef.scala"
    )

  //SCL-16305
  def testClassObjectNameClash(): Unit = doResolveTest(
    """
      |package hints
      |
      |case class Hint()
      |object Hint
      |""".stripMargin -> "hintsHint.scala",

    s"""
      |package implicits
      |
      |import hints.Hint
      |
      |object ${REFTGT}Hint {
      |  def addTo(hint: Hint) = ???
      |}
      |
      |""".stripMargin -> "implicitsHint.scala",

    s"""
       |import hints.Hint
       |
       |package object implicits {
       |
       |  def add(hint: Hint) = ${REFSRC}Hint.addTo(hint) //object from `implicits` package shadows top-level import
       |}
       |""".stripMargin -> "package.scala"
  )

}

class ResolvePrecedenceAnyVersionTest extends ResolvePrecedenceTest {
  override protected def supportedIn(version: ScalaVersion) = true

  def testSCL6146(): Unit = doResolveTest(
    "case class Foo()" -> "Foo.scala",

    s"""
      |object SCL6146 {
      |  case class ${REFTGT}Foo()
      |}
      |class SCL6146 {
      |  import SCL6146._
      |  def foo(c: Any) = c match {
      |    case f: ${REFSRC}Foo =>
      |  }
      |
      |  def foo2 = Foo()
      |}
      |""".stripMargin -> "SCL6146.scala"
  )
}