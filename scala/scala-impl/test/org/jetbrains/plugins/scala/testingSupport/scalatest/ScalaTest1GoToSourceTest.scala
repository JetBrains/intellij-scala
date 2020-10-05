package org.jetbrains.plugins.scala.testingSupport.scalatest

trait ScalaTest1GoToSourceTest extends ScalaTestGoToSourceTest {

  override def getSuccessfulTestPath: TestNodePath = TestNodePath("[root]", "Successful test should run fine")
  override def getPendingTestPath: TestNodePath = TestNodePath("[root]", "pending test should be pending")
  //for ignored test, we launch the whole suite
  override def getIgnoredTestPath: TestNodePath = TestNodePath("[root]", goToSourceClassName, "pending test should be ignored !!! IGNORED !!!")
  override def getFailedTestPath: TestNodePath = TestNodePath("[root]", "failed test should fail")

  //TODO: for now, scalaTest v1.x only supports 'topOfClass' location
  override def getSuccessfulLocationLine: Int = 2
  override def getPendingLocationLine: Int = 2
  override def getIgnoredLocationLine: Int = 2
  override def getFailedLocationLine: Int = 2
}
