package org.jetbrains.plugins.scala.scalai18n.lang.properties

import com.intellij.lang.properties.IProperty
import com.intellij.psi._
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

/**
 * @author Ksenia.Sautina
 * @since 7/17/12
 */

class ScalaPropertiesReferenceProvider extends PsiReferenceProvider {
  override def acceptsTarget(target: PsiElement): Boolean = {
    target.isInstanceOf[IProperty]
  }

  def getReferencesByElement(element: PsiElement, context: ProcessingContext): Array[PsiReference] = {
    if (ScalaProjectSettings.getInstance(element.getProject).isDisableI18N) return Array.empty

    object PossibleKey {
      def unapply(literal: ScLiteral): Option[String] = {
        if (!literal.isString) return None
        literal.getValue match {
          case str: String if !str.contains(" ") => Some(str)
          case _ => None
        }
      }
    }

    element match {
      case PossibleKey(str) =>
        Array(new PropertyScalaReference(str, element))
      case _ => Array.empty
    }
  }
}
