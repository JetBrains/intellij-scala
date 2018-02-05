package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package templates

import com.intellij.psi.{PsiClass, PsiElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParameterizedTypeElement, ScSimpleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.SyntheticMembersInjector
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, ModCount}
import org.jetbrains.plugins.scala.project.ProjectContext

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:23:53
*/

trait ScTemplateParents extends ScalaPsiElement {
  def typeElements: Seq[ScTypeElement]
  @Cached(ModCount.getBlockModificationCount, this)
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
  def supers: Seq[PsiClass] = ScTemplateParents.extractSupers(allTypeElements)
  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case visitor: ScalaElementVisitor => visitor.visitTemplateParents(this)
      case _ => visitor.visitElement(this)
    }
  }
}

object ScTemplateParents {

  def extractSupers(typeElements: Seq[ScTypeElement])
                   (implicit project: ProjectContext): Seq[PsiClass] =
    typeElements.flatMap { element =>
      def tail(): Option[PsiClass] =
        element.`type`().toOption
          .flatMap(_.extractClass)

      def refTail(reference: ScStableCodeReferenceElement): Option[PsiClass] =
        reference.resolveNoConstructor match {
          case Array(head) => head.element match {
            case c: PsiClass => Some(c)
            case ta: ScTypeAliasDefinition =>
              ta.aliasedType.toOption
                .flatMap(_.extractClass)
            case _ => tail()
          }
          case _ => tail()
        }

      val maybeReference = element match {
        case ScSimpleTypeElement(result) => result
        case ScParameterizedTypeElement(ScSimpleTypeElement(result), _) => result
        case _ => None
      }

      maybeReference match {
        case Some(reference) => refTail(reference)
        case _ => tail()
      }
    }
}