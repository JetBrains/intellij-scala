package org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.arguments

import com.intellij.psi.{PsiMethod, PsiParameter}
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.InvokedElement
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.arguments.Argument.ProperArgument
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction

object ParamToArgMapping {

  def generateParamToArgMapping(invokedElement: Option[InvokedElement],
                                properArguments: List[List[Argument]]): List[Option[Int]] = {
    val indexedArgs = properArguments.flatten.zipWithIndex
    val paramMappings = invokedElement.map { element =>
      element.psiElement match {
        case function: ScFunction => function.parameters.map(getIndexOfMappingArgument(indexedArgs, _))
        case synthetic: ScSyntheticFunction => synthetic.paramClauses.flatten.map(param => param.psiParam match {
          case None => None
          case Some(psiParam) => getIndexOfMappingArgument(indexedArgs, psiParam)
        })
        case psiMethod: PsiMethod => psiMethod.getParameterList.getParameters.map(getIndexOfMappingArgument(indexedArgs, _)).toList
      }
    }

    paramMappings.map(_.toList).getOrElse(Nil)
  }

  private def getIndexOfMappingArgument(indexedArgs: List[(Argument, Int)], parameter: PsiParameter): Option[Int] = {
    val matchingArgs = indexedArgs.filter {
      case (arg, _) => arg.kind match {
        case ProperArgument(mapping) => mapping.psiParam.contains(parameter)
        case _ => false
      }
    }

    matchingArgs.map(_._2).headOption
  }
}
