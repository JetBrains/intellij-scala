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
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.config.ScalaLibrariesConfiguration;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.Arrays;

/**
 * @author ilyas
 */
public class ScalaFacetTab extends FacetEditorTab {

  public static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.scala.config.ui.ScalaFacetTab");

  private Module myModule;
  private JPanel myPanel;
  private JCheckBox myUseSettingsChb;
  private JLabel myHintLabel;
  private TextFieldWithBrowseButton ComplerLibraryTextField;
  private TextFieldWithBrowseButton SDKLibraryTextField;
  private JCheckBox useAdditionalJarsForCheckBox;
  private JTextField CompilerAddTextField;
  private JTextField SDKAddTextField;
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

    addFileChooser("Choose scala-compiler.jar", ComplerLibraryTextField, myModule.getProject());
    addFileChooser("Choose scala-library.jar", SDKLibraryTextField, myModule.getProject());

    ComplerLibraryTextField.setEnabled(myConfiguration.takeFromSettings);
    String res = "";
    for (String text : myConfiguration.myScalaCompilerJarPaths) {
      res += PathUtil.getLocalPath(text);
      res += File.pathSeparator;
    }
    if (res.endsWith(File.pathSeparator)) res = res.substring(0, res.lastIndexOf(File.pathSeparator));
    CompilerAddTextField.setText(res);
    ComplerLibraryTextField.setText(PathUtil.getLocalPath(myConfiguration.myScalaCompilerJarPaths[0]));
    res = "";
    for (String text : myConfiguration.myScalaSdkJarPaths) {
      res += PathUtil.getLocalPath(text);
      res += File.pathSeparator;
    }
    if (res.endsWith(File.pathSeparator)) res = res.substring(0, res.lastIndexOf(File.pathSeparator));
    SDKAddTextField.setText(res);
    SDKLibraryTextField.setText(PathUtil.getLocalPath(myConfiguration.myScalaSdkJarPaths[0]));
    useAdditionalJarsForCheckBox.setSelected(myConfiguration.myScalaSdkJarPaths.length != 1 || myConfiguration.myScalaCompilerJarPaths.length != 1);
    setEnables();


    myUseSettingsChb.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        setEnables();
      }
    });
    useAdditionalJarsForCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        setEnables();
      }
    });
    reset();
  }

  public void setEnables() {
    boolean b1 = myUseSettingsChb.isSelected();
    boolean b2 = useAdditionalJarsForCheckBox.isSelected();
    ComplerLibraryTextField.setEnabled(b1 && !b2);
    SDKLibraryTextField.setEnabled(b1 && !b2);
    myHintLabel.setEnabled(b1 && b2);
    CompilerAddTextField.setEnabled(b1 && b2);
    SDKAddTextField.setEnabled(b1 && b2);

  }

  @Nls
  public String getDisplayName() {
    return ScalaBundle.message("scala.sdk.configuration");
  }

  public JComponent createComponent() {
    return myPanel;
  }

  public boolean isModified() {
    return !(
        myConfiguration.takeFromSettings == myUseSettingsChb.isSelected() &&
        Arrays.equals(myConfiguration.myScalaCompilerJarPaths, getCompilerPath().split(File.pathSeparator)) &&
        Arrays.equals(myConfiguration.myScalaSdkJarPaths, getSdkPath().split(File.pathSeparator)));
  }

  private String getSdkPath() {
    if (useAdditionalJarsForCheckBox.isSelected()) return SDKAddTextField.getText();
    return SDKLibraryTextField.getText();
  }

  private String getCompilerPath() {
    if (useAdditionalJarsForCheckBox.isSelected()) return CompilerAddTextField.getText();
    return ComplerLibraryTextField.getText();
  }

  @Override
  public String getHelpTopic() {
    return super.getHelpTopic();
  }

  public void onFacetInitialized(@NotNull Facet facet) {
  }

  public void apply() throws ConfigurationException {
    String[] strings = getCompilerPath().split(File.pathSeparator);
    for (int i = 0; i < strings.length; ++i) {
      strings[i] = PathUtil.getCanonicalPath(strings[i]);
    }
    myConfiguration.myScalaCompilerJarPaths = strings.length == 0 ? new String[]{""} : strings;
    strings = getSdkPath().split(File.pathSeparator);
    for (int i = 0; i < strings.length; ++i) {
      strings[i] = PathUtil.getCanonicalPath(strings[i]);
    }
    myConfiguration.myScalaSdkJarPaths = strings.length == 0 ? new String[]{""} : strings;
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
