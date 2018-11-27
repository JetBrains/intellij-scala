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
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject}
import org.jetbrains.plugins.scala.lang.psi.impl.base.types.ScSimpleTypeElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, Nothing, TypeParameterType, UndefinedType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
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
                      substitutor(params(i).`type`()
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
    import ScSubstitutor.bind
    ref.bind() match {
      case Some(r) =>
        r.element match {
          //todo: remove all classes?
          case td: ScClass if td.typeParameters.nonEmpty =>
            val refType: ScType = ScSimpleTypeElementImpl.
              calculateReferenceType(ref).getOrElse(ScalaType.designator(td))
            val newSubst = {
              val clazzType = ScParameterizedType(refType, td.getTypeParameters.map(UndefinedType(_)))
              val toAnySubst = bind(td.typeParameters)(Function.const(Any))

              this.expectedType.flatMap {
                clazzType.conformanceSubstitutor(_)
              }.fold(toAnySubst) {
                _.followed(toAnySubst)
              }
            }
            Right(ScParameterizedType(refType, td.getTypeParameters.map(tp => newSubst(TypeParameterType(tp)))))
          case td: ScClass => Right(ScalaType.designator(td))
          case obj: ScObject => Right(ScalaType.designator(obj))
          case fun: ScFunction /*It's unapply method*/ if (fun.name == "unapply" || fun.name == "unapplySeq") &&
                  fun.parameters.count(!_.isImplicitParameter) == 1 =>
            val substitutor = r.substitutor
            val typeParams = fun.typeParameters
            val subst =
              if (typeParams.isEmpty) substitutor
              else {
                val maybeSubstitutor = for {
                  Typeable(parameterType) <- fun.parameters.headOption
                  functionType = bind(typeParams)(UndefinedType(_)).apply(parameterType)

                  expectedType <- this.expectedType
                  newSubstitutor <- functionType.conformanceSubstitutor(expectedType)
                } yield newSubstitutor

                maybeSubstitutor.fold(substitutor) {
                  _.followed(substitutor)
                }.followed {
                  bind(typeParams)(_.upperBound.getOrAny)
                }
              }
            fun.paramClauses.clauses.head.parameters.head.`type`().map(subst)
          case _ => Right(Nothing)
        }
      case _ => Failure("Cannot resolve symbol")
    }
  }

}