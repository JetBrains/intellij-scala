package org.jetbrains.plugins.scala.lang.refactoring.introduceVariable;

import com.intellij.psi.PsiType;

/**
 * User: Alexander Podkhalyuzin
 * Date: 24.06.2008
 */
public interface ScalaIntroduceVariableSettings {
  public String getEnteredName();

  public boolean isReplaceAllOccurrences();

  public boolean isDeclareVariable();

  public PsiType getSelectedType();
}
