package org.jetbrains.plugins.scala
package lang
package psi
package stubs

import com.intellij.psi.impl.java.stubs.PsiClassStub
import com.intellij.psi.{PsiElement, PsiClass}
import com.intellij.util.io.StringRef
import com.intellij.psi.stubs.{StubElement, IStubElementType, NamedStub}
import api.toplevel.typedef.{ScTemplateDefinition, ScTypeDefinition}

/**
 * @author ilyas
 */

trait ScTemplateDefinitionStub extends PsiClassStub[ScTemplateDefinition] {

  def qualName: String

  def sourceFileName: String

  def getSourceFileName: String = sourceFileName

  def getQualifiedName: String = qualName

  def isPackageObject: Boolean

  /**
   * Only method names without values and variables.
   * @return method names
   */
  def methodNames: Array[String]
}