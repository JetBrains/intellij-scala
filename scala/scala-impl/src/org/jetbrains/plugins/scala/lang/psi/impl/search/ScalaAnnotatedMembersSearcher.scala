package org.jetbrains.plugins.scala
package lang
package psi
package impl
package search

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiMember
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.util.{Processor, QueryExecutor}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAnnotation, ScAnnotations}
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys

/**
  * User: Alexander Podkhalyuzin
  * Date: 10.01.2009
  */

class ScalaAnnotatedMembersSearcher extends QueryExecutor[PsiMember, AnnotatedElementsSearch.Parameters] {

  def execute(p: AnnotatedElementsSearch.Parameters, consumer: Processor[PsiMember]): Boolean = {
    val annClass = p.getAnnotationClass
    assert(annClass.isAnnotationType, "Annotation type should be passed to annotated members search")
    val annotationFQN = annClass.qualifiedName
    assert(annotationFQN != null)

    ApplicationManager.getApplication.runReadAction(new Computable[Boolean] {
      def compute: Boolean = {
        val scope = p.getScope match {
          case searchScope: GlobalSearchScope => searchScope
          case _ => return true
        }

        import ScalaIndexKeys._
        val iter = ANNOTATED_MEMBER_KEY.elements(annClass.name, scope, classOf[ScAnnotation])(annClass.getProject)
          .iterator
        while (iter.hasNext) {
          val annotation = iter.next
          annotation.getParent match {
            case ann: ScAnnotations => ann.getParent match {
              case member: PsiMember => if (!consumer.process(member)) return false
              case _ =>
            }
            case _ =>
          }
        }
        true
      }
    })


    true
  }
}