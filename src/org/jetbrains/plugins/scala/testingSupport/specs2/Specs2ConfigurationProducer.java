package org.jetbrains.plugins.scala.testingSupport.specs2;

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
public class Specs2ConfigurationProducer extends RuntimeConfigurationProducer implements Cloneable {
  private PsiElement myPsiElement;

  public Specs2ConfigurationProducer() {
    super(new Specs2ConfigurationType());
  }

  public Specs2ConfigurationProducer(final LocatableConfigurationType configurationType) {
    super(new Specs2ConfigurationType());
  }

  public PsiElement getSourceElement() {
    return myPsiElement;
  }

  @Nullable
  protected RunnerAndConfigurationSettingsImpl createConfigurationByElement(final Location location, final ConfigurationContext context) {
    myPsiElement = location.getPsiElement();
    return (RunnerAndConfigurationSettingsImpl) ((Specs2ConfigurationType) getConfigurationType()).createConfigurationByLocation(location);
  }

  public int compareTo(final Object o) {
    return -2;
  }
}
