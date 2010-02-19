package org.jetbrains.plugins.scala
package lang
package psi
package types

import com.intellij.psi.{JavaPsiFacade, PsiClass}
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import api.toplevel.typedef.{ScClass, ScTrait}
import decompiler.DecompilerUtil

/**
* @author ilyas
*/

/**
 * @param shortDef is true for functions defined as follows
 *  def foo : Type = ...
 */
case class ScFunctionType(returnType: ScType, params: Seq[ScType]) extends ValueType {
  private var project: Project = null
  def getProject: Project = {
    if (project != null) project else DecompilerUtil.obtainProject
  }

  def this(returnType: ScType, params: Seq[ScType], project: Project) {
    this(returnType, params)
    this.project = project
  } 

  override def equiv(that : ScType): Boolean = that match {
    case ScFunctionType(rt1, params1) => {
      if (params1.length != params.length) return false
      if (!returnType.equiv(rt1)) return false
      val iter1 = params.iterator
      val iter2 = params1.iterator
      while (iter1.hasNext) {
        if (!iter1.next.equiv(iter2.next)) return false
      }
      true
    }
    case p: ScParameterizedType => {
      p.getFunctionType match {
        case Some(function) => this.equiv(function)
        case _ => false
      }
    }
    case _ => false
  }

  def resolveFunctionTrait: Option[ScParameterizedType] = resolveFunctionTrait(getProject)

  def resolveFunctionTrait(project: Project): Option[ScParameterizedType] = {
    def findClass(fullyQualifiedName: String) : Option[PsiClass] = {
        val psiFacade = JavaPsiFacade.getInstance(project)
        val allScope = GlobalSearchScope.allScope(project)
        Option(psiFacade.findClass(functionTraitName, allScope))
    }
    findClass(functionTraitName) match {
      case Some(t: ScTrait) => {
        val typeParams = params.toList ++ List(returnType)
        Some(ScParameterizedType(ScDesignatorType(t), typeParams))
      }
      case _ => None
    }
  }

  private def functionTraitName = "scala.Function" + params.length

  private var Implicit: Boolean = false

  def isImplicit: Boolean = Implicit

  def this(returnType: ScType, params: Seq[ScType], isImplicit: Boolean) = {
    this(returnType, params)
    Implicit = isImplicit
  }
}

case class ScTupleType private (components: Seq[ScType]) extends ValueType {
  private var project: Project = null
  def getProject: Project = {
    if (project != null) project else DecompilerUtil.obtainProject
  }

  def this(components: Seq[ScType], project: Project) = {
    this(components)
    this.project = project
  }

  override def equiv(that : ScType): Boolean = that match {
    case ScTupleType(c1) if c1.length == components.length => {
      val iter1 = components.iterator
      val iter2 = c1.iterator
      while (iter1.hasNext) {
        if (!iter1.next.equiv(iter2.next)) return false
      }
      true
    }
    case p: ScParameterizedType => {
      p.getTupleType match {
        case Some(tuple) => this.equiv(tuple)
        case _ => false
      }
    }
    case _ => false
  }

  def resolveTupleTrait: Option[ScParameterizedType] = resolveTupleTrait(getProject)

  def resolveTupleTrait(project: Project): Option[ScParameterizedType] = {
    def findClass(fullyQualifiedName: String) : Option[PsiClass] = {
        val psiFacade = JavaPsiFacade.getInstance(project)
        val allScope = GlobalSearchScope.allScope(project)
        Option(psiFacade.findClass(tupleTraitName, allScope))
    }
    findClass(tupleTraitName) match {
      case Some(t: ScClass) => {
        val typeParams = components.toList
        Some(ScParameterizedType(ScDesignatorType(t), typeParams))
      }
      case _ => None
    }
  }

  private def tupleTraitName = "scala.Tuple" + components.length
}
