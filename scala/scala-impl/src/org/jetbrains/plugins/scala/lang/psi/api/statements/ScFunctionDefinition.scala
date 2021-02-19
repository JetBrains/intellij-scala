package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import org.jetbrains.plugins.scala.lang.psi.api._


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
trait ScFunctionDefinitionBase extends ScFunctionBase with ScControlFlowOwnerBase { this: ScFunctionDefinition =>

  def body: Option[ScExpression]

  override def hasAssign: Boolean

  def assignment: Option[PsiElement]

  def returnUsages: Set[ScExpression] = ScFunctionDefinitionExt(this).returnUsages

  override def controlFlowScope: Option[ScalaPsiElement] = body

  @Cached(BlockModificationTracker(this), this)
  def getStaticTraitFunctionWrapper(cClass: PsiClassWrapper): StaticTraitScFunctionWrapper =
    new StaticTraitScFunctionWrapper(this, cClass)
}

abstract class ScFunctionDefinitionCompanion {
  object withBody {
    def unapply(fun: ScFunctionDefinition): Option[ScExpression] = Option(fun).flatMap(_.body)
  }
  object withName {
    def unapply(fun: ScFunctionDefinition): Option[String] = Some(fun.name)
  }
}