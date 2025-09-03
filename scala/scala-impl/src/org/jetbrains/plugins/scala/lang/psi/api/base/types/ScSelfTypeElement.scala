package org.jetbrains.plugins.scala.lang.psi.api.base
package types

import org.jetbrains.plugins.scala.lang.ir.typeTree.TypeTreeHolder
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypedDefinition}

trait ScSelfTypeElement extends ScNamedElement with ScTypedDefinition {
  def typePsiElement: Option[ScTypeElement]
  def typeTreeHolder: Option[TypeTreeHolder]

  def classNames: Array[String]
}