package org.jetbrains.plugins.scala
package lang
package navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction.findAllTargetElements
import com.intellij.psi.{PsiElement, PsiPackage}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction.CommonNames.Apply
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject}
import org.junit.Assert._

/**
  * Nikolay.Tropin
  * 08-Nov-17
  */
abstract class GotoDeclarationTestBase extends GoToTestBase {
  protected def doTest(fileText: String, expected: (PsiElement => Boolean, String)*): Unit = {
    configureFromFileText(fileText)

    val editor = getEditor
    val targets =
      findAllTargetElements(getProject, editor, editor.getCaretModel.getOffset)
        .map(_.getNavigationElement)
        .toSet

    assertEquals("Wrong number of targets: ", expected.size, targets.size)

    val wrongTargets = for {
      (actualElement, (predicate, expectedName)) <- targets.zip(expected)

      if !predicate(actualElement) || actualName(actualElement) != expectedName
    } yield actualElement

    assertTrue("Wrong targets: " + wrongTargets, wrongTargets.isEmpty)
  }
}

class GoToDeclarationTest extends GotoDeclarationTestBase {

  def testSyntheticApply(): Unit = doTest(
    s"""
       |case class Test(i: Int)
       |
       |object Test {
       |  def apply(boolean: Boolean) = ${CARET}Test(0)
       |}
      """,
    expected = (is[ScClass], "Test")
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
    expected = (is[ScClass], "Test")
  )

  def testSyntheticCopy(): Unit = doTest(
    s"""
       |case class Test(i: Int)
       |
       |object Test {
       |  Test(1).${CARET}copy(i = 2)
       |}
      """,
    expected = (is[ScClass], "Test")
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
    expected = (is[ScObject], "Test"), (is[ScFunction], Apply)
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
    expected = (isPackageObject, "org.example.foo.bar")
  )

  def testPackageObjectAmbiguity(): Unit = doTest(
    s"""
       |package org.example.foo
       |
       |package object bar { }
       |
       |import org.example.foo.b${CARET}ar
     """,
    expected = (isPackageObject, "org.example.foo.bar"),
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
    (_.isInstanceOf[PsiPackage], "org.example.foo.bar"),
    (isPackageObject, "org.example.foo.bar")
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
    expected = (isPackageObject, "org.example.foo.bar")
  )

  def testGenerator_map(): Unit = doTest(
    s"""
       |object Test {
       |  for {
       |    x <$CARET- Option(1)
       |  } yield x
       |}
     """.stripMargin,
    expected = (is[ScFunction], "map")
  )

  def testGenerator_foreach(): Unit = doTest(
    s"""
       |object Test {
       |  for {
       |    x <$CARET- Option(1)
       |  } x
       |}
     """.stripMargin,
    expected = (is[ScFunction], "foreach")
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
    expected = (is[ScFunction], "withFilter")
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
    expected = (is[ScFunction], "map")
  )

  def testLibraryClassParam(): Unit = doTestFromLibrarySource(
    s"""import scala.util.Failure
      |
      |object Test {
      |  val f: Failure[Unit] = ???
      |  f.${CARET}exception
      |}
      |""".stripMargin,
    expected = (is[ScClassParameter], "exception")
  )

  def testLibraryClass(): Unit = doTestFromLibrarySource(
    s"""object TestLibraryClass {
       |  val option: ${CARET}Option[Int] = ???
       |}
       |""".stripMargin,
    (is[ScClass], "scala.Option")

  )

  def testLibraryObject(): Unit = doTestFromLibrarySource(
    s"""object TestLibraryObject {
       |  ${CARET}None
       |}
       |""".stripMargin,
    (is[ScObject], "scala.None")
  )

  def testLibraryTypeAlias(): Unit = doTestFromLibrarySource(
    s"""object TestLibraryTypeAlias {
       |  val seq: ${CARET}Seq[Int] = ???
       |}""".stripMargin,
    (is[ScTypeAlias], "Seq")
  )

  def testLibraryVal(): Unit = doTestFromLibrarySource(
    s"""object TestLibraryVal {
       |  Predef.${CARET}Map
       |}
       |""".stripMargin,
    (isVal, "Map")
  )

  def testLibraryFunction(): Unit = doTestFromLibrarySource(
    s"""object TestLibraryFunction {
       |  None.${CARET}get
       |}
       |""".stripMargin,
    (is[ScFunction], "get")
  )

  def testLibraryFunctionParam(): Unit = doTestFromLibrarySource(
    s"""object TestLibraryFunctionParam {
       |  Nil.mkString(${CARET}start = "(", sep = ",", end = "")
       |}
       |""".stripMargin,
    (is[ScParameter], "start")
  )

  private def doTestFromLibrarySource(fileText: String, expected: (PsiElement => Boolean, String)*): Unit = {
    val conditionsWithSourceCheck = expected.map {
      case (condition, name) => ((element: PsiElement) => isFromScalaSource(element) && condition(element), name)
    }
    doTest(fileText, conditionsWithSourceCheck: _*)
  }
}