package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package patterns

import _root_.org.jetbrains.plugins.scala.lang.psi.types._
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScPrimaryConstructor, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject}
import org.jetbrains.plugins.scala.lang.psi.impl.base.types.ScSimpleTypeElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, Nothing, TypeParameterType, UndefinedType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._

/** 
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

class ScConstructorPatternImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScPatternImpl with ScConstructorPattern {

  override def toString: String = "ConstructorPattern"

  override def subpatterns: Seq[ScPattern] = if (args != null) args.patterns else Seq.empty

  override def isIrrefutableFor(t: Option[ScType]): Boolean =
    ScConstructorPatternImpl.isIrrefutable(t, ref, subpatterns)

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
              val clazzType = ScParameterizedType(refType, td.getTypeParameters.map(UndefinedType(_)).toSeq)
              val toAnySubst = bind(td.typeParameters)(Function.const(Any))

              this.expectedType.flatMap {
                clazzType.conformanceSubstitutor(_)
              }.fold(toAnySubst) {
                _.followed(toAnySubst)
              }
            }
            Right(ScParameterizedType(refType, td.getTypeParameters.map(tp => newSubst(TypeParameterType(tp))).toSeq))
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
      case _ => Failure(ScalaBundle.message("cannot.resolve.unknown.symbol"))
    }
  }

}

object ScConstructorPatternImpl {
  def isIrrefutable(typeOpt: Option[ScType], ref: ScStableCodeReference, subpatterns: Seq[ScPattern]): Boolean = {
    val typedParamsOpt = for {
      matchedType <- typeOpt
      unapplyMethod <- resolveUnapplyMethodFromReference(ref)
      caseClass <- Option(unapplyMethod.syntheticCaseClass)
      (clazz: ScClass, substitutor) <- matchedType.extractClassType
      if clazz.isCase && clazz == caseClass
      constr <- clazz.constructor
    } yield getTypedParametersOfPrimaryConstructor(constr, substitutor)

    typedParamsOpt exists {
      // check if the patterns are irrefutable for the parameter types
      typedParams =>
        subpatterns.corresponds(typedParams) {
          case (pattern, (param, paramType)) =>
            if (param.isRepeatedParameter) extractsRepeatedParameterIrrefutably(pattern)
            else pattern.isIrrefutableFor(paramType)
        }
    }
  }

  private def resolveUnapplyMethodFromReference(ref: ScStableCodeReference): Option[ScFunction] = for {
    resolveResult <- ref.bind()
    maybeUnapplyMethod = Option(resolveResult.getElement)
    unapplyMethod <- maybeUnapplyMethod.collect { case method: ScFunction if method.isUnapplyMethod => method }
  } yield unapplyMethod

  private def getTypedParametersOfPrimaryConstructor(constr: ScPrimaryConstructor, substitutor: ScSubstitutor): Seq[(ScParameter, Option[ScType])] = {
    val params = constr.parameterList.clauses.headOption.map(_.parameters).getOrElse(Seq.empty)
    for {
      param <- params
      paramType = param.`type`().toOption.map(substitutor)
    } yield param -> paramType
  }

  private def extractsRepeatedParameterIrrefutably(pattern: ScPattern): Boolean = {
    pattern match {
      case _: ScSeqWildcardPattern => true
      case p: ScNamingPattern => extractsRepeatedParameterIrrefutably(p.named)
      case p: ScParenthesisedPattern => p.innerElement.exists(extractsRepeatedParameterIrrefutably)
      case _ => false
    }
  }
}