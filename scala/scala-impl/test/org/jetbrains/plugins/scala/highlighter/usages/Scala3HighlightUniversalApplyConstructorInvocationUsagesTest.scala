package org.jetbrains.plugins.scala.highlighter.usages

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class Scala3HighlightUniversalApplyConstructorInvocationUsagesTest extends ScalaHighlightConstructorInvocationUsagesTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_3_0

  def testClassDefinitionUsage(): Unit = {
    val code =
      s"""
         |object Obj {
         |  class ${start}Te${CARET}st$end
         |  val x: ${start}Test$end = ${start}Test$end()
         |}
       """.stripMargin
    doTest(code)
  }

  def testClassConstructorInvocationUsage_WithUniversalApplySyntax(): Unit = {
    val code =
      s"""
         |object Obj {
         |  class ${start}Test$end
         |  val x: Test = ${start}Te${CARET}st$end()
         |  ${start}Test$end()
         |  new ${start}Test$end
         |}
       """.stripMargin
    doTest(code)
  }

  def testClassConstructorInvocationUsage_WithUniversalApplySyntax_2(): Unit = {
    val code =
      s"""class ${start}MyClass$end(s: String) {
         |  def this(x: Int) = ${start}this$end(x.toString)
         |  def this(x: Short) = this(x.toInt)
         |}
         |
         |new ${start}MyClass$end("test1")
         |new ${start}MyClass$end("test2")
         |$start${CARET}MyClass$end("test1")
         |${start}MyClass$end("test2")
         |
         |new MyClass(23)
         |new MyClass(42)
         |MyClass(23)
         |MyClass(42)
         |
         |new MyClass(23.toShort)
         |new MyClass(42.toShort)
         |MyClass(23.toShort)
         |MyClass(42.toShort)
         |
         |val x: MyClass = ???
         |""".stripMargin
    doTest(code)
  }

  def testAuxiliaryConstructorInvocationUsage_WithUniversalApplySyntax_(): Unit = {
    val code =
      s"""
         |object Obj {
         |  class ${start}Test$end {
         |    def ${start}this$end(i: Int) = this()
         |  }
         |  val x: Test = ${start}Te${CARET}st$end(3)
         |  Test()
         |}
         |""".stripMargin
    doTest(code)
  }


  def testAuxiliaryConstructorInvocationUsage_WithUniversalApplySyntax_2(): Unit = {
    val code =
      s"""class ${start}MyClass$end(s: String) {
         |  def ${start}this$end(x: Int) = this(x.toString)
         |  def this(x: Short) = ${start}this$end(x.toInt)
         |}
         |
         |new MyClass("test1")
         |new MyClass("test2")
         |MyClass("test1")
         |MyClass("test2")
         |
         |new ${start}MyClass$end(23)
         |new ${start}MyClass$end(42)
         |${start}MyClass$end(23)
         |$start${CARET}MyClass$end(42)
         |
         |new MyClass(23.toShort)
         |new MyClass(42.toShort)
         |MyClass(23.toShort)
         |MyClass(42.toShort)
         |
         |val x: MyClass = ???
         |""".stripMargin
    doTest(code)
  }

  def testAuxiliaryConstructorInvocationUsage_WithUniversalApplySyntax_3(): Unit = {
    val code =
      s"""class ${start}MyClass$end(s: String) {
         |  def this(x: Int) = this(x.toString)
         |  def ${start}this$end(x: Short) = this(x.toInt)
         |}
         |
         |new MyClass("test1")
         |new MyClass("test2")
         |MyClass("test1")
         |MyClass("test2")
         |
         |new MyClass(23)
         |new MyClass(42)
         |MyClass(23)
         |MyClass(42)
         |
         |new ${start}MyClass$end(23.toShort)
         |new ${start}MyClass$end(42.toShort)
         |${start}MyClass$end(23.toShort)
         |$start${CARET}MyClass$end(42.toShort)
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
         |  val x: Test = ${start}Test$end(3)
         |  Test()
         |}
         |""".stripMargin
    doTest(code)
  }

  private def addMyJavaClass(): Unit = {
    getFixture.addFileToProject("MyJavaClass.java",
      """public class MyJavaClass {
        |    public MyJavaClass() {}
        |    public MyJavaClass(int i) {}
        |    public MyJavaClass(String s) {}
        |}
        |""".stripMargin
    )
  }

  def testJavaConstructorInvocation_1(): Unit = {
    addMyJavaClass()
    doTest(
      s"""object wrapper {
         |  new ${start}MyJavaClass$end()
         |  $CARET${start}MyJavaClass$end()
         |
         |  new MyJavaClass(42)
         |  MyJavaClass(42)
         |
         |  new MyJavaClass("42")
         |  MyJavaClass("42")
         |}
         |""".stripMargin
    )
  }

  def testJavaConstructorInvocation_2(): Unit = {
    addMyJavaClass()
    doTest(
      s"""object wrapper {
         |  new MyJavaClass()
         |  MyJavaClass()
         |
         |  new ${start}MyJavaClass$end(42)
         |  $CARET${start}MyJavaClass$end(42)
         |
         |  new MyJavaClass("42")
         |  MyJavaClass("42")
         |}
         |""".stripMargin
    )
  }

  def testJavaConstructorInvocation_3(): Unit = {
    addMyJavaClass()
    doTest(
      s"""object wrapper {
         |  new MyJavaClass()
         |  MyJavaClass()
         |
         |  new MyJavaClass(42)
         |  MyJavaClass(42)
         |
         |  new ${start}MyJavaClass$end("42")
         |  $CARET${start}MyJavaClass$end("42")
         |}
         |""".stripMargin
    )
  }

  def testClassTypeAnnotationUsage(): Unit = {
    val code =
      s"""
         |object Obj {
         |  class ${start}Test$end
         |  val x: ${start}Te${CARET}st$end = ${start}Test$end()
         |  ${start}Test$end()
         |}
       """.stripMargin
    doTest(code)
  }
}
