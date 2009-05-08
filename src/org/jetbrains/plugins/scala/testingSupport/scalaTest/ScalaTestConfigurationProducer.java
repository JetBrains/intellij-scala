package org.jetbrains.plugins.scala.testingSupport.scalaTest;

import com.intellij.execution.junit.RuntimeConfigurationProducer;
import com.intellij.execution.LocatableConfigurationType;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.testingSupport.specs.SpecsConfigurationType;

/**
 * User: Alexander Podkhalyuzin
 * Date: 08.05.2009
 */
public class ScalaTestConfigurationProducer extends RuntimeConfigurationProducer implements Cloneable {
    private PsiElement myPsiElement;

    public ScalaTestConfigurationProducer(final LocatableConfigurationType configurationType) {
      super(configurationType);
    }

    public PsiElement getSourceElement() {
      return myPsiElement;
    }

    @Nullable
    protected RunnerAndConfigurationSettingsImpl createConfigurationByElement(final Location location, final ConfigurationContext context) {
      myPsiElement = location.getPsiElement();
      return (RunnerAndConfigurationSettingsImpl)(new ScalaTestConfigurationType()).createConfigurationByLocation(location);
    }

    public int compareTo(final Object o) {
      return -1;
    }
  }
