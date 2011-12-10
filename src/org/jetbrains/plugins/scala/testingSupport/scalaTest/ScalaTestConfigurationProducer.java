package org.jetbrains.plugins.scala.testingSupport.scalaTest;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.junit.RuntimeConfigurationProducer;
import com.intellij.execution.LocatableConfigurationType;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.config.ScalaFacet;
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager;
import org.jetbrains.plugins.scala.testingSupport.specs.SpecsConfigurationType;
import scala.Some;

/**
 * User: Alexander Podkhalyuzin
 * Date: 08.05.2009
 */
public class ScalaTestConfigurationProducer extends RuntimeConfigurationProducer implements Cloneable {
  private PsiElement myPsiElement;

  public ScalaTestConfigurationProducer() {
    super(new ScalaTestConfigurationType());
  }

  public ScalaTestConfigurationProducer(final LocatableConfigurationType configurationType) {
    super(new ScalaTestConfigurationType());
  }

  public PsiElement getSourceElement() {
    return myPsiElement;
  }

  @Nullable
  protected RunnerAndConfigurationSettingsImpl createConfigurationByElement(final Location location, final ConfigurationContext context) {
    GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(context.getModule(), true);
    if (ScalaPsiManager.instance(context.getProject()).getCachedClass(scope, "org.scalatest.Suite") == null)
      return null;
    myPsiElement = location.getPsiElement();
    return (RunnerAndConfigurationSettingsImpl) (new ScalaTestConfigurationType()).createConfigurationByLocation(location);
  }

  public int compareTo(final Object o) {
    return -1;
  }

  @Override
  protected RunnerAndConfigurationSettings findExistingByElement(Location location,
                                                                 @NotNull RunnerAndConfigurationSettings[] existingConfigurations,
                                                                 ConfigurationContext context) {
    for (RunnerAndConfigurationSettings configuration : existingConfigurations) {
      if (new ScalaTestConfigurationType().isConfigurationByLocation(configuration.getConfiguration(), location)) {
        return configuration;
      }
    }
    return null;
  }
}
