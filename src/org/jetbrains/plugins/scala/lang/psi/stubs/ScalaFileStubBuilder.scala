package org.jetbrains.plugins.scala
package lang.psi.stubs

import com.intellij.psi.{PsiElement, PsiFile}
import com.intellij.psi.stubs.{DefaultStubBuilder, StubElement}
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScFileStubImpl

/**
 * User: Alexander Podkhalyuzin
 * Date: 12.02.2010
 */
class ScalaFileStubBuilder extends DefaultStubBuilder {
  protected override def createStubForFile(file: PsiFile): StubElement[_ <: PsiElement] = {
    val s: ScalaFile = file.getViewProvider.getPsi(ScalaLanguage.Instance).asInstanceOf[ScalaFile]
    new ScFileStubImpl(s, StringRef.fromString(s.packageName), StringRef.fromString(s.sourceName), s.isCompiled,
      s.isScriptFile(withCaching = false))
  }
}