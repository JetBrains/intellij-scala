package org.jetbrains.plugins.scala.lang.psi.stubs
import impl.ScFileStubImpl
import com.intellij.psi.PsiFile
import com.intellij.psi.stubs.DefaultStubBuilder

/**
 * @author ilyas
 */

class ScalaFileStubBuilder extends DefaultStubBuilder {

  override def createStubForFile(file: PsiFile) = {
    file match {
      case s : ScalaFile => new ScFileStubImpl(s)
      case _ => super.createStubForFile(file)
    }
  }

}