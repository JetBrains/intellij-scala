package org.jetbrains.plugins.scala.annotator.template

import com.intellij.lang.annotation.{AnnotationHolder, Annotator}
import com.intellij.psi.{PsiAnonymousClass, PsiElement, PsiJavaCodeReferenceElement, PsiReferenceList}
import org.jetbrains.plugins.scala.annotator.AnnotatorUtils.ErrorAnnotationMessage
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.annotator.annotationHolder.ScalaAnnotationHolderAdapter
import org.jetbrains.plugins.scala.extensions._

final class SealedJavaInheritance extends Annotator {

  override def annotate(element: PsiElement, holder: AnnotationHolder): Unit =
    SealedJavaInheritance.annotate(element)(new ScalaAnnotationHolderAdapter(holder))
}

object SealedJavaInheritance {
  private[this] def isValidRole(role: PsiReferenceList.Role): Boolean =
    role == PsiReferenceList.Role.EXTENDS_LIST ||
      role == PsiReferenceList.Role.IMPLEMENTS_LIST

  private def annotate(element: PsiElement)(implicit holder: ScalaAnnotationHolder): Unit = element match {
    case list: PsiReferenceList if isValidRole(list.getRole) => annotateReferences(list.getReferenceElements.toSeq: _*)
    case clazz: PsiAnonymousClass                            => annotateReferences(clazz.getBaseClassReference)
    case _                                                   => ()
  }

  private[this] def annotateReferences(references: PsiJavaCodeReferenceElement*)
                                      (implicit holder: ScalaAnnotationHolder): Unit =
    references.foreach {
      case reference@ResolvesTo(ErrorAnnotationMessage(message)) =>
        holder.createErrorAnnotation(reference, message.nls)
      case _ =>
    }
}
