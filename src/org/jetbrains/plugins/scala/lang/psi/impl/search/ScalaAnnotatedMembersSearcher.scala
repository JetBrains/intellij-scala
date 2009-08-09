package org.jetbrains.plugins.scala
package lang
package psi
package impl
package search

import _root_.scala.collection.mutable.ArrayBuffer
import api.statements.ScAnnotationsHolder
import api.toplevel.typedef.ScMember
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.{AnnotatedElementsSearch, AnnotatedMembersSearch}
import com.intellij.psi.{PsiMember, PsiElement}
import com.intellij.util.{QueryExecutor, Processor}
import stubs.util.ScalaStubsUtil
/**
 * User: Alexander Podkhalyuzin
 * Date: 10.01.2009
 */

class ScalaAnnotatedMembersSearcher extends QueryExecutor[PsiMember, AnnotatedElementsSearch.Parameters] {

  def execute(p: AnnotatedElementsSearch.Parameters, consumer: Processor[PsiMember]): Boolean = {
    val annClass = p.getAnnotationClass
    assert(annClass.isAnnotationType(), "Annotation type should be passed to annotated members search")
    val annotationFQN = annClass.getQualifiedName
    assert(annotationFQN != null)

    val scope = p.getScope match {case x: GlobalSearchScope => x case _ => return true}

    ApplicationManager.getApplication().runReadAction(new Computable[Boolean] {
      def compute: Boolean = {
        val candidates = ScalaStubsUtil.getAnnotatedMembers(annClass, scope)
        for (candidate <- candidates if candidate.isInstanceOf[ScAnnotationsHolder];
             cand = candidate.asInstanceOf[ScAnnotationsHolder]) {
          if (cand.hasAnnotation(annClass)) {
            if (!consumer.process(candidate)) {
              return false
            }
          }
        }
        true
      }
    })


    return true;
  }
}