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

  override def fillActions(element: LookupElement,
                           lookup: Lookup,
                           consumer: Consumer[LookupElementAction]): Unit = element match {
    case element: ScalaLookupItem if element.isClassName &&
      !element.usedImportStaticQuickfix &&
      !element.getPsiElement.isInstanceOf[PsiClass] =>

      import PlatformIcons.{CHECK_ICON => checkIcon}
      val icon = if (element.shouldImport)
        EmptyIcon.create(checkIcon.getIconWidth, checkIcon.getIconHeight)
      else
        checkIcon

      consumer.consume(new LookupElementAction(icon, ScalaBundle.message("action.import.method")) {

        import LookupElementAction.Result.ChooseItem

        override def performLookupAction: ChooseItem = {
          element.usedImportStaticQuickfix = true
          new ChooseItem(element)
        }
      })
    case _ =>
  }
}