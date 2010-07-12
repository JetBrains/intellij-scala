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

  override def removeAbstracts = ScTupleType(components.map(_.removeAbstracts))

  private def tupleTraitName = "scala.Tuple" + components.length
}
