/*
 * Copyright 2000-2006 JetBrains s.r.o.
 *
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

package org.jetbrains.plugins.scala.sdk;

import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.IdeBorderFactory;

import javax.swing.*;
import java.util.List;
import java.util.ArrayList;
import java.awt.*;

/**
 * @author ven
 */
public class ScalaSdkConfigurable implements AdditionalDataConfigurable {

  JComboBox myJavaSdkCbx;

  private Sdk myScalaSdk;

  private SdkModel.Listener myListener;
  private SdkModel mySdkModel;

  public ScalaSdkConfigurable(SdkModel sdkModel) {
    mySdkModel = sdkModel;
    myJavaSdkCbx = new JComboBox();
    reloadModel();
    myJavaSdkCbx.setRenderer(new DefaultListCellRenderer(){
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        final Component listCellRendererComponent = super.getListCellRendererComponent(list, value, index, isSelected,
                                                                                       cellHasFocus);
        if (value instanceof ProjectJdk) {
          setText(((ProjectJdk)value).getName());
        }
        return listCellRendererComponent;
      }
    });

    myListener = new SdkModel.Listener() {
      public void sdkAdded(Sdk sdk) {
        reloadModel();
      }

      public void beforeSdkRemove(Sdk sdk) {
        reloadModel();
      }

      public void sdkChanged(Sdk sdk, String previousName) {
        reloadModel();
      }

      public void sdkHomeSelected(Sdk sdk, String newSdkHome) {
        reloadModel();
      }
    };
    sdkModel.addListener(myListener);
  }

  private void reloadModel() {
    myJavaSdkCbx.setModel(new DefaultComboBoxModel(getJavaSdkNames()));
  }

  private String[] getJavaSdkNames() {
    Sdk[] sdks = mySdkModel.getSdks();
    List<String> result = new ArrayList<String>();
    for (Sdk sdk : sdks) {
      SdkType sdkType = sdk.getSdkType();
      if (Comparing.equal(sdkType, JavaSdk.getInstance()) || sdkType.getName().equals("IDEA JDK")) {
        result.add(sdk.getName());
      }
    }

    return result.toArray(new String[result.size()]);
  }

  public void setSdk(Sdk sdk) {
    myScalaSdk = sdk;
  }

  public JComponent createComponent() {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.add(myJavaSdkCbx, new GridBagConstraints(0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 5, 0, 5), 0, 0));
    JavaSdkData sdkAdditionalData = (JavaSdkData) myScalaSdk.getSdkAdditionalData();
    if (sdkAdditionalData != null) {
      Sdk javaSdk = sdkAdditionalData.findSdk();
      if (javaSdk != null) {
        myJavaSdkCbx.setSelectedItem(javaSdk.getName());
      }
    }
    setupPaths();
    panel.setBorder(IdeBorderFactory.createTitledBorder("Select Java SDK"));
    return panel;
  }

  private void setupPaths() {
    final SdkModificator modificator = myScalaSdk.getSdkModificator();
    String item = (String) myJavaSdkCbx.getSelectedItem();
    if (item != null) {
      modificator.setSdkAdditionalData(new JavaSdkData(item, mySdkModel));
    }
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        modificator.commitChanges();
      }
    });
    myScalaSdk.getSdkType().setupSdkPaths(myScalaSdk);
  }

  public boolean isModified() {
    JavaSdkData additionalData = (JavaSdkData) myScalaSdk.getSdkAdditionalData();
    return additionalData == null || !Comparing.equal(myJavaSdkCbx.getSelectedItem(), additionalData.getJavaSdkName());
  }

  public void apply() throws ConfigurationException {
    setupPaths();
  }

  public void reset() {
    SdkAdditionalData sdkAdditionalData = myScalaSdk.getSdkAdditionalData();
    if (sdkAdditionalData != null) {
      String javaSdkName = ((JavaSdkData) sdkAdditionalData).getJavaSdkName();
      myJavaSdkCbx.setSelectedItem(javaSdkName);
    }
  }

  public void disposeUIResources() {
    mySdkModel.removeListener(myListener);
  }
}