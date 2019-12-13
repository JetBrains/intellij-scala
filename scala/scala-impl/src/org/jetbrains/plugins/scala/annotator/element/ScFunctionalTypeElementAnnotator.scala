package org.jetbrains.plugins.scala
package annotator
package element

import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScFunctionalTypeElement, ScParenthesisedTypeElement, ScTupleTypeElement}

private[annotator] object ScFunctionalTypeElementAnnotator
    extends ElementAnnotator[ScFunctionalTypeElement] {
  override def annotate(
    element:   ScFunctionalTypeElement,
    typeAware: Boolean
  )(implicit
    holder: ScalaAnnotationHolder
  ): Unit = element.paramTypeElement match {
    case ScParenthesisedTypeElement(arg) if arg.isRepeated =>
      errorIf2_13(arg, ScalaBundle.message("repeated.param.non.method"))
    case ScTupleTypeElement(args @ _*) =>
      args.collect {
        case arg if arg.isRepeated =>
          errorIf2_13(arg, ScalaBundle.message("repeated.param.non.method"))
      }
    case _ => ()
  }
}
