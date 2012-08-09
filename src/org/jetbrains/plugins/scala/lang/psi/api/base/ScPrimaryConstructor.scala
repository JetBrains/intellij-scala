package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import com.intellij.psi.PsiMethod
import psi.types.nonvalue.{ScMethodType, ScTypePolymorphicType, TypeParameter}
import psi.types._
import impl.ScalaPsiElementFactory
import toplevel.ScTypeParametersOwner
import caches.CachesUtil
import com.intellij.psi.util.PsiModificationTracker

/**
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

trait ScPrimaryConstructor extends ScMember with ScMethodLike {
  /**
   *  @return has annotation
   */
  def hasAnnotation: Boolean

  def hasMalformedSignature = parameterList.clauses.exists {
    _.parameters.dropRight(1).exists(_.isRepeatedParameter)
  }
  
  /**
   *  @return has access modifier
   */
  def hasModifier: Boolean

  def getClassNameText: String

  def parameterList: ScParameters

  def parameters : Seq[ScClassParameter] = parameterList.clauses.flatMap(_.unsafeClassParameters)

  /**
   * return only parameters, which are additionally members.
   */
  def valueParameters: Seq[ScClassParameter] = parameters.filter((p: ScClassParameter) => p.isVal || p.isVar)

  def effectiveParameterClauses: Seq[ScParameterClause] = {
    CachesUtil.get(this, CachesUtil.EFFECTIVE_PARAMETER_CLAUSE,
      new CachesUtil.MyProvider(this, (p: ScPrimaryConstructor) => p.effectiveParametersInner)
      (PsiModificationTracker.MODIFICATION_COUNT))
  }

  /**
    * All classes must have one non-implicit parameter list. If this is not declared in in the code,
    * it is assumed by the compiler.
    *
    * In addition, view and context bounds generate an additional implicit parameter section.
    */
  private def effectiveParametersInner: Seq[ScParameterClause] = {
    def emptyParameterList: ScParameterClause =
      ScalaPsiElementFactory.createEmptyClassParamClauseWithContext(getManager, parameterList)
    val clausesWithInitialEmpty = parameterList.clauses match {
      case Seq() => Seq(emptyParameterList)
      case Seq(clause) if clause.isImplicit => Seq(emptyParameterList, clause)
      case clauses => clauses
    }
    clausesWithInitialEmpty ++ syntheticParamClause
  }

  def effectiveFirstParameterSection: Seq[ScClassParameter] = effectiveParameterClauses.head.unsafeClassParameters

  private def syntheticParamClause: Option[ScParameterClause] = {
    val hasImplicit = parameterList.clauses.exists(_.isImplicit)
    if (hasImplicit) None else ScalaPsiUtil.syntheticParamClause(containingClass.asInstanceOf[ScTypeParametersOwner], parameterList, classParam = true)
  }

  def methodType(result: Option[ScType]): ScType = {
    val parameters: ScParameters = parameterList
    val clauses = parameters.clauses
    val returnType: ScType = result.getOrElse({
      val clazz = getParent.asInstanceOf[ScTypeDefinition]
      val typeParameters = clazz.typeParameters
      val parentClazz = ScalaPsiUtil.getPlaceTd(clazz)
      val designatorType: ScType =
        if (parentClazz != null)
          ScProjectionType(ScThisType(parentClazz), clazz, ScSubstitutor.empty, false)
        else ScDesignatorType(clazz)
      if (typeParameters.length == 0) designatorType
      else {
        ScParameterizedType(designatorType, typeParameters.map(new ScTypeParameterType(_, ScSubstitutor.empty)))
      }
    })
    if (clauses.length == 0) return new ScMethodType(returnType, Seq.empty, false)(getProject, getResolveScope)
    val res = clauses.foldRight[ScType](returnType){(clause: ScParameterClause, tp: ScType) =>
      new ScMethodType(tp, clause.getSmartParameters, clause.isImplicit)(getProject, getResolveScope)
    }
    res.asInstanceOf[ScMethodType]
  }

  def polymorphicType: ScType = {
    val typeParameters = getParent.asInstanceOf[ScTypeDefinition].typeParameters
    if (typeParameters.length == 0) methodType
    else ScTypePolymorphicType(methodType, typeParameters.map(tp =>
      TypeParameter(tp.name, tp.lowerBound.getOrNothing, tp.upperBound.getOrAny, tp)))
  }

  def getParamByName(name: String, clausePosition: Int = -1): Option[ScParameter] = {
    clausePosition match {
      case -1 => {
        for (param <- parameters if ScalaPsiUtil.memberNamesEquals(param.name, name)) return Some(param)
        None
      }
      case i if i < 0 => None
      case i if i >= effectiveParameterClauses.length => None
      case i => {
        val clause: ScParameterClause = effectiveParameterClauses.apply(i)
        for (param <- clause.parameters if ScalaPsiUtil.memberNamesEquals(param.name, name)) return Some(param)
        None
      }
    }
  }
}