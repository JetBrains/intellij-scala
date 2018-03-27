package org.jetbrains.plugins.scala.testingSupport.scalatest.fileStructureView

import org.jetbrains.plugins.scala.lang.structureView.element.Test._
import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase
import org.jetbrains.plugins.scala.testingSupport.test.structureView.TestNodeProvider

/**
  * @author Roman.Shein
  * @since 19.04.2015.
  */
trait FeatureSpecFileStructureViewTest extends ScalaTestTestCase {

  private val className = "FeatureSpecViewTest"

  private def runTest(status: Int, names: String*) {
    runFileStructureViewTest(className, status, names: _*)
  }

  private def runTest(testName: String, parent: Option[String] = None): Unit = {
    runFileStructureViewTest(className, testName, parent)
  }

  addSourceFile(className + ".scala",
    s"""
      |import org.scalatest._
      |
      |class $className extends FeatureSpec {
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

  def testFeatureSpecNormal(): Unit = runTest("scenario(\"child1\")", Some("feature(\"parent\")"))

  def testFeatureSpecPending(): Unit = runTest(PendingStatusId, "scenario(\"pending1\")")

  def testFeatureSpecIgnored(): Unit = runTest(IgnoredStatusId, "ignore(\"ignored1\")", "ignore(\"ignored2\")")

  def testFeatureSpecIgnoredHierarchy(): Unit = runTest("scenario(\"ignored_inner\")", Some("ignore(\"ignored2\")" +
    TestNodeProvider.ignoredSuffix))
}
