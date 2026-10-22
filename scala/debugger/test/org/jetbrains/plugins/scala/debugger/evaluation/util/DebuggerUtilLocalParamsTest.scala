package org.jetbrains.plugins.scala
package debugger
package evaluation
package util

import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import scala.jdk.CollectionConverters.*

/**
 * Checks which local values the debugger considers captured by a local function or class, i.e. passed to it as
 * extra parameters by the compiler (see SCL-26185).
 */
@RunWith(classOf[JUnit4])
@Category(Array(classOf[DebuggerTests]))
class DebuggerUtilLocalParamsTest extends ScalaLightCodeInsightFixtureTestCase:
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  @Test
  def topLevelValIsNotCapturedByTopLevelClass(): Unit =
    configureFromFileText(
      """package p
        |
        |val topLevelVal = 42
        |
        |class TopLevelClass:
        |  def useIt: Int = topLevelVal + 1
        |""".stripMargin)

    assertEquals(Seq.empty, namesOf(DebuggerUtil.localParamsForConstructor(classNamed("TopLevelClass"))))

  @Test
  def memberMethodCapturesNothing(): Unit =
    configureFromFileText(
      """object O:
        |  val field = 1
        |  def member(p: Int): Int =
        |    def local(q: Int): Int = p + q + field
        |    local(1)
        |""".stripMargin)

    assertEquals(Seq.empty, namesOf(DebuggerUtil.localParamsForFunDef(functionNamed("member"))))

  @Test
  def localFunctionCapturesLocalValues(): Unit =
    configureFromFileText(
      """object O:
        |  val field = 1
        |  def member(p: Int): Int =
        |    val x = 2
        |    def local(q: Int): Int = x + p + q + field
        |    local(1)
        |""".stripMargin)

    assertEquals(Seq("x", "p"), namesOf(DebuggerUtil.localParamsForFunDef(functionNamed("local"))))

  @Test
  def localClassCapturesLocalValues(): Unit =
    configureFromFileText(
      """object O:
        |  val field = 1
        |  def member(p: Int): Int =
        |    val x = 2
        |    class Local:
        |      def f: Int = x + p + field
        |    new Local().f
        |""".stripMargin)

    assertEquals(Seq("x", "p"), namesOf(DebuggerUtil.localParamsForConstructor(classNamed("Local"))))

  private def namesOf(definitions: Seq[ScTypedDefinition]): Seq[String] = definitions.map(_.name)

  private def classNamed(name: String): ScClass =
    PsiTreeUtil.findChildrenOfType(getFile, classOf[ScClass]).asScala.find(_.name == name)
      .getOrElse(throw AssertionError(s"No class $name in the test file"))

  private def functionNamed(name: String): ScFunctionDefinition =
    PsiTreeUtil.findChildrenOfType(getFile, classOf[ScFunctionDefinition]).asScala.find(_.name == name)
      .getOrElse(throw AssertionError(s"No function $name in the test file"))
