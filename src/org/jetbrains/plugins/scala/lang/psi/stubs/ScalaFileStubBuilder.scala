package org.jetbrains.plugins.scala
package lang.psi.stubs

import com.intellij.psi.stubs.{DefaultStubBuilder, StubElement}
import com.intellij.psi.{PsiElement, PsiFile}
import com.intellij.util.io.StringRef
import com.intellij.util.io.StringRef.fromString
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScFileStubImpl

/**
 * User: Alexander Podkhalyuzin
 * Date: 12.02.2010
 */
class ScalaFileStubBuilder extends DefaultStubBuilder {
  protected override def createStubForFile(file: PsiFile): StubElement[_ <: PsiElement] = {
    val scalaFile = file.getViewProvider.getPsi(ScalaLanguage.Instance).asInstanceOf[ScalaFile]
    fileStub(scalaFile,
      fromString(scalaFile.packageName),
      fromString(scalaFile.sourceName),
      scalaFile.isCompiled,
      scalaFile.isScriptFile(withCaching = false))
  }

  def fileStub(file: ScalaFile, packageName: StringRef, fileName: StringRef, compiled: Boolean, script: Boolean): ScFileStub =
    new ScFileStubImpl(file, packageName, fileName, compiled, script)
}