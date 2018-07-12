package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSimpleTypeElement
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil
import org.jetbrains.plugins.scala.extensions._

trait TypePresentationContext {
  def nameResolvesTo(name: String, target: PsiElement): Boolean
}

object TypePresentationContext {
  import scala.language.implicitConversions

  implicit def psiElementPresentationContext(place: PsiElement): TypePresentationContext = (text, target) => {

    if (place.isValid) {

      val typeElem = ScalaPsiElementFactory.createTypeElementFromText(text, place.getContext, place)

      val reference = (typeElem match {
        case null                         => None
        case ScSimpleTypeElement(Some(r)) => Some(r)
        case _                            => None
      }).flatMap(_.resolve.toOption)

      reference.exists(ScEquivalenceUtil.smartEquivalence(_, target))
    }
    else true //let's just show short version for invalid elements
  }

  implicit val emptyContext: TypePresentationContext = (_, _) => false
}
