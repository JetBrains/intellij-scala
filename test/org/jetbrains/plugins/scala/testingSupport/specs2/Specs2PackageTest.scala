package org.jetbrains.plugins.scala.testingSupport.specs2

/**
  * @author Roman.Shein
  * @since 06.09.2015.
  */
abstract class Specs2PackageTest extends Specs2TestCase {
  protected val packageName = "testPackage"

  protected def addPackageTest(): Unit = {
    addFileToProject(packageName + "/Test1.scala",
      """
        |package testPackage
        |
        |import org.specs2.mutable.Specification
        |
        |class Test1 extends Specification {
        |  "One" should {
        |    "run" in {
        |      success
        |    }
        |  }
        |
        |  "Two" should {
        |    "run" in {
        |      success
        |    }
        |  }
        |}
      """.stripMargin)

    addFileToProject(packageName + "/Test2.scala",
      """
        |package testPackage
        |
        |import org.specs2.mutable.Specification
        |
        |class Test2 extends Specification {
        |  "One" should {
        |    "run" in {
        |      success
        |    }
        |  }
        |
        |  "Two" should {
        |    "run" in {
        |      success
        |    }
        |  }
        |}
      """.stripMargin)
  }

  def testPackageTestRun(): Unit = {
    addPackageTest()
    runTestByConfig(createTestFromPackage(packageName), checkPackageConfigAndSettings(_, packageName),
      root => checkResultTreeHasExactNamedPath(root, "[root]", "Test1", "One should", "run") &&
        checkResultTreeHasExactNamedPath(root, "[root]", "Test1", "Two should", "run") &&
        checkResultTreeHasExactNamedPath(root, "[root]", "Test2", "One should", "run") &&
        checkResultTreeHasExactNamedPath(root, "[root]", "Test2", "Two should", "run"))
  }
}
