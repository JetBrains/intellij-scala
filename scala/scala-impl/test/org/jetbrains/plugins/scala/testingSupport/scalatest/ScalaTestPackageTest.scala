package org.jetbrains.plugins.scala.testingSupport.scalatest

trait ScalaTestPackageTest extends ScalaTestTestCase {

  protected val packageName = "myPackage"
  protected val secondPackageName = "secondPackage"
  protected val packageNameWithReservedKeyword = "type"

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
    runTestByLocation(
      packageLoc(packageName),
      assertPackageConfigAndSettings(_, packageName),
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
      packageLoc(packageNameWithReservedKeyword),
      assertPackageConfigAndSettings(_, packageNameWithReservedKeyword),
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
}
