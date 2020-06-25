package org.jetbrains.plugins.scala.testingSupport.scalatest.fileStructureView

import org.jetbrains.plugins.scala.lang.structureView.element.Test._
import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase
import org.jetbrains.plugins.scala.testingSupport.test.structureView.TestNodeProvider

trait FeatureSpecFileStructureViewTest extends ScalaTestTestCase {

  private val className = "FeatureSpecViewTest"

  protected def feature = "feature"
  protected def scenario = "scenario"

  private def runTest(status: Int, names: String*): Unit = {
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
      | $feature("parent") {
      |   $scenario("pending1") (pending)
      |   $scenario("child1") {}
      |   ignore("ignored1") {}
      | }
      |
      | ignore("ignored2") {
      |   $scenario("ignored_inner") {}
      | }
      |}
    """.stripMargin.trim()
  )

  def testFeatureSpecNormal(): Unit = runTest(s"""$scenario("child1")""", Some(s"""$feature("parent")"""))

  def testFeatureSpecPending(): Unit = runTest(PendingStatusId, s"""$scenario("pending1")""")

  def testFeatureSpecIgnored(): Unit = runTest(IgnoredStatusId, s"""ignore("ignored1")""", s"""ignore("ignored2")""")

  def testFeatureSpecIgnoredHierarchy(): Unit = runTest(
    s"""$scenario("ignored_inner")""", Some(s"""ignore("ignored2")""" + TestNodeProvider.IgnoredSuffix)
  )
}
