package org.jetbrains.plugins.scala.debugger.breakpoints

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.impl.{DebuggerUtilsImpl, JvmSteppingCommandProvider}
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.xdebugger.XDebuggerUtil
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.debugger.ScalaDebuggerTestCase
import org.jetbrains.plugins.scala.extensions.inReadAction

abstract class RunToCursorTestBase extends ScalaDebuggerTestCase {
  protected def runToCursorTest(className: String, offset: Int, originalLine: Int, expectedLine: Int): Unit = {
    createLocalProcess(className)
    onBreakpoint { suspendContext =>
      val loc = suspendContext.getLocation
      assertEquals(originalLine, loc.lineNumber())

      inReadAction {
        val psiClass = JavaPsiFacade.getInstance(myProject).findClass(className, GlobalSearchScope.projectScope(myProject))
        val pos = XDebuggerUtil.getInstance().createPositionByOffset(psiClass.getContainingFile.getVirtualFile, offset)
        var runToCursorCommand = DebuggerUtilsImpl
          .computeSafeIfAny[JvmSteppingCommandProvider, DebugProcessImpl#ResumeCommand](JvmSteppingCommandProvider.EP_NAME, _.getRunToCursorCommand(suspendContext, pos, false))
        if (runToCursorCommand == null) runToCursorCommand = myDebugProcess.createRunToCursorCommand(suspendContext, pos, false)
        getDebugProcess.getManagerThread.schedule(runToCursorCommand)
      }
    }

    onBreakpoint { suspendContext =>
      val loc = suspendContext.getLocation
      val actual = loc.lineNumber()
      assertEquals(expectedLine, actual)
      resume(suspendContext)
    }
  }
}

class RunToCursorTest_3 extends RunToCursorTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  addSourceFile("runToCursorNoPackage.scala",
    s"""@main
       |def toplevelNoPackage(): Unit =
       |  println("Hello cat!") $breakpoint
       |  println("Hello dog!")
       |  println("Hello fox!")
       |""".stripMargin)

  def testRunToCursorNoPackage(): Unit = {
    runToCursorTest("toplevelNoPackage", 111, 3, 5)
  }

  addSourceFile("mypackage/runToCursorInPackage.scala",
    s"""package mypackage
       |
       |@main
       |def toplevelInPackage(): Unit =
       |  println("Hello cat!") $breakpoint
       |  println("Hello dog!")
       |  println("Hello fox!")
       |""".stripMargin)

  def testRunToCursorInPackage(): Unit = {
    runToCursorTest("mypackage.toplevelInPackage", 120, 5, 7)
  }

  addSourceFile("mainInObject.scala",
    s"""object Main:
       |  @main
       |  def mainInObject(): Unit =
       |    println("Hello cat!") $breakpoint
       |    println("Hello dog!")
       |    println("Hello fox!")
       |""".stripMargin)

  def testMainInObject(): Unit = {
    runToCursorTest("mainInObject", 135, 4, 6)
  }

  addSourceFile("mainInObjectInPackage.scala",
    s"""package mypackage
       |
       |object Main:
       |  @main
       |  def mainInObjectInPackage(): Unit =
       |    println("Hello cat!") $breakpoint
       |    println("Hello dog!")
       |    println("Hello fox!")
       |""".stripMargin)

  def testMainInObjectInPackage(): Unit = {
    runToCursorTest("mypackage.mainInObjectInPackage", 140, 6, 7)
  }
}

class RunToCursorTest_3_RC extends RunToCursorTest_3 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_RC
}

class RunToCursorTest_3_Next_RC extends RunToCursorTest_3 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_Next_RC
}
