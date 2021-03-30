package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import com.intellij.psi._
import org.jetbrains.plugins.scala.caches.BlockModificationTracker
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.light.{PsiClassWrapper, StaticTraitScFunctionWrapper}
import org.jetbrains.plugins.scala.macroAnnotations.Cached

/**
 * @author Alexander Podkhalyuzin
 *         Date: 22.02.2008
 *         Time: 9:49:36
 */
trait ScFunctionDefinition extends ScFunction with ScControlFlowOwner with ScDefinitionWithAssignment {

  def body: Option[ScExpression]

  override def hasAssign: Boolean

  def returnUsages: Set[ScExpression] = ScFunctionDefinitionExt(this).returnUsages

  override def controlFlowScope: Option[ScalaPsiElement] = body

  @Cached(BlockModificationTracker(this), this)
  def getStaticTraitFunctionWrapper(cClass: PsiClassWrapper): StaticTraitScFunctionWrapper =
    new StaticTraitScFunctionWrapper(this, cClass)
}

object ScFunctionDefinition {
  object withBody {
    def unapply(fun: ScFunctionDefinition): Option[ScExpression] = Option(fun).flatMap(_.body)
  }
  object withName {
    def unapply(fun: ScFunctionDefinition): Option[String] = Some(fun.name)
  }
}