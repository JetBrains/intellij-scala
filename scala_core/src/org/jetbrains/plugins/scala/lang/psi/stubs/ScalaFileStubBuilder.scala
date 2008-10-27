package org.jetbrains.plugins.scala.lang.psi.stubs

import api.ScalaFile

import com.intellij.psi.stubs.{StubElement, DefaultStubBuilder, PsiFileStub}
import com.intellij.psi.{PsiElement, PsiFile}
import impl.ScFileStubImpl
/**
 * @author ilyas
 */

class ScalaFileStubBuilder extends DefaultStubBuilder {

  override def createStubForFile(file: PsiFile) = {
    file match {
      case s : ScalaFile => new ScFileStubImpl(s)
    }
  }

}