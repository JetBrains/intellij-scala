package org.jetbrains.plugins.hocon.misc

import com.intellij.psi.PsiElement
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider
import org.jetbrains.plugins.hocon.lang.HoconLanguage
import org.jetbrains.plugins.hocon.psi.HKeyedField

class HoconBreadcrumbsInfoProvider extends BreadcrumbsProvider {

  def getElementInfo(e: PsiElement): String = e match {
    case kf: HKeyedField => kf.key.map(_.stringValue).getOrElse("")
    case _ => ""
  }

  def acceptElement(e: PsiElement): Boolean = e match {
    case _: HKeyedField => true
    case _ => false
  }

  def getLanguages = Array(HoconLanguage)
}
