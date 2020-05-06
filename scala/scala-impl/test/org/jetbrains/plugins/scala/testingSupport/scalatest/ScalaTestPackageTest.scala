package org.jetbrains.plugins.scala.testingSupport.scalatest

trait ScalaTestPackageTest extends ScalaTestTestCase {

  private val packageName = "myPackage"
  private val secondPackageName = "secondPackage"
  private val thirdPackageName = "thirdPackage"
  private val packageNameWithReservedKeyword = "type"

  addSourceFile(packageName + "/Test1.scala",
    s"""
       |package $packageName
       |
       |import org.scalatest._
       |
       |class Test1 extends FunSuite {
       |
       |  test("Test1") {
       |  }
       |}
    """.stripMargin.trim())

  addSourceFile(packageName + "/Test2.scala",
    s"""
       |package $packageName
       |
       |import org.scalatest._
       |
       |class Test2 extends FunSuite {
       |
       |  test("Test2") {
       |  }
       |}
    """.stripMargin.trim())

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

  addSourceFile(secondPackageName + "/Test1.scala",
    s"""
       |package $secondPackageName
       |
       |import org.scalatest._
       |
       |class Test1 extends FunSuite {
       |
       |  test("SecondTest") {}
       |}
    """.stripMargin.trim())

  addSourceFile(packageNameWithReservedKeyword + "/Test3.scala",
    s"""
       |package `$packageNameWithReservedKeyword`
       |
       |import org.scalatest._
       |
       |class Test3 extends FunSuite {
       |
       |  test("some test name") {}
       |}
    """.stripMargin.trim())

  def testPackageTestRun(): Unit =
    runTestByConfig2(createTestFromPackage(packageName),
      assertPackageConfigAndSettings(_, packageName),
      root => {
        assertResultTreeHasExactNamedPaths(root)(Seq(
          Seq("[root]", "Test1", "Test1"),
          Seq("[root]", "Test2", "Test2")
        ))
        assertResultTreeDoesNotHaveNodes(root, "SecondTest")
      }
    )

  def testPackageTestRun_ShouldSkipNonDiscoverableTests(): Unit =
    runTestByConfig2(createTestFromPackage(thirdPackageName),
      assertPackageConfigAndSettings(_, thirdPackageName),
      root => {
        assertResultTreeHasExactNamedPaths(root)(Seq(
          Seq("[root]", "NestedStepsSuite", "StepSuiteNotDiscoverable1", "test1.1"),
          Seq("[root]", "NestedStepsSuite", "StepSuiteNotDiscoverable1", "test1.2"),
          Seq("[root]", "NestedStepsSuite", "StepSuiteNotDiscoverable2", "test2.1"),
          Seq("[root]", "NestedStepsSuite", "StepSuiteDiscoverable", "test3.1"),
          Seq("[root]", "StepSuiteDiscoverable", "test3.1"),
        ))
        assertResultTreeHasNotGotExactNamedPaths(root)(Seq(
          Seq("[root]", "StepSuiteNotDiscoverable1", "test1.1"),
          Seq("[root]", "StepSuiteNotDiscoverable1", "test1.2"),
          Seq("[root]", "StepSuiteNotDiscoverable2", "test2.1")
        ))
      }
    )

  def testPackageTestRun_WithReservedKeywordInName(): Unit =
    runTestByConfig2(
      createTestFromPackage(packageNameWithReservedKeyword),
      assertPackageConfigAndSettings(_, packageNameWithReservedKeyword),
      root => assertResultTreeHasExactNamedPaths(root)(Seq(
        Seq("[root]", "Test3", "some test name")
      ))
    )

  def testModuleTestRun(): Unit =
    runTestByConfig2(createTestFromModule(testClassName),
      assertPackageConfigAndSettings(_, generatedName = "ScalaTests in 'src'"),
      root => assertResultTreeHasExactNamedPaths(root)(Seq(
        Seq("[root]", "Test1", "Test1"),
        Seq("[root]", "Test2", "Test2"),
        Seq("[root]", "Test1", "SecondTest"),
        Seq("[root]", "Test3", "some test name"),
      ))
    )
}
