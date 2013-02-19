package org.jetbrains.plugins.scala.settings.uiControls;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings;

import javax.swing.*;

/**
 * User: Dmitry Naydanov
 * Date: 2/18/13
 */
public abstract class ScalaUiWithDependency {
  public static ExtensionPointName<ScalaUiWithDependency> EP_NAME = ExtensionPointName.create("org.intellij.scala.scalaUiWithDependency");

  public abstract ComponentWithSettings createComponent(JPanel uiPlace);
  
  public abstract String getName();
  
  public interface ComponentWithSettings {
    void loadSettings(ScalaProjectSettings settings);
    void saveSettings(ScalaProjectSettings settings);
    boolean isModified(ScalaProjectSettings settings);
  }
  
  public static class NullComponentWithSettings implements ComponentWithSettings {
    public void loadSettings(ScalaProjectSettings settings) { }
    public void saveSettings(ScalaProjectSettings settings) { }
    public boolean isModified(ScalaProjectSettings settings) { return false; }
  }
}
