package org.jetbrains.plugins.scala
package lang
package psi
package stubs
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