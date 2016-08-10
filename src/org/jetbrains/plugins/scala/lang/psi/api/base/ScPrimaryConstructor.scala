package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.light.ScPrimaryConstructorWrapper
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{ScMethodType, ScTypePolymorphicType, TypeParameter}
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, CachedInsidePsiElement, ModCount}

import scala.collection.mutable.ArrayBuffer

/**
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

trait ScPrimaryConstructor extends ScMember with ScMethodLike with ScAnnotationsHolder {
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

  /**
   * All classes must have one non-implicit parameter list. If this is not declared in in the code,
   * it is assumed by the compiler.
   *
   * In addition, view and context bounds generate an additional implicit parameter section.
   */
  @CachedInsidePsiElement(this, ModCount.getBlockModificationCount)
  def effectiveParameterClauses: Seq[ScParameterClause] = {
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
          ScProjectionType(ScThisType(parentClazz), clazz, superReference = false)
        else ScDesignatorType(clazz)
      if (typeParameters.isEmpty) designatorType
      else {
        ScParameterizedType(designatorType, typeParameters.map(new ScTypeParameterType(_, ScSubstitutor.empty)))
      }
    })
    if (clauses.isEmpty) return new ScMethodType(returnType, Seq.empty, false)(getProject, getResolveScope)
    val res = clauses.foldRight[ScType](returnType){(clause: ScParameterClause, tp: ScType) =>
      new ScMethodType(tp, clause.getSmartParameters, clause.isImplicit)(getProject, getResolveScope)
    }
    res.asInstanceOf[ScMethodType]
  }

  def polymorphicType: ScType = {
    val typeParameters = getParent.asInstanceOf[ScTypeDefinition].typeParameters
    if (typeParameters.isEmpty) methodType
    else ScTypePolymorphicType(methodType, typeParameters.map(new TypeParameter(_)))
  }

  def getParamByName(name: String, clausePosition: Int = -1): Option[ScParameter] = {
    clausePosition match {
      case -1 =>
        for (param <- parameters if ScalaPsiUtil.memberNamesEquals(param.name, name)) return Some(param)
        None
      case i if i < 0 => None
      case i if i >= effectiveParameterClauses.length => None
      case i =>
        val clause: ScParameterClause = effectiveParameterClauses.apply(i)
        for (param <- clause.parameters if ScalaPsiUtil.memberNamesEquals(param.name, name)) return Some(param)
        None
    }
  }

  @Cached(synchronized = false, ModCount.getBlockModificationCount, this)
  def getFunctionWrappers: Seq[ScPrimaryConstructorWrapper] = {
    val buffer = new ArrayBuffer[ScPrimaryConstructorWrapper]()
    buffer += new ScPrimaryConstructorWrapper(this)
    for {
      first <- parameterList.clauses.headOption
      if first.hasRepeatedParam
      if hasAnnotation("scala.annotation.varargs").isDefined
    } {
      buffer += new ScPrimaryConstructorWrapper(this, isJavaVarargs = true)
    }

    val params = parameters
    for (i <- params.indices if params(i).baseDefaultParam) {
      buffer += new ScPrimaryConstructorWrapper(this, forDefault = Some(i + 1))
    }

    buffer.toSeq
  }
}

object ScPrimaryConstructor {
  object ofClass {
    def unapply(pc: ScPrimaryConstructor): Option[ScClass] = {
      pc.containingClass match {
        case c: ScClass => Some(c)
        case _ => None
      }
    }
  }
}