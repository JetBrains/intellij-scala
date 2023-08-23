package org.jetbrains.plugins.scala.lang.psi.api.statements

import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, cached}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.{ScControlFlowOwner, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.light.{PsiClassWrapper, StaticTraitScFunctionWrapper}

trait ScFunctionDefinition extends ScFunction with ScControlFlowOwner with ScDefinitionWithAssignment {

  def body: Option[ScExpression]

  override def hasAssign: Boolean

  def returnUsages: Set[ScExpression] = ScFunctionDefinitionExt(this).returnUsages

  override def controlFlowScope: Option[ScalaPsiElement] = body

  def getStaticTraitFunctionWrapper(cClass: PsiClassWrapper): StaticTraitScFunctionWrapper = _getStaticTraitFunctionWrapper(cClass)

  private val _getStaticTraitFunctionWrapper = cached("getStaticTraitFunctionWrapper", BlockModificationTracker(this), (cClass: PsiClassWrapper) => {
    new StaticTraitScFunctionWrapper(this, cClass)
  })
}

object ScFunctionDefinition {
  object withBody {
    def unapply(fun: ScFunctionDefinition): Option[ScExpression] = Option(fun).flatMap(_.body)
  }
  object withName {
    def unapply(fun: ScFunctionDefinition): Option[String] = Some(fun.name)
  }
}