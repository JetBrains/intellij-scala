package org.jetbrains.plugins.scala.testingSupport.utest

/**
  * @author Roman.Shein
  * @since 05.09.2015.
  */
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
    runTestByConfig(createTestFromPackage(packageName), checkPackageConfigAndSettings(_, packageName),
      root => checkResultTreeHasExactNamedPath(root, "[root]", "Test1", "tests", "test1") &&
        checkResultTreeHasExactNamedPath(root, "[root]", "Test1", "tests", "test2") &&
        checkResultTreeHasExactNamedPath(root, "[root]", "Test2", "tests", "test1") &&
        checkResultTreeHasExactNamedPath(root, "[root]", "Test2", "tests", "test2") &&
        checkResultTreeDoesNotHaveNodes(root, "test"),
      debug=true)
  }

  def testModuleTestRun(): Unit = {
    runTestByConfig(createTestFromModule(testClassName),
      checkPackageConfigAndSettings(_, generatedName = "ScalaTests in 'src'"),
      root => checkResultTreeHasExactNamedPath(root, "[root]", "Test1", "tests", "test1") &&
        checkResultTreeHasExactNamedPath(root, "[root]", "Test1", "tests", "test2") &&
        checkResultTreeHasExactNamedPath(root, "[root]", "Test2", "tests", "test1") &&
        checkResultTreeHasExactNamedPath(root, "[root]", "Test2", "tests", "test2") &&
        checkResultTreeHasExactNamedPath(root, "[root]", "Test2", "tests", "test"))
  }
}
