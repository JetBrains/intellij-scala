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
