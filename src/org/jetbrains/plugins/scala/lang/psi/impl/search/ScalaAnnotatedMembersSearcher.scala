package org.jetbrains.plugins.scala
package lang
package psi
package impl
package search

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.psi.{PsiMember, PsiElement}
import com.intellij.util.{QueryExecutor, Processor}
import com.intellij.psi.stubs.StubIndex
import stubs.index.ScAnnotatedMemberIndex
import api.expr.{ScAnnotations, ScAnnotation}
import stubs.util.ScalaStubsUtil
import extensions.toPsiClassExt

/**
 * User: Alexander Podkhalyuzin
 * Date: 10.01.2009
 */

class ScalaAnnotatedMembersSearcher extends QueryExecutor[PsiMember, AnnotatedElementsSearch.Parameters] {

  def execute(p: AnnotatedElementsSearch.Parameters, consumer: Processor[PsiMember]): Boolean = {
    val annClass = p.getAnnotationClass
    assert(annClass.isAnnotationType(), "Annotation type should be passed to annotated members search")
    val annotationFQN = annClass.qualifiedName
    assert(annotationFQN != null)

    val scope = p.getScope match {case x: GlobalSearchScope => x case _ => return true}

    ApplicationManager.getApplication().runReadAction(new Computable[Boolean] {
      def compute: Boolean = {
        val candidates: java.util.Collection[_ <: PsiElement] = StubIndex.getInstance.get(ScAnnotatedMemberIndex.KEY,
          annClass.getName, annClass.getProject, scope)
        val iter = candidates.iterator
        while (iter.hasNext) {
          val next = iter.next
          if (ScalaStubsUtil.checkPsiForAnnotation(next)) {
            val annotation = next.asInstanceOf[ScAnnotation]
            annotation.getParent match {
              case ann: ScAnnotations => ann.getParent match {
                case member: PsiMember => if (!consumer.process(member)) return false
                case _ =>
              }
              case _ =>
            }
          }
        }
        true
      }
    })


    return true;
  }
}