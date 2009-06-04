package org.jetbrains.plugins.scala.lang.psi.stubs
import api.ScalaFile
import com.intellij.psi.stubs.{PsiClassHolderFileStub, PsiFileStub}

/**
 * @author ilyas
 */

trait ScFileStub extends PsiClassHolderFileStub[ScalaFile]{

  def packageName: String

  def getFileName: String

  def isCompiled: Boolean

  def isScript: Boolean
}