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

  override def isIrrefutableFor(t: Option[ScType]): Boolean =
    ScConstructorPattern.isIrrefutable(t, ref, subpatterns)

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