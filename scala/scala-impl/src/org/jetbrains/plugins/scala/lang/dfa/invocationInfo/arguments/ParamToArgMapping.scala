package org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments

import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.InvokedElement
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments.Argument.ProperArgument
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter

object ParamToArgMapping {

  def generateParamToArgMapping(invokedElement: Option[InvokedElement],
                                properArguments: List[List[Argument]]): List[Option[Int]] = {
    val indexedArgs = properArguments.flatten.zipWithIndex
    val paramMappings = invokedElement.map { element =>
      element.psiElement match {
        case function: ScFunction => function.parameters
          .map(param => getIndexOfMappingArgument(indexedArgs, _.psiParam.contains(param)))
        case synthetic: ScSyntheticFunction => synthetic.paramClauses.flatten
          .map(param => getIndexOfMappingArgument(indexedArgs, _ == param))
        case psiMethod: PsiMethod => psiMethod.getParameterList.getParameters
          .map(param => getIndexOfMappingArgument(indexedArgs, _.psiParam.contains(param))).toList
        case _ => Nil
      }
    }

    paramMappings.map(_.toList).getOrElse(Nil)
  }

  private def getIndexOfMappingArgument(indexedArgs: List[(Argument, Int)], paramEquals: Parameter => Boolean): Option[Int] = {
    val matchingArgs = indexedArgs.filter {
      case (arg, _) => arg.kind match {
        case ProperArgument(mapping) => paramEquals(mapping)
        case _ => false
      }
    }

    matchingArgs.map(_._2).headOption
  }
}
