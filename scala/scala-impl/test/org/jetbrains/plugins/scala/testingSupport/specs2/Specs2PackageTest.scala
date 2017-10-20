package org.jetbrains.plugins.scala.testingSupport.specs2

/**
  * @author Roman.Shein
  * @since 06.09.2015.
  */
abstract class Specs2PackageTest extends Specs2TestCase {
  protected val packageName = "testPackage"
  protected val secondPackageName = "otherPackage"

  addSourceFile(packageName + "/Test1.scala",
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
    """.stripMargin.trim())

  addSourceFile(packageName + "/Test2.scala",
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
    """.stripMargin.trim())

  addSourceFile(secondPackageName + "/Test1.scala",
    """
      |package otherPackage
      |
      |import org.specs2.mutable.Specification
      |
      |class Test2 extends Specification {
      |  "Three" should {
      |    "run" in { success }
      |  }
      |}
    """.stripMargin.trim())

  def testPackageTestRun(): Unit = {
    runTestByConfig(createTestFromPackage(packageName), checkPackageConfigAndSettings(_, packageName),
      root => checkResultTreeHasExactNamedPath(root, "[root]", "Test1", "One should", "run") &&
        checkResultTreeHasExactNamedPath(root, "[root]", "Test1", "Two should", "run") &&
        checkResultTreeHasExactNamedPath(root, "[root]", "Test2", "One should", "run") &&
        checkResultTreeHasExactNamedPath(root, "[root]", "Test2", "Two should", "run") &&
        checkResultTreeDoesNotHaveNodes(root, "Three should"))
  }

  def testModuleTestRun(): Unit = {
    runTestByConfig(createTestFromModule(testClassName),
      checkPackageConfigAndSettings(_, generatedName = "ScalaTests in 'src'"),
      root => checkResultTreeHasExactNamedPath(root, "[root]", "Test1", "One should", "run") &&
        checkResultTreeHasExactNamedPath(root, "[root]", "Test1", "Two should", "run") &&
        checkResultTreeHasExactNamedPath(root, "[root]", "Test2", "One should", "run") &&
        checkResultTreeHasExactNamedPath(root, "[root]", "Test2", "Two should", "run") &&
        checkResultTreeHasExactNamedPath(root, "[root]", "Test2", "Three should", "run"))
  }
}
