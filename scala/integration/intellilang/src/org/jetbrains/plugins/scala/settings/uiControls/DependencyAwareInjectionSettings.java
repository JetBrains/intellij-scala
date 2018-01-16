package org.jetbrains.plugins.scala.settings.uiControls;

import org.jetbrains.plugins.scala.settings.ScalaProjectSettingsPanel;

import javax.swing.*;

/**
 * User: Dmitry Naydanov
 * Date: 2/18/13
 */
public class DependencyAwareInjectionSettings extends ScalaUiWithDependency {
  @Override
  public ComponentWithSettings createComponent(JPanel uiPlace) {
    ScalaInterpolatedPrefixMappingTable uiComponent = new ScalaInterpolatedPrefixMappingTable();
    uiComponent.setMyMainPanel(uiPlace);
    return uiComponent;
  }

  @Override
  public String getName() {
    return ScalaProjectSettingsPanel.INJECTION_SETTINGS_NAME;
  }
}
