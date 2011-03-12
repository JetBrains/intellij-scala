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

  private def tupleTraitName = "scala.Tuple" + components.length
}
