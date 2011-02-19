package org.jetbrains.plugins.scala
package lang
package psi
package types

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import api.toplevel.typedef.{ScClass, ScTrait}
import decompiler.DecompilerUtil
import com.intellij.psi.{PsiElement, JavaPsiFacade, PsiClass}

/**
* @author ilyas
*/

/**
 * @param shortDef is true for functions defined as follows
 *  def foo : Type = ...
 */
case class ScFunctionType private (returnType: ScType, params: Seq[ScType]) extends ValueType {
  private var project: Project = null
  private var scope: GlobalSearchScope = GlobalSearchScope.allScope(getProject)
  def getProject: Project = {
    if (project != null) project else DecompilerUtil.obtainProject
  }

  def getScope = scope

  def this(returnType: ScType, params: Seq[ScType], project: Project, scope: GlobalSearchScope) {
    this(returnType, params)
    this.project = project
    this.scope = scope
  }

  def resolveFunctionTrait: Option[ScParameterizedType] = resolveFunctionTrait(getProject)

  def resolveFunctionTrait(project: Project): Option[ScParameterizedType] = {
    def findClass(fullyQualifiedName: String) : Option[PsiClass] = {
        val psiFacade = JavaPsiFacade.getInstance(project)
        Option(psiFacade.findClass(functionTraitName, getScope))
    }
    findClass(functionTraitName) match {
      case Some(t: ScTrait) => {
        val typeParams = params.toList ++ List(returnType)
        Some(ScParameterizedType(ScDesignatorType(t), typeParams))
      }
      case _ => None
    }
  }

  override def removeAbstracts = new ScFunctionType(returnType.removeAbstracts, params.map(_.removeAbstracts), project, scope)

  override def updateThisType(place: PsiElement) =
    new ScFunctionType(returnType.updateThisType(place), params.map(_.updateThisType(place)), project, scope)

  override def updateThisType(tp: ScType) =
    new ScFunctionType(returnType.updateThisType(tp), params.map(_.updateThisType(tp)), project, scope)

  private def functionTraitName = "scala.Function" + params.length

  private var Implicit: Boolean = false

  def isImplicit: Boolean = Implicit

  def this(returnType: ScType, params: Seq[ScType], isImplicit: Boolean) = {
    this(returnType, params)
    Implicit = isImplicit
  }

  override def equivInner(r: ScType, uSubst: ScUndefinedSubstitutor, falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    var undefinedSubst = uSubst
    r match {
      case ScFunctionType(rt1, params1) => {
        if (params1.length != params.length) return (false, undefinedSubst)
        var t = Equivalence.equivInner(returnType, rt1, undefinedSubst, falseUndef)
        if (!t._1) return (false, undefinedSubst)
        undefinedSubst = t._2
        val iter1 = params.iterator
        val iter2 = params1.iterator
        while (iter1.hasNext) {
          t = Equivalence.equivInner(iter1.next, iter2.next, undefinedSubst, falseUndef)
          if (!t._1) return (false, undefinedSubst)
          undefinedSubst = t._2
        }
        (true, undefinedSubst)
      }
      case p: ScParameterizedType => {
        p.getFunctionType match {
          case Some(function) => this.equivInner(function, undefinedSubst, falseUndef)
          case _ => (false, undefinedSubst)
        }
      }
      case _ => (false, undefinedSubst)
    }
  }
}

case class ScTupleType private (components: Seq[ScType]) extends ValueType {
  private var project: Project = null
  private var scope: GlobalSearchScope = GlobalSearchScope.allScope(getProject)
  def getProject: Project = {
    if (project != null) project else DecompilerUtil.obtainProject
  }

  def getScope = scope

  def this(components: Seq[ScType], project: Project, scope: GlobalSearchScope) = {
    this(components)
    this.project = project
    this.scope = scope
  }

  def resolveTupleTrait: Option[ScParameterizedType] = resolveTupleTrait(getProject)

  def resolveTupleTrait(project: Project): Option[ScParameterizedType] = {
    def findClass(fullyQualifiedName: String) : Option[PsiClass] = {
        val psiFacade = JavaPsiFacade.getInstance(project)
        Option(psiFacade.findClass(tupleTraitName, getScope))
    }
    findClass(tupleTraitName) match {
      case Some(t: ScClass) => {
        val typeParams = components.toList
        Some(ScParameterizedType(ScDesignatorType(t), typeParams))
      }
      case _ => None
    }
  }

  override def equivInner(r: ScType, uSubst: ScUndefinedSubstitutor, falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    var undefinedSubst = uSubst
    r match {
      case ScTupleType(c1) if c1.length == components.length => {
        val iter1 = components.iterator
        val iter2 = c1.iterator
        while (iter1.hasNext) {
          val t = Equivalence.equivInner(iter1.next, iter2.next, undefinedSubst, falseUndef)
          if (!t._1) return (false, undefinedSubst)
          undefinedSubst = t._2
        }
        (true, undefinedSubst)
      }
      case p: ScParameterizedType => {
        p.getTupleType match {
          case Some(tuple) => this.equivInner(tuple, undefinedSubst, falseUndef)
          case _ => (false, undefinedSubst)
        }
      }
      case _ => (false, undefinedSubst)
    }
  }

  override def removeAbstracts = ScTupleType(components.map(_.removeAbstracts))

  override def updateThisType(place: PsiElement) = ScTupleType(components.map(_.updateThisType(place)))

  override def updateThisType(tp: ScType) = ScTupleType(components.map(_.updateThisType(tp)))

  private def tupleTraitName = "scala.Tuple" + components.length
}
