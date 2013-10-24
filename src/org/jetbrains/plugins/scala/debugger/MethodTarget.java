package org.jetbrains.plugins.scala.debugger;

import com.intellij.debugger.actions.JvmSmartStepIntoHandler;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// Copy of JavaSmartStepIntoHandler.MethodTarget
class MethodTarget implements JvmSmartStepIntoHandler.StepTarget {
  private final PsiMethod myMethod;
  private final String myLabel;
  private final boolean myNeedBreakpointRequest;

  MethodTarget(@NotNull PsiMethod method, String currentParamName, boolean needBreakpointRequest) {
    myMethod = method;
    myLabel = currentParamName == null? null : currentParamName + ".";
    myNeedBreakpointRequest = needBreakpointRequest;
  }

  @Nullable
  public String getMethodLabel() {
    return myLabel;
  }

  @NotNull
  public PsiMethod getMethod() {
    return myMethod;
  }

  public boolean needsBreakpointRequest() {
    return myNeedBreakpointRequest;
  }

  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final MethodTarget that = (MethodTarget)o;

    if (!myMethod.equals(that.myMethod)) {
      return false;
    }

    return true;
  }

  public int hashCode() {
    return myMethod.hashCode();
  }
}
