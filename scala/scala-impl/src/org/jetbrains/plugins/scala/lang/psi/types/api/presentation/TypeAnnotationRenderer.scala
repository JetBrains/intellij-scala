package org.jetbrains.plugins.scala.lang.psi.types.api.presentation

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.IndexNotReadyException
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.TypeAnnotationRenderer.ParameterTypeDecorateOptions
import org.jetbrains.plugins.scala.lang.psi.types.result.{TypeResult, Typeable}

class TypeAnnotationRenderer(
  typeRenderer: TypeRenderer,
  parameterTypeDecorateOptions: ParameterTypeDecorateOptions = ParameterTypeDecorateOptions.DecorateNone
) {

  def render(buffer: StringBuilder, typed: Typeable): Unit = {
    buffer.append(": ")
    renderWithoutColon(buffer, typed)
  }

  def renderWithoutColon(buffer: StringBuilder, typed: Typeable): Unit = {
    val typeText = renderType(typed)
    typed match {
      case param: ScParameter => decoratedParameterType(buffer, param, typeText)
      case _                  => buffer.append(typeText)
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
      case fun: ScFunction => fun.returnType
      case _               => typed.`type`()
    }

  private def decoratedParameterType(buffer: StringBuilder, param: ScParameter, typeText: String): Unit = {
    if (parameterTypeDecorateOptions.showByNameArrow && param.isCallByNameParameter) {
      buffer.append(ScalaPsiUtil.functionArrow(param.getProject))
      buffer.append(" ")
    }

    buffer.append(typeText)

    if (param.isRepeatedParameter)
      buffer.append("*")

    if (parameterTypeDecorateOptions.showDefaultValue && param.isDefaultParam) {
      buffer.append(" = ")
      param.getDefaultExpressionInSource match {
        case Some(expr) =>
          val text: String = expr.getText.replace(" /* compiled code */ ", "")
          val cutTo = 20
          buffer.append(text.substring(0, text.length.min(cutTo)))
          if (text.length > cutTo)
            buffer.append("...")
        case _ =>
          buffer.append("...")
      }
    }
  }
}

object TypeAnnotationRenderer {

  case class ParameterTypeDecorateOptions(
    showByNameArrow: Boolean,
    showDefaultValue: Boolean
  )

  //noinspection TypeAnnotation
  object ParameterTypeDecorateOptions {
    val DecorateAll = ParameterTypeDecorateOptions(showByNameArrow = true, showDefaultValue = true)
    val DecorateNone = ParameterTypeDecorateOptions(showByNameArrow = false, showDefaultValue = false)
  }
}
