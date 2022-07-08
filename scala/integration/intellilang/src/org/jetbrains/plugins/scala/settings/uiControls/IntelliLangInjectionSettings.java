package org.jetbrains.plugins.scala.settings.uiControls;

import javax.swing.*;

public class IntelliLangInjectionSettings extends DependencyAwareInjectionSettings {
  private final static String INJECTION_SETTINGS_NAME = "IntelliLangInjectionSettings";

  @Override
  public ComponentWithSettings createComponent(JPanel uiPlace) {
    ScalaInterpolatedPrefixMappingTable uiComponent = new ScalaInterpolatedPrefixMappingTable();
    uiComponent.setMyMainPanel(uiPlace);
    return uiComponent;
  }

  @Override
  public String getName() {
    return INJECTION_SETTINGS_NAME;
  }
}
