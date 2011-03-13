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
        Some(ScParameterizedType(ScType.designator(t), typeParams))
      }
      case _ => None
    }
  }

  override def removeAbstracts = new ScFunctionType(returnType.removeAbstracts, params.map(_.removeAbstracts), project, scope)

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
