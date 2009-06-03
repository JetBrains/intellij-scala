package org.jetbrains.plugins.scala.testingSupport.specs;

import com.intellij.execution.LocatableConfigurationType;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.junit.RuntimeConfigurationProducer;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * User: Alexander Podkhalyuzin
 * Date: 04.05.2009
 */
public class SpecsConfigurationProducer extends RuntimeConfigurationProducer implements Cloneable {
  private PsiElement myPsiElement;

  public SpecsConfigurationProducer() {
    super(new SpecsConfigurationType());
  }

  public SpecsConfigurationProducer(final LocatableConfigurationType configurationType) {
    super(new SpecsConfigurationType());
  }

  public PsiElement getSourceElement() {
    return myPsiElement;
  }

  @Nullable
  protected RunnerAndConfigurationSettingsImpl createConfigurationByElement(final Location location, final ConfigurationContext context) {
    myPsiElement = location.getPsiElement();
    return (RunnerAndConfigurationSettingsImpl) ((SpecsConfigurationType) getConfigurationType()).createConfigurationByLocation(location);
  }

  public int compareTo(final Object o) {
    return -2;
  }
}
