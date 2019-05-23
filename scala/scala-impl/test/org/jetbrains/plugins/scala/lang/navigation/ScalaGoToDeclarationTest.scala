package org.jetbrains.plugins.scala
package lang
package navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.base.{ScalaLightCodeInsightFixtureTestAdapter, ScalaLightPlatformCodeInsightTestCaseAdapter}
import org.junit.Assert.assertEquals

/**
  * Nikolay.Tropin
  * 08-Nov-17
  */
class ScalaGoToDeclarationTest extends ScalaLightPlatformCodeInsightTestCaseAdapter {

  import EditorTestUtil.{CARET_TAG => CARET}

  def testSyntheticApply(): Unit = doTest(
    s"""
       |case class Test(i: Int)
       |
       |object Test {
       |  def apply(boolean: Boolean) = ${CARET}Test(0)
       |}
      """,
    expected = "ScClass: Test"
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
    expected = "ScClass: Test"
  )

  def testSyntheticCopy(): Unit = doTest(
    s"""
       |case class Test(i: Int)
       |
       |object Test {
       |  Test(1).${CARET}copy(i = 2)
       |}
      """,
    expected = "ScClass: Test"
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
    expected = "ScObject: Test", "ScFunctionDefinition: apply"
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
    expected = "ScPackageObject: bar"
  )

  def testPackageObjectAmbiguity(): Unit = doTest(
    s"""
       |package org.example.foo
       |
       |package object bar { }
       |
       |import org.example.foo.b${CARET}ar
     """,
    expected = "ScPackageObject: bar", "PsiPackage:org.example.foo.bar"
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
    expected = "PsiPackage:org.example.foo.bar", "ScPackageObject: bar"
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
    expected = "ScPackageObject: bar"
  )

  def testGenerator_map(): Unit = doTest(
    s"""
       |object Test {
       |  for {
       |    x <$CARET- Option(1)
       |  } yield x
       |}
     """.stripMargin,
    expected = "ScFunctionDefinition: map"
  )

  def testGenerator_foreach(): Unit = doTest(
    s"""
       |object Test {
       |  for {
       |    x <$CARET- Option(1)
       |  } x
       |}
     """.stripMargin,
    expected = "ScFunctionDefinition: foreach"
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
    expected = "ScFunctionDefinition: withFilter"
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
    expected = "ScFunctionDefinition: map"
  )

  private def doTest(fileText: String, expected: String*): Unit = {
    val text = ScalaLightCodeInsightFixtureTestAdapter.normalize(fileText)
    configureFromFileTextAdapter("dummy.scala", text)

    val editor = getEditorAdapter
    val targets = GotoDeclarationAction.findAllTargetElements(
      getProjectAdapter,
      editor,
      editor.getCaretModel.getOffset
    ).map(_.toString).toSet

    assertEquals("Wrong number of targets: ", expected.size, targets.size)
    assertEquals("Wrong targets: ", expected.toSet, targets)
  }

}