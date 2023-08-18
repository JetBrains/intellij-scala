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
         |  val x: Test = new ${|<}Te${CARET}st${>|}
         |  new ${|<}Test${>|}
         |}
       """.stripMargin
    doTest(code)
  }

  def testClassConstructorInvocationUsage_2(): Unit = {
    val code =
      s"""class ${start}MyClass$end(s: String) {
         |  def this(x: Int) = ${start}this$end(x.toString)
         |  def this(x: Short) = this(x.toInt)
         |}
         |
         |new $start${CARET}MyClass$end("test1")
         |new ${start}MyClass$end("test2")
         |
         |new MyClass(23)
         |new MyClass(42)
         |
         |new MyClass(23.toShort)
         |new MyClass(42.toShort)
         |
         |val x: MyClass = ???
         |""".stripMargin
    doTest(code)
  }

  def testAuxiliaryConstructorInvocationUsage(): Unit = {
    val code =
      s"""
         |object Obj {
         |  class ${|<}Test${>|} {
         |    def ${|<}this${>|}(i: Int) = this()
         |  }
         |  val x: Test = new ${|<}Te${CARET}st${>|}(3)
         |  new Test
         |}
         |""".stripMargin
    doTest(code)
  }

  def testAuxiliaryConstructorInvocationUsage_2(): Unit = {
    val code =
      s"""class ${start}MyClass$end(s: String) {
         |  def ${start}this$end(x: Int) = this(x.toString)
         |  def this(x: Short) = ${start}this$end(x.toInt)
         |}
         |
         |new MyClass("test1")
         |new MyClass("test2")
         |
         |new $start${CARET}MyClass$end(23)
         |new ${start}MyClass$end(42)
         |
         |new MyClass(23.toShort)
         |new MyClass(42.toShort)
         |
         |val x: MyClass = ???
         |""".stripMargin
    doTest(code)
  }

  def testAuxiliaryConstructorInvocationUsage_3(): Unit = {
    val code =
      s"""class ${start}MyClass${end}(s: String) {
         |  def this(x: Int) = this(x.toString)
         |  def ${start}this$end(x: Short) = this(x.toInt)
         |}
         |
         |new MyClass("test1")
         |new MyClass("test2")
         |
         |new MyClass(23)
         |new MyClass(42)
         |
         |new $start${CARET}MyClass$end(23.toShort)
         |new ${start}MyClass$end(42.toShort)
         |
         |val x: MyClass = ???
         |""".stripMargin
    doTest(code)
  }

  def testAuxiliaryConstructorUsage(): Unit = {
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


