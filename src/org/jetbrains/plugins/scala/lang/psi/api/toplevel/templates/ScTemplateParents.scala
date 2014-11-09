package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package templates

import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParameterizedTypeElement, ScSimpleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:23:53
*/

trait ScTemplateParents extends ScalaPsiElement {
  def typeElements: Seq[ScTypeElement]
  def typeElementsWithoutConstructor: Seq[ScTypeElement] =
    findChildrenByClassScala(classOf[ScTypeElement])
  def superTypes: Seq[ScType]
  def supers: Seq[PsiClass] = {
    typeElements.map {
      case element: ScTypeElement =>
        def tail(): PsiClass = {
          element.getType(TypingContext.empty).map {
            case tp: ScType => ScType.extractClass(tp) match {
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
                  case Success(te, _) => ScType.extractClass(te, Some(getProject)) match {
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