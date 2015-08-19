package org.jetbrains.plugins.scala.lang.psi;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.wrappers.DummyASTNode;

import java.lang.reflect.Field;

/**
 * @author Alexander Podkhalyuzin
 */
public abstract class ScalaStubBaseElementImplJavaRawTypeHack<T extends PsiElement>
    extends StubBasedPsiElementBase<StubElement<T> >
    implements StubBasedPsiElement<StubElement<T> >  {
  private static Logger LOG = Logger.getInstance(ScalaStubBaseElementImplJavaRawTypeHack.class);

  public ScalaStubBaseElementImplJavaRawTypeHack(StubElement<T> stub, IElementType nodeType, ASTNode node) {
    super(stub, nodeType, node);
  }

  @NotNull
  @Override
  public IStubElementType getElementType() {
    if (getStub() != null) return getStub().getStubType();
    else return (IStubElementType) getNode().getElementType();
  }
}
