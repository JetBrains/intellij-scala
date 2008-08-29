package org.jetbrains.plugins.scala.lang.psi.stubs.elements.wrappers;

import com.intellij.psi.PsiElement;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;

/**
 * @author ilyas
 */
public interface StubBasedPsiElementWrapper<StubT extends StubElement, PsiT extends PsiElement> extends StubBasedPsiElement<StubT> {

  IStubElementType<?, ?> getElementType();

}
