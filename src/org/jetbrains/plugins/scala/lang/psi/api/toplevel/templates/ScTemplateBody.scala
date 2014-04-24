package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package templates

import base.types.ScSelfTypeElement
import statements.{ScFunction, ScDeclaredElementsHolder, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import api.toplevel.typedef._
import expr.ScExpression
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.{PsiElement, ResolveState}

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:38:36
*/

trait ScTemplateBody extends ScalaPsiElement with ScControlFlowOwner {
  def members: Array[ScMember]

  def holders: Array[ScDeclaredElementsHolder]

  def functions: Array[ScFunction]

  def aliases: Array[ScTypeAlias]

  def typeDefinitions: Seq[ScTypeDefinition]

  def exprs: Array[ScExpression]

  def selfTypeElement: Option[ScSelfTypeElement]
}