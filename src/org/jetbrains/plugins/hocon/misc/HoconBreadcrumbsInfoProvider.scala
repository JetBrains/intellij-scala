package org.jetbrains.plugins.hocon.misc

import com.intellij.psi.PsiElement
import com.intellij.xml.breadcrumbs.BreadcrumbsInfoProvider
import org.jetbrains.plugins.hocon.lang.HoconLanguage
import org.jetbrains.plugins.hocon.psi.HKeyedField

class HoconBreadcrumbsInfoProvider extends BreadcrumbsInfoProvider {
  def getElementTooltip(e: PsiElement) = null

  def getElementInfo(e: PsiElement) = e match {
    case kf: HKeyedField => kf.key.map(_.stringValue).getOrElse("")
    case _ => ""
  }

  def acceptElement(e: PsiElement) = e match {
    case _: HKeyedField => true
    case _ => false
  }

  def getLanguages = Array(HoconLanguage)
}
