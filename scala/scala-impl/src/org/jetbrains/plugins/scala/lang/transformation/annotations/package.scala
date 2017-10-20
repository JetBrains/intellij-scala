package org.jetbrains.plugins.scala.lang.transformation

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInsight.intention.types.AddOnlyStrategy
import org.jetbrains.plugins.scala.extensions.{&&, FirstChild}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScSimpleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
  * @author Pavel Fatin
  */
package object annotations {

  import AddOnlyStrategy._

  def appendTypeAnnotation(`type`: ScType, anchor: PsiElement)
                          (function: ScTypeElement => PsiElement = addActualType(_, anchor)): Unit =
    annotationFor(`type`, anchor)
      .map(function)
      .foreach {
        case (t: ScSimpleTypeElement) && FirstChild(r: ScReferenceElement) =>
          bindTo(r, t.getText)
        case _ => // TODO support compound types
      }
}
