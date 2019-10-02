package org.jetbrains.plugins.scala
package lang
package navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction.findAllTargetElements
import com.intellij.psi.{PsiElement, PsiPackage}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction.CommonNames.Apply
import org.junit.Assert._

/**
  * Nikolay.Tropin
  * 08-Nov-17
  */
class GoToDeclarationTest extends GoToTestBase {

  def testSyntheticApply(): Unit = doTest(
    s"""
       |case class Test(i: Int)
       |
       |object Test {
       |  def apply(boolean: Boolean) = ${CARET}Test(0)
       |}
      """,
    expected = (isClass, "Test")
  )

  def testSyntheticUnapply(): Unit = doTest(
    s"""
       |case class Test(i: Int)
       |
       |object Test {
       |  null match {
       |    case ${CARET}Test(1) =>
       |  }
       |}
      """,
    expected = (isClass, "Test")
  )

  def testSyntheticCopy(): Unit = doTest(
    s"""
       |case class Test(i: Int)
       |
       |object Test {
       |  Test(1).${CARET}copy(i = 2)
       |}
      """,
    expected = (isClass, "Test")
  )

  def testApply(): Unit = doTest(
    s"""
       |case class Test(i: Int)
       |
       |object Test {
       |  def apply(b: Boolean) = Test(0)
       |
       |  ${CARET}Test(false)
       |}
      """,
    expected = (isObject, "Test$"), (isFunction, Apply)
  )

  def testPackageObjectOnly(): Unit = doTest(
    s"""
       |package org.example.foo
       |
       |package object bar {
       | def foo(): Unit = ???
       |}
       |
       |import org.example.foo.b${CARET}ar.foo
     """,
    expected = (isPackageObject, "org.example.foo.bar.package$")
  )

  def testPackageObjectAmbiguity(): Unit = doTest(
    s"""
       |package org.example.foo
       |
       |package object bar { }
       |
       |import org.example.foo.b${CARET}ar
     """,
    expected = (isPackageObject, "org.example.foo.bar.package$"),
    (_.isInstanceOf[PsiPackage], "org.example.foo.bar")
  )

  def testImportSelectorsMixed(): Unit = doTest(
    s"""
       |package org.example.foo
       |
       |package bar {
       |  object Bar
       |}
       |
       |package object bar {
       |  def baz(): Unit = ???
       |}
       |
       |import org.example.foo.ba${CARET}r.{Bar, baz}
     """,
    expected = (_.isInstanceOf[PsiPackage], "org.example.foo.bar"),
    (isPackageObject, "org.example.foo.bar.package$")
  )

  def testImportSelectorsExclusive(): Unit = doTest(
    s"""
       |package org.example.foo
       |
       |package bar {
       |  object Bar
       |}
       |
       |package object bar {
       |  def baz(): Unit = ???
       |  def qux(): Int = 52
       |}
       |
       |import org.example.foo.ba${CARET}r.{qux, baz}
     """,
    expected = (isPackageObject, "org.example.foo.bar.package$")
  )

  def testGenerator_map(): Unit = doTest(
    s"""
       |object Test {
       |  for {
       |    x <$CARET- Option(1)
       |  } yield x
       |}
     """.stripMargin,
    expected = (isFunction, "map")
  )

  def testGenerator_foreach(): Unit = doTest(
    s"""
       |object Test {
       |  for {
       |    x <$CARET- Option(1)
       |  } x
       |}
     """.stripMargin,
    expected = (isFunction, "foreach")
  )

  def testGuard(): Unit = doTest(
    s"""
       |object Test {
       |  for {
       |    x <- Option(1)
       |    i${CARET}f x > 9
       |  } x
       |}
     """.stripMargin,
    expected = (isFunction, "withFilter")
  )

  def testForBinding_none(): Unit = doTest(
    s"""
       |object Test {
       |  for {
       |    x <- Option(1)
       |    y = x
       |  } yield y
       |}
     """.stripMargin
  )

  def testForBinding_map(): Unit = doTest(
    s"""
       |object Test {
       |  for {
       |    x <- Option(1)
       |    y $CARET= x
       |    if y > 0
       |  } yield y
       |}
     """.stripMargin,
    expected = (isFunction, "map")
  )

  private def doTest(fileText: String, expected: (PsiElement => Boolean, String)*): Unit = {
    configureFromFileText(fileText)

    val editor = getEditor
    val targets = findAllTargetElements(
      getProject,
      editor,
      editor.getCaretModel.getOffset
    ).toSet

    assertEquals("Wrong number of targets: ", expected.size, targets.size)

    val wrongTargets = for {
      (actualElement, (predicate, expectedName)) <- targets.zip(expected)

      if !(predicate(actualElement) && hasExpectedName(actualElement, expectedName))
    } yield actualElement

    assertTrue("Wrong targets: " + wrongTargets, wrongTargets.isEmpty)
  }
}