package org.jetbrains.plugins.scala
package lang
package completion3

import com.intellij.codeInsight.completion.CompletionType.SMART

/**
 * @author Alefas
 * @since 04.09.13
 */
class ScalaSuperParametersTest extends ScalaCodeInsightTestBase {

  def testConstructorCall(): Unit = doCompletionTest(
    fileText =
      s"""class A(x: Int, y: Int) {
         |  def this(x: Int, y: Int, z: Int) = this(x, y)
         |}
         |
         |class B(x: Int, y: Int, z: Int) extends A($CARET)
        """.stripMargin,
    resultText =
      s"""class A(x: Int, y: Int) {
         |  def this(x: Int, y: Int, z: Int) = this(x, y)
         |}
         |
         |class B(x: Int, y: Int, z: Int) extends A(x, y, z)$CARET
        """.stripMargin,
    item = "x, y, z"
  )

  def testConstructorCall2(): Unit = doCompletionTest(
    fileText =
      s"""class A(x: Int, y: Int) {
         |  def this(x: Int, y: Int, z: Int) = this(x, y)
         |}
         |
         |class B(x: Int, y: Int, z: Int) extends A($CARET)
        """.stripMargin,
    resultText =
      s"""class A(x: Int, y: Int) {
         |  def this(x: Int, y: Int, z: Int) = this(x, y)
         |}
         |
         |class B(x: Int, y: Int, z: Int) extends A(x, y)$CARET
        """.stripMargin,
    item = "x, y"
  )

  def testConstructorCall2Smart(): Unit = doCompletionTest(
    fileText =
      s"""class A(x: Int, y: Int) {
         |  def this(x: Int, y: Int, z: Int) = this(x, y)
         |}
         |
         |class B(x: Int, y: Int, z: Int) extends A($CARET)
        """.stripMargin,
    resultText =
      s"""class A(x: Int, y: Int) {
         |  def this(x: Int, y: Int, z: Int) = this(x, y)
         |}
         |
         |class B(x: Int, y: Int, z: Int) extends A(x, y)$CARET
      """.stripMargin,
    item = "x, y",
    completionType = SMART
  )

  def testEmptyConstructor(): Unit = checkNoCompletion(
    s"""class A()
       |
       |class B(x: Int, y: Int) extends A($CARET)
       |""".stripMargin
  )

  def testTooShortConstructor(): Unit = checkNoCompletion(
    s"""class A(x: Int)
       |
       |class B(x: Int, y: Int) extends A($CARET)
       |""".stripMargin
  )

  def testNoNameMatchingConstructor(): Unit = checkNoBasicCompletion(
    fileText =
      s"""class A(x: Int, y: Int)
         |
         |class B(x: Int, z: Int) extends A($CARET)
         |""".stripMargin,
    item = "x, y"
  )

  def testNoTypeMatchingConstructor(): Unit = checkNoBasicCompletion(
    fileText =
      s"""class A(x: Int, y: Int)
         |
         |class B(x: Int, y: String) extends A($CARET)
         |""".stripMargin,
    item = "x, y"
  )

  def testSuperCall(): Unit = doCompletionTest(
    fileText =
      s"""class A {
         |  def foo(x: Int, y: Int, z: Int) = 1
         |  def foo(x: Int, y: Int) = 2
         |}
         |
         |class B extends A {
         |  override def foo(x: Int, y: Int, z: Int) = {
         |    super.foo($CARET)
         |  }
         |}
        """.stripMargin,
    resultText =
      s"""class A {
         |  def foo(x: Int, y: Int, z: Int) = 1
         |  def foo(x: Int, y: Int) = 2
         |}
         |
         |class B extends A {
         |  override def foo(x: Int, y: Int, z: Int) = {
         |    super.foo(x, y)$CARET
         |  }
         |}
        """.stripMargin,
    item = "x, y"
  )

  def testSuperCall2(): Unit = doCompletionTest(
    fileText =
      s"""class A {
         |  def foo(x: Int, y: Int, z: Int) = 1
         |  def foo(x: Int, y: Int) = 2
         |}
         |
         |class B extends A {
         |  override def foo(x: Int, y: Int, z: Int) = {
         |    super.foo($CARET)
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""class A {
         |  def foo(x: Int, y: Int, z: Int) = 1
         |  def foo(x: Int, y: Int) = 2
         |}
         |
         |class B extends A {
         |  override def foo(x: Int, y: Int, z: Int) = {
         |    super.foo(x, y, z)$CARET
         |  }
         |}
      """.stripMargin,
    item = "x, y, z"
  )

  def testEmptySuperMethod(): Unit = checkNoCompletion(
    s"""class A {
       |  def foo() = 42
       |}
       |
       |class B extends A {
       |  override def foo() =
       |    super.foo($CARET)
       |}
       |""".stripMargin
  )

  def testTooShortSuperMethod(): Unit = checkNoCompletion(
    s"""class A {
       |  def foo(x: Int) = 42
       |}
       |
       |class B extends A {
       |  override def foo(x: Int) =
       |    super.foo($CARET)
       |}
       |""".stripMargin
  )

  def testNoNameMatchingSuperMethod(): Unit = checkNoBasicCompletion(
    fileText =
      s"""class A {
         |  def foo(x: Int, y: Int) = 42
         |}
         |
         |class B extends A {
         |  override def foo(x: Int, z: Int) =
         |    super.foo($CARET)
         |}
         |""".stripMargin,
    item = "x, y"
  )

  def testMethodCall(): Unit = doCompletionTest(
    fileText =
      s"""class A {
         |  def foo(x: Int, y: Int) = 1
         |}
         |
         |class B extends A {
         |  def bar(x: Int, y: Int) = {
         |    foo($CARET)
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""class A {
         |  def foo(x: Int, y: Int) = 1
         |}
         |
         |class B extends A {
         |  def bar(x: Int, y: Int) = {
         |    foo(x, y)$CARET
         |  }
         |}
      """.stripMargin,
    item = "x, y"
  )

  def testQualifiedMethodCall(): Unit = doCompletionTest(
    fileText =
      s"""class A {
         |  def foo(x: Int, y: Int) = 1
         |}
         |
         |class B {
         |  private val a = new A
         |
         |  def bar(x: Int, y: Int) = {
         |    a.foo($CARET)
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""class A {
         |  def foo(x: Int, y: Int) = 1
         |}
         |
         |class B {
         |  private val a = new A
         |
         |  def bar(x: Int, y: Int) = {
         |    a.foo(x, y)$CARET
         |  }
         |}
      """.stripMargin,
    item = "x, y"
  )

  def testQualifiedMethodCallCompletionChar(): Unit = doCompletionTest(
    fileText =
      s"""class A {
         |  def foo(x: Int, y: Int) = 1
         |}
         |
         |class B {
         |  private val a = new A
         |
         |  def bar(x: Int, y: Int) = {
         |    a.foo($CARET)
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""class A {
         |  def foo(x: Int, y: Int) = 1
         |}
         |
         |class B {
         |  private val a = new A
         |
         |  def bar(x: Int, y: Int) = {
         |    a.foo(x, y)$CARET
         |  }
         |}
      """.stripMargin,
    item = "x, y",
    char = ')'
  )

  def testEmptyMethod(): Unit = checkNoCompletion(
    s"""class A {
       |  def foo() = 42
       |}
       |
       |class B extends A {
       |  def bar() =
       |    foo($CARET)
       |}
       |""".stripMargin
  )

  def testTooShortMethod(): Unit = checkNoCompletion(
    s"""class A {
       |  def foo(x: Int) = 42
       |}
       |
       |class B extends A {
       |  def bar(x: Int) =
       |    foo($CARET)
       |}
       |""".stripMargin
  )

  def testNoNameMatchingMethod(): Unit = checkNoBasicCompletion(
    fileText =
      s"""class A {
         |  def foo(x: Int, y: Int) = 42
         |}
         |
         |class B extends A {
         |  def bar(x: Int, z: Int) =
         |    foo($CARET)
         |}
         |""".stripMargin,
    item = "x, y"
  )

  def testNoTypeMatchingMethod(): Unit = checkNoBasicCompletion(
    fileText =
      s"""class A {
         |  def foo(x: Int, y: Int) = 42
         |}
         |
         |class B extends A {
         |  def bar(x: Int, y: String) =
         |    foo($CARET)
         |}
         |""".stripMargin,
    item = "x, y"
  )

  def testCaseClass(): Unit = doRawCompletionTest(
    fileText =
      s"""final case class Foo()(foo: Int, bar: Int)
         |
         |Foo()(f$CARET)
         |""".stripMargin,
    resultText =
      s"""final case class Foo()(foo: Int, bar: Int)
         |
         |Foo()(foo = ???, bar = ???)$CARET
         |""".stripMargin,
  ) {
    ScalaCodeInsightTestBase.hasItemText(_, "foo, bar")(tailText = " = ")
  }

  def testPhysicalApplyMethod(): Unit = doCompletionTest(
    fileText =
      s"""final class Foo private(val foo: Int,
         |                        val bar: Int,
         |                        val baz: Int)
         |
         |object Foo {
         |
         |  def apply(foo: Int,
         |            bar: Int,
         |            baz: Int) =
         |    new Foo(foo, bar, baz)
         |
         |  def apply(foo: Int,
         |            bar: Int) =
         |    new Foo(foo, bar, 42)
         |}
         |
         |Foo(f$CARET)
         |""".stripMargin,
    resultText =
      s"""final class Foo private(val foo: Int,
         |                        val bar: Int,
         |                        val baz: Int)
         |
         |object Foo {
         |
         |  def apply(foo: Int,
         |            bar: Int,
         |            baz: Int) =
         |    new Foo(foo, bar, baz)
         |
         |  def apply(foo: Int,
         |            bar: Int) =
         |    new Foo(foo, bar, 42)
         |}
         |
         |Foo(foo = ???, bar = ???, baz = ???)$CARET
         |""".stripMargin,
    item = "foo, bar, baz"
  )

  def testPhysicalApplyMethod2(): Unit = doCompletionTest(
    fileText =
      s"""final class Foo private(val foo: Int,
         |                        val bar: Int,
         |                        val baz: Int)
         |
         |object Foo {
         |
         |  def apply(foo: Int,
         |            bar: Int,
         |            baz: Int) =
         |    new Foo(foo, bar, baz)
         |
         |  def apply(foo: Int,
         |            bar: Int) =
         |    new Foo(foo, bar, 42)
         |}
         |
         |Foo(f$CARET)
         |""".stripMargin,
    resultText =
      s"""final class Foo private(val foo: Int,
         |                        val bar: Int,
         |                        val baz: Int)
         |
         |object Foo {
         |
         |  def apply(foo: Int,
         |            bar: Int,
         |            baz: Int) =
         |    new Foo(foo, bar, baz)
         |
         |  def apply(foo: Int,
         |            bar: Int) =
         |    new Foo(foo, bar, 42)
         |}
         |
         |Foo(foo = ???, bar = ???)$CARET
         |""".stripMargin,
    item = "foo, bar"
  )

  def testCaseClassCompletionChar(): Unit = doCompletionTest(
    fileText =
      s"""final case class Foo(foo: Int, bar: Int)
         |
         |Foo(f$CARET)
         |""".stripMargin,
    resultText =
      s"""final case class Foo(foo: Int, bar: Int)
         |
         |Foo(foo, bar)$CARET
         |""".stripMargin,
    item = "foo, bar",
    char = ')'
  )

  def testEmptyCaseClass(): Unit = checkNoCompletion(
    s"""final case class Foo()
       |
       |Foo(f$CARET)
       |""".stripMargin
  )

  def testTooShortCaseClass(): Unit = checkNoCompletion(
    s"""final case class Foo(foo: Int)
       |
       |Foo(f$CARET)
       |""".stripMargin
  )

  def testNonApplyMethod(): Unit = checkNoCompletion(
    s"""object Foo {
       |  def baz(foo: Int, bar: Int): Unit = {}
       |}
       |
       |Foo.baz(f$CARET)
       |""".stripMargin
  )

  private def checkNoCompletion(fileText: String): Unit =
    super.checkNoCompletion(fileText) {
      _.getLookupString.contains(", ")
    }
}
