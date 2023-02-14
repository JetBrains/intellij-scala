package org.jetbrains.plugins.scala.testingSupport.specs2

import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude

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

  def testPackageTestRun(): Unit =
    runTestByLocation(
      packageLoc(packageName),
      config => {
        assertPackageConfigAndSettings(config, packageName, "Specs2 in 'testPackage'")
      },
      root => {
        assertResultTreePathsEqualsUnordered(root)(Seq(
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "Test1", "One should", "run"),
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "Test1", "Two should", "run"),
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "Test2", "One should", "run"),
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "Test2", "Two should", "run"),
        ))
      }
    )

  def testModuleTestRun(): Unit =
    runTestByLocation(
      moduleLoc(getModule.getName),
      config => {
        assertPackageConfigAndSettings(config, "", s"Specs2 in 'scala-${version.minor}'")
      },
      root => {
        assertResultTreePathsEqualsUnordered(root)(Seq(
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "Test1", "One should", "run"),
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "Test1", "Two should", "run"),
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "Test2", "One should", "run"),
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "Test2", "Two should", "run"),
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "Test2", "Three should", "run"),
        ))
      }
    )
}
