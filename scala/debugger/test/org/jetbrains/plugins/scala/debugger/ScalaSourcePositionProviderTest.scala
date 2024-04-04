package org.jetbrains.plugins.scala.debugger

import com.intellij.debugger.engine.{JavaValue, SourcePositionProvider}
import com.intellij.xdebugger.XDebuggerTestUtil
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.extensions.inReadAction

abstract class ScalaSourcePositionProviderTestBase extends ScalaDebuggerTestCase {

  addSourceFile("ClassParameters.scala",
    s"""object ClassParameters {
       |  def main(args: Array[String]): Unit =
       |    new ClassParameters(1, 1, 1).foo(1)
       |}
       |
       |class ClassParameters(
       |  xxx: Int,
       |  val yyy: Int,
       |  private val zzz: Int
       |) {
       |  def foo(param: Int): Unit = {
       |    val value = 1
       |
       |    param match {
       |      case 1 =>
       |    }
       |
       |    value match {
       |      case 1 =>
       |    }
       |
       |    xxx match {
       |      case 1 =>
       |    }
       |
       |    yyy match {
       |      case 1 =>
       |    }
       |
       |    zzz match { $breakpoint
       |      case 1 =>
       |    }
       |  }
       |}
       |""".stripMargin)

  def testClassParameters(): Unit = {
    createLocalProcess("ClassParameters")

    doWhenXSessionPausedThenResume { () =>
      val project = getProject
      val context = getDebugProcess.getDebuggerContext
      val session = getDebuggerSession.getXDebugSession
      val stackFrameVariables = XDebuggerTestUtil.collectChildren(session.getCurrentStackFrame)
      val thisVariable = XDebuggerTestUtil.findVar(stackFrameVariables, "this")
      XDebuggerTestUtil.computePresentation(thisVariable)
      val children = XDebuggerTestUtil.collectChildren(thisVariable)

      val xxx = XDebuggerTestUtil.findVar(children, "xxx").asInstanceOf[JavaValue]
      val sourcePositionXXX = inReadAction(SourcePositionProvider.getSourcePosition(xxx.getDescriptor, project, context))
      assertEquals(6, sourcePositionXXX.getLine)
      assertEquals(133, sourcePositionXXX.getOffset)

      val yyy = XDebuggerTestUtil.findVar(children, "yyy").asInstanceOf[JavaValue]
      val sourcePositionYYY = inReadAction(SourcePositionProvider.getSourcePosition(yyy.getDescriptor, project, context))
      assertEquals(7, sourcePositionYYY.getLine)
      assertEquals(149, sourcePositionYYY.getOffset)

      val zzz = XDebuggerTestUtil.findVar(children, "zzz").asInstanceOf[JavaValue]
      val sourcePositionZZZ = inReadAction(SourcePositionProvider.getSourcePosition(zzz.getDescriptor, project, context))
      assertEquals(8, sourcePositionZZZ.getLine)
      assertEquals(173, sourcePositionZZZ.getOffset)

      val value = XDebuggerTestUtil.findVar(stackFrameVariables, "value").asInstanceOf[JavaValue]
      val sourcePositionValue = inReadAction(SourcePositionProvider.getSourcePosition(value.getDescriptor, project, context))
      assertEquals(11, sourcePositionValue.getLine)
      assertEquals(226, sourcePositionValue.getOffset)

      val param = XDebuggerTestUtil.findVar(stackFrameVariables, "param").asInstanceOf[JavaValue]
      val sourcePositionParam = inReadAction(SourcePositionProvider.getSourcePosition(param.getDescriptor, project, context))
      assertEquals(10, sourcePositionParam.getLine)
      assertEquals(196, sourcePositionParam.getOffset)

      // TODO: We should also test the output of
      //       `SourcePositionProvider.getSourcePosition(descriptor, project, context, nearest = true)`, but there's
      //       currently a ClassCastException in the platform when running in test mode that prevents us from
      //       asserting the produced source position. I have notified the debugger team and will update these tests
      //       as soon as I get some advice.
    }
  }
}

class ScalaSourcePositionProviderTest_2_11 extends ScalaSourcePositionProviderTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_11
}

class ScalaSourcePositionProviderTest_2_12 extends ScalaSourcePositionProviderTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_12
}

class ScalaSourcePositionProviderTest_2_13 extends ScalaSourcePositionProviderTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13
}

class ScalaSourcePositionProviderTest_3 extends ScalaSourcePositionProviderTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3
}

class ScalaSourcePositionProviderTest_3_RC extends ScalaSourcePositionProviderTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_RC
}
