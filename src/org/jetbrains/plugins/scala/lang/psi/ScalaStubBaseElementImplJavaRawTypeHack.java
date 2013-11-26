package org.jetbrains.plugins.scala.lang.psi;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.wrappers.DummyASTNode;

/**
 * @author Alexander Podkhalyuzin
 */
public abstract class ScalaStubBaseElementImplJavaRawTypeHack<T extends PsiElement>
    extends StubBasedPsiElementBase<StubElement<T> >
    implements StubBasedPsiElement<StubElement<T> >  {
  public ScalaStubBaseElementImplJavaRawTypeHack() {
    super(DummyASTNode.getInstanceForJava());
  }

  @NotNull
  @Override
  public IStubElementType getElementType() {
    if (getStub() != null) return getStub().getStubType();
    else return (IStubElementType) getNode().getElementType();
  }
}
