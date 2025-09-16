package org.jetbrains.plugins.scala.lang.parser.parsing.builder

import com.intellij.lang.PsiBuilderFactory
import com.intellij.psi.impl.DebugUtil.psiToString
import com.intellij.psi.impl.source.DummyHolderFactory
import com.intellij.psi.impl.source.tree.FileElement
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.lang.lexer.ScalaLexer
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.util.assertions.AssertionMatchers.AssertMatchersExt
import org.jetbrains.plugins.scala.{NlsString, ScalaLanguage}
import org.junit.Assert.assertEquals

class ErrorTrackingMarkerTest extends SimpleTestCase {
  private def checkErrorCount(expectedErrorCount: Int, @Nullable expectedPsi: String = null)(f: ScalaPsiBuilder => Unit): Unit = {
    val context = parseScalaFile("")
    val holder: FileElement = DummyHolderFactory.createHolder(context.getManager, context).getTreeElement
    val builder: ScalaPsiBuilderImpl = {
      val delegate = PsiBuilderFactory.getInstance.createBuilder(context.getProject, holder, new ScalaLexer(false, null), ScalaLanguage.INSTANCE, "")
      new ScalaPsiBuilderImpl(delegate, isScala3 = false)
    }

    val doneMarker = builder.mark()
    val dropMarker = builder.mark()
    val (errors, _) = builder.countDoneErrorsIn {
      f(builder)
    }
    dropMarker.drop()
    doneMarker.done(ScalaElementType.BLOCK)

    val node = builder.getTreeBuilt

    errors shouldBe expectedErrorCount

    Option(expectedPsi).foreach { expectedPsiText =>
      val resultTree = psiToString(node.getPsi, true)
      assertEquals(expectedPsiText.trim, resultTree.trim)
    }
  }

  private def someError: String = NlsString.force("someError")
  private def someError2: String = NlsString.force("someError2")

  def testRootError(): Unit = checkErrorCount(1) { builder =>
    builder.error(someError)
  }

  def testErrorInDroppedMarker(): Unit = checkErrorCount(2) { builder =>
    builder.error(someError)

    val marker = builder.mark()
    builder.error(someError)
    marker.drop()
  }

  def testErrorInRolledBackMarker(): Unit = checkErrorCount(1) { builder =>
    builder.error(someError)

    val marker = builder.mark()
    builder.error(someError)
    marker.rollbackTo()
  }

  def testErrorInDoneMarker(): Unit = checkErrorCount(
    expectedErrorCount = 1,
    expectedPsi =
      """
        |BlockOfExpressions
        |  ConstructorInvocation
        |    PsiErrorElement:someError
        |      <empty list>
        |""".stripMargin
  ) { builder =>
    val marker = builder.mark()
    builder.error(someError)
    marker.done(ScalaElementType.CONSTRUCTOR)
  }

  def testErrorInDoneMarkerWithRollback(): Unit = checkErrorCount(1) { builder =>
    builder.error(someError)

    val marker1 = builder.mark()
    val marker2 = builder.mark()
    builder.error(someError)
    marker2.done(ScalaElementType.TYPE)
    marker1.rollbackTo()
  }

  def testErrorInPrecedingMarker(): Unit = checkErrorCount(2) { builder =>
    builder.error(someError) // yes

    val inner = builder.mark()
    builder.error(someError) // no
    val outer = inner.precede()
    builder.error(someError) // no
    inner.rollbackTo()
    builder.error(someError) // yes
    outer.done(ScalaElementType.TYPE)
  }

  def testErrorOnMarkerAndNotDrop(): Unit = checkErrorCount(
    expectedErrorCount = 1,
    expectedPsi =
      """
        |BlockOfExpressions
        |  PsiErrorElement:someError
        |    <empty list>
        |""".stripMargin
  ) { builder =>
    val marker = builder.mark()
    marker.error(someError)  // no
  }

  def testErrorOnMarkerAndThenDrop(): Unit = checkErrorCount(
    expectedErrorCount = 1,
    expectedPsi =
      """
        |BlockOfExpressions
        |  PsiErrorElement:someError
        |    <empty list>
        |""".stripMargin
  ) { builder =>
    val marker = builder.mark()
    marker.error(someError2)  // no
    builder.error(someError) // yes
    marker.drop()
  }

  def testErrorOnMarkerAndThenRollback(): Unit = checkErrorCount(
    expectedErrorCount = 0,
    expectedPsi =
      """
        |BlockOfExpressions
        |  <empty list>
        |""".stripMargin
  ) { builder =>
    val marker = builder.mark()
    builder.error(someError) // yes
    marker.error(someError)  // no
    marker.rollbackTo()
  }
}
