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
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.config.ScalaLibrariesConfiguration;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

/**
 * @author ilyas
 */
public class ScalaFacetTab extends FacetEditorTab {

  public static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.scala.config.ui.ScalaFacetTab");

  private Module myModule;
  private JPanel myPanel;
  private JCheckBox myCompilerExcludeCb;
  private JCheckBox myLibraryExcludeCb;
  private JCheckBox myUseSettingsChb;
  private JLabel myHintLabel;
  private TextFieldWithBrowseButton ComplerLibraryTextField;
  private TextFieldWithBrowseButton SDKLibraryTextField;
  private FacetEditorContext myEditorContext;
  private FacetValidatorsManager myValidatorsManager;
  private final ScalaLibrariesConfiguration myConfiguration;

  public ScalaFacetTab(FacetEditorContext editorContext, FacetValidatorsManager validatorsManager, ScalaLibrariesConfiguration configuration) {
    myModule = editorContext.getModule();
    myEditorContext = editorContext;
    myValidatorsManager = validatorsManager;

    myConfiguration = configuration;

    myUseSettingsChb.setEnabled(true);
    myUseSettingsChb.setSelected(myConfiguration.takeFromSettings);

    myHintLabel.setEnabled(myUseSettingsChb.isSelected());

    addFileChooser("Choose scala-compiler.jar", ComplerLibraryTextField, myModule.getProject());
    addFileChooser("Choose scala-library.jar", SDKLibraryTextField, myModule.getProject());

    ComplerLibraryTextField.setEnabled(myConfiguration.takeFromSettings);
    ComplerLibraryTextField.setText(PathUtil.getLocalPath(myConfiguration.myScalaCompilerJarPath));
    SDKLibraryTextField.setEnabled(myConfiguration.takeFromSettings);
    SDKLibraryTextField.setText(PathUtil.getLocalPath(myConfiguration.myScalaSdkJarPath));

    myCompilerExcludeCb.setVisible(false);
    myLibraryExcludeCb.setVisible(false);


    myUseSettingsChb.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final boolean b = myUseSettingsChb.isSelected();
        ComplerLibraryTextField.setEnabled(b);
        SDKLibraryTextField.setEnabled(b);
        myHintLabel.setEnabled(b);
      }
    });
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
        myConfiguration.myExcludeSdkFromModuleScope == myLibraryExcludeCb.isSelected() &&
        myConfiguration.takeFromSettings == myUseSettingsChb.isSelected() &&
        myConfiguration.myScalaCompilerJarPath.equals(getCompilerPath()) &&
        myConfiguration.myScalaSdkJarPath.equals(getSdkPath()));
  }

  private String getSdkPath() {
    return PathUtil.getCanonicalPath(SDKLibraryTextField.getText());
  }

  private String getCompilerPath() {
    return PathUtil.getCanonicalPath(ComplerLibraryTextField.getText());
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
    myConfiguration.myScalaCompilerJarPath = getCompilerPath();
    myConfiguration.myScalaSdkJarPath = getSdkPath();
    myConfiguration.takeFromSettings = myUseSettingsChb.isSelected();
  }

  public void reset() {
  }

  public void disposeUIResources() {
  }

  private void createUIComponents() {
  }

  private FileChooserDescriptor addFileChooser(final String title,
                                               final TextFieldWithBrowseButton textField,
                                               final Project project) {
    final FileChooserDescriptor fileChooserDescriptor = new FileChooserDescriptor(false, false, true, false, false, false);
    fileChooserDescriptor.setTitle(title);
    textField.addBrowseFolderListener(title, null, project, fileChooserDescriptor);
    return fileChooserDescriptor;
  }


}
