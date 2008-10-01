package org.jetbrains.plugins.scala.lang.psi.impl.compiled.wrappers;

import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.compiled.ClsRepositoryPsiElement;
import com.intellij.psi.stubs.StubElement;

/**
 * @author ilyas
 */
abstract public class ScClsElementWrapperImpl<S extends PsiElement, T extends StubElement<S>> extends ClsRepositoryPsiElement<T>{
  protected ScClsElementWrapperImpl(T stub) {
    super(stub);
  }
}
