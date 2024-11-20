package org.jetbrains.plugins.scala.debugger.exactBreakpoints

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.xdebugger.XDebuggerUtil
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.debugger.ScalaDebuggerTestCase
import org.jetbrains.plugins.scala.debugger.breakpoints.ScalaLineBreakpointType
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager

import scala.jdk.CollectionConverters._

abstract class PriorityTestBase extends ScalaDebuggerTestCase {

  protected def assertVariantPriorities(className: String = getTestName(false))(lineNumber: Int, priorities: Int*): Unit = inReadAction {
    val manager = ScalaPsiManager.instance(getProject)
    val psiClass = manager.getCachedClass(GlobalSearchScope.allScope(getProject), className)
    val psiFile = psiClass.map(_.getContainingFile).getOrElse(throw new AssertionError(s"Could not find class $className"))
    val virtualFile = psiFile.getVirtualFile
    val scalaBreakpointType = XDebuggerUtil.getInstance().findBreakpointType(classOf[ScalaLineBreakpointType])
    val xSourcePosition = XDebuggerUtil.getInstance().createPosition(virtualFile, lineNumber)
    val actual = scalaBreakpointType.computeVariants(getProject, xSourcePosition).asScala.map(_.getPriority(getProject)).toSeq
    assertEquals(priorities, actual)
  }

  addSourceFile("LineHasPriority.scala",
    """object LineHasPriority {
      |  def main(args: Array[String]): Unit = {
      |    println(Seq(1, 2, 3).map(x => x * 2))
      |  }
      |}
      |""".stripMargin)

  def testLineHasPriority(): Unit = {
    assertVariantPriorities()(2, 101, 101, 101)
  }

  addSourceFile("LineIsLowPriority.scala",
    """object LineIsLowPriority {
      |  def main(args: Array[String]): Unit = {
      |    Seq(1, 2, 3)
      |      .map { x => x + 1 }
      |      .map(_ + 1)
      |      .foreach(println)
      |  }
      |}
      |""".stripMargin)

  def testLineIsLowPriority(): Unit = {
    assertVariantPriorities()(3, 101, 51, 101)
    assertVariantPriorities()(4, 101, 51, 101)
    assertVariantPriorities()(5, 101, 51, 101)
  }

  addSourceFile("ExtraPriorityForLambda.scala",
    """object ExtraPriorityForLambda {
      |  def main(args: Array[String]): Unit = {
      |    Seq(1, 2, 3).map(
      |      x => x + 1
      |    ).map {
      |      ({({_ + 2})})
      |    }.map {
      |      x => x + 1
      |    }
      |  }
      |}
      |""".stripMargin)

  def testExtraPriorityForLambda(): Unit = {
    assertVariantPriorities()(3, 101, 101, 151)
    assertVariantPriorities()(5, 101, 101, 151)
    assertVariantPriorities()(7, 101, 101, 151)
  }

  addSourceFile("TwoLambdasOnLine.scala",
    """object TwoLambdasOnLine {
      |  def main(args: Array[String]): Unit = {
      |    Seq(1, 2, 3)
      |      .map(x => x + 1).map(x => x + 2)
      |  }
      |}
      |""".stripMargin)

  def testTwoLambdasOnLine(): Unit = {
    assertVariantPriorities()(3, 101, 51, 101, 101)
  }
}

class PriorityTest_2_11 extends PriorityTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_11
}

class PriorityTest_2_12 extends PriorityTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_12
}

class PriorityTest_2_13 extends PriorityTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13
}

class PriorityTest_3_3 extends PriorityTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_3
}

class PriorityTest_3_4 extends PriorityTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_4
}

class PriorityTest_3_5 extends PriorityTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_5
}

class PriorityTest_3_6 extends PriorityTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_6
}

class PriorityTest_3_RC extends PriorityTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_LTS_RC
}

class PriorityTest_3_Next_RC extends PriorityTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_Next_RC
}
