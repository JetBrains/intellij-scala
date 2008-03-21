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

package org.jetbrains.plugins.scala.facet;

import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ShowSettingsUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.plugins.scala.ScalaBundle;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

/**
 * @author ilyas
 */
public class ScalaConfigurationTab extends FacetEditorTab {
  private FacetEditorContext myEditorContext;

  private JPanel myMainPanel;
  private JButton myConfButton;
  private boolean myModified;

  public ScalaConfigurationTab(FacetEditorContext editorContext) {
    myEditorContext = editorContext;
  }

  @Nls
  public String getDisplayName() {
    return ScalaBundle.message("config.display.name");
  }

  public JComponent createComponent() {
    return myMainPanel;
  }

  public boolean isModified() {
    return myModified;
  }

  public void apply() throws ConfigurationException {

  }

  public void reset() {

  }

  public void disposeUIResources() {

  }
}
