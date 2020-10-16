package org.jetbrains.plugins.scala.testingSupport.scalatest

trait ScalaTest2GoToSourceTest extends ScalaTestGoToSourceTest {

  override def getSuccessfulTestPath: TestNodePath = TestNodePath("[root]", goToSourceClassName, "Successful test", "should run fine")
  override def getPendingTestPath: TestNodePath = TestNodePath("[root]", goToSourceClassName, "pending test", "should be pending")
  override def getIgnoredTestPath: TestNodePath = TestNodePath("[root]", goToSourceClassName, "pending test", "should be ignored !!! IGNORED !!!")
  override def getFailedTestPath: TestNodePath = TestNodePath("[root]", goToSourceClassName, "failed test", "should fail")
  override def getTemplateTestPath: TestNodePath = TestNodePath("[root]", goToSourceClassName, "Successful in template", "should run fine")

  override def getSuccessfulLocationLine: Int = 3
  override def getPendingLocationLine: Int = 6
  override def getIgnoredLocationLine: Int = 10
  override def getFailedLocationLine: Int = 13
  override def getTemplateLocationLine: Int = 3
}
