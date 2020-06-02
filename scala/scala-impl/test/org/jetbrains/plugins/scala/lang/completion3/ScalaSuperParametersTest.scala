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
      s"""
         |class A(x: Int, y: Int) {
         |  def this(x: Int, y: Int, z: Int) = this(x, y)
         |}
         |
         |class B(x: Int, y: Int, z: Int) extends A(x, y)$CARET
      """.stripMargin,
    item = "x, y",
    completionType = SMART
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
}
