package org.jetbrains.plugins.scala.lang.completion

import javax.swing.Icon
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.{Consumer, Icons}
import com.intellij.codeInsight.lookup.{LookupElement, Lookup, LookupElementAction, LookupActionProvider}
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils.ScalaLookupObject
import com.intellij.psi.PsiClass

/**
 * @author Alexander Podkhalyuzin
 */
class ScalaImportStaticLookupActionProvider extends LookupActionProvider {
  def fillActions(element: LookupElement, lookup: Lookup, consumer: Consumer[LookupElementAction]) {
    val isClassName = element.getUserData(ResolveUtils.classNameKey)
    if (isClassName == null || !isClassName.booleanValue()) return

    element.getObject match {
      case ScalaLookupObject(elem, _, _, _) if elem.isInstanceOf[PsiClass] => return
      case _ =>
    }

    val shouldImport = element.getUserData(ResolveUtils.shouldImportKey)
    if (shouldImport == null) return

    val checkIcon: Icon = Icons.CHECK_ICON
    val icon: Icon =
      if (!shouldImport.booleanValue()) checkIcon
      else new EmptyIcon(checkIcon.getIconWidth, checkIcon.getIconHeight)
    consumer.consume(new LookupElementAction(icon, "Import method") {
      def performLookupAction: LookupElementAction.Result = {
        element.putUserData(ResolveUtils.usedImportStaticQuickfixKey, java.lang.Boolean.TRUE)
        new LookupElementAction.Result.ChooseItem(element)
      }
    })
  }
}