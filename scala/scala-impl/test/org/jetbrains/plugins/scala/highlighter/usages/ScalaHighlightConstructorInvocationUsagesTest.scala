package org.jetbrains.plugins.scala.highlighter.usages

class ScalaHighlightConstructorInvocationUsagesTest extends ScalaHighlightConstructorInvocationUsagesTestBase {
  def testClassDefinitionUsage(): Unit = {
    val code =
      s"""
         |object Obj {
         |  class ${start}Te${CARET}st$end
         |  val x: ${start}Test$end = new ${start}Test$end
         |}
       """.stripMargin
    doTest(code)
  }

  def testClassConstructorInvocationUsage(): Unit = {
    val code =
      s"""
         |object Obj {
         |  class ${start}Test$end
         |  val x: Test = new ${start}Te${CARET}st$end
         |  new ${start}Test$end
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
         |  class ${start}Test$end {
         |    def ${start}this$end(i: Int) = this()
         |  }
         |  val x: Test = new ${start}Te${CARET}st$end(3)
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
      s"""class ${start}MyClass$end(s: String) {
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
         |    def ${start}th${CARET}is$end(i: Int) = this()
         |  }
         |  val x: Test = new ${start}Test$end(3)
         |  new Test
         |}
         |""".stripMargin
    doTest(code)
  }

  def testClassTypeAnnotationUsage(): Unit = {
    val code =
      s"""
         |object Obj {
         |  class ${start}Test$end
         |  val x: ${start}Te${CARET}st$end = new ${start}Test$end
         |  new ${start}Test$end
         |}
       """.stripMargin
    doTest(code)
  }

  def testTraitTypeAnnotationUsage(): Unit = {
    val code =
      s"""
         |object Obj {
         |  trait ${start}Test$end
         |  val x: ${start}Test$end = new ${start}Te${CARET}st$end {}
         |  new ${start}Test$end {}
         |}
       """.stripMargin
    doTest(code)
  }

  def testCaseClassOnClass(): Unit = doTestWithDifferentCarets(
    s"""
       |case class ${start}Blub$end(a: Int)
       |
       |val a = ${start}B${multiCaret(0)}l${multiCaret(1)}ub$end(42)
       |val b = new ${start}Blub$end(43)
       |val ${start}Bl${multiCaret(2)}ub$end(c) = a
       |
       |for (${start}Bl${multiCaret(3)}ub$end(d) <- Option(b)) {
       |}
       |""".stripMargin
  )
}


