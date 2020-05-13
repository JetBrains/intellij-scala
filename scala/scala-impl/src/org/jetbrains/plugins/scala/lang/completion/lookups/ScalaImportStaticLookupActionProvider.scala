package org.jetbrains.plugins.scala
package lang
package completion
package lookups

import com.intellij.codeInsight.lookup.{Lookup, LookupActionProvider, LookupElement, LookupElementAction}
import com.intellij.psi.PsiClass
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.{Consumer, PlatformIcons}

/**
 * @author Alexander Podkhalyuzin
 */
final class ScalaImportStaticLookupActionProvider extends LookupActionProvider {

  override def fillActions(element: LookupElement, lookup: Lookup, consumer: Consumer[LookupElementAction]): Unit = {
    element match {
      case elem: ScalaLookupItem if elem.element.isInstanceOf[PsiClass] =>
      case elem: ScalaLookupItem =>
        if (!elem.isClassName) return
        if (elem.usedImportStaticQuickfix) return

        val checkIcon = PlatformIcons.CHECK_ICON
        val icon =
          if (!elem.shouldImport) checkIcon
          else EmptyIcon.create(checkIcon.getIconWidth, checkIcon.getIconHeight)
        consumer.consume(new LookupElementAction(icon, ScalaBundle.message("action.import.method")) {
          override def performLookupAction: LookupElementAction.Result = {
            elem.usedImportStaticQuickfix = true
            new LookupElementAction.Result.ChooseItem(elem)
          }
        })
      case _ =>
    }
  }
}