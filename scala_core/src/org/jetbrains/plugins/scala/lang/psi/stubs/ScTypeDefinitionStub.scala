package org.jetbrains.plugins.scala.lang.psi.stubs

import com.intellij.psi.impl.java.stubs.PsiClassStub
import com.intellij.psi.{PsiElement, PsiClass}
import com.intellij.util.io.StringRef
import com.intellij.psi.stubs.{StubElement, IStubElementType, NamedStub}
import api.toplevel.typedef.ScTypeDefinition

/**
 * @author ilyas
 */

trait ScTypeDefinitionStub extends PsiClassStub[ScTypeDefinition] with NamedStub[ScTypeDefinition] {

  def qualName: String

  def sourceFileName: String

  def getSourceFileName: String = sourceFileName

  def getQualifiedName: String = qualName

}