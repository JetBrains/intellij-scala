package org.jetbrains.plugins.scala.testingSupport.utest

/**
  * @author Roman.Shein
  * @since 05.09.2015.
  */
trait UTestPackageTest extends UTestTestCase {
  val packageName = "myPackage"
  val secondPackageName = "otherPackage"

  protected def addPackageTest(): Unit = {
    addFileToProject(packageName + "/Test1.scala",
      """
        |package myPackage
        |
        |import utest.framework.TestSuite
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

    addFileToProject(packageName + "/Test2.scala",
      """
        |package myPackage
        |
        |import utest.framework.TestSuite
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

    addFileToProject(secondPackageName + "/Test1.scala",
      """
        |package otherPackage
        |
        |import utest.framework.TestSuite
        |import utest._
        |
        |object Test2 extends TestSuite {
        |  val tests = TestSuite {
        |    "test" - {}
        |  }
        |}
      """.stripMargin.trim())
  }

  def testPackageTestRun(): Unit = {
    addPackageTest()
    runTestByConfig(createTestFromPackage(packageName), checkPackageConfigAndSettings(_, packageName),
      root => checkResultTreeHasExactNamedPath(root, "[root]", "Test1", "tests", "test1") &&
        checkResultTreeHasExactNamedPath(root, "[root]", "Test1", "tests", "test2") &&
        checkResultTreeHasExactNamedPath(root, "[root]", "Test2", "tests", "test1") &&
        checkResultTreeHasExactNamedPath(root, "[root]", "Test2", "tests", "test2") &&
        checkResultTreeDoesNotHaveNodes(root, "[root]", "test"))
  }

  def testModuleTestRun(): Unit = {
    addPackageTest()
    runTestByConfig(createTestFromModule(testClassName),
      checkPackageConfigAndSettings(_, generatedName = "ScalaTests in 'src'"),
      root => checkResultTreeHasExactNamedPath(root, "[root]", "Test1", "tests", "test1") &&
        checkResultTreeHasExactNamedPath(root, "[root]", "Test1", "tests", "test2") &&
        checkResultTreeHasExactNamedPath(root, "[root]", "Test2", "tests", "test1") &&
        checkResultTreeHasExactNamedPath(root, "[root]", "Test2", "tests", "test2") &&
        checkResultTreeHasExactNamedPath(root, "[root]", "Test2", "tests", "test"))
  }
}
