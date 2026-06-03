package org.jetbrains.plugins.scala.externalLibraries.contextApplied

import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.ValueClassType

/**
 * @see [[ContextApplied.SyntheticElementsOwner]]
 * @see <a href="https://github.com/augustjune/context-applied">context-applied</a>
 */
object ContextAppliedUtil {
  private[this] def createSyntheticContextAppliedDef(
    name:              String,
    boundTypeElements: Seq[ScTypeElement],
    context:           ScalaPsiElement
  ): ScalaPsiElement = {
    val tpeText = boundTypeElements.reverse.map(_.getText + s"[$name]").mkString(" with ")
    ScalaPsiElementFactory.createMethodWithContext(s"def $name: $tpeText = ???", context, null)
  }

  /**
   * For each type parameter `T: Foo : Bar` with context bounds in [[typeParameters]]
   * generate synthetic definition `def T: Bar with Foo` in context [[context]].
   * Note: skip any type parameters, whose name is used as a [[context]] value parameter.
   */
  def createSyntheticElementsFor(
    context:        ScalaPsiElement,
    enclosingClass: PsiClass,
    parameters:     Seq[ScParameter],
    typeParameters: Seq[ScTypeParam]
  ): Seq[ScalaPsiElement] =
    if (context.contextAppliedEnabled && !ValueClassType.isValueClass(enclosingClass)) {
      val parameterNames = parameters.map(_.name)

      val paramsWithContextBounds = typeParameters.collect {
        case tp if !parameterNames.contains(tp.name) && tp.contextBounds.nonEmpty =>
          tp.name -> tp.contextBounds.map(_.typeElement)
      }

      paramsWithContextBounds.map {
        case (name, bounds) =>
          createSyntheticContextAppliedDef(name, bounds, context)
      }
    } else Nil
}
