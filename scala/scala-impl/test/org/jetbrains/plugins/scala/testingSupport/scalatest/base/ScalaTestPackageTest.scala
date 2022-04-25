package org.jetbrains.plugins.scala.testingSupport.scalatest.base

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
      assertPackageConfigAndSettings(_, packageName1),
      root => {
        assertResultTreeHasExactNamedPaths(root)(Seq(
          TestNodePath("[root]", "Test1", "Test1"),
          TestNodePath("[root]", "Test2", "Test2")
        ))
        assertResultTreeDoesNotHaveNodes(root, "SecondTest")
      }
    )

  def testPackageTestRun_WithReservedKeywordInName(): Unit =
    runTestByLocation(
      packageLoc(packageNameEqualToReservedKeyword),
      assertPackageConfigAndSettings(_, packageNameEqualToReservedKeyword),
      root => assertResultTreeHasExactNamedPaths(root)(Seq(
        TestNodePath("[root]", "Test3", "some test name")
      ))
    )

  def testModuleTestRun(): Unit =
    runTestByLocation(
      moduleLoc(getModule.getName),
      assertPackageConfigAndSettings(_, generatedName = "ScalaTests in 'src'"),
      root => assertResultTreeHasExactNamedPaths(root)(Seq(
        TestNodePath("[root]", "Test1", "Test1"),
        TestNodePath("[root]", "Test2", "Test2"),
        TestNodePath("[root]", "Test1", "SecondTest"),
        TestNodePath("[root]", "Test3", "some test name"),
      ))
    )

  def testPackageTestRun_ShouldSkipNonDiscoverableTests(): Unit =
    runTestByLocation(
      packageLoc(packageName3),
      assertPackageConfigAndSettings(_, packageName3),
      root => {
        assertResultTreeHasExactNamedPaths(root)(Seq(
          TestNodePath("[root]", "NestedStepsSuite", "StepSuiteNotDiscoverable1", "test1.1"),
          TestNodePath("[root]", "NestedStepsSuite", "StepSuiteNotDiscoverable1", "test1.2"),
          TestNodePath("[root]", "NestedStepsSuite", "StepSuiteNotDiscoverable2", "test2.1"),
          TestNodePath("[root]", "NestedStepsSuite", "StepSuiteDiscoverable", "test3.1"),
          TestNodePath("[root]", "StepSuiteDiscoverable", "test3.1"),
        ))
        assertResultTreeHasNotGotExactNamedPaths(root)(Seq(
          TestNodePath("[root]", "StepSuiteNotDiscoverable1", "test1.1"),
          TestNodePath("[root]", "StepSuiteNotDiscoverable1", "test1.2"),
          TestNodePath("[root]", "StepSuiteNotDiscoverable2", "test2.1")
        ))
      }
    )
}
