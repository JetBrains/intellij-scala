package org.jetbrains.plugins.scala
package lang
package psi
package stubs

import com.intellij.psi.impl.java.stubs.PsiClassStub
import api.toplevel.typedef.ScTemplateDefinition

/**
 * @author ilyas
 */

trait ScTemplateDefinitionStub extends PsiClassStub[ScTemplateDefinition] with ScMemberOrLocal {

  def qualName: String

  def javaQualName: String

  def sourceFileName: String

  def getSourceFileName: String = sourceFileName

  def getQualifiedName: String = qualName

  def isPackageObject: Boolean

  def isVisibleInJava: Boolean

  def isScriptFileClass: Boolean

  def isImplicitObject: Boolean

  def isImplicitClass: Boolean

  /**
   * Only method names without values and variables.
   * @return method names
   */
  def methodNames: Array[String]

  def additionalJavaNames: Array[String]

  def javaName: String
}