package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiMemberExt}
import org.jetbrains.plugins.scala.lang.parser.ErrMsg
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScGivenAliasDefinition, ScTrait}

object ScGivenAliasDefinitionAnnotator extends ElementAnnotator[ScGivenAliasDefinition] {
  override def annotate(
    element: ScGivenAliasDefinition,
    typeAware: Boolean
  )(implicit
    holder: ScalaAnnotationHolder
  ): Unit = {
    val ownerCls = element.getContainingClass

    if (!ownerCls.is[ScTrait] && isDeferredGiven(element))
      holder.createErrorAnnotation(element, ErrMsg("deferred.inside.class"))
  }

  private[annotator] def isDeferredGiven(gvn: ScGivenAliasDefinition): Boolean =
    (
      for {
        refExpr          <- gvn.body.collect { case ref: ScReferenceExpression => ref }
        srr              <- refExpr.bind()
        deferredFunction <- srr.element.asOptionOf[ScFunctionDefinition]
        fqn              <- deferredFunction.qualifiedNameOpt
      } yield fqn == "scala.compiletime.deferred"
    ).getOrElse(false)

}
