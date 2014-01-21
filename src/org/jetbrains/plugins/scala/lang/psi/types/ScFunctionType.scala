package org.jetbrains.plugins.scala
package lang
package psi
package types

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import api.toplevel.typedef.ScTrait
import com.intellij.psi.{PsiManager, PsiClass}
import impl.ScalaPsiManager
import collection.immutable.HashSet

/**
* @author ilyas
*/
case class ScFunctionType(returnType: ScType, params: Seq[ScType])(project: Project, scope: GlobalSearchScope) extends ValueType {
  private var hash: Int = -1

  override def hashCode: Int = {
    if (hash == -1) {
      hash = returnType.hashCode() + params.hashCode() * 31
    }
    hash
  }

  def getProject: Project = project

  def getScope = scope

  def resolveFunctionTrait: Option[ScParameterizedType] = resolveFunctionTrait(getProject)

  def resolveFunctionTrait(project: Project): Option[ScParameterizedType] = {
    def findClass(fullyQualifiedName: String) : Option[PsiClass] = {
      Option(ScalaPsiManager.instance(project).getCachedClass(getScope, fullyQualifiedName))
    }
    findClass(functionTraitName) match {
      case Some(t: ScTrait) => {
        val typeParams = params.toList :+ returnType
        Some(ScParameterizedType(ScType.designator(t), typeParams))
      }
      case _ => None
    }
  }

  def arity: Int = params.length

  override def removeAbstracts =
    new ScFunctionType(returnType.removeAbstracts, params.map(_.removeAbstracts))(project, scope)

  override def recursiveUpdate(update: ScType => (Boolean, ScType), visited: HashSet[ScType]): ScType = {
    if (visited.contains(this)) {
      return update(this) match {
        case (true, res) => res
        case _ => this
      }
    }
    val newVisited = visited + this
    update(this) match {
      case (true, res) => res
      case _ =>
        new ScFunctionType(returnType.recursiveUpdate(update, newVisited), params.map(_.recursiveUpdate(update, newVisited)))(project, scope)
    }
  }

  override def recursiveVarianceUpdateModifiable[T](data: T, update: (ScType, Int, T) => (Boolean, ScType, T),
                                                    variance: Int = 1): ScType = {
    update(this, variance, data) match {
      case (true, res, _) => res
      case (_, _, newData) =>
        new ScFunctionType(returnType.recursiveVarianceUpdateModifiable(newData, update, variance),
          params.map(_.recursiveVarianceUpdateModifiable(newData, update, -variance)))(project, scope)
    }
  }

  private def functionTraitName = "scala.Function" + params.length

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

  def visitType(visitor: ScalaTypeVisitor) {
    visitor.visitFunctionType(this)
  }
}
