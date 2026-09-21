package org.jetbrains.plugins.scala.actions

import com.intellij.openapi.ide.CopyPasteManager
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.util.assertions.AssertionMatchers.AssertMatchersExt

import java.awt.datatransfer.{DataFlavor, Transferable}

class CopyTypeActionTest extends ScalaLightCodeInsightFixtureTestCase {

  override def runInDispatchThread(): Boolean = false

  def doTest(fileText: String, expectedCopiedText: String): Unit = {
    configureFromFileText(fileText)

    var actualCopiedText: String = null
    CopyPasteManager.getInstance.addContentChangedListener((_: Transferable, newTransferable: Transferable) => {
      if (actualCopiedText != null) {
        // note: this might not fail the test, but the exception should be visible in the logs
        throw new AssertionError("More than one content changed event was fired")
      }
      val copiedText = newTransferable.getTransferData(DataFlavor.stringFlavor).asInstanceOf[String]
      actualCopiedText = copiedText
    }, getTestRootDisposable)

    ScalaAsyncActionTestUtils.invokeActionAndWaitForCompletion(
      getProject,
      classOf[org.jetbrains.plugins.scala.actions.CopyTypeAction],
      () => {
        getFixture.performEditorAction(CopyTypeAction.ActionId)
      },
    )

    actualCopiedText shouldBe expectedCopiedText
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

  def testUnitReturned(): Unit = doTest(
    s"""def test = 4
       |
       |def blub: Unit = te${CARET}st
       |""".stripMargin,
    "Int"
  )

  def testLocalStableType(): Unit = doTest(
    s"""
       |class Bar
       |
       |def test: Unit = {
       |  val bar = new Bar
       |  b${CARET}ar.getClass
       |}
       |""".stripMargin,
    "Bar" // and not a.type
  )

  def testGenericCall(): Unit = doTest(
    s"""
       |class Blub[T]
       |def foo[T](i: T): Blub[T] = ???
       |
       |fo${CARET}o(1)
       |""".stripMargin,
    "Int => Blub[Int]"
  )

  def testGenericCallWithByNameParameter(): Unit = doTest(
    s"""def foo[A](f: => A): List[A] = List()
       |
       |fo${CARET}o(1)
       |""".stripMargin,
    "Int => List[Int]"
  )

  def testSelectedGenericMethodReference(): Unit = doTest(
    s"""def foo[A](a: A): List[A] = List(a)
       |${START}foo$END(1)
       |""".stripMargin,
    "Int => List[Int]"
  )

  def testGenericCallWithMultipleTypeParameters(): Unit = doTest(
    s"""def foo[A, B](a: A, b: B): (A, B) = (a, b)
       |fo${CARET}o(1, "text")
       |""".stripMargin,
    "(Int, String) => (Int, String)"
  )

  def testGenericCallWithUnconstrainedTypeParameterAndExpectedType(): Unit = doTest(
    s"""def foo[A](): List[A] = List()
       |val result: List[String] = fo${CARET}o()
       |""".stripMargin,
    "() => List[Nothing]"
  )

  def testGenericParameterlessMethodWithExpectedType(): Unit = doTest(
    s"""def foo[A]: A = ???
       |val result: String = fo${CARET}o
       |""".stripMargin,
    "String"
  )

  def testGenericCallWithUnconstrainedTypeParameter(): Unit = doTest(
    s"""def foo[A](i: Int): List[A] = List()
       |fo${CARET}o(1)
       |""".stripMargin,
    "Int => List[Nothing]"
  )

  def testGenericInfixCall(): Unit = doTest(
    s"""class Foo {
       |  def foo[A](a: A): List[A] = List(a)
       |}
       |new Foo fo${CARET}o 1
       |""".stripMargin,
    "Int => List[Int]"
  )

  def testGenericCallWithExplicitTypeArguments(): Unit = doTest(
    s"""def foo[A](a: A): List[A] = List(a)
       |fo${CARET}o[String]("text")
       |""".stripMargin,
    "String => List[String]"
  )

  def testGenericCurriedCall(): Unit = doTest(
    s"""def foo[A, B](a: A)(b: B): (A, B) = (a, b)
       |fo${CARET}o(1)("text")
       |""".stripMargin,
    "Int => String => (Int, String)"
  )

  def testGenericEtaExpansion(): Unit = doTest(
    s"""def foo[A](a: A): List[A] = List(a)
       |val f: Int => List[Int] = fo${CARET}o _
       |""".stripMargin,
    "Int => List[Int]"
  )

  def testInner(): Unit = doTest(
    s"""
       |object A {
       |  class C
       |}
       |
       |object B {
       |  import A.C
       |
       |  new ${CARET}C
       |}
       |""".stripMargin,
    "A.C"
  )
}
