package org.jetbrains.plugins.scala
package lang
package psi
package types

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScTypeUtil.AliasType

import scala.annotation.tailrec

/**
* @author ilyas
*/
object ScFunctionType {
  def apply(returnType: ScType, params: Seq[ScType])(project: Project, scope: GlobalSearchScope): ValueType = {
    def findClass(fullyQualifiedName: String) : Option[PsiClass] = {
      ScalaPsiManager.instance(project).getCachedClass(scope, fullyQualifiedName)
    }
    findClass("scala.Function" + params.length) match {
      case Some(t: ScTrait) =>
        val typeParams = params.toList :+ returnType
        ScParameterizedType(ScalaType.designator(t), typeParams)
      case _ => types.Nothing
    }
  }

  def unapply(tp: ScType)
             (implicit typeSystem: TypeSystem): Option[(ScType, Seq[ScType])] = {
    ScSynteticSugarClassesUtil.extractForPrefix(tp, "scala.Function") match {
      case Some((clazz, typeArgs)) if typeArgs.nonEmpty =>
        val (params, Seq(ret)) = typeArgs.splitAt(typeArgs.length - 1)
        Some(ret, params)
      case _ => None
    }
  }

  def isFunctionType(tp: ScType)
                    (implicit typeSystem: TypeSystem): Boolean = unapply(tp).isDefined
}

object ScPartialFunctionType {
  def apply(returnType: ScType, param: ScType)(project: Project, scope: GlobalSearchScope): ValueType = {
    def findClass(fullyQualifiedName: String) : Option[PsiClass] = {
      ScalaPsiManager.instance(project).getCachedClass(scope, fullyQualifiedName)
    }
    findClass("scala.PartialFunction") match {
      case Some(t: ScTrait) =>
        val typeParams = param :: returnType :: Nil
        ScParameterizedType(ScalaType.designator(t), typeParams)
      case _ => types.Nothing
    }
  }

  def unapply(tp: ScType)
             (implicit typeSystem: TypeSystem): Option[(ScType, ScType)] = {
    ScSynteticSugarClassesUtil.extractForPrefix(tp, "scala.PartialFunction") match {
      case Some((clazz, typeArgs)) if typeArgs.length == 2 =>
        Some(typeArgs(1), typeArgs(0))
      case _ => None
    }
  }

  def isFunctionType(tp: ScType)
                    (implicit typeSystem: TypeSystem): Boolean = unapply(tp).isDefined
}

object ScTupleType {
  def apply(components: Seq[ScType])(project: Project, scope: GlobalSearchScope): ValueType = {
    def findClass(fullyQualifiedName: String) : Option[PsiClass] = {
      ScalaPsiManager.instance(project).getCachedClass(scope, fullyQualifiedName)
    }
    findClass("scala.Tuple" + components.length) match {
      case Some(t: ScClass) =>
        ScParameterizedType(ScalaType.designator(t), components)
      case _ => types.Nothing
    }
  }

  def unapply(tp: ScType)
             (implicit typeSystem: TypeSystem): Option[Seq[ScType]] = {
    ScSynteticSugarClassesUtil.extractForPrefix(tp, "scala.Tuple") match {
      case Some((clazz, typeArgs)) if typeArgs.nonEmpty =>
        Some(typeArgs)
      case _ => None
    }
  }
}

object ScSynteticSugarClassesUtil {
  @tailrec
  def extractForPrefix(tp: ScType, prefix: String, depth: Int = 100)
                      (implicit typeSystem: TypeSystem): Option[(ScTypeDefinition, Seq[ScType])] = {
    if (depth == 0) return None //hack for http://youtrack.jetbrains.com/issue/SCL-6880 to avoid infinite loop.
    tp.isAliasType match {
      case Some(AliasType(t: ScTypeAliasDefinition, Success(lower, _), _)) => extractForPrefix(lower, prefix, depth - 1)
      case _ =>
        tp match {
          case p: ScParameterizedType =>
            def startsWith(clazz: PsiClass, qualNamePrefix: String) = clazz.qualifiedName != null && clazz.qualifiedName.startsWith(qualNamePrefix)

            p.designator.extractClassType() match {
              case Some((clazz: ScTypeDefinition, sub)) if startsWith(clazz, prefix) =>
                val result = clazz.getType(TypingContext.empty)
                result match {
                  case Success(t, _) =>
                    val substituted = (sub followed p.substitutor).subst(t)
                    substituted match {
                      case pt: ScParameterizedType => Some((clazz, pt.typeArgs))
                      case _ => None
                    }
                  case _ => None
                }
              case _ => None
            }
          case _ => None
        }
    }
  }
}
