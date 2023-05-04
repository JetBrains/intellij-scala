package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSimpleTypeElement
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.{getInstance => ScalaApplicationSettings}
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil

trait TypePresentationContext {
  def nameResolvesTo(name: String, target: PsiElement): Boolean
  def compoundTypeWithAndToken: Boolean // TODO isScala3

  final def compoundTypeSeparatorText: String =
    if (compoundTypeWithAndToken) (if (ScalaApplicationSettings.PRECISE_TEXT) " with " else " & ") // SCL-21195
    else " with "
}

object TypePresentationContext {

  import scala.language.implicitConversions

  def apply(place: PsiElement): TypePresentationContext = psiElementPresentationContext(place)

  implicit def psiElementPresentationContext(place: PsiElement): TypePresentationContext = new TypePresentationContext {
    override def nameResolvesTo(name: String, target: PsiElement): Boolean =
      if (place.isValid) {
        val context = place.getContext
        if (context != null) {
          val element = ScalaPsiElementFactory.createTypeElementFromText(name, context, place)
          element match {
            case ScSimpleTypeElement(ResolvesTo(reference)) =>
              ScEquivalenceUtil.smartEquivalence(reference, target)
            case _ => false
          }
        } else true
      }
      else true //let's just show short version for invalid elements

    override lazy val compoundTypeWithAndToken: Boolean = place.containingFile.exists(_.isScala3OrSource3Enabled)
  }

  val emptyContext: TypePresentationContext = new TypePresentationContext {
    override def nameResolvesTo(name: String, target: PsiElement): Boolean = false
    override def compoundTypeWithAndToken: Boolean = false
  }
}
