package org.jetbrains.plugins.scala.testingSupport.scalatest

trait ScalaTest2GoToSourceTest extends ScalaTestGoToSourceTest {
  override def getSuccessfulTestPath: List[String] = List("[root]", goToSourceClassName, "Successful test", "should run fine")
  override def getPendingTestPath: List[String] = List("[root]", goToSourceClassName, "pending test", "should be pending")
  override def getIgnoredTestPath: List[String] = List("[root]", goToSourceClassName, "pending test", "should be ignored !!! IGNORED !!!")
  override def getFailedTestPath: List[String] = List("[root]", goToSourceClassName, "failed test", "should fail")

  override def getSuccessfulLocationLine: Int = 3
  override def getPendingLocationLine: Int = 6
  override def getIgnoredLocationLine: Int = 10
  override def getFailedLocationLine: Int = 13
}
