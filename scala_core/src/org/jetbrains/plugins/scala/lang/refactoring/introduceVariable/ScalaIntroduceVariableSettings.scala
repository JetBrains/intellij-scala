package org.jetbrains.plugins.scala.lang.refactoring.introduceVariable;

import com.intellij.psi.PsiType;

/**
 * User: Alexander Podkhalyuzin
 * Date: 24.06.2008
 */

trait ScalaIntroduceVariableSettings {
  def getEnteredName(): String;

  def isReplaceAllOccurrences(): Boolean;

  def isDeclareVariable(): Boolean;

  def getSelectedType(): PsiType;
}
