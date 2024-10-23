package org.jetbrains.plugins.scala.actions

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.actions.ShowTypeInfoAction.ActionId
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.util.EditorHintFixtureEx
import org.junit.Assert.assertEquals

/**
 * Note: IntelliJ inserts some annoying `<wbr>`` tags after `.`<br>
 * See `transpileTextNode` method in [[com.intellij.ui.components.impl.JBHtmlPaneInputTranspiler]]
 */
abstract class ShowTypeInfoActionTestBase extends ScalaLightCodeInsightFixtureTestCase {

  private var editorHintFixture: EditorHintFixtureEx = _

  override protected def setUp(): Unit = {
    super.setUp()
    editorHintFixture = new EditorHintFixtureEx(this.getTestRootDisposable)
  }

  protected def doShowTypeInfoTest(
    fileText: String,
    expectedTypeInfoHintBodyContent: String,
  ): Unit = {
    configureFromFileText(fileText)
    getFixture.performEditorAction(ActionId)

    assertEquals(
      expectedTypeInfoHintBodyContent.trim,
      editorHintFixture.getCurrentHintBodyText.trim
    )
  }

  def testTypeAlias_String(): Unit = doShowTypeInfoTest(
    s"type ${CARET}T = String",
    "String"
  )

  def testTypeAlias_Map(): Unit = doShowTypeInfoTest(
    s"type ${CARET}T[A] = Map[A, String]",
    "Map[A, String]"
  )

  def testTypeAccessibleFromTheContext_ClassType(): Unit = doShowTypeInfoTest(
    s"""object Scope {
       |  object ScopeInner {
       |    class MyClass
       |    val ${CARET}value: MyClass = ???
       |  }
       |}""".stripMargin,
    "MyClass"
  )

  def testTypeAccessibleFromTheContext_ClassType_1(): Unit = doShowTypeInfoTest(
    s"""object Scope {
       |  class MyClass
       |  object ScopeInner {
       |    val ${CARET}value: MyClass = ???
       |  }
       |}""".stripMargin,
    "MyClass"
  )

  def testTypeAccessibleFromTheContext_ClassType_2(): Unit = doShowTypeInfoTest(
    s"""object Scope {
       |  import ScopeInner.MyClass
       |  val ${CARET}value: ScopeInner.MyClass = ???
       |
       |  object ScopeInner {
       |    class MyClass
       |  }
       |}""".stripMargin,
    "MyClass"
  )

  def testTypeNotAccessibleFromTheContext_ClassType_2(): Unit = doShowTypeInfoTest(
    s"""object Scope {
       |  val ${CARET}value: ScopeInner.MyClass = ???
       |
       |  object ScopeInner {
       |    class MyClass
       |  }
       |}""".stripMargin,
    //NOTE: ideally it could also strip `Scope.` in this context
    "Scope.<wbr>ScopeInner.<wbr>MyClass"
  )

  def testTypeAccessibleFromTheContext_SingletonType_1(): Unit = doShowTypeInfoTest(
    s"""object Scope {
       |  val myValue = 23
       |  object ScopeInner {
       |    val ${CARET}value: myValue.type = ???
       |  }
       |}""".stripMargin,
    "myValue.<wbr>type"
  )

  def testTypeAccessibleFromTheContext_SingletonType_2(): Unit = doShowTypeInfoTest(
    s"""object Scope {
       |  import ScopeInner.myValue
       |  val ${CARET}value: ScopeInner.myValue.type = ???
       |7
       |  object ScopeInner {
       |    val myValue = 23
       |  }
       |}""".stripMargin,
    "myValue.<wbr>type"
  )

  def testTypeNotAccessibleFromTheContext_SingletonType_2(): Unit = doShowTypeInfoTest(
    s"""object Scope {
       |  val ${CARET}value: ScopeInner.myValue.type = ???
       |
       |  object ScopeInner {
       |    val myValue = 23
       |  }
       |}""".stripMargin,
    //NOTE: ideally it could also strip `Scope.` in this context
    "Scope.<wbr>ScopeInner.<wbr>myValue.<wbr>type"
  )

  def testUnderscoreParameter_WithSelection(): Unit = doShowTypeInfoTest(
    s"""Seq(1, 2).map($CARET${START}_$END.toString)""",
    "Int"
  )

  def testUnderscoreParameter_WithoutSelection_CaretBeforeUnderscore(): Unit = doShowTypeInfoTest(
    s"""Seq(1, 2).map(${CARET}_.toString)""",
    "Int"
  )

  def testUnderscoreParameter_WithoutSelection_CaretAfterUnderscore(): Unit = doShowTypeInfoTest(
    s"""Seq(1, 2).map(_$CARET.toString)""",
    "Int"
  )

  def testUnderscoreParameterWithType_WithSelection(): Unit = doShowTypeInfoTest(
    s"""Seq(1, 2).map(($CARET${START}_$END: Int).toString)""",
    "Int"
  )

  def testUnderscoreParameterWithType_WithoutSelection_CaretBeforeUnderscore(): Unit = doShowTypeInfoTest(
    s"""Seq(1, 2).map((${CARET}_: Int).toString)""",
    "Int"
  )

  def testUnderscoreParameterWithType_WithoutSelection_CaretAfterUnderscore(): Unit = doShowTypeInfoTest(
    s"""Seq(1, 2).map((_$CARET: Int).toString)""",
    "Int"
  )

  def testLambdaParameter_WithSelection(): Unit = doShowTypeInfoTest(
    s"""Seq(1, 2).map($CARET${START}x$END => x.toString)""",
    "Int"
  )

  def testLambdaParameter_WithoutSelection(): Unit = doShowTypeInfoTest(
    s"""Seq(1, 2).map(${CARET}x => x.toString)""",
    "Int"
  )

  def testLambdaParameter_WithSelection_WithCase_MultipleParams(): Unit = doShowTypeInfoTest(
    s"""Seq((1, 2)).map { case (a, $CARET${START}b$END) => "42" }""",
    "Type: Int"
  )

  def testLambdaParameter_WithoutSelection_WithCase_MultipleParams(): Unit = doShowTypeInfoTest(
    s"""Seq((1, 2)).map { case (a, ${CARET}b) => "42" }""",
    "Int"
  )

  def testAnonymousLambdaParameter_WithSelection(): Unit = doShowTypeInfoTest(
    s"""Seq(1, 2).map($CARET${START}_$END => "42")""",
    "Int"
  )

  def testAnonymousLambdaParameter_WithoutSelection(): Unit = doShowTypeInfoTest(
    s"""Seq(1, 2).map(${CARET}_ => "42")""",
    "Int"
  )

  def testUnderscoreWildcardPattern_WithSelection(): Unit = doShowTypeInfoTest(
    s"""Seq(1, 2).map { case $CARET${START}_$END => "42" }""",
    "Type: Int"
  )

  def testUnderscoreWildcardPattern_WithoutSelection(): Unit = doShowTypeInfoTest(
    s"""Seq(1, 2).map{ case ${CARET}_ => "42" }""",
    "Int"
  )
}

class ShowTypeInfoActionTest_Scala2 extends ShowTypeInfoActionTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_2_13
}

class ShowTypeInfoActionTest_Scala3 extends ShowTypeInfoActionTest_Scala2 {

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_3

  def testTypeAccessibleFromTheContext_TypeAliasToUnionTypeOfStableTypes(): Unit = doShowTypeInfoTest(
    s"""object Scope {
       |  val red = "red"
       |  object green
       |  def blue: "blue" = "blue"
       |
       |  type Color = red.type | green.type | blue.type
       |  val color: ${CARET}Color = ???
       |}""".stripMargin,
    """red.<wbr>type | green.<wbr>type | "blue""""
  )

  def testLambdaParameter_WithSelection_WithoutCase_MultipleParams(): Unit = doShowTypeInfoTest(
    s"""Seq((1, 2)).map((a, $CARET${START}b$END) => "42")""",
    "Int"
  )

  def testLambdaParameter_WithoutSelection_WithoutCase_MultipleParams(): Unit = doShowTypeInfoTest(
    s"""Seq((1, 2)).map((a, ${CARET}b) => "42")""",
    "Int"
  )
}