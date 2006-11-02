package org.jetbrains.plugins.scala.sdk;

import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.projectRoots.SdkModel;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.options.ConfigurationException;

/**
 * @author ven
*/
class JavaSdkData implements SdkAdditionalData {
  String myJavaSdkName;

  public JavaSdkData(String javaSdkName) {
    myJavaSdkName = javaSdkName;
  }

  public String getJavaSdkName() {
    return myJavaSdkName;
  }

  public JavaSdkData clone() throws CloneNotSupportedException {
    return (JavaSdkData) super.clone();
  }

  public void checkValid(SdkModel sdkModel) throws ConfigurationException {
    if (myJavaSdkName == null) throw new ConfigurationException("No java sdk configured");
    if (findJavaSdkByName(myJavaSdkName) == null) throw new ConfigurationException("Cannot find jdk");
  }

  Sdk findSdk() {
    return findJavaSdkByName(myJavaSdkName);
  }

  static Sdk findJavaSdkByName(String sdkName) {
    for (Sdk sdk : ProjectJdkTable.getInstance().getAllJdks()) {
      if (sdk.getName().equals(sdkName)) return sdk;
    }
    return null;
  }
}
