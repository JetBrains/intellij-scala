package org.jetbrains.plugins.scala.testingSupport.scalatest

/**
  * @author Roman.Shein
  * @since 04.09.2015.
  */
trait ScalaTestPackageTest extends ScalaTestTestCase {
  protected val packageName = "myPackage"

  protected def addFiles(): Unit = {
    addFileToProject(packageName + "/Test1.scala",
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

    addFileToProject(packageName + "/Test2.scala",
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
      """.stripMargin)
  }

  def testPackageTestRun(): Unit = {
    addFiles()
    runTestByConfig(createTestFromPackage(packageName), checkPackageConfigAndSettings(_, packageName),
      root => checkResultTreeHasExactNamedPath(root, "[root]", "Test1", "Test1") &&
        checkResultTreeHasExactNamedPath(root, "[root]", "Test2", "Test2"))
  }
}
