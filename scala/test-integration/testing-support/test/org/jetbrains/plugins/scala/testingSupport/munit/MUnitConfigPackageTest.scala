package org.jetbrains.plugins.scala.testingSupport.munit

import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude
import org.jetbrains.plugins.scala.util.assertions.ExceptionAssertions

class MUnitConfigPackageTest extends MUnitTestCase {

  private val packageName0 = "org"
  private val packageName1 = "org.example1"
  private val packageName2 = "org.example2"

  private def classWithFileName(packageName: String, className: String): (String, String) = {
    val pathPrefix = packageName.replace('.', '/')
    (className, s"$pathPrefix/$className.scala")
  }

  protected val (className01, fileName01) = classWithFileName(packageName0, "MyTest01")
  protected val (className02, fileName02) = classWithFileName(packageName0, "MyTest02")

  protected val (className11, fileName11) = classWithFileName(packageName1, "MyTest11")
  protected val (className12, fileName12) = classWithFileName(packageName1, "MyTest12")

  protected val (className21, fileName21) = classWithFileName(packageName2, "MyTest21")
  protected val (className22, fileName22) = classWithFileName(packageName2, "MyTest22")

  private def testClassContent(packageName: String, className: String, testNameSuffix: String) =
    s"""package $packageName
       |
       |import munit.FunSuite
       |
       |class $className extends FunSuite {
       |  test("test success $testNameSuffix") {
       |  }
       |  test("test error $testNameSuffix") {
       |    assertEquals(1, 2)
       |  }
       |}
       |""".stripMargin

  addSourceFile(fileName01, testClassContent(packageName0, className01, "01"))
  addSourceFile(fileName02, testClassContent(packageName0, className02, "02"))

  addSourceFile(fileName11, testClassContent(packageName1, className11, "11"))
  addSourceFile(fileName12, testClassContent(packageName1, className12, "12"))

  addSourceFile(fileName21, testClassContent(packageName2, className21, "21"))
  addSourceFile(fileName22, testClassContent(packageName2, className22, "22"))

  private val optionsWithErrorCode = defaultTestOptions.withErrorCode(-1)

  def testPackage0(): Unit =
    runTestByLocation2(
      packageLoc(packageName0),
      config => {
        assertPackageConfigAndSettings(config, packageName0, "UTest in 'org'")
      },
      root => assertResultTreePathsEqualsUnordered(root.testTreeRoot.get)(Seq(
        TestNodePathWithStatus(Magnitude.FAILED_INDEX, TestNodePath.parse("[root] / MyTest01 / MyTest01.test error 01")),
        TestNodePathWithStatus(Magnitude.PASSED_INDEX, TestNodePath.parse("[root] / MyTest01 / MyTest01.test success 01")),
        TestNodePathWithStatus(Magnitude.FAILED_INDEX, TestNodePath.parse("[root] / MyTest02 / MyTest02.test error 02")),
        TestNodePathWithStatus(Magnitude.PASSED_INDEX, TestNodePath.parse("[root] / MyTest02 / MyTest02.test success 02")),
        TestNodePathWithStatus(Magnitude.FAILED_INDEX, TestNodePath.parse("[root] / MyTest11 / MyTest11.test error 11")),
        TestNodePathWithStatus(Magnitude.PASSED_INDEX, TestNodePath.parse("[root] / MyTest11 / MyTest11.test success 11")),
        TestNodePathWithStatus(Magnitude.FAILED_INDEX, TestNodePath.parse("[root] / MyTest12 / MyTest12.test error 12")),
        TestNodePathWithStatus(Magnitude.PASSED_INDEX, TestNodePath.parse("[root] / MyTest12 / MyTest12.test success 12")),
        TestNodePathWithStatus(Magnitude.FAILED_INDEX, TestNodePath.parse("[root] / MyTest21 / MyTest21.test error 21")),
        TestNodePathWithStatus(Magnitude.PASSED_INDEX, TestNodePath.parse("[root] / MyTest21 / MyTest21.test success 21")),
        TestNodePathWithStatus(Magnitude.FAILED_INDEX, TestNodePath.parse("[root] / MyTest22 / MyTest22.test error 22")),
        TestNodePathWithStatus(Magnitude.PASSED_INDEX, TestNodePath.parse("[root] / MyTest22 / MyTest22.test success 22")),
      ))
    )(optionsWithErrorCode)

  def testPackage1(): Unit =
    runTestByLocation2(
      packageLoc(packageName1),
      config => {
        assertPackageConfigAndSettings(config, packageName1, "UTest in 'example1'")
      },
      root => assertResultTreePathsEqualsUnordered(root.testTreeRoot.get)(Seq(
        TestNodePathWithStatus(Magnitude.FAILED_INDEX, TestNodePath.parse("[root] / MyTest11 / MyTest11.test error 11")),
        TestNodePathWithStatus(Magnitude.PASSED_INDEX, TestNodePath.parse("[root] / MyTest11 / MyTest11.test success 11")),
        TestNodePathWithStatus(Magnitude.FAILED_INDEX, TestNodePath.parse("[root] / MyTest12 / MyTest12.test error 12")),
        TestNodePathWithStatus(Magnitude.PASSED_INDEX, TestNodePath.parse("[root] / MyTest12 / MyTest12.test success 12"))
      ))
    )(optionsWithErrorCode)

  def testPackage2(): Unit =
    runTestByLocation2(
      packageLoc(packageName2),
      config => {
        assertPackageConfigAndSettings(config, packageName2, "UTest in 'example2'")
      },
      root => assertResultTreePathsEqualsUnordered(root.testTreeRoot.get)(Seq(
        TestNodePathWithStatus(Magnitude.FAILED_INDEX, TestNodePath.parse("[root] / MyTest21 / MyTest21.test error 21")),
        TestNodePathWithStatus(Magnitude.PASSED_INDEX, TestNodePath.parse("[root] / MyTest21 / MyTest21.test success 21")),
        TestNodePathWithStatus(Magnitude.FAILED_INDEX, TestNodePath.parse("[root] / MyTest22 / MyTest22.test error 22")),
        TestNodePathWithStatus(Magnitude.PASSED_INDEX, TestNodePath.parse("[root] / MyTest22 / MyTest22.test success 22")),
      ))
    )(optionsWithErrorCode)

  def testPackage_EnsureAssertionFails(): Unit = ExceptionAssertions.assertException[java.lang.AssertionError] {
    runTestByLocation2(
      packageLoc(packageName2),
      config => {
        assertPackageConfigAndSettings(config, packageName2, "")
      },
      root => assertResultTreePathsEqualsUnordered(root.testTreeRoot.get)(Seq(
        TestNodePathWithStatus(Magnitude.FAILED_INDEX, TestNodePath.parse("[root] / MyTest21 / MyTest21.test error 21"))
      ))
    )(optionsWithErrorCode)
  }
}
