package org.jetbrains.plugins.scala.testingSupport.scalatest

/**
 * @author Roman.Shein
 * @since 11.02.2015.
 */
trait ScalaTest2GoToSourceTest extends ScalaTestGoToSourceTest {
  def getSuccessfulTestPath: List[String] = List("[root]", goToSourceClassName, "Successful test", "should run fine")
  def getPendingTestPath: List[String] = List("[root]", goToSourceClassName, "pending test", "should be pending")
  def getIgnoredTestPath: List[String] = List("[root]", goToSourceClassName, "pending test", "should be ignored !!! IGNORED !!!")
  def getFailedTestPath: List[String] = List("[root]", goToSourceClassName, "failed test", "should fail")

  def getSuccessfulLocationLine: Int = 3
  def getPendingLocationLine: Int = 6
  def getIgnoredLocationLine: Int = 10
  def getFailedLocationLine: Int = 13
}
