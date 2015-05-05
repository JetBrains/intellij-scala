package org.jetbrains.plugins.scala.testingSupport.scalatest.fileStructureView

import org.jetbrains.plugins.scala.lang.structureView.elements.impl.TestStructureViewElement._
import org.jetbrains.plugins.scala.testingSupport.IntegrationTest
import org.jetbrains.plugins.scala.testingSupport.test.structureView.TestNodeProvider

/**
 * @author Roman.Shein
 * @since 19.04.2015.
 */
trait FeatureSpecFileStructureViewTest extends IntegrationTest {

  private val className = "FeatureSpecViewTest"

  private def runTest(status: Int, names: String*) {
    addFeatureSpec()
    runFileStructureViewTest(className, status, names:_*)
  }

  private def runTest(testName: String, parent: Option[String] = None): Unit = {
    addFeatureSpec()
    runFileStructureViewTest(className, testName, parent)
  }

  def addFeatureSpec() {
    addFileToProject(className + ".scala",
      """
        |import org.scalatest._
        |
        |class FeatureSpecViewTest extends FeatureSpec {
        | feature("parent") {
        |   scenario("pending1") (pending)
        |   scenario("child1") {}
        |   ignore("ignored1") {}
        | }
        |
        | ignore("ignored2") {
        |   scenario("ignored_inner") {}
        | }
        |}
      """.stripMargin.trim()
    )
  }

  def testFeatureSpecNormal(): Unit = runTest("scenario(\"child1\")", Some("feature(\"parent\")"))

  def testFeatureSpecPending(): Unit = runTest(pendingStatusId, "scenario(\"pending1\")")

  def testFeatureSpecIgnored(): Unit = runTest(ignoredStatusId, "ignore(\"ignored1\")", "ignore(\"ignored2\")")

  def testFeatureSpecIgnoredHierarchy(): Unit = runTest("scenario(\"ignored_inner\")", Some("ignore(\"ignored2\")" +
    TestNodeProvider.ignoredSuffix))
}
