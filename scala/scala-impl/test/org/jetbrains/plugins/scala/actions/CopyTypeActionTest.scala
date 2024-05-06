package org.jetbrains.plugins.scala.actions

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.util.assertions.AssertionMatchers.AssertMatchersExt

class CopyTypeActionTest extends ScalaLightCodeInsightFixtureTestCase {
  def doTest(text: String, expected: String): Unit = {
    configureFromFileText(text)
    var copied: String = null
    CopyTypeAction.withUnitTestClipboardListener(copied = _) {
      getFixture.performEditorAction(CopyTypeAction.ActionId)
    }

    copied shouldBe expected
  }

  def testClass(): Unit = doTest(
    s"class ${CARET}A",
    "A"
  )

  def testTrait(): Unit = doTest(
    s"trait ${CARET}A",
    "A"
  )

  def testObject(): Unit = doTest(
    s"object ${CARET}A",
    "A.type"
  )

  def testVal(): Unit = doTest(
    s"val ${CARET}a: Int",
    "Int"
  )

  def testVar(): Unit = doTest(
    s"var ${CARET}a: Int",
    "Int"
  )

  def testDef(): Unit = doTest(
    s"def ${CARET}foo: Int",
    "Int"
  )

  def testLiteral(): Unit = doTest(
    s"val a = ${CARET}1",
    "Int"
  )

  def testTuple(): Unit = doTest(
    s"val a = ${CARET}(1, 2)",
    "(Int, Int)"
  )

  def testInfixExpr(): Unit = doTest(
    s"val a = ${START}1 :: Nil$END",
    "List[Int]"
  )

  def testType(): Unit = doTest(
    s"""trait A
       |val a: ${CARET}A
       |""".stripMargin,
    "A"
  )

  def testTypeProjection1(): Unit = doTest(
    s"""trait A {
       |  type B
       |}
       |val a: ${CARET}A#B
       |""".stripMargin,
    "A"
  )

  def testTypeProjection2(): Unit = doTest(
    s"""trait A {
       |  type B
       |}
       |val a: A#${CARET}B
       |""".stripMargin,
    "A#B"
  )

  def testInfixType1(): Unit = doTest(
    s"""trait A
       |trait B
       |trait Or[X, Y]
       |val a: ${CARET}A Or B
       |""".stripMargin,
    "A"
  )

  def testInfixType2(): Unit = doTest(
    s"""trait A
       |trait B
       |trait Or[X, Y]
       |val a: ${START}A Or B$END
       |""".stripMargin,
    "Or[A, B]"
  )

  def testResolvedAlias(): Unit = doTest(
    s"""type A = Int
       |val a: ${CARET}A = ???
       |""".stripMargin,
    "Int"
  )

  def testSelectedMethodCall(): Unit = doTest(
    s"""def test[T](t: T): Seq[T] = ???
       |${START}test(1)$END
       |""".stripMargin,
    "Seq[Int]"
  )

  def testSelectedPrint(): Unit = doTest(
    s"""def blub = ${START}println(1)$END
       |""".stripMargin,
    "Unit"
  )
}
