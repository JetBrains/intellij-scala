package org.jetbrains.plugins.scala.lang.completion.lookups

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import com.intellij.psi.PsiElement
import com.intellij.codeInsight.lookup.{LookupElementPresentation, LookupItem}
import com.intellij.util.ui.EmptyIcon

/**
 * @author Alefas
 * @since 27.03.12
 */

class ScalaKeywordLookupItem(val keyword: String, position: PsiElement) extends {
  val keywordPsi: PsiElement = new ScalaLightKeyword(position.getManager, keyword)
} with LookupItem[PsiElement](keywordPsi, keyword) {
  override def hashCode(): Int = keyword.hashCode

  override def equals(o: Any): Boolean = {
    o match {
      case s: ScalaKeywordLookupItem => s.keyword == keyword
      case _ => false
    }
  }

  override def renderElement(presentation: LookupElementPresentation) {
    presentation.setItemText(keyword)
    presentation.setItemTextBold(true)
    presentation.setIcon(new EmptyIcon(16, 16))
  }
}
