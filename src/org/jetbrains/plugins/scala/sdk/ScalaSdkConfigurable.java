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
  private SdkModel mySdkModel;

  public ScalaSdkConfigurable(SdkModel sdkModel) {
    mySdkModel = sdkModel;
    myJavaSdkCbx = new JComboBox();
    DefaultComboBoxModel model = new DefaultComboBoxModel(getJavaSdks(sdkModel));
    myJavaSdkCbx.setModel(model);
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
  }

  private Sdk[] getJavaSdks(SdkModel sdkModel) {
    Sdk[] sdks = sdkModel.getSdks();
    List<Sdk> result = new ArrayList<Sdk>();
    for (Sdk sdk : sdks) {
      SdkType sdkType = sdk.getSdkType();
      if (Comparing.equal(sdkType, JavaSdk.getInstance())) {
        result.add(sdk);
      }
    }

    return result.toArray(new Sdk[result.size()]);
  }

  public void setSdk(Sdk sdk) {
    myScalaSdk = sdk;
  }

  public JComponent createComponent() {
    JPanel panel = new JPanel();
    panel.add(myJavaSdkCbx);
    panel.setBorder(IdeBorderFactory.createTitledBorder("Select Java SDK"));
    return panel;
  }

  public boolean isModified() {
    MyAdditionalData additionalData = (MyAdditionalData) myScalaSdk.getSdkAdditionalData();
    return additionalData == null || !Comparing.equal(myJavaSdkCbx.getSelectedItem(), additionalData.getJavaSdkName());
  }

  public void apply() throws ConfigurationException {
    final SdkModificator modificator = myScalaSdk.getSdkModificator();
    modificator.setSdkAdditionalData(new MyAdditionalData(((Sdk) myJavaSdkCbx.getSelectedItem()).getName()));
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        modificator.commitChanges();
      }
    });
    myScalaSdk.getSdkType().setupSdkPaths(myScalaSdk);
  }

  public void reset() {
    SdkAdditionalData sdkAdditionalData = myScalaSdk.getSdkAdditionalData();
    if (sdkAdditionalData != null) {
      Sdk selected = findJavaSdkByName(((MyAdditionalData) sdkAdditionalData).getJavaSdkName());
      myJavaSdkCbx.setSelectedItem(selected);
    }
  }

  public void disposeUIResources() {
  }

  class MyAdditionalData implements SdkAdditionalData {
    String myJavaSdkName;
    
    public MyAdditionalData(String javaSdkName) {
      myJavaSdkName = javaSdkName;
    }


    public String getJavaSdkName() {
      return myJavaSdkName;
    }

    public MyAdditionalData clone() throws CloneNotSupportedException {
      return (MyAdditionalData) super.clone();
    }

    public void checkValid(SdkModel sdkModel) throws ConfigurationException {
      if (myJavaSdkName == null) throw new ConfigurationException("No java sdk configured");
      if (findJavaSdkByName(myJavaSdkName) == null) throw new ConfigurationException("Cannot find jdk");
    }

    Sdk findSdk() {
      return findJavaSdkByName(myJavaSdkName);
    }
  }

  private Sdk findJavaSdkByName(String sdkName) {
    for (Sdk sdk : mySdkModel.getSdks()) {
      if (sdk.getName().equals(sdkName)) return sdk;
    }
    return null;
  }
}