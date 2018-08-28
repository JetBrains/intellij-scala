package org.jetbrains.plugins.scala
package annotator
package template

import com.intellij.lang.annotation.{AnnotationHolder, Annotator}
import com.intellij.psi.{PsiAnonymousClass, PsiElement, PsiJavaCodeReferenceElement, PsiReferenceList}
import org.jetbrains.plugins.scala.extensions._

final class SealedJavaInheritance extends Annotator {

  override def annotate(element: PsiElement, holder: AnnotationHolder): Unit =
    SealedJavaInheritance.annotate(element)(holder)
}

object SealedJavaInheritance {

  private def annotate(element: PsiElement)
                      (implicit holder: AnnotationHolder): Unit = element match {
    case list: PsiReferenceList => annotateReferences(list.getReferenceElements: _*)
    case clazz: PsiAnonymousClass => annotateReferences(clazz.getBaseClassReference)
    case _ =>
  }

  import SealedClassInheritance.ErrorAnnotationMessage

  private[this] def annotateReferences(references: PsiJavaCodeReferenceElement*)
                                      (implicit holder: AnnotationHolder): Unit =
    references.foreach {
      case reference@ResolvesTo(ErrorAnnotationMessage(message)) =>
        holder.createErrorAnnotation(reference, message)
      case _ =>
    }
}
