package org.jetbrains.plugins.scala.lang
package psi
package light.scala

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result._

import scala.annotation.tailrec

/**
  * @author Alefas
  * @since 04/04/14.
  */
final class ScLightFieldId private(override protected val delegate: ScFieldId)
                                  (implicit private val returnType: ScType)
  extends ScLightElement(delegate) with ScFieldId {

  override def getNavigationElement: PsiElement = super.getNavigationElement

  override def `type`(): TypeResult = Right(returnType)

  override def getParent: PsiElement = delegate.getParent //to find right context
}

object ScLightFieldId {

  @tailrec
  def apply(fieldId: ScFieldId)
           (implicit returnType: ScType): ScLightFieldId = fieldId match {
    case light: ScLightFieldId => apply(light.delegate)
    case definition: ScFieldId => new ScLightFieldId(definition)
  }
}