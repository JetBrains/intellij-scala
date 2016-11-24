package org.jetbrains.plugins.scala.extensions

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition

/**
 * @author Nikolay.Tropin
 */
object LazyVal {
  def unapply(pd: ScPatternDefinition): Option[ScPatternDefinition] = {
    val element =
      if (pd.isValid && pd.containingScalaFile.exists(_.isCompiled))
        pd.getNavigationElement
      else pd

    if (isLazyValInner(element)) Some(pd)
    else None
  }

  private def isLazyValInner(e: PsiElement) = e match {
    case pd: ScPatternDefinition => pd.hasModifierProperty("lazy")
    case _ => false
  }
}
