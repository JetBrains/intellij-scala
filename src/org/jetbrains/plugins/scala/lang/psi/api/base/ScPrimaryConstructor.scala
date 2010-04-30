package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import statements.ScFunction
import com.intellij.psi.PsiMethod
import psi.types.nonvalue.{ScMethodType, ScTypePolymorphicType, TypeParameter}
import psi.types._

/**
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

trait ScPrimaryConstructor extends ScMember with PsiMethod {
  /**
   *  @return has annotation
   */
  def hasAnnotation: Boolean

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

  def methodType: ScMethodType = {
    //todo: infer result type of recursive methods from super methods
    val parameters: ScParameters = parameterList
    val clauses = parameters.clauses
    val clazz = getParent.asInstanceOf[ScTypeDefinition]
    val typeParameters = clazz.typeParameters
    val returnType: ScType = if (typeParameters.length == 0) ScDesignatorType(clazz) else {
      ScParameterizedType(ScDesignatorType(clazz), typeParameters.map(new ScTypeParameterType(_, ScSubstitutor.empty)))
    }
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