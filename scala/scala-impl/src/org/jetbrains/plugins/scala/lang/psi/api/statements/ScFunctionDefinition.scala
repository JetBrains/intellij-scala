package org.jetbrains.plugins.scala.lang.psi.api.statements

import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, cached}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.api.{ScControlFlowOwner, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.light.{PsiClassWrapper, StaticTraitScFunctionWrapper}
import org.jetbrains.plugins.scala.lang.psi.types.ValueClassType.extendsAnyVal

trait ScFunctionDefinition extends ScFunction with ScControlFlowOwner with ScDefinitionWithAssignment {

  def body: Option[ScExpression]

  override def hasAssign: Boolean

  def returnUsages: Set[ScExpression] = ScFunctionDefinitionExt(this).returnUsages

  override def controlFlowScope: Option[ScalaPsiElement] = body

  /**
   * Note that this method is only called in non-Scala contexts. For Scala contexts super#name is used.
   *
   * The below represents the special case for public function definitions of implicit classes that extend AnyVal -- a
   * common approach for Scala 2 extension methods. Such functions, from the perspective of non-Scala JVM languages,
   * have `$extension` appended to their name. See https://docs.scala-lang.org/overviews/core/value-classes.html#extension-methods.
   */
  override def getName: String =
    if (this.isPrivate || this.isProtected) {
      super.getName
    } else {
      val maybeAnyValClass =
        Option(containingClass).collect { case c: ScClass if c.getModifierList.isImplicit && extendsAnyVal(c) => c }

      val suffix = maybeAnyValClass.map(_ => "$extension").getOrElse("")

      super.getName + suffix
    }

  def getStaticTraitFunctionWrapper(cClass: PsiClassWrapper): StaticTraitScFunctionWrapper = _getStaticTraitFunctionWrapper(cClass)

  private val _getStaticTraitFunctionWrapper = cached("ScFunctionDefinition.getStaticTraitFunctionWrapper", BlockModificationTracker(this), (cClass: PsiClassWrapper) => {
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