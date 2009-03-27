package org.jetbrains.plugins.scala.lang.refactoring.introduceVariable;

/**
 * User: Alexander Podkhalyuzin
 * Date: 01.07.2008
 */
public interface ScalaIntroduceVariableDialogInterface {
  public boolean isOK();
  public ScalaIntroduceVariableSettings getSettings();
  public void show();
  public String getEnteredName();
  public boolean isReplaceAllOccurrences();
}
