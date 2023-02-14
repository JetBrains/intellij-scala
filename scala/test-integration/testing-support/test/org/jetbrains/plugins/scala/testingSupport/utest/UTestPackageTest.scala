package org.jetbrains.plugins.scala.testingSupport.utest

import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude

trait UTestPackageTest extends UTestTestCase {
  val packageName = "myPackage"
  val secondPackageName = "otherPackage"

  addSourceFile(packageName + "/Test1.scala",
    s"""
       |package myPackage
       |
       |$testSuiteSecondPrefix
       |import utest._
       |
       |object Test1 extends TestSuite {
       |  val tests = TestSuite {
       |    "test1" - {}
       |
       |    "test2" - {}
       |  }
       |}
      """.stripMargin.trim())

  addSourceFile(packageName + "/Test2.scala",
    s"""
       |package myPackage
       |
       |$testSuiteSecondPrefix
       |import utest._
       |
       |object Test2 extends TestSuite {
       |  val tests = TestSuite {
       |    "test1" - {}
       |
       |    "test2" - {}
       |  }
       |}
      """.stripMargin.trim())

  addSourceFile(secondPackageName + "/Test1.scala",
    s"""
       |package otherPackage
       |
       |$testSuiteSecondPrefix
       |import utest._
       |
       |object Test2 extends TestSuite {
       |  val tests = TestSuite {
       |    "test" - {}
       |  }
       |}
      """.stripMargin.trim())

  def testPackageTestRun(): Unit = {
    runTestByLocation(
      packageLoc(packageName),
      config => {
        assertPackageConfigAndSettings(config, packageName, "UTests in 'myPackage'")
      },
      root => {
        assertResultTreePathsEqualsUnordered(root)(Seq(
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "Test1", "tests", "test1"),
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "Test1", "tests", "test2"),
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "Test2", "tests", "test1"),
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "Test2", "tests", "test2"),
        ))
      }
    )
  }

  def testModuleTestRun(): Unit =
    runTestByLocation(
      moduleLoc(getModule.getName),
      config => {
        assertPackageConfigAndSettings(config, "", s"UTests in 'scala-${version.minor}'")
      },
      root => assertResultTreePathsEqualsUnordered(root)(Seq(
        TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "Test1", "tests", "test1"),
        TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "Test1", "tests", "test2"),
        TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "Test2", "tests", "test1"),
        TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "Test2", "tests", "test2"),
        TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "Test2", "tests", "test")
      ))
    )
}
