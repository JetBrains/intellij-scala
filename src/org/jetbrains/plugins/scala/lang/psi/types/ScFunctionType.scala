package org.jetbrains.plugins.scala
package lang
package psi
package types

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTypeDefinition, ScTrait}
import com.intellij.psi.PsiClass
import impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}
import org.jetbrains.plugins.scala.extensions.toPsiClassExt
import org.jetbrains.plugins.scala.lang.psi.types.Conformance.AliasType
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import scala.annotation.tailrec

/**
* @author ilyas
*/
object ScFunctionType {
  def apply(returnType: ScType, params: Seq[ScType])(project: Project, scope: GlobalSearchScope): ValueType = {
    def findClass(fullyQualifiedName: String) : Option[PsiClass] = {
      Option(ScalaPsiManager.instance(project).getCachedClass(scope, fullyQualifiedName))
    }
    findClass("scala.Function" + params.length) match {
      case Some(t: ScTrait) =>
        val typeParams = params.toList :+ returnType
        ScParameterizedType(ScType.designator(t), typeParams)
      case _ => types.Nothing
    }
  }

  @tailrec
  def extractForPrefix(tp: ScType, prefix: String): Option[(ScTypeDefinition, Seq[ScType])] = {
    tp.isAliasType match {
      case Some(AliasType(t: ScTypeAliasDefinition, Success(lower, _), _)) => extractForPrefix(lower, prefix)
      case _ =>
        tp match {
          case p: ScParameterizedType =>
            def startsWith(clazz: PsiClass, qualNamePrefix: String) = clazz.qualifiedName != null && clazz.qualifiedName.startsWith(qualNamePrefix)

            ScType.extractClassType(p.designator) match {
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

  def unapply(tp: ScType): Option[(ScType, Seq[ScType])] = {
    extractForPrefix(tp, "scala.Function") match {
      case Some((clazz, typeArgs)) if typeArgs.length > 0 =>
        val (params, Seq(ret)) = typeArgs.splitAt(typeArgs.length - 1)
        Some(ret, params)
      case _ => None
    }
  }

  def isFunctionType(tp: ScType): Boolean = unapply(tp).isDefined
}
