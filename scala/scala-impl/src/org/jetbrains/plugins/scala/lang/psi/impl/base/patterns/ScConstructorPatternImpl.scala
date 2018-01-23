package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package patterns

import _root_.org.jetbrains.plugins.scala.lang.psi.types._
import com.intellij.lang.ASTNode
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.PsiTypeParameterExt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject}
import org.jetbrains.plugins.scala.lang.psi.impl.base.types.ScSimpleTypeElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, Nothing, TypeParameterType, UndefinedType}
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/** 
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

class ScConstructorPatternImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScConstructorPattern {
  override def accept(visitor: PsiElementVisitor): Unit = {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  override def toString: String = "ConstructorPattern"

  override def subpatterns: Seq[ScPattern] = if (args != null) args.patterns else Seq.empty

  override def isIrrefutableFor(t: Option[ScType]): Boolean = {
    if (t.isEmpty) return false
    ref.bind() match {
      case Some(ScalaResolveResult(clazz: ScClass, _)) if clazz.isCase =>
        t.get.extractClassType match {
          case Some((clazz2: ScClass, substitutor: ScSubstitutor)) if clazz2 == clazz =>
            clazz.constructor match {
              case Some(constr: ScPrimaryConstructor) =>
                val clauses = constr.parameterList.clauses
                if (clauses.isEmpty) subpatterns.isEmpty
                else {
                  val params = clauses.head.parameters
                  if (params.isEmpty) return subpatterns.isEmpty
                  if (params.length != subpatterns.length) return false  //todo: repeated parameters?
                  var i = 0
                  while (i < subpatterns.length) {
                    val tp = {
                      substitutor.subst(params(i).`type`()
                        .getOrElse(return false))
                    }
                    if (!subpatterns.apply(i).isIrrefutableFor(Some(tp))) {
                      return false
                    }
                    i = i + 1
                  }
                  true
                }
              case _ => subpatterns.isEmpty
            }
          case _ => false
        }
      case _ => false
    }
  }

  override def `type`(): TypeResult = {
    ref.bind() match {
      case Some(r) =>
        r.element match {
          //todo: remove all classes?
          case td: ScClass if td.typeParameters.nonEmpty =>
            val refType: ScType = ScSimpleTypeElementImpl.
              calculateReferenceType(ref, shapesOnly = false).getOrElse(ScalaType.designator(td))
            val newSubst = {
              val clazzType = ScParameterizedType(refType, td.getTypeParameters.map(tp =>
                UndefinedType(tp, r.substitutor)))
              val toAnySubst: ScSubstitutor = ScSubstitutor.bind(td.typeParameters)(Function.const(Any))
              this.expectedType match {
                case Some(tp) =>
                  val conformance = clazzType.conforms(tp, ScUndefinedSubstitutor())
                  if (conformance._1) {
                    conformance._2.getSubstitutor match {
                      case Some(subst) => subst.followed(toAnySubst)
                      case _ => toAnySubst
                    }
                  } else toAnySubst
                case _ => toAnySubst
              }
            }
            Right(ScParameterizedType(refType, td.getTypeParameters.map(tp => newSubst.subst(TypeParameterType(tp)))))
          case td: ScClass => Right(ScalaType.designator(td))
          case obj: ScObject => Right(ScalaType.designator(obj))
          case fun: ScFunction /*It's unapply method*/ if (fun.name == "unapply" || fun.name == "unapplySeq") &&
                  fun.parameters.count(!_.isImplicitParameter) == 1 =>
            val substitutor = r.substitutor
            val typeParams = fun.typeParameters
            val subst =
              if (typeParams.isEmpty) substitutor
              else {
                val undefSubst: ScSubstitutor = ScSubstitutor.bind(typeParams)(UndefinedType(_, substitutor))
                val toUpperSubst: ScSubstitutor = ScSubstitutor.bind(typeParams)(_.upperBound.getOrAny)

                val emptyRes = substitutor.followed(toUpperSubst)
                fun.parameters.head.`type`() match {
                  case Right(result) =>
                    val funType = undefSubst.subst(result)
                    this.expectedType match {
                      case Some(tp) =>
                        val conformance = funType.conforms(tp, ScUndefinedSubstitutor())
                        if (conformance._1) {
                          conformance._2.getSubstitutor match {
                            case Some(newSubst) => newSubst followed substitutor followed toUpperSubst
                            case _ => emptyRes
                          }
                        } else emptyRes
                      case _ => emptyRes
                    }
                  case Left(_) => emptyRes
                }
              }
            fun.paramClauses.clauses.head.parameters.head.`type`().map(subst.subst)
          case _ => Right(Nothing)
        }
      case _ => Failure("Cannot resolve symbol")
    }
  }

}