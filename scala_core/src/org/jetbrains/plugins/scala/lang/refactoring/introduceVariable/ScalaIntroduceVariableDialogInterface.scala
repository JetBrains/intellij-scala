package org.jetbrains.plugins.scala.lang.refactoring.introduceVariable

/** 
* User: Alexander Podkhalyuzin
* Date: 01.07.2008
*/

trait ScalaIntroduceVariableDialogInterface {
  def isOK(): Boolean
  def getSettings(): ScalaIntroduceVariableSettings
  def show()
  def getEnteredName(): String
  def isReplaceAllOccurrences(): Boolean
}