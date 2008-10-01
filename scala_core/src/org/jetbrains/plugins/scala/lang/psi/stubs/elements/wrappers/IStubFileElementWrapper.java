package org.jetbrains.plugins.scala.lang.psi.stubs.elements.wrappers;

import com.intellij.lang.Language;
import com.intellij.psi.PsiFile;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.tree.IStubFileElementType;
import org.jetbrains.annotations.NonNls;

import java.io.IOException;

/**
 * @author ilyas
 */
public abstract class IStubFileElementWrapper<S extends PsiFile, T extends PsiFileStub<S>> extends IStubFileElementType<T>{

  public IStubFileElementWrapper(@NonNls String debugName, Language language) {
    super(debugName, language);
  }

  //Dirty delegate hack to avoid problems with inheritance in Scala which doesn't allow rawtyped parameters
  @Override
  public T deserialize(StubInputStream dataStream, StubElement parentStub) throws IOException {
    return deserializeImpl(dataStream, parentStub);
  }

  protected abstract T deserializeImpl(StubInputStream dataStream, Object parentStub);

}
