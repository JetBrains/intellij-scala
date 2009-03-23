/*
 * Copyright 2000-2008 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.scala.config;

import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.FacetManagerImpl;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jdom.Element;
import org.jetbrains.plugins.scala.config.ui.ScalaFacetTab;

/**
 * @author ilyas
 */

@State(
    name = "ScalaFacetConfiguration",
    storages = {
      @Storage(
        id = "default",
        file = "$MODULE_FILE$"
      )
    }
)
public class ScalaFacetConfiguration implements FacetConfiguration, PersistentStateComponent<ScalaLibrariesConfiguration> {
  private final ScalaLibrariesConfiguration myScalaLibrariesConfiguration = new ScalaLibrariesConfiguration();

  public String getDisplayName() {
    return "Scala";
  }

  public FacetEditorTab[] createEditorTabs(FacetEditorContext editorContext, FacetValidatorsManager validatorsManager) {
    return new FacetEditorTab[]{
        new ScalaFacetTab(editorContext, validatorsManager, myScalaLibrariesConfiguration)
    };
  }

  public void readExternal(Element element) throws InvalidDataException {
  }

  public void writeExternal(Element element) throws WriteExternalException {
  }

  public ScalaLibrariesConfiguration getMyScalaLibrariesConfiguration() {
    return myScalaLibrariesConfiguration;
  }

  public ScalaLibrariesConfiguration getState() {
    return myScalaLibrariesConfiguration;
  }

  public void loadState(ScalaLibrariesConfiguration state) {
    XmlSerializerUtil.copyBean(state, myScalaLibrariesConfiguration);
  }
}
