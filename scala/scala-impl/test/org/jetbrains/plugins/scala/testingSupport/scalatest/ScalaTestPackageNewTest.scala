package org.jetbrains.plugins.scala.testingSupport.scalatest

// For >= 2.x scala version
trait ScalaTestPackageNewTest extends ScalaTestPackageTest {

  protected val thirdPackageName = "thirdPackage"

  addSourceFile(thirdPackageName + "/NestedStepsSuite.scala",
    s"""package $thirdPackageName
       |
       |import org.scalatest._
       |
       |class NestedStepsSuite extends Suites(
       |  new StepSuiteNotDiscoverable1,
       |  new StepSuiteNotDiscoverable2,
       |  new StepSuiteDiscoverable
       |)
       |@DoNotDiscover
       |class StepSuiteNotDiscoverable1 extends FunSuite {
       |  test("test1.1") { println("1.1" ) }
       |  test("test1.2") { println("1.2" ) }
       |}
       |@DoNotDiscover
       |class StepSuiteNotDiscoverable2 extends FunSuite {
       |  test("test2.1") { println("2.1" ) }
       |}
       |class StepSuiteDiscoverable extends FunSuite {
       |  test("test3.1") { println("3.1" ) }
       |}
       |""".stripMargin
  )

  def testPackageTestRun_ShouldSkipNonDiscoverableTests(): Unit =
    runTestByLocation(
      packageLoc(thirdPackageName),
      assertPackageConfigAndSettings(_, thirdPackageName),
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
