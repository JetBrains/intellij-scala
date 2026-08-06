package org.jetbrains.plugins.scala.testingSupport.munit

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.client.ClientSystemInfo
import com.intellij.testFramework.common.ThreadLeakTracker
import org.jetbrains.plugins.scala.util.assertions.ExceptionAssertions

abstract class MUnitGoToSourceTestBase extends MUnitTestCase {

  private val qqq = "\"\"\""

  private val ClassNameFunSuite = "MUnitGoToSource_Test_FunSuite"
  private val FileNameFunSuite = ClassNameFunSuite + ".scala"

  addSourceFile(FileNameFunSuite,
    s"""import munit.FunSuite
       |
       |class $ClassNameFunSuite extends FunSuite {
       |  test("test single line") {
       |  }
       |
       |  test(${qqq}test 2$qqq) {
       |  }
       |}
       |""".stripMargin
  )

  private val ClassNameScalaCheckSuite = "MUnitGoToSource_Test_ScalaCheckSuite"
  private val FileNameScalaCheckSuite = ClassNameScalaCheckSuite + ".scala"

  addSourceFile(FileNameScalaCheckSuite,
    s"""import munit.ScalaCheckSuite
       |
       |import org.scalacheck.Prop.forAll
       |
       |class $ClassNameScalaCheckSuite extends ScalaCheckSuite {
       |  test("simple test") {
       |  }
       |
       |  property("property test") {
       |    forAll { (n1: Int, n2: Int) => n1 + n2 == n2 + n1 }
       |  }
       |}
       |""".stripMargin
  )

  def testGoTo_FunSuite(): Unit = {
    val runConfig = createTestFromLocation(loc(FileNameFunSuite, 2, 10))
    val runResult = runTestFromConfig(runConfig)
    val testTreeRoot = runResult.requireTestTreeRoot

    assertGoToSourceTest(testTreeRoot, TestNodePath("[root]"), GoToLocation(FileNameFunSuite, 2))
    assertGoToSourceTest(testTreeRoot, TestNodePath("[root]", s"$ClassNameFunSuite.test single line"), GoToLocation(FileNameFunSuite, 3))
    assertGoToSourceTest(testTreeRoot, TestNodePath("[root]", s"$ClassNameFunSuite.test 2"), GoToLocation(FileNameFunSuite, 6))
  }

  def testGoTo_ScalaCheckSuite(): Unit = {
    val runConfig = createTestFromLocation(loc(FileNameScalaCheckSuite, 4, 10))
    val runResult = runTestFromConfig(runConfig)
    val testTreeRoot = runResult.requireTestTreeRoot

    assertGoToSourceTest(testTreeRoot, TestNodePath("[root]"), GoToLocation(FileNameScalaCheckSuite, 4))
    assertGoToSourceTest(testTreeRoot, TestNodePath("[root]", s"$ClassNameScalaCheckSuite.simple test"), GoToLocation(FileNameScalaCheckSuite, 5))
    assertGoToSourceTest(testTreeRoot, TestNodePath("[root]", s"$ClassNameScalaCheckSuite.property test"), GoToLocation(FileNameScalaCheckSuite, 8))
  }

  def testGoTo_EnsureAssertionFails(): Unit = ExceptionAssertions.assertException[java.lang.AssertionError] {
    val runConfig = createTestFromLocation(loc(FileNameFunSuite, 2, 10))
    val runResult = runTestFromConfig(runConfig)
    val testTreeRoot = runResult.requireTestTreeRoot
    assertGoToSourceTest(testTreeRoot, TestNodePath("[root]"), GoToLocation(FileNameFunSuite, 5))
  }
}

class MUnit_0_7_GoToSourceTest extends MUnitGoToSourceTestBase with MUnit_0_7 {
  override protected def setUp(): Unit = {
    super.setUp()

    //noinspection ApiStatus,UnstableApiUsage
    if (ClientSystemInfo.isWindows) {
      ThreadLeakTracker.longRunningThreadCreated(
        ApplicationManager.getApplication,
        "JavaProcessMonitor"
      )
    }
  }
}

class MUnit_1_0_GoToSourceTest extends MUnitGoToSourceTestBase with MUnit_1_0
