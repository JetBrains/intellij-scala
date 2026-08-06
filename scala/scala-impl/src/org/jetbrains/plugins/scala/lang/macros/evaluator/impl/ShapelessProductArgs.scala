package org.jetbrains.plugins.scala.lang.macros.evaluator.impl

import org.jetbrains.plugins.scala.lang.macros.evaluator.{MacroImpl, MacroInvocationContext, ScalaMacroExpandable}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._

/**
  * From ProductArgs documentation:
  *
  * Trait supporting mapping dynamic argument lists to HList arguments.
  *
  * Mixing in this trait enables method applications of the form,
  *
  * {{{
  * lhs.method(23, "foo", true)
  * }}}
  *
  * to be rewritten as,
  *
  * {{{
  * lhs.methodProduct(23 :: "foo" :: true)
  * }}}
  *
  * ie. the arguments are rewritten as HList elements and the application is
  * rewritten to an application of an implementing method (identified by the
  * "Product" suffix) which accepts a single HList argument.
  *
  */
object ShapelessProductArgs extends ScalaMacroExpandable with ShapelessUtils {

  override val boundMacro: Seq[MacroImpl] = MacroImpl("applyDynamic", "shapeless.ProductArgs") :: Nil

  override def expandMacro(macros: ScFunction, context: MacroInvocationContext): Option[ScExpression] = {
    val MacroInvocationContext(mc, resolveResult) = context

    val nameArg = resolveResult.nameArgForDynamic match {
      case Some(name) => name
      case _ =>
        //todo: support explicit invocation like x.applyDynamic("methodName")(args)
        return None
    }

    val argTypes = mc.argumentExpressions.map(_.`type`().getOrAny)
    val productType = hlistText(argTypes)

    val exprOfProductType = s"null: $productType" //to avoid type inference in dummy elements

    val invokedExprText = mc.getEffectiveInvokedExpr match {
      case ref: ScReferenceExpression if ref.refName == nameArg =>
        ref.qualifier.map(_.getText).getOrElse("")
      case expr => expr.getText // foo() may be interpreted as foo.applyDynamic("apply")()
    }

    val invokedWithDot = if (invokedExprText.isEmpty) "" else s"$invokedExprText."

    val newCallText = s"$invokedWithDot${nameArg}Product($exprOfProductType)"

    Option(createExpressionWithContextFromText(newCallText, mc, null))
  }
}
