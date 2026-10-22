package org.jetbrains.plugins.scala
package debugger

import com.intellij.debugger.engine.{ContextUtil, SuspendContextImpl}
import org.jetbrains.plugins.scala.debugger.evaluation.ExpressionEvaluationTestBase
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import java.util
import scala.jdk.CollectionConverters.*

class ScalaFrameExtraVariablesProviderTest_2_13 extends ScalaFrameExtraVariablesProviderTestBase:
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13

class ScalaFrameExtraVariablesProviderTest_3 extends ScalaFrameExtraVariablesProviderTestBase:
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

/**
 * The variables of enclosing scopes that the debugger offers in addition to the locals of the current frame.
 * A partial function literal is compiled to a class of its own, so the value it captures has to be evaluated
 * speculatively before it is offered (see [[ScalaFrameExtraVariablesProvider]]).
 */
@RunWith(classOf[JUnit4])
abstract class ScalaFrameExtraVariablesProviderTestBase extends ExpressionEvaluationTestBase:

  addSourceFile("OuterScopeVariables.scala",
    s"""object OuterScopeVariables {
       |  def main(args: Array[String]): Unit = {
       |    val outer = 1
       |    val unused = 2
       |    Seq(10).foreach { x =>
       |      println(x + outer) $breakpoint
       |    }
       |    Seq(20).foreach { case x =>
       |      println(x + outer) $breakpoint
       |    }
       |  }
       |}""".stripMargin.trim)

  @Test
  def variablesFromOuterScopes(): Unit =
    expressionEvaluationTest("OuterScopeVariables")(
      implicit ctx => assertEquals(Set("outer"), extraVariables(alreadyCollected = Set("x"))),
      implicit ctx => assertEquals(Set("outer"), extraVariables(alreadyCollected = Set("x")))
    )

  private def extraVariables(alreadyCollected: Set[String])(implicit ctx: SuspendContextImpl): Set[String] =
    val evaluationContext = createEvaluationContext(ctx)
    val position = inReadAction(ContextUtil.getSourcePosition(evaluationContext))
    val collected: util.Set[String] = new util.HashSet(alreadyCollected.asJava)
    new ScalaFrameExtraVariablesProvider().collectVariables(position, evaluationContext, collected).asScala.map(_.getText).toSet
