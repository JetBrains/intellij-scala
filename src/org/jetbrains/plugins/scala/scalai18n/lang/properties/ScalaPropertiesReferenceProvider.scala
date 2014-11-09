package org.jetbrains.plugins.scala.scalai18n.lang.properties

import com.intellij.lang.properties.IProperty
import com.intellij.lang.properties.references.PropertyReference
import com.intellij.psi._
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScLiteral}

/**
 * @author Ksenia.Sautina
 * @since 7/17/12
 */

class ScalaPropertiesReferenceProvider extends PsiReferenceProvider {
  override def acceptsTarget(target: PsiElement): Boolean = {
    target.isInstanceOf[IProperty]
  }

  def getReferencesByElement(element: PsiElement, context: ProcessingContext): Array[PsiReference] = {
    object PossibleKey {
      def unapply(lit: ScLiteral): Option[String] = {
        if (!lit.isString || lit.isMultiLineString || lit.isInstanceOf[ScInterpolatedStringLiteral])
          return None

        lit.getValue match {
          case str: String if !str.contains(" ") => Some(str)
          case _ => None
        }
      }
    }

    element match {
      case PossibleKey(str) =>
        Array(new PropertyReference(str, element, null, true))
      case _ => Array.empty
    }
  }
}
