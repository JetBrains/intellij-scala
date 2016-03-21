package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package templates

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParameterizedTypeElement, ScSimpleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.SyntheticMembersInjector
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, ModCount}
import org.jetbrains.plugins.scala.project.ProjectExt

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:23:53
*/

trait ScTemplateParents extends ScalaPsiElement {
  def typeElements: Seq[ScTypeElement]
  @Cached(false, ModCount.getBlockModificationCount, this)
  def syntheticTypeElements: Seq[ScTypeElement] = {
    getContext.getContext match {
      case td: ScTypeDefinition => SyntheticMembersInjector.injectSupers(td)
      case _ => Seq.empty
    }
  }
  def allTypeElements: Seq[ScTypeElement] = typeElements ++ syntheticTypeElements
  def typeElementsWithoutConstructor: Seq[ScTypeElement] =
    findChildrenByClassScala(classOf[ScTypeElement])
  def superTypes: Seq[ScType]
  def supers: Seq[PsiClass] = ScTemplateParents.extractSupers(allTypeElements, getProject)
}

object ScTemplateParents {
  def extractSupers(typeElements: Seq[ScTypeElement], project: Project)
                   (implicit typeSystem: TypeSystem = project.typeSystem): Seq[PsiClass] = {
    typeElements.map {
      case element: ScTypeElement =>
        def tail(): PsiClass = {
          element.getType(TypingContext.empty).map {
            case tp: ScType => tp.extractClass() match {
              case Some(clazz) => clazz
              case _ => null
            }
          }.getOrElse(null)
        }

        def refTail(ref: ScStableCodeReferenceElement): PsiClass = {
          val resolve = ref.resolveNoConstructor
          if (resolve.length == 1) {
            resolve(0) match {
              case ScalaResolveResult(c: PsiClass, _) => c
              case ScalaResolveResult(ta: ScTypeAliasDefinition, _) =>
                ta.aliasedType match {
                  case Success(te, _) => te.extractClass(project) match {
                    case Some(c) => c
                    case _ => null
                  }
                  case _ => null
                }
              case _ => tail()
            }
          } else tail()
        }
        element match {
          case s: ScSimpleTypeElement =>
            s.reference match {
              case Some(ref) => refTail(ref)
              case _ => tail()
            }
          case p: ScParameterizedTypeElement =>
            p.typeElement match {
              case s: ScSimpleTypeElement =>
                s.reference match {
                  case Some(ref) => refTail(ref)
                  case _ => tail()
                }
              case _ => tail()
            }
          case _ => tail()
        }
    }.filter(_ != null)
  }
}