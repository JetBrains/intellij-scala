package org.jetbrains.plugins.scala.lang.resolve

import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.{ScalaVersion, Scala_2_12, Scala_2_13}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.lang.resolve.SimpleResolveTestBase.{REFSRC, REFTGT}


class ResolvePrecedenceTest2_13
    extends ScalaLightCodeInsightFixtureTestAdapter
    with SimpleResolveTestBase {
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
}

class ResolvePrecedenceTest2_12
  extends ScalaLightCodeInsightFixtureTestAdapter
    with SimpleResolveTestBase {
  override protected def supportedIn(version: ScalaVersion) = version <= Scala_2_12

  def testSCL16057(): Unit = doResolveTest(
    s"""
       |package foo
       |
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
}

class ResolvePrecedenceTestWildcardImportSameUnit
    extends ScalaLightCodeInsightFixtureTestAdapter
    with SimpleResolveTestBase {
  override protected def supportedIn(version: ScalaVersion) = version >= Scala_2_13

  def testScalacIssue11593(): Unit = doResolveTest(
    s"""
       |package foo {
       |  class Properties
       |
       |  import java.util._
       |  object X extends App {
       |    def bar(x: P${REFSRC}roperties): Unit = println(x.getClass.getName)
       |    bar(new Properties)
       |  }
       |}
       |""".stripMargin
  )
}

class ResolvePrecedenceTestWildcardImportOtherUnit
  extends ScalaLightCodeInsightFixtureTestAdapter
    with SimpleResolveTestBase {
  override protected def supportedIn(version: ScalaVersion) = version >= Scala_2_13

  override protected def getTgt(
    source: String,
    file:   PsiFile
  ) = myFixture.getJavaFacade.findClass("java.util.Properties")

  def testScalacIssue11593(): Unit = doResolveTest(
    s"""
       |package foo {
       |  import java.util._
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
}
