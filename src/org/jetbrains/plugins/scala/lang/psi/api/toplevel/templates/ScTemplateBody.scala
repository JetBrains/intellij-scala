package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package templates

import base.types.ScSelfTypeElement
import statements.{ScFunction, ScDeclaredElementsHolder, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import toplevel.typedef.ScMember
import api.toplevel.typedef._
/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:38:36
*/

trait ScTemplateBody extends ScalaPsiElement {
  def members: Array[ScMember]

  def holders: Array[ScDeclaredElementsHolder]

  def functions: Array[ScFunction]

  def aliases: Array[ScTypeAlias]

  def typeDefinitions: Seq[ScTypeDefinition]

  def selfTypeElement: Option[ScSelfTypeElement]
}