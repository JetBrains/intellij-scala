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
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject}
import org.jetbrains.plugins.scala.lang.psi.impl.base.types.ScSimpleTypeElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, Success, TypeResult, TypingContext}
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
    if (t == None) return false
    ref.bind() match {
      case Some(ScalaResolveResult(clazz: ScClass, _)) if clazz.isCase =>
        ScType.extractClassType(t.get, Some(clazz.getProject)) match {
          case Some((clazz2: ScClass, substitutor: ScSubstitutor)) if clazz2 == clazz =>
            clazz.constructor match {
              case Some(constr: ScPrimaryConstructor) =>
                val clauses = constr.parameterList.clauses
                if (clauses.length == 0) subpatterns.length == 0
                else {
                  val params = clauses(0).parameters
                  if (params.length == 0) return subpatterns.length == 0
                  if (params.length != subpatterns.length) return false  //todo: repeated parameters?
                  var i = 0
                  while (i < subpatterns.length) {
                    val tp = {
                      substitutor.subst(params(i).getType(TypingContext.empty).
                              getOrElse(return false))
                    }
                    if (!subpatterns.apply(i).isIrrefutableFor(Some(tp))) {
                      return false
                    }
                    i = i + 1
                  }
                  true
                }
              case _ => subpatterns.length == 0
            }
          case _ => false
        }
      case _ => false
    }
  }

  override def getType(ctx: TypingContext): TypeResult[ScType] = {
    ref.bind() match {
      case Some(r) =>
        r.element match {
          //todo: remove all classes?
          case td: ScClass if td.typeParameters.length > 0 =>
            val refType: ScType = ScSimpleTypeElementImpl.
                    calculateReferenceType(ref, shapesOnly = false).getOrElse(ScType.designator(td))
            val newSubst = {
              val clazzType = ScParameterizedType(refType, td.getTypeParameters.map(tp =>
                ScUndefinedType(tp match {case tp: ScTypeParam => new ScTypeParameterType(tp, r.substitutor)
                case _ => new ScTypeParameterType(tp, r.substitutor)})))
              val emptySubst: ScSubstitutor = new ScSubstitutor(Map(td.typeParameters.map(tp =>
                ((tp.name, ScalaPsiUtil.getPsiElementId(tp)), Any)): _*), Map.empty, None)
              expectedType match {
                case Some(tp) =>
                  val t = Conformance.conforms(tp, clazzType)
                  if (t) {
                    val undefSubst = Conformance.undefinedSubst(tp, clazzType)
                    undefSubst.getSubstitutor match {
                      case Some(subst) => subst followed emptySubst
                      case _ => emptySubst
                    }
                  } else emptySubst
                case _ => emptySubst
              }
            }
            Success(ScParameterizedType(refType, collection.immutable.Seq(td.getTypeParameters.map({
              tp => newSubst.subst(ScalaPsiManager.typeVariable(tp))
            }).toSeq : _*)), Some(this))
          case td: ScClass => Success(ScType.designator(td), Some(this))
          case obj: ScObject => Success(ScType.designator(obj), Some(this))
          case fun: ScFunction /*It's unapply method*/ if (fun.name == "unapply" || fun.name == "unapplySeq") &&
                  fun.parameters.length == 1 =>
            val substitutor = r.substitutor
            val subst = if (fun.typeParameters.length == 0) substitutor else {
              val undefSubst: ScSubstitutor = fun.typeParameters.foldLeft(ScSubstitutor.empty)((s, p) =>
                s.bindT((p.name, ScalaPsiUtil.getPsiElementId(p)), ScUndefinedType(new ScTypeParameterType(p,
                  substitutor))))
              val emptySubst: ScSubstitutor = fun.typeParameters.foldLeft(ScSubstitutor.empty)((s, p) =>
                s.bindT((p.name, ScalaPsiUtil.getPsiElementId(p)), p.upperBound.getOrAny))
              val emptyRes = substitutor followed emptySubst
              val result = fun.parameters(0).getType(TypingContext.empty)
              if (result.isEmpty) emptyRes
              else {
                val funType = undefSubst.subst(result.get)
                expectedType match {
                  case Some(tp) =>
                    val t = Conformance.conforms(tp, funType)
                    if (t) {
                      val undefSubst = Conformance.undefinedSubst(tp, funType)
                      undefSubst.getSubstitutor match {
                        case Some(newSubst) => newSubst followed substitutor followed emptySubst
                        case _ => emptyRes
                      }
                    } else emptyRes
                  case _ => emptyRes
                }
              }
            }
            fun.paramClauses.clauses.apply(0).parameters.apply(0).getType(TypingContext.empty).map(subst.subst)
          case _ => Success(Nothing, Some(this))
        }
      case _ => Failure("Cannot resolve symbol", Some(this))
    }
  }

}