package org.jetbrains.plugins.scala.lang.psi.stubs.elements;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import scala.ScalaObject;

import java.rmi.RemoteException;

/**
 * @author ilyas
 */
public abstract class PsiStubElementWrapper<StubT extends StubElement<PsiT>, PsiT extends PsiElement>
        extends StubBasedPsiElementBase<StubT> implements ScalaObject{

  protected PsiStubElementWrapper(StubT stub, IStubElementType<StubT, PsiT> nodeType) {
    super(stub, nodeType);
  }

  public PsiStubElementWrapper(ASTNode node) {
    super(node);
  }

  public abstract int $tag() throws RemoteException;
}
