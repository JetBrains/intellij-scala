package org.jetbrains.plugins.scala.lang.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction.findAllTargetElements
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScGiven
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

final class GoToDeclarationGivenImportTest extends GotoDeclarationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  override protected def doTest(fileText: String, expected: (PsiElement => Boolean, String)*): Unit = {
    configureFromFileText(fileText)

    val editor = getEditor
    val targets = findAllTargetElements(getProject, editor, editor.getCaretModel.getOffset).toSeq

    checkTargets(targets, expected)
  }

  def testGoToWildcardGiven(): Unit = doTest(
    s"""
       |object Foo {
       |  given str: String = "foo"
       |  given Int = 42
       |}
       |
       |object Test {
       |  import Foo.giv${CARET}en
       |}
       |""".stripMargin,
    expected = (is[ScGiven], "str"), (is[ScGiven], "given_Int")
  )

  def testGoToGivenSelectorByType_caretOnKeyword(): Unit = doTest(
    s"""
       |object Foo {
       |  given str: String = "foo"
       |  given Int = 42
       |}
       |
       |object Test {
       |  import Foo.giv${CARET}en Int
       |}
       |""".stripMargin,
    expected = (is[ScGiven], "given_Int")
  )

  def testGoToGivenSelectorByType_caretOnType(): Unit = doTest(
    s"""
       |object Foo {
       |  class Bar
       |  given Bar = Bar()
       |}
       |
       |object Test {
       |  import Foo.Bar
       |  import Foo.given B${CARET}ar
       |}
       |""".stripMargin,
    expected = (is[ScGiven], "given_Bar")
  )

  def testGoToGivenSelectorByType_caretOnTypeParameter(): Unit = doTest(
    s"""
       |object Foo {
       |  class Bar
       |  given List[Bar] = Bar() :: Nil
       |  given List[String] = "foo" :: Nil
       |}
       |
       |object Test {
       |  import Foo.Bar
       |  import Foo.given List[Ba${CARET}r]
       |}
       |""".stripMargin,
    expected = (is[ScGiven], "given_List_Bar")
  )

  def testGoToGivenSelectorByType_caretOnParameterizedType(): Unit = doTest(
    s"""
       |object Foo {
       |  class Bar
       |  given List[Bar] = Bar() :: Nil
       |  given List[String] = "foo" :: Nil
       |}
       |
       |object Test {
       |  import Foo.Bar
       |  import Foo.given Li${CARET}st[Bar]
       |}
       |""".stripMargin,
    expected = (is[ScGiven], "given_List_Bar")
  )

  def testGoToImportedGivenName(): Unit = doTest(
    s"""
       |object Foo {
       |  given str: String = "foo"
       |  given Int = 42
       |}
       |
       |object Test {
       |  import Foo.s${CARET}tr
       |}
       |""".stripMargin,
    expected = (is[ScGiven], "str")
  )
}
