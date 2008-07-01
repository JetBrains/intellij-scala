package org.jetbrains.plugins.scala.lang.refactoring.introduceVariable;

import org.jetbrains.plugins.scala.lang.refactoring.introduceVariable.typeManipulator.IType;

/**
 * User: Alexander Podkhalyuzin
 * Date: 01.07.2008
 */
public interface ScalaIntroduceVariableSettings {
  public String getEnteredName();

  public boolean isReplaceAllOccurrences();

  public boolean isDeclareVariable();

  public IType getSelectedType();
}
