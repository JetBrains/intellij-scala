package org.jetbrains.plugins.scala
package lang
package psi
package types

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import api.toplevel.typedef.{ScClass, ScTrait}
import decompiler.DecompilerUtil
import com.intellij.psi.{PsiElement, JavaPsiFacade, PsiClass}
import impl.ScalaPsiManager
import collection.immutable.HashSet

/**
* @author ilyas
*/
case class ScTupleType(components: Seq[ScType])(project: Project, scope: GlobalSearchScope) extends ValueType {
  def getProject: Project = project

  def getScope = scope

  def resolveTupleTrait: Option[ScParameterizedType] = resolveTupleTrait(getProject)

  def resolveTupleTrait(project: Project): Option[ScParameterizedType] = {
    def findClass(fullyQualifiedName: String) : Option[PsiClass] = {
      Option(ScalaPsiManager.instance(project).getCachedClass(getScope, tupleTraitName))
    }
    findClass(tupleTraitName) match {
      case Some(t: ScClass) => {
        val typeParams = components.toList
        Some(ScParameterizedType(ScType.designator(t), typeParams))
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
          val t = Equivalence.equivInner(iter1.next(), iter2.next(), undefinedSubst, falseUndef)
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

  override def removeAbstracts = ScTupleType(components.map(_.removeAbstracts))(project, scope)

  override def recursiveUpdate(update: ScType => (Boolean, ScType), visited: HashSet[ScType]): ScType = {
    if (visited.contains(this)) {
      return update(this) match {
        case (true, res) => res
        case _ => this
      }
    }
    update(this) match {
      case (true, res) => res
      case _ =>
        ScTupleType(components.map(_.recursiveUpdate(update, visited + this)))(project, scope)
    }
  }

  override def recursiveVarianceUpdateModifiable[T](data: T, update: (ScType, Int, T) => (Boolean, ScType, T),
                                           variance: Int = 1): ScType = {
    update(this, variance, data) match {
      case (true, res, _) => res
      case (_, _, newData) =>
        ScTupleType(components.map(_.recursiveVarianceUpdateModifiable(newData, update, variance)))(project, scope)
    }
  }

  private def tupleTraitName = "scala.Tuple" + components.length

  def visitType(visitor: ScalaTypeVisitor) {
    visitor.visitTupleType(this)
  }
}
