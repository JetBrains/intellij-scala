package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSimpleTypeElement
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil

trait TypePresentationContext {
  def nameResolvesTo(name: String, target: PsiElement): Boolean
}

object TypePresentationContext {
  import scala.language.implicitConversions

  def apply(place: PsiElement): TypePresentationContext = psiElementPresentationContext(place)

  implicit def psiElementPresentationContext(place: PsiElement): TypePresentationContext = (text, target) => {
    if (place.isValid) {
      ScalaPsiElementFactory.createTypeElementFromText(text, place.getContext, place) match {
        case ScSimpleTypeElement(ResolvesTo(reference)) => ScEquivalenceUtil.smartEquivalence(reference, target)
        case _ => false
      }
    }
    else true //let's just show short version for invalid elements
  }

  val emptyContext: TypePresentationContext = (_, _) => false
}
