package org.jetbrains.plugins.scala.testingSupport.scalatest.base

import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude

trait ScalaTestPackageTest extends ScalaTestTestCase {

  protected val packageName1 = "myPackage1"
  protected val packageName2 = "myPackage2"
  protected val packageName3 = "myPackage3"
  protected val packageNameEqualToReservedKeyword = "type"

  addSourceFile(packageName1 + "/Test1.scala",
    s"""package $packageName1
       |
       |$ImportsForFunSuite
       |
       |class Test1 extends $FunSuiteBase {
       |
       |  test("Test1") {
       |  }
       |}
       |""".stripMargin
  )

  addSourceFile(packageName1 + "/Test2.scala",
    s"""package $packageName1
       |
       |$ImportsForFunSuite
       |
       |class Test2 extends $FunSuiteBase {
       |
       |  test("Test2") {
       |  }
       |}
       |""".stripMargin
  )

  addSourceFile(packageName2 + "/Test1.scala",
    s"""package $packageName2
       |
       |$ImportsForFunSuite
       |
       |class Test1 extends $FunSuiteBase {
       |
       |  test("SecondTest") {}
       |}
       |""".stripMargin
  )

  addSourceFile(packageName3 + "/NestedStepsSuite.scala",
    s"""package $packageName3
       |
       |$ImportsForFunSuite
       |
       |class NestedStepsSuite extends Suites(
       |  new StepSuiteNotDiscoverable1,
       |  new StepSuiteNotDiscoverable2,
       |  new StepSuiteDiscoverable
       |)
       |@DoNotDiscover
       |class StepSuiteNotDiscoverable1 extends $FunSuiteBase {
       |  test("test1.1") { println("1.1" ) }
       |  test("test1.2") { println("1.2" ) }
       |}
       |@DoNotDiscover
       |class StepSuiteNotDiscoverable2 extends $FunSuiteBase {
       |  test("test2.1") { println("2.1" ) }
       |}
       |class StepSuiteDiscoverable extends $FunSuiteBase {
       |  test("test3.1") { println("3.1" ) }
       |}
       |""".stripMargin
  )

  addSourceFile(packageNameEqualToReservedKeyword + "/Test3.scala",
    s"""package `$packageNameEqualToReservedKeyword`
       |
       |$ImportsForFunSuite
       |
       |class Test3 extends $FunSuiteBase {
       |
       |  test("some test name") {}
       |}
       |""".stripMargin
  )

  def testPackageTestRun(): Unit =
    runTestByLocation(
      packageLoc(packageName1),
      config => {
        assertPackageConfigAndSettings(config, packageName1, "ScalaTests in 'myPackage1'")
      },
      root => {
        assertResultTreePathsEqualsUnordered(root)(Seq(
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "Test1", "Test1"),
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "Test2", "Test2")
        ))
      }
    )

  def testPackageTestRun_WithReservedKeywordInName(): Unit =
    runTestByLocation(
      packageLoc(packageNameEqualToReservedKeyword),
      config => {
        assertPackageConfigAndSettings(config, packageNameEqualToReservedKeyword, "ScalaTests in 'type'")
      },
      root => assertResultTreePathsEqualsUnordered(root)(Seq(
        TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "Test3", "some test name")
      ))
    )

  def testModuleTestRun(): Unit =
    runTestByLocation(
      moduleLoc(getModule.getName),
      config => {
        //TODO: the name shouldn't be `scala-2.13.10`, it should be the module name!
        assertPackageConfigAndSettings(config, "", s"ScalaTests in 'scala-${version.minor}'")
      },
      root => assertResultTreePathsEqualsUnordered(root)(Seq(
        TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "Test1", "Test1"),
        TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "Test2", "Test2"),
        TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "Test1", "SecondTest"),
        TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "Test3", "some test name"),
        TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "StepSuiteDiscoverable", "test3.1"),
        TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "NestedStepsSuite", "StepSuiteDiscoverable", "test3.1"),
        TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "NestedStepsSuite", "StepSuiteNotDiscoverable1", "test1.1"),
        TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "NestedStepsSuite", "StepSuiteNotDiscoverable1", "test1.2"),
        TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "NestedStepsSuite", "StepSuiteNotDiscoverable2", "test2.1"),
      ))
    )

  def testPackageTestRun_ShouldSkipNonDiscoverableTests(): Unit =
    runTestByLocation(
      packageLoc(packageName3),
      config => {
        assertPackageConfigAndSettings(config, packageName3, "ScalaTests in 'myPackage3'")
      },
      root => {
        assertResultTreePathsEqualsUnordered(root)(Seq(
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "NestedStepsSuite", "StepSuiteNotDiscoverable1", "test1.1"),
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "NestedStepsSuite", "StepSuiteNotDiscoverable1", "test1.2"),
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "NestedStepsSuite", "StepSuiteNotDiscoverable2", "test2.1"),
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "NestedStepsSuite", "StepSuiteDiscoverable", "test3.1"),
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "StepSuiteDiscoverable", "test3.1"),
        ))
      }
    )
}
