package org.jetbrains.plugins.scala.sdk;

import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.Nullable;

/**
 * @author ven
*/
class JavaSdkData implements SdkAdditionalData {
  String myJavaSdkName;
  @Nullable
  private SdkModel mySdkModel;

  public JavaSdkData(String javaSdkName, @Nullable SdkModel mySdkModel) {
    myJavaSdkName = javaSdkName;
    this.mySdkModel = mySdkModel;
  }

  public String getJavaSdkName() {
    return myJavaSdkName;
  }

  public JavaSdkData clone() throws CloneNotSupportedException {
    return (JavaSdkData) super.clone();
  }

  public void checkValid(SdkModel sdkModel) throws ConfigurationException {
    if (myJavaSdkName == null) throw new ConfigurationException("No java sdk configured");
    if (findJavaSdkByName(myJavaSdkName, mySdkModel) == null) throw new ConfigurationException("Cannot find jdk");
  }

  Sdk findSdk() {
    return findJavaSdkByName(myJavaSdkName, mySdkModel);
  }

  static Sdk findJavaSdkByName(String sdkName, @Nullable SdkModel sdkModel) {
    for (Sdk sdk : ProjectJdkTable.getInstance().getAllJdks()) {
      if (sdk.getName().equals(sdkName)) return sdk;
    }
    if (sdkModel != null) {
      for (Sdk sdk : sdkModel.getSdks()) {
        if (sdk.getName().equals(sdkName)) return sdk;
      }
    }
    return null;
  }
}
