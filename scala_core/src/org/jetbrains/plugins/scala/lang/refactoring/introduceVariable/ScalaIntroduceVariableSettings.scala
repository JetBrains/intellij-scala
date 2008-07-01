package org.jetbrains.plugins.scala.lang.refactoring.introduceVariable

import psi.types.ScType

/** 
* User: Alexander Podkhalyuzin
* Date: 01.07.2008
*/

trait ScalaIntroduceVariableSettings {
  def getEnteredName(): String

  def isReplaceAllOccurrences(): Boolean

  def isDeclareVariable(): Boolean

  def getSelectedType(): ScType
}