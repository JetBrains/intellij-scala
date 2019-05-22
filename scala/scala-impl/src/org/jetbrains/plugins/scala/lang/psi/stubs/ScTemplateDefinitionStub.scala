package org.jetbrains.plugins.scala
package lang
package psi
package stubs

import com.intellij.psi.impl.java.stubs.PsiClassStub
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

/**
 * @author ilyas
 */
trait ScTemplateDefinitionStub[T <: ScTemplateDefinition]
  extends PsiClassStub[T]
    with ScMemberOrLocal
    with ScImplicitInstanceStub {

  def javaQualifiedName: String

  def isPackageObject: Boolean

  def isVisibleInJava: Boolean

  def isScriptFileClass: Boolean

  def isImplicitObject: Boolean

  def isImplicitClass: Boolean

  def additionalJavaName: Option[String]

  def javaName: String
}