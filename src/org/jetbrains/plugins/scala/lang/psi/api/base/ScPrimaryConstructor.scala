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

/**
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

trait ScPrimaryConstructor extends ScMember with PsiMethod with ScMethodLike {
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

  //hack: no ClassParamList present at the moment
  def parameters : Seq[ScClassParameter] = parameterList.params.asInstanceOf[Seq[ScClassParameter]]

  /**
   * return only parameters, which are additionally members.
   */
  def valueParameters: Seq[ScClassParameter] = parameters.filter((p: ScClassParameter) => p.isVal || p.isVar)

  def methodType(result: Option[ScType]): ScType = {
    val parameters: ScParameters = parameterList
    val clauses = parameters.clauses
    val returnType: ScType = result.getOrElse({
      val clazz = getParent.asInstanceOf[ScTypeDefinition]
      val typeParameters = clazz.typeParameters
      val parentClazz = ScalaPsiUtil.getPlaceTd(clazz)
      val designatorType: ScType =
        if (parentClazz != null)
          ScProjectionType(ScThisType(parentClazz), clazz, ScSubstitutor.empty)
        else ScDesignatorType(clazz)
      if (typeParameters.length == 0) designatorType
      else {
        ScParameterizedType(designatorType, typeParameters.map(new ScTypeParameterType(_, ScSubstitutor.empty)))
      }
    })
    if (clauses.length == 0) return new ScMethodType(returnType, Seq.empty, false, getProject, getResolveScope)
    val res = clauses.foldRight[ScType](returnType){(clause: ScParameterClause, tp: ScType) =>
      new ScMethodType(tp, clause.getSmartParameters, clause.isImplicit, getProject, getResolveScope)
    }
    res.asInstanceOf[ScMethodType]
  }

  def polymorphicType: ScType = {
    val typeParameters = getParent.asInstanceOf[ScTypeDefinition].typeParameters
    if (typeParameters.length == 0) return methodType
    else return ScTypePolymorphicType(methodType, typeParameters.map(tp =>
      TypeParameter(tp.name, tp.lowerBound.getOrElse(Nothing), tp.upperBound.getOrElse(Any), tp)))
  }

  def getParamByName(name: String, clausePosition: Int = -1): Option[ScParameter] = {
    clausePosition match {
      case -1 => {
        for (param <- parameters if param.name == name) return Some(param)
        return None
      }
      case i if i < 0 => return None
      case i if i >= parameterList.clauses.length => return None
      case i => {
        val clause: ScParameterClause = parameterList.clauses.apply(i)
        for (param <- clause.parameters if param.name == name) return Some(param)
        return None
      }
    }
  }
}