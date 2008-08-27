package org.jetbrains.plugins.scala.lang.psi.stubs.elements.wrappers;

import com.intellij.psi.PsiFile;
import com.intellij.psi.stubs.PsiFileStubImpl;
import com.intellij.psi.tree.IStubFileElementType;

/**
 * @author ilyas
 */
public class PsiFileStubWrapperImpl<T extends PsiFile> extends PsiFileStubImpl<T>{

  public PsiFileStubWrapperImpl(T file) {
    super(file);
  }

  @Override
  public IStubFileElementType<?> getType() {
    return super.getType();
  }
}
