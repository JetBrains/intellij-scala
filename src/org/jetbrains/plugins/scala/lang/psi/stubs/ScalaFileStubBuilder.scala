package org.jetbrains.plugins.scala.lang.psi.stubs

import api.ScalaFile

import com.intellij.psi.stubs.{StubElement, DefaultStubBuilder, PsiFileStub}
import com.intellij.psi.{PsiElement, PsiFile}
import impl.ScFileStubImpl
import com.intellij.util.io.StringRef
/**
 * @author ilyas
 */

class ScalaFileStubBuilder extends DefaultStubBuilder {

  override def createStubForFile(file: PsiFile) = {
    implicit def str2ref = StringRef.fromString _
    file match {
      case s : ScalaFile => new ScFileStubImpl(s, s.getPackageName, s.sourceName, s.isCompiled, s.isScriptFile(false))
    }
  }

}