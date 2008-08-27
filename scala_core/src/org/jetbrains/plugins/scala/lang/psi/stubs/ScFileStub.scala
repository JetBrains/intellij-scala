package org.jetbrains.plugins.scala.lang.psi.stubs
import com.intellij.psi.stubs.PsiFileStub

/**
 * @author ilyas
 */

trait ScFileStub extends PsiFileStub[ScalaFile]{

  def packageName: String

  def getName: String
}