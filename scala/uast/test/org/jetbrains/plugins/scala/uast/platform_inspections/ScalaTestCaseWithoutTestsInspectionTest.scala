package org.jetbrains.plugins.scala.uast.platform_inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.test.TestCaseWithoutTestsInspection
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase

final class ScalaTestCaseWithoutTestsInspectionTest extends ScalaInspectionTestBase {
  override protected val classOfInspection: Class[_ <: LocalInspectionTool] = classOf[TestCaseWithoutTestsInspection]

  override protected val description: String = null

  override protected def descriptionMatches(s: String): Boolean = s match {
    case s"Test class '$_' has no tests" => true
    case _ => false
  }

  override protected def additionalLibraries: Seq[LibraryLoader] = Seq(
    IvyManagedLoader("junit" % "junit" % "4.13.2")
  )

  def testCaseWithoutTests(): Unit = checkTextHasError(s"class ${START}TestCaseWithNoTests$END extends junit.framework.TestCase")

  def testCaseWithOneTest(): Unit = checkTextHasNoErrors(
    s"""
       |class TestWithOneMethod extends junit.framework.TestCase {
       |  def testSomething(): Unit = assert(1 == 1)
       |}
       |""".stripMargin)

  def testCaseWithoutTestsButClassIsIgnored(): Unit = checkTextHasNoErrors(
    s"""
       |@org.junit.Ignore
       |class IgnoredTestCase extends junit.framework.TestCase
       |""".stripMargin)

  // See SCL-22925, without the fix there would be a highlighting around `SomeTestClass`
  def testCaseWithTestInParentClass(): Unit = checkTextHasNoErrors(
    s"""
       |class SomeParentClass extends junit.framework.TestCase {
       |  def testSomething(): Unit = assert(1 == 1)
       |}
       |
       |class SomeTestClass extends SomeParentClass
       |""".stripMargin
  )

  def testCaseWithoutTestsEvenInParentClass(): Unit = checkTextHasError(
    s"""
       |class ${START}SomeParentClass$END extends junit.framework.TestCase {
       |  override def setUp(): Unit = super.setUp()
       |
       |  override def tearDown(): Unit = super.tearDown()
       |}
       |
       |class ${START}SomeTestClass$END extends SomeParentClass
       |""".stripMargin
  )
}
