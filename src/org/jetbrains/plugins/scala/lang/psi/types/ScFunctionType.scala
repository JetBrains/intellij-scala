package org.jetbrains.plugins.scala
package lang
package psi
package types

import com.intellij.psi.{JavaPsiFacade, PsiClass}
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import api.toplevel.typedef.{ScClass, ScTrait}

/**
* @author ilyas
*/

/**
 * @param shortDef is true for functions defined as follows
 *  def foo : Type = ...
 */
case class ScFunctionType(returnType: ScType, params: Seq[ScType]) extends ScType {

  override def equiv(that : ScType) = that match {
    case ScFunctionType(rt1, params1) => (returnType equiv rt1) &&
                               (params.zip(params1) forall {case (x,y) => x equiv y})
    case ScParameterizedType(ScDesignatorType(c : PsiClass), args)
      if args.length > 0 && c.getQualifiedName == functionTraitName => {
      val (otherArgsTypes, List(otherReturnType)) = args.splitAt(args.length - 1)
      (returnType equiv otherReturnType) && otherArgsTypes.zip(params.toArray[ScType]).forall{case (t1, t2) => t1 equiv t2}
    }
    case _ => false
  }

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
}

case class ScTupleType(components: Seq[ScType]) extends ScType {
  override def equiv(that : ScType) = that match {
    case ScTupleType(c1) => components.zip(c1) forall {case (x,y)=> x equiv y}
    case ScParameterizedType(ScDesignatorType(c : PsiClass), args)
      if args.length > 0 && c.getQualifiedName == "scala.Tuple" + args.length => {
      args.zip(components.toArray[ScType]).forall{case (t1, t2) => t1 equiv t2}
    }
    case _ => false
  }

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
