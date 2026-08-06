package org.jetbrains.plugins.scala.lang.psi.types.api.presentation

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.IndexNotReadyException
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScGivenDefinition
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.TypeAnnotationRenderer.ParameterTypeDecorator
import org.jetbrains.plugins.scala.lang.psi.types.result.{TypeResult, Typeable}

class TypeAnnotationRenderer(
  typeRenderer: TypeRenderer,
  parameterTypeDecorator: ParameterTypeDecorator = ParameterTypeDecorator.DecorateNone
) {

  def render(buffer: StringBuilder, typed: Typeable): Unit = {
    buffer.append(": ")
    renderWithoutColon(buffer, typed)
  }

  def renderWithoutColon(buffer: StringBuilder, typed: Typeable): Unit = {
    val typeText = renderType(typed)
    typed match {
      case param: ScParameter =>
        decoratedParameterType(buffer, param, typeText)
      case _                  =>
        buffer.append(typeText)
    }
  }

  private def renderType(typed: Typeable): String =
    try {
      val typ = typeAnnotation(typed).getOrAny
      typeRenderer.render(typ)
    }
    catch {
      // NOTE: before refactoring some usage places intercepted IndexNotReadyException:
      // catch {
      //   case _: IndexNotReadyException =>
      //     "NoTypeInfo"
      // }
      // usages: (via ScalaPsiPresentationUtils.methodPresentableText)
      //     - org.jetbrains.plugins.scala.overrideImplement.ScMethodMember
      //     - org.jetbrains.plugins.scala.lang.refactoring.ui.ScalaMemberInfoBase
      //
      // Not sure whether that was a good approach cause the code shouldn't have been called during indexing at all
      // So, I make some compromise:
      //     - report IndexNotReadyException exception only for internal users to catch the places where the code is used improperly
      //     - recover and show dummy type for most of the users not to annoy them much in case of exceptions
      case _: IndexNotReadyException if !ApplicationManager.getApplication.isInternal =>
        "NoTypeInfo"
    }

  private def typeAnnotation(typed: Typeable): TypeResult =
    typed match {

      case givenDef: ScGivenDefinition => givenDef.givenType()
      case fun: ScFunction             => fun.returnType
      case _                           => typed.`type`()
    }

  private def decoratedParameterType(buffer: StringBuilder, param: ScParameter, typeText: String): Unit = {
    parameterTypeDecorator.decorate(buffer, param) {
      buffer.append(typeText)

      if (param.isRepeatedParameter)
        buffer.append("*")
    }
  }
}

object TypeAnnotationRenderer {

  class ParameterTypeDecorator(showByNameArrow: Boolean, showDefaultValue: Boolean) {
    final def decorate(buffer: StringBuilder, param: ScParameter)(action: => Unit): Unit = {
      if (showByNameArrow && param.isCallByNameParameter) {
        buffer.append(ScalaPsiUtil.functionArrow(param.getProject))
        buffer.append(" ")
      }

      action

      if (showDefaultValue && param.isDefaultParam) {
        buffer.append(" = ")
        renderDefaultValue(buffer, param)
      }
    }

    protected def renderDefaultValue(buffer: StringBuilder, param: ScParameter): Unit = {}
  }

  object ParameterTypeDecorator {
    private val Ellipsis = '…'

    val DecorateNone: ParameterTypeDecorator =
      new ParameterTypeDecorator(showByNameArrow = false, showDefaultValue = false)

    val DecorateAll: ParameterTypeDecorator =
      new ParameterTypeDecorator(showByNameArrow = true, showDefaultValue = true) {
        override protected def renderDefaultValue(buffer: StringBuilder, param: ScParameter): Unit = {
          param.getDefaultExpressionInSource match {
            case Some(expr) =>
              val text: String = expr.getText.replace(" /* compiled code */ ", "")
              val cutTo = 20
              buffer.append(text.substring(0, text.length.min(cutTo)))
              if (text.length > cutTo)
                buffer.append(Ellipsis)
            case _ =>
              buffer.append(Ellipsis)
          }
        }
      }

    val DecorateAllMinimized: ParameterTypeDecorator =
      new ParameterTypeDecorator(showByNameArrow = true, showDefaultValue = true) {
        override protected def renderDefaultValue(buffer: StringBuilder, param: ScParameter): Unit =
          buffer.append(Ellipsis)
      }
  }
}
