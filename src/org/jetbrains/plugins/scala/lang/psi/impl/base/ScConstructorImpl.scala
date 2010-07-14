package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.psi.api.base._
import resolve.processor.MethodResolveProcessor
import psi.types.Compatibility.Expression
import resolve.{StdKinds, ScalaResolveResult}
import com.intellij.psi.{ResolveState, PsiClass, PsiMethod}
import psi.types.result.{TypingContext, Success}
import psi.types.{ScType, ScSubstitutor}
import api.base.types.{ScTypeElement, ScSimpleTypeElement}

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScConstructorImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScConstructor {

  override def toString: String = "Constructor"

  //todo cache
  def resolveConstructorMethod: Array[ScalaResolveResult] = {
    typeElement match {
      case element: ScTypeElement => {
        element.getType(TypingContext.empty) match {
          case Success(tp: ScType, _) => ScType.extractClassType(tp) match {
            case Some((clazz, subst)) => {
              val processor = new MethodResolveProcessor(element, "this",
                arguments.toList.map(_.exprs.map(new Expression(_))), Seq.empty, StdKinds.methodsOnly,
                constructorResolve = true)
              val state: ResolveState = ResolveState.initial.put(ScSubstitutor.key, subst)
              clazz.getConstructors.foreach(processor.execute(_, state))
              processor.candidates
            }
            case _ => Array.empty
          }
          case _ => Array.empty
        }
      }
      case _ => return Array.empty //todo: parameterized type element
    }
  }
}