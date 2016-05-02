package org.jetbrains.plugins.scala.lang

import com.intellij.psi.{PsiElement, PsiMember, PsiNamedElement}
import org.jetbrains.plugins.scala.codeInsight.intention.types.UpdateStrategy
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, PsiMemberExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScThisType}
import org.jetbrains.plugins.scala.lang.resolve.{ResolvableReferenceElement, ScalaResolveResult}

/**
  * @author Pavel Fatin
  */
package object transformation {
  def quote(s: String): String = "\"" + s + "\""

  // Tries to use simple name, then partially qualified name, then fully qualified name instead of adding imports
  def bindTo(r0: ScReferenceExpression, target: String) {
    val paths = target.split("\\.").toVector

    val r1 = if (r0.text == paths.last) r0 else
      r0.replace(parseElement(paths.last, r0.psiManager)).asInstanceOf[ScReferenceExpression]

    if (!isResolvedTo(r1, target)) {
      if (paths.length > 1) {
        if (paths.length > 2) {
          val r2 = r1.replace(parseElement(paths.takeRight(2).mkString("."), r0.psiManager))

          if (!isResolvedTo(r2.asInstanceOf[ScReferenceExpression], target)) {
            r2.replace(parseElement(target, r0.psiManager))
          }
        } else {
          r1.replace(parseElement(target, r0.psiManager))
        }
      }
    }
  }

  def isResolvedTo(reference: ResolvableReferenceElement, target: String) =
    reference.bind().exists(result => qualifiedNameOf(result.element) == target)

  def qualifiedNameOf(e: PsiNamedElement): String = e match {
    case it: PsiMember => Option(it.containingClass).map(_.qualifiedName + ".").getOrElse("") + it.name
    case it => it.name
  }

  def targetFor(result: ScalaResolveResult): String = {
    result.substitutor.updateThisType.collect {
      case t: ScThisType => t.element.qualifiedName + "." + result.element.name
      case t: ScDesignatorType => qualifiedNameOf(t.element) + "." + result.element.name
    } getOrElse {
      qualifiedNameOf(result.element)
    }
  }

  def annotationFor(t: ScType, context: PsiElement): ScTypeElement =
    UpdateStrategy.annotationsFor(t, context).head

  def appendTypeAnnotation(e: PsiElement, t: ScType): Unit = {
    val annotation = UpdateStrategy.annotationsFor(t, e).head
    appendTypeAnnotation(e, annotation)
  }

  def appendTypeAnnotation(e: PsiElement, annotation: ScTypeElement): Unit = {
    val whitespace = ScalaPsiElementFactory.createWhitespace(e.getManager)
    val colon = ScalaPsiElementFactory.createColon(e.getManager)

    val parent = e.getParent
    parent.addAfter(annotation, e)
    parent.addAfter(whitespace, e)
    parent.addAfter(colon, e)
  }

  object RenamedReference {
    def unapply(r: ScReferenceElement): Option[(String, String)] = {
      val id = r.nameId
      r.bind().flatMap(_.innerResolveResult).orElse(r.bind()).map(_.element) collect  {
        case target: PsiNamedElement if id.getText != target.name => (id.getText, target.name)
      }
    }
  }

  object QualifiedReference {
    def unapply(r: ScReferenceElement): Some[(Option[ScalaPsiElement], PsiElement)] =
      Some(r.qualifier, r.nameId)
  }
}
