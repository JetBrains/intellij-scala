package org.jetbrains.plugins.scala
package lang
package psi
package stubs

import com.intellij.psi.PsiFile
import com.intellij.psi.stubs.{PsiClassHolderFileStub, PsiFileStub}
import com.intellij.psi.tree.IStubFileElementType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

/**
 * @author ilyas
 */

trait ScFileStub extends PsiClassHolderFileStub[ScalaFile] {
  def packageName: String

  def sourceName: String

  def isCompiled: Boolean

  def isScript: Boolean

  override def getType: IStubFileElementType[Nothing] =
    fileElementType.asInstanceOf[IStubFileElementType[Nothing]]

  protected def fileElementType: IStubFileElementType[_ <: PsiFileStub[_ <: PsiFile]]
}