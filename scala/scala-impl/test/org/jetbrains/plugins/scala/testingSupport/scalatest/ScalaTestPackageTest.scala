package org.jetbrains.plugins.scala.testingSupport.scalatest

trait ScalaTestPackageTest extends ScalaTestTestCase {

  private val packageName = "myPackage"
  private val secondPackageName = "secondPackage"
  private val packageNameWithReservedKeyword = "type"

  addSourceFile(packageName + "/Test1.scala",
    """
      |package myPackage
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
    """
      |package myPackage
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
    """
      |package secondPackage
      |
      |import org.scalatest._
      |
      |class Test1 extends FunSuite {
      |
      |  test("SecondTest") {}
      |}
    """.stripMargin.trim())

  addSourceFile(packageNameWithReservedKeyword + "/Test3.scala",
    """
      |package `type`
      |
      |import org.scalatest._
      |
      |class Test3 extends FunSuite {
      |
      |  test("some test name") {}
      |}
    """.stripMargin.trim())

  def testPackageTestRun(): Unit = {
    runTestByConfig(createTestFromPackage(packageName), checkPackageConfigAndSettings(_, packageName),
      root => checkResultTreeHasExactNamedPath(root, "[root]", "Test1", "Test1") &&
        checkResultTreeHasExactNamedPath(root, "[root]", "Test2", "Test2") &&
        checkResultTreeDoesNotHaveNodes(root, "SecondTest"))
  }

  def testPackageTestRun_WithReservedKeywordInName(): Unit = {
    runTestByConfig(
      createTestFromPackage(packageNameWithReservedKeyword),
      checkPackageConfigAndSettings(_, packageNameWithReservedKeyword),
      root => checkResultTreeHasExactNamedPath(root, "[root]", "Test3", "some test name")
    )
  }

  def testModuleTestRun(): Unit = {
    runTestByConfig(createTestFromModule(testClassName),
      checkPackageConfigAndSettings(_, generatedName = "ScalaTests in 'src'"),
      root => checkResultTreeHasExactNamedPath(root, "[root]", "Test1", "Test1") &&
        checkResultTreeHasExactNamedPath(root, "[root]", "Test2", "Test2") &&
        checkResultTreeHasExactNamedPath(root, "[root]", "Test1", "SecondTest") &&
        checkResultTreeHasExactNamedPath(root, "[root]", "Test3", "some test name")
    )
  }
}
