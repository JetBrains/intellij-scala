package org.jetbrains.plugins.scala.lang.refactoring.changeSignature.changeInfo;

import com.intellij.refactoring.changeSignature.JavaChangeInfo;
import com.intellij.refactoring.changeSignature.JavaParameterInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.lang.refactoring.changeSignature.ScalaParameterInfo;

abstract class ScalaChangeInfoBase implements JavaChangeInfo {
  private ScalaParameterInfo[] myNewParams;

  ScalaChangeInfoBase(ScalaParameterInfo[] newParams) {
    myNewParams = newParams;
  }

  @NotNull
  @Override
  public JavaParameterInfo[] getNewParameters() {
    return myNewParams;
  }
}
