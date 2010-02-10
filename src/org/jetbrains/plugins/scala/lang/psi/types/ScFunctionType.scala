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

  override def equiv(that : ScType) = that match {
    case ScFunctionType(rt1, params1) => (returnType equiv rt1) &&
                               (params.zip(params1) forall {case (x,y) => x equiv y})
    case ScParameterizedType(ScDesignatorType(c : PsiClass), args)
      if args.length > 0 && c.getQualifiedName == functionTraitName => {
      //to prevent MatchError, here used clear definition
      val otherArgsTypes = args.slice(0, args.length - 1)
      val otherReturnType = args.apply(args.length - 1)
      (returnType equiv otherReturnType) && otherArgsTypes.zip(params.toArray[ScType]).forall{case (t1, t2) => t1 equiv t2}
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

  override def equiv(that : ScType) = that match {
    case ScTupleType(c1) if c1.length == components.length => components.zip(c1) forall {case (x,y)=> x equiv y}
    case ScParameterizedType(ScDesignatorType(c : PsiClass), args)
      if args.length > 0 && c.getQualifiedName == "scala.Tuple" + args.length => {
      args.zip(components.toArray[ScType]).forall{case (t1, t2) => t1 equiv t2}
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
