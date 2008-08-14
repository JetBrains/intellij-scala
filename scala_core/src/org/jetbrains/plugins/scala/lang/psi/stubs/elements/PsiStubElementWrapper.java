package org.jetbrains.plugins.scala.lang.psi.stubs.elements;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;

/**
 * @author ilyas
 */
public class PsiStubElementWrapper<T extends StubElement<? extends PsiElement>>
        extends StubBasedPsiElementBase<T> {

  public PsiStubElementWrapper(T stub, IStubElementType<?, ?> nodeType) {
    super(stub, nodeType);
  }

}
