package org.jetbrains.plugins.scala.testingSupport.munit

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
      AssertPackageConfigAndSettings(packageName0),
      AssertResultTreePathsEqualsUnordered(Seq(
        TestNodePath.p("[root] / MyTest01 / MyTest01.test error 01"),
        TestNodePath.p("[root] / MyTest01 / MyTest01.test success 01"),
        TestNodePath.p("[root] / MyTest02 / MyTest02.test error 02"),
        TestNodePath.p("[root] / MyTest02 / MyTest02.test success 02"),
        TestNodePath.p("[root] / MyTest11 / MyTest11.test error 11"),
        TestNodePath.p("[root] / MyTest11 / MyTest11.test success 11"),
        TestNodePath.p("[root] / MyTest12 / MyTest12.test error 12"),
        TestNodePath.p("[root] / MyTest12 / MyTest12.test success 12"),
        TestNodePath.p("[root] / MyTest21 / MyTest21.test error 21"),
        TestNodePath.p("[root] / MyTest21 / MyTest21.test success 21"),
        TestNodePath.p("[root] / MyTest22 / MyTest22.test error 22"),
        TestNodePath.p("[root] / MyTest22 / MyTest22.test success 22"),
      ))
    )(optionsWithErrorCode)

  def testPackage1(): Unit =
    runTestByLocation2(
      packageLoc(packageName1),
      AssertPackageConfigAndSettings(packageName1),
      AssertResultTreePathsEqualsUnordered(Seq(
        TestNodePath.p("[root] / MyTest11 / MyTest11.test error 11"),
        TestNodePath.p("[root] / MyTest11 / MyTest11.test success 11"),
        TestNodePath.p("[root] / MyTest12 / MyTest12.test error 12"),
        TestNodePath.p("[root] / MyTest12 / MyTest12.test success 12")
      ))
    )(optionsWithErrorCode)

  def testPackage2(): Unit =
    runTestByLocation2(
      packageLoc(packageName2),
      AssertPackageConfigAndSettings(packageName2),
      AssertResultTreePathsEqualsUnordered(Seq(
        TestNodePath.p("[root] / MyTest21 / MyTest21.test error 21"),
        TestNodePath.p("[root] / MyTest21 / MyTest21.test success 21"),
        TestNodePath.p("[root] / MyTest22 / MyTest22.test error 22"),
        TestNodePath.p("[root] / MyTest22 / MyTest22.test success 22"),
      ))
    )(optionsWithErrorCode)

  def testPackage_EnsureAssertionFails(): Unit = ExceptionAssertions.assertException[java.lang.AssertionError] {
    runTestByLocation2(
      packageLoc(packageName2),
      AssertPackageConfigAndSettings(packageName2),
      AssertResultTreePathsEqualsUnordered(Seq(
        TestNodePath.p("[root] / MyTest21 / MyTest21.test error 21")
      ))
    )(optionsWithErrorCode)
  }
}
