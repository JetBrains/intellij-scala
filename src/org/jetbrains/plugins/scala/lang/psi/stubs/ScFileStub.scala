package org.jetbrains.plugins.scala
package lang
package psi
package stubs

import com.intellij.psi.stubs.PsiClassHolderFileStub
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

/**
 * @author ilyas
 */

trait ScFileStub extends PsiClassHolderFileStub[ScalaFile] {
  def packageName: String

  def sourceName: String

  def isCompiled: Boolean

  def isScript: Boolean
}