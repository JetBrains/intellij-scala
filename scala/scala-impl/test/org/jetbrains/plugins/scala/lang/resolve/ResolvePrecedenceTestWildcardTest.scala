package org.jetbrains.plugins.scala.lang.resolve

import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_12, Scala_2_13}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.lang.resolve.SimpleResolveTestBase.{REFSRC, REFTGT}

class ResolvePrecedenceTestWildcardImportSameUnit
    extends ScalaLightCodeInsightFixtureTestAdapter
    with SimpleResolveTestBase {
  override implicit val version: ScalaVersion = Scala_2_13

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
  override implicit val version: ScalaVersion = Scala_2_13

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
