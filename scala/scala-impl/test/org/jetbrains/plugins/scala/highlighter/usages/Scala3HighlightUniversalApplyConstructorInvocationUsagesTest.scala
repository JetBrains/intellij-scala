package org.jetbrains.plugins.scala.highlighter.usages

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class Scala3HighlightUniversalApplyConstructorInvocationUsagesTest extends ScalaHighlightConstructorInvocationUsagesTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_3_0

  def testClassDefinitionUsage(): Unit = {
    val code =
      s"""
         |object Obj {
         |  class ${|<}Te${|}st${>|}
         |  val x: ${|<}Test${>|} = ${|<}Test${>|}()
         |}
       """.stripMargin
    doTest(code)
  }

  def testClassConstructorInvocationUsage(): Unit = {
    val code =
      s"""
         |object Obj {
         |  class ${|<}Test${>|}
         |  val x: ${|<}Test${>|} = ${|<}Te${|}st${>|}()
         |  ${|<}Test${>|}()
         |  new ${|<}Test${>|}
         |}
       """.stripMargin
    doTest(code)
  }

  def testAuxiliaryConstructorUsage(): Unit = {
    val code =
      s"""
         |object Obj {
         |  class ${|<}Test${>|} {
         |    def ${|<}this${>|}(i: Int) = this()
         |  }
         |  val x: ${|<}Test${>|} = ${|<}Te${|}st${>|}(3)
         |  ${|<}Test${>|}()
         |}
         |""".stripMargin
    doTest(code)
  }

  def testAuxiliaryConstructorInvocationUsage(): Unit = {
    val code =
      s"""
         |object Obj {
         |  class Test {
         |    def ${|<}th${|}is${>|}(i: Int) = this()
         |  }
         |  val x: Test = ${|<}Test${>|}(3)
         |  Test()
         |}
         |""".stripMargin
    doTest(code)
  }

  def testClassTypeAnnotationUsage(): Unit = {
    val code =
      s"""
         |object Obj {
         |  class ${|<}Test${>|}
         |  val x: ${|<}Te${|}st${>|} = ${|<}Test${>|}()
         |  ${|<}Test${>|}()
         |}
       """.stripMargin
    doTest(code)
  }
}
