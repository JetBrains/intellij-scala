package org.jetbrains.plugins.scala.lang.psi.stubs.elements.wrappers;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;

/**
 * Wrapper for standard StubBasedElement types to inherit them in Scala
 *
 * @author ilyas
 */
public abstract class PsiStubElementWrapper<StubT extends StubElement, PsiT extends PsiElement>
        extends StubBasedPsiElementBase<StubT> {

  protected PsiStubElementWrapper(StubT stub, IStubElementType<StubT, PsiT> nodeType) {
    super(stub, nodeType);
  }

  public PsiStubElementWrapper(ASTNode node) {
    super(node);
  }

  // One more wrapper for correct inheritance in Scala
  @Override
  public IStubElementType<StubT, PsiT> getElementType() {
    return super.getElementType();
  }
}
