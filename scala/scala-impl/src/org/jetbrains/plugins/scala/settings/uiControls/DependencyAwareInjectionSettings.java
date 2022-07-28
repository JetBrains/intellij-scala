package org.jetbrains.plugins.scala.settings.uiControls;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings;

import javax.swing.*;

public abstract class DependencyAwareInjectionSettings {
  public static ExtensionPointName<DependencyAwareInjectionSettings> EP_NAME = ExtensionPointName.create("org.intellij.scala.dependencyAwareInjectionSettings");

  public abstract ComponentWithSettings createComponent(JPanel uiPlace);
  
  public abstract String getName();
  
  public interface ComponentWithSettings {
    void loadSettings(ScalaProjectSettings settings);
    void saveSettings(ScalaProjectSettings settings);
    boolean isModified(ScalaProjectSettings settings);
  }
}
