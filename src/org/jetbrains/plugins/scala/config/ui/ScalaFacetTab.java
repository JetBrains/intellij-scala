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

package org.jetbrains.plugins.scala.config.ui;

import com.intellij.facet.Facet;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.config.ScalaLibrariesConfiguration;

import javax.swing.*;

/**
 * @author ilyas
 */
public class ScalaFacetTab extends FacetEditorTab {

  public static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.scala.config.ui.ScalaFacetTab");

  private Module myModule;
  private JPanel myPanel;
  private JCheckBox myCompilerExcludeCb;
  private JCheckBox myLibraryExcludeCb;
  private FacetEditorContext myEditorContext;
  private FacetValidatorsManager myValidatorsManager;
  private final ScalaLibrariesConfiguration myConfiguration;

  public ScalaFacetTab(FacetEditorContext editorContext, FacetValidatorsManager validatorsManager, ScalaLibrariesConfiguration configuration) {
    myModule = editorContext.getModule();
    myEditorContext = editorContext;
    myValidatorsManager = validatorsManager;

    myConfiguration = configuration;

    myCompilerExcludeCb.setSelected(myConfiguration.myExcludeCompilerFromModuleScope);
    myLibraryExcludeCb.setSelected(myConfiguration.myExcludeSdkFromModuleScope);
    reset();
  }

  @Nls
  public String getDisplayName() {
    return ScalaBundle.message("scala.sdk.configuration");
  }

  public JComponent createComponent() {
    return myPanel;
  }

  public boolean isModified() {
    return !(myConfiguration.myExcludeCompilerFromModuleScope == myCompilerExcludeCb.isSelected() &&
        myConfiguration.myExcludeSdkFromModuleScope == myLibraryExcludeCb.isSelected());
  }

  @Override
  public String getHelpTopic() {
    return super.getHelpTopic();
  }

  public void onFacetInitialized(@NotNull Facet facet) {
  }

  public void apply() throws ConfigurationException {
    myConfiguration.myExcludeCompilerFromModuleScope = myCompilerExcludeCb.isSelected();
    myConfiguration.myExcludeSdkFromModuleScope = myLibraryExcludeCb.isSelected();
  }

  public void reset() {
  }

  public void disposeUIResources() {
  }

  private void createUIComponents() {
  }


}
