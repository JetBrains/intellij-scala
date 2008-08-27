package org.jetbrains.plugins.scala.lang.psi.stubs.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;

/**
 * @author ilyas
 */
public class StubBaseWrapper<T extends PsiElement> extends StubBase<T> {

  protected StubBaseWrapper(StubElement<?> parent, IStubElementType<?, ?> elementType) {
    super(parent, elementType);
  }
}
