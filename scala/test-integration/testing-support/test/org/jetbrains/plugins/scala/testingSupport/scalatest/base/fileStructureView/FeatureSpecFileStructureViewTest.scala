package org.jetbrains.plugins.scala.testingSupport.scalatest.base.fileStructureView

import org.jetbrains.plugins.scala.structureView.element.Test._
import org.jetbrains.plugins.scala.testingSupport.scalatest.base.ScalaTestTestCase
import org.jetbrains.plugins.scala.testingSupport.test.structureView.TestNodeProvider

trait FeatureSpecFileStructureViewTest extends ScalaTestTestCase {

  private val className = "FeatureSpecViewTest"

  import featureSpecApi._

  private def runTest(status: Int, names: String*): Unit = {
    runFileStructureViewTest(className, status, names: _*)
  }

  private def runTest(testName: String, parent: Option[String] = None): Unit = {
    runFileStructureViewTest(className, testName, parent)
  }

  addSourceFile(className + ".scala",
    s"""$ImportsForFeatureSpec
       |
       |class $className extends $FeatureSpecBase {
       |  $featureMethodName("parent") {
       |    $scenarioMethodName("pending1") (pending)
       |    $scenarioMethodName("child1") {}
       |    ignore("ignored1") {}
       |  }
       |
       |  ignore("ignored2") {
       |    $scenarioMethodName("ignored_inner") {}
       |  }
       |}
       |""".stripMargin
  )

  def testFeatureSpecNormal(): Unit = runTest(s"""$scenarioMethodName("child1")""", Some(s"""$featureMethodName("parent")"""))

  def testFeatureSpecPending(): Unit = runTest(PendingStatusId, s"""$scenarioMethodName("pending1")""")

  def testFeatureSpecIgnored(): Unit = runTest(IgnoredStatusId, s"""ignore("ignored1")""", s"""ignore("ignored2")""")

  def testFeatureSpecIgnoredHierarchy(): Unit = runTest(
    s"""$scenarioMethodName("ignored_inner")""", Some(s"""ignore("ignored2")""" + TestNodeProvider.IgnoredSuffix)
  )
}
