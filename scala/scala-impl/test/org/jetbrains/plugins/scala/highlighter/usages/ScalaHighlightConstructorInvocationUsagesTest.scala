package org.jetbrains.plugins.scala.highlighter.usages

class ScalaHighlightConstructorInvocationUsagesTest extends ScalaHighlightConstructorInvocationUsagesTestBase {
  def testClassDefinitionUsage(): Unit = {
    val code =
      s"""
         |object Obj {
         |  class ${|<}Te${CARET}st${>|}
         |  val x: ${|<}Test${>|} = new ${|<}Test${>|}
         |}
       """.stripMargin
    doTest(code)
  }

  def testClassConstructorInvocationUsage(): Unit = {
    val code =
      s"""
         |object Obj {
         |  class ${|<}Test${>|}
         |  val x: ${|<}Test${>|} = new ${|<}Te${CARET}st${>|}
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
         |  val x: ${|<}Test${>|} = new ${|<}Te${CARET}st${>|}(3)
         |  new ${|<}Test${>|}
         |}
         |""".stripMargin
    doTest(code)
  }

  def testAuxiliaryConstructorInvocationUsage(): Unit = {
    val code =
      s"""
         |object Obj {
         |  class Test {
         |    def ${|<}th${CARET}is${>|}(i: Int) = this()
         |  }
         |  val x: Test = new ${|<}Test${>|}(3)
         |  new Test
         |}
         |""".stripMargin
    doTest(code)
  }

  def testClassTypeAnnotationUsage(): Unit = {
    val code =
      s"""
         |object Obj {
         |  class ${|<}Test${>|}
         |  val x: ${|<}Te${CARET}st${>|} = new ${|<}Test${>|}
         |  new ${|<}Test${>|}
         |}
       """.stripMargin
    doTest(code)
  }

  def testTraitTypeAnnotationUsage(): Unit = {
    val code =
      s"""
         |object Obj {
         |  trait ${|<}Test${>|}
         |  val x: ${|<}Test${>|} = new ${|<}Te${CARET}st${>|} {}
         |  new ${|<}Test${>|} {}
         |}
       """.stripMargin
    doTest(code)
  }
}


