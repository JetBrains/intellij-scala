package org.jetbrains.plugins.scala.testingSupport.utest.utest_0_8

import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude
import org.jetbrains.plugins.scala.testingSupport.utest.UTestTestCase

trait UTest_0_8_PackageTest extends UTestTestCase {

  val packageName = "myPackage"
  val secondPackageName = "otherPackage"

  addSourceFile(packageName + "/Test1.scala",
    s"""
       |package myPackage
       |
       |import utest._
       |
       |object Test1 extends TestSuite {
       |  val tests = Tests {
       |    test("test1") {}
       |
       |    test("test2") {}
       |  }
       |}
       |""".stripMargin.trim())

  addSourceFile(packageName + "/Test2.scala",
    s"""
       |package myPackage
       |
       |import utest._
       |
       |object Test2 extends TestSuite {
       |  val tests = Tests {
       |    test("test1") {}
       |
       |    test("test2") {}
       |  }
       |}
       |""".stripMargin.trim())

  addSourceFile(secondPackageName + "/Test1.scala",
    s"""
       |package otherPackage
       |
       |import utest._
       |
       |object Test2 extends TestSuite {
       |  val tests = Tests {
       |    test("test") {}
       |  }
       |}
       |""".stripMargin.trim())

  def testPackageTestRun(): Unit =
    runTestByLocation(
      packageLoc(packageName),
      config => {
        assertRunConfigTestPackage(config, packageName)
        assertRunConfigName(config, "UTests in 'myPackage'")
      },
      root => {
        assertResultTreePathsEqualsUnordered(root)(Seq(
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "Test1", "tests", "test1"),
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "Test1", "tests", "test2"),
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "Test2", "tests", "test1"),
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "Test2", "tests", "test2"),
        ))
        assertResultTreeStatus(root, Magnitude.COMPLETE_INDEX)
      }
    )

  def testModuleTestRun(): Unit =
    runTestByLocation(
      moduleLoc(getModule.getName),
      config => {
        assertRunConfigTestPackage(config, "")
        assertRunConfigName(config, s"UTests in 'scala-${version.minor}'")
      },
      root => {
        assertResultTreePathsEqualsUnordered(root)(Seq(
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "Test1", "tests", "test1"),
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "Test1", "tests", "test2"),
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "Test2", "tests", "test1"),
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "Test2", "tests", "test2"),
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "Test2", "tests", "test"),
        ))
        assertResultTreeStatus(root, Magnitude.COMPLETE_INDEX)
      }
    )
}
