package org.jetbrains.plugins.scala.testingSupport.utest

trait UTestNewSyntaxPackageTest extends UTestTestCase {

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
      assertPackageConfigAndSettings(_, packageName),
      root => {
        assertResultTreeHasExactNamedPaths(root)(Seq(
          TestNodePath("[root]", "Test1", "tests", "test1"),
          TestNodePath("[root]", "Test1", "tests", "test2"),
          TestNodePath("[root]", "Test2", "tests", "test1"),
          TestNodePath("[root]", "Test2", "tests", "test2"),
        ))
        assertResultTreeDoesNotHaveNodes(root, "test")
      }
    )

  def testModuleTestRun(): Unit =
    runTestByLocation(
      moduleLoc(getModule.getName),
      assertPackageConfigAndSettings(_, generatedName = "ScalaTests in 'src'"),
      root => assertResultTreeHasExactNamedPaths(root)(Seq(
        TestNodePath("[root]", "Test1", "tests", "test1"),
        TestNodePath("[root]", "Test1", "tests", "test2"),
        TestNodePath("[root]", "Test2", "tests", "test1"),
        TestNodePath("[root]", "Test2", "tests", "test2"),
        TestNodePath("[root]", "Test2", "tests", "test"),
      ))
    )
}
