package org.jetbrains.plugins.scala.lang
package parameterInfo
package typeParameterInfo

import org.jetbrains.plugins.scala.lang.parameterInfo.ScalaTypeParameterInfoHandler
import com.intellij.lang.parameterInfo.ParameterInfoUIContext
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.utils.parameterInfo.MockUpdateParameterInfoContext
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeArgs
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.parameterInfo.typeParameterInfo.TypeParameterInfoNamedTypeArgsTest.TypeParameterInfoPresentation
import org.junit.Assert._

import java.awt.Color

class TypeParameterInfoNamedTypeArgsTest extends ScalaLightCodeInsightFixtureTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_3

  def testNamedTypeArgRemapsHighlightByFormalParameterName(): Unit = {
    val presentation = renderTypeParameterInfoAtCaret(
      """
        |import scala.language.experimental.namedTypeArguments
        |
        |def construct[Elem, Coll](xs: Elem*): Coll = ???
        |
        |val xs = construct[Coll = List[Int], Ele<caret>m = Int](1, 2, 3)
        |""".stripMargin,
      requireTypeArgAtCaret = true
    )

    assertHighlightedName(presentation, "Elem")
  }

  def testMixedNamedAndPositionalHighlightsNamedTypeArg(): Unit = {
    val presentation = renderTypeParameterInfoAtCaret(
      """
        |import scala.language.experimental.namedTypeArguments
        |
        |def construct[Elem, Coll](xs: Elem*): Coll = ???
        |
        |val xs = construct[Col<caret>l = List[Int], Int](1, 2, 3)
        |""".stripMargin,
      requireTypeArgAtCaret = true
    )

    assertHighlightedName(presentation, "Coll")
  }

  def testMixedNamedAndPositionalPositionalTypeArgHasNoHighlight(): Unit = {
    val presentation = renderTypeParameterInfoAtCaret(
      """
        |import scala.language.experimental.namedTypeArguments
        |
        |def construct[Elem, Coll](xs: Elem*): Coll = ???
        |
        |val xs = construct[Coll = List[Int], In<caret>t](1, 2, 3)
        |""".stripMargin,
      requireTypeArgAtCaret = false
    )

    assertEquals("Expected no highlighted type parameter in mixed named/positional mode", -1, presentation.highlightStart)
    assertEquals("Expected no highlighted type parameter in mixed named/positional mode", -1, presentation.highlightEnd)
    assertTrue("Expected type parameter info text to stay available", presentation.text.nonEmpty)
  }

  def testFindCallUpdateContextHandlesNamedTypeArgNameElement(): Unit = {
    val index = findCallCurrentParameterIndexAtCaret(
      """
        |import scala.language.experimental.namedTypeArguments
        |
        |def construct[Elem, Coll](xs: Elem*): Coll = ???
        |
        |val xs = construct[Col<caret>l = List[Int], Elem = Int](1, 2, 3)
        |""".stripMargin
    )

    assertEquals("Expected caret in named type-arg name to map to that argument index", 0, index)
  }

  private def renderTypeParameterInfoAtCaret(code: String, requireTypeArgAtCaret: Boolean): TypeParameterInfoPresentation = {
    configureScala3FromFileText(code)

    val offset = myFixture.getCaretOffset
    val elementAtCaret = getFile.findElementAt(offset)
    val typeArgs = PsiTreeUtil.getParentOfType(elementAtCaret, classOf[ScTypeArgs])
    assertNotNull("Expected ScTypeArgs at caret", typeArgs)

    val currentArgIndex = typeArgs.typeArguments.indexWhere(typeArg =>
      typeArg.typeElement.exists(_.getTextRange.containsOffset(offset)) ||
        typeArg.nameElement.exists(_.getTextRange.containsOffset(offset))
    )
    if (requireTypeArgAtCaret) {
      assertTrue(s"Expected caret to be inside a type argument, got index = $currentArgIndex", currentArgIndex >= 0)
    }

    val typeParametersOwner = PsiTreeUtil.findChildOfType(getFile, classOf[ScFunction])
    assertNotNull("Expected type parameters owner in test code", typeParametersOwner)

    val handler = new ScalaTypeParameterInfoHandler
    var result: Option[TypeParameterInfoPresentation] = None

    handler.updateUI(
      (typeParametersOwner, ScSubstitutor.empty),
      new ParameterInfoUIContext {
        override def getParameterOwner = typeArgs

        override def setupUIComponentPresentation(
          text: String,
          highlightStartOffset: Int,
          highlightEndOffset: Int,
          isDisabled: Boolean,
          strikeout: Boolean,
          isDisabledBeforeHighlight: Boolean,
          background: Color
        ): String = {
          result = Option(TypeParameterInfoPresentation(text, highlightStartOffset, highlightEndOffset, isDisabled))
          text
        }

        override def getDefaultParameterColor: Color = null

        override def isUIComponentEnabled: Boolean = true

        override def getCurrentParameterIndex: Int = currentArgIndex

        override def setUIComponentEnabled(enabled: Boolean): Unit = {
          result = Option(TypeParameterInfoPresentation("", -1, -1, !enabled))
        }

        override def isSingleParameterInfo: Boolean = false

        override def isSingleOverload: Boolean = false

        override def setupRawUIComponentPresentation(htmlText: String): Unit = {}
      }
    )

    result.getOrElse(throw new AssertionError("Expected parameter info presentation to be collected"))
  }

  private def assertHighlightedName(presentation: TypeParameterInfoPresentation, expectedName: String): Unit = {
    assertTrue(
      s"Expected highlight offsets for '$expectedName', got ${presentation.highlightStart}..${presentation.highlightEnd}",
      presentation.highlightStart >= 0 && presentation.highlightEnd > presentation.highlightStart
    )
    val actualName = presentation.text.substring(presentation.highlightStart, presentation.highlightEnd)
    assertEquals(expectedName, actualName)
  }

  private def findCallCurrentParameterIndexAtCaret(code: String): Int = {
    configureScala3FromFileText(code)

    val handler = new ScalaTypeParameterInfoHandler
    var currentParameter: Option[Int] = None
    val context = new MockUpdateParameterInfoContext(getEditor, getFile, Array.empty[AnyRef]) {
      override def removeHint(): Unit = {}

      override def setCurrentParameter(index: Int): Unit = {
        currentParameter = Option(index)
        super.setCurrentParameter(index)
      }
    }

    val owner = handler.findElementForUpdatingParameterInfo(context)
    assertNotNull("Expected type args owner from findCall", owner)

    currentParameter.getOrElse(throw new AssertionError("Expected current parameter to be set"))
  }
}

object TypeParameterInfoNamedTypeArgsTest {
  final case class TypeParameterInfoPresentation(
    text: String,
    highlightStart: Int,
    highlightEnd: Int,
    disabled: Boolean
  )
}
