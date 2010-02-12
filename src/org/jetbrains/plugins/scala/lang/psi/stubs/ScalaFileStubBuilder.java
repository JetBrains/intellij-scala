package org.jetbrains.plugins.scala.lang.psi.stubs;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.stubs.DefaultStubBuilder;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.io.StringRef;
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile;
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScFileStubImpl;

/**
 * User: Alexander Podkhalyuzin
 * Date: 12.02.2010
 */
public class ScalaFileStubBuilder extends DefaultStubBuilder {
  @Override
  protected StubElement createStubForFile(PsiFile file) {
    ScalaFile s = (ScalaFile) file;
    return new ScFileStubImpl(s, StringRef.fromString(s.getPackageName()), StringRef.fromString(s.sourceName()),
        s.isCompiled(), s.isScriptFile(false));
  }
}
