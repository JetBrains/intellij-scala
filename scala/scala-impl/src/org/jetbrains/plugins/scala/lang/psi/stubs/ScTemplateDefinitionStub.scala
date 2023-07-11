package org.jetbrains.plugins.scala.lang.psi.stubs

import com.intellij.psi.impl.java.stubs.PsiClassStub
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

trait ScTemplateDefinitionStub[T <: ScTemplateDefinition]
  extends ScTopLevelElementStub[T]
    with PsiClassStub[T]
    with ScMemberOrLocal[T]
    with ScImplicitStub[T]
    with ScGivenStub
    with ScEnumStub
    with ScEnumCaseStub {

  def javaQualifiedName: String

  def isPackageObject: Boolean

  def isVisibleInJava: Boolean

  def isImplicitObject: Boolean

  def additionalJavaName: Option[String]

  def javaName: String
}
