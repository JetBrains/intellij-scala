package org.jetbrains.plugins.scala.lang.refactoring.introduceVariable;

/**
 * User: Alexander Podkhalyuzin
 * Date: 24.06.2008
 */
trait ScalaValidator extends NameValidator{
  def isOK(dialog: ScalaIntroduceVariableDialogInterface): Boolean;
}
