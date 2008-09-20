package org.jetbrains.plugins.scala.lang.psi.stubs

import com.intellij.util.io.StringRef
import com.intellij.psi.stubs.{StubElement, IStubElementType, NamedStub}
import com.intellij.psi.PsiElement
import api.toplevel.typedef.ScTypeDefinition

/**
 * @author ilyas
 */

trait ScTypeDefinitionStub extends NamedStub[ScTypeDefinition] {
  def qualName: String

  def sourceFileName: String

}