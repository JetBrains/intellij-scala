package org.jetbrains.plugins.scala
package lang
package psi
package stubs

import com.intellij.psi.impl.java.stubs.PsiClassStub
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

trait ScTemplateDefinitionStub[T <: ScTemplateDefinition]
  extends ScTopLevelElementStub[T]
    with PsiClassStub[T]
    with ScMemberOrLocal
    with ScImplicitStub
    with ScGivenStub {

  def javaQualifiedName: String

  def isPackageObject: Boolean

  def isVisibleInJava: Boolean

  def isScriptFileClass: Boolean

  def isImplicitObject: Boolean

  def additionalJavaName: Option[String]

  def javaName: String
}
