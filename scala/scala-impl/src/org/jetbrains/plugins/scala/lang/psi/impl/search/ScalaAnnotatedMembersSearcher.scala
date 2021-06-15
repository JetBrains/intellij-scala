package org.jetbrains.plugins.scala
package lang
package psi
package impl
package search

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.psi.{PsiField, PsiMember, PsiModifier}
import com.intellij.util.{Processor, QueryExecutor}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, ScAnnotations}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariable
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.light.ScLightField
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys

import scala.collection.mutable

/**
 * User: Alexander Podkhalyuzin
 * Date: 10.01.2009
 */

class ScalaAnnotatedMembersSearcher extends QueryExecutor[PsiMember, AnnotatedElementsSearch.Parameters] {

  private def convertToLightField(fieldLike: ScValueOrVariable): Option[PsiField] =
    for {
      anchor <- fieldLike.declaredElements.headOption
      containingTypeDef <- Option(fieldLike.containingClass).collect { case c: ScTypeDefinition => c }
    } yield {
      val javaModifiers = mutable.ArrayBuffer.empty[String]
      // access modifier
      // very rude mapping from flexible Scala access modifiers
      javaModifiers += fieldLike.getModifierList.accessModifier
          .map {
            case e if e.isPrivate || e.isThis => PsiModifier.PRIVATE
            case e if e.isProtected           => PsiModifier.PROTECTED
            case _                            => PsiModifier.PUBLIC
          }
          .getOrElse(PsiModifier.PUBLIC)

      // immutability modifier
      if (anchor.isVal)
        javaModifiers += PsiModifier.FINAL

      ScLightField(
        anchor.name,
        anchor.`type`().getOrAny,
        containingTypeDef,
        javaModifiers.toSeq: _*
      )
    }

  override def execute(p: AnnotatedElementsSearch.Parameters, consumer: Processor[_ >: PsiMember]): Boolean = {
    val annClass = p.getAnnotationClass
    assert(annClass.isAnnotationType, "Annotation type should be passed to annotated members search")
    val annotationFQN = annClass.qualifiedName
    assert(annotationFQN != null)

    ApplicationManager.getApplication.runReadAction(new Computable[Boolean] {
      override def compute: Boolean = {
        val scope = p.getScope match {
          case searchScope: GlobalSearchScope => searchScope
          case _ => return true
        }

        import ScalaIndexKeys._
        val iter = ANNOTATED_MEMBER_KEY.elements(annClass.name, scope)(annClass.getProject)
            .iterator
        while (iter.hasNext) {
          val annotation = iter.next()
          annotation.getParent match {
            case ann: ScAnnotations =>
              val continue = Option(ann.getParent)
                  .collect {
                    case fieldLike: ScValueOrVariable =>
                      convertToLightField(fieldLike).getOrElse(fieldLike)
                    case member: PsiMember => member
                  }
                  .forall(consumer.process(_))

              if (!continue)
                return false
            case _ =>
          }
        }
        true
      }
    })


    true
  }
}