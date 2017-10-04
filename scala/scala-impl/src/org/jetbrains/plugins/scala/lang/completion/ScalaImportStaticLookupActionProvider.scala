package org.jetbrains.plugins.scala.lang.completion

import javax.swing.Icon

import com.intellij.codeInsight.lookup.{Lookup, LookupActionProvider, LookupElement, LookupElementAction}
import com.intellij.psi.PsiClass
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.{Consumer, PlatformIcons}
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem

/**
 * @author Alexander Podkhalyuzin
 */
class ScalaImportStaticLookupActionProvider extends LookupActionProvider {
  def fillActions(element: LookupElement, lookup: Lookup, consumer: Consumer[LookupElementAction]) {
    element match {
      case elem: ScalaLookupItem if elem.element.isInstanceOf[PsiClass] =>
      case elem: ScalaLookupItem =>
        if (!elem.isClassName) return
        if (elem.usedImportStaticQuickfix) return

        val checkIcon: Icon = PlatformIcons.CHECK_ICON
        val icon: Icon =
          if (!elem.shouldImport) checkIcon
          else EmptyIcon.create(checkIcon.getIconWidth, checkIcon.getIconHeight)
        consumer.consume(new LookupElementAction(icon, "Import method") {
          def performLookupAction: LookupElementAction.Result = {
            elem.usedImportStaticQuickfix = true
            new LookupElementAction.Result.ChooseItem(elem)
          }
        })
      case _ =>
    }
  }
}