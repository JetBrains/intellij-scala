package org.jetbrains.plugins.scala.lang.psi.api.expr

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScPathElement, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.types.ScType

trait ScSuperReference extends ScExpression with ScPathElement {
  /**
    * @return is reference in decompiled file from Self type class
    */
  def isHardCoded: Boolean

  //type of M for super[M]
  def staticSuper: Option[ScType]

  //name of super type as written in code
  def staticSuperName: String

  //for A.super or simply super
  def drvTemplate: Option[ScTemplateDefinition]

  def reference: Option[ScStableCodeReference] = findChild[ScStableCodeReference]

  def identifier: Option[PsiElement]

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitSuperReference(this)
  }
}
