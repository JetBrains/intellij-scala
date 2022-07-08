package org.jetbrains.plugins.scala.lang.transformation

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInsight.intention.types.AddOnlyStrategy
import org.jetbrains.plugins.scala.extensions.{&&, FirstChild}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScSimpleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.types.ScType

package object annotations {

  import AddOnlyStrategy._

  def appendTypeAnnotation(`type`: ScType, anchor: PsiElement): Unit =
    appendTypeAnnotation(`type`) {
      addActualType(_, anchor)
    }

  def appendTypeAnnotation(`type`: ScType)
                          (function: ScTypeElement => PsiElement): Unit =
    annotationsFor(`type`).headOption
      .map(function)
      .foreach {
        case (t: ScSimpleTypeElement) && FirstChild(reference: ScReference) =>
          bindTo(reference, t.getText)
        case _ => // TODO support compound types
      }
}
