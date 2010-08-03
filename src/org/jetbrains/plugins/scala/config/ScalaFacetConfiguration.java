package org.jetbrains.plugins.scala.config;

import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jdom.Element;
import org.jetbrains.plugins.scala.config.ui.FacetConfigurationEditor;

/**
 * Pavel.Fatin, 26.07.2010
 */


@State(name = "ScalaFacetConfiguration", storages = {@Storage(id = "default", file = "$MODULE_FILE$")})
class ScalaFacetConfiguration implements FacetConfiguration, PersistentStateComponent<ConfigurationData> {
  private ConfigurationData data = new ConfigurationData();

  public FacetEditorTab[] createEditorTabs(FacetEditorContext editorContext, FacetValidatorsManager validatorsManager) {
    return new FacetEditorTab[]{new FacetConfigurationEditor(data, editorContext, validatorsManager)};
  }

  @Deprecated
  public void readExternal(Element element) {
  }

  @Deprecated
  public void writeExternal(Element element) {
  }

  public ConfigurationData getState() {
    return data;
  }

  public void loadState(ConfigurationData state) {
    XmlSerializerUtil.copyBean(state, data);
  }
}
