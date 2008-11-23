package org.jetbrains.plugins.scala.lang.rename

import _root_.scala.collection.mutable.ArrayBuffer
import com.intellij.psi.PsiElement
import com.intellij.refactoring.util.TextOccurrencesUtil
import com.intellij.usageView.UsageInfo
import java.util.ArrayList
import psi.api.statements.params.{ScClassParameter, ScParameter}
import psi.api.statements.{ScFunction, ScValue, ScVariable}
import psi.api.toplevel.templates.ScTemplateBody
import psi.api.toplevel.{ScEarlyDefinitions, ScNamedElement}
import psi.ScalaPsiUtil

/**
 * User: Alexander Podkhalyuzin
 * Date: 23.11.2008
 */

object ScalaInplaceVariableRenamer {
  def mayImplaceRename(element: PsiElement, context: PsiElement): Boolean = {
    element match {
      case name: ScNamedElement => {
        ScalaPsiUtil.nameContext(name) match {
          case v@(_: ScValue | _: ScVariable | _: ScParameter | _: ScFunction) if !v.getParent.isInstanceOf[ScTemplateBody] &&
                  !v.isInstanceOf[ScClassParameter] && !v.getParent.isInstanceOf[ScEarlyDefinitions] => {
            val stringToSearch = name.getName
            val usages = new ArrayList[UsageInfo]
            if (stringToSearch != null) {
              TextOccurrencesUtil.addUsagesInStringsAndComments(element, stringToSearch, usages, new TextOccurrencesUtil.UsageInfoFactory {
                def createUsageInfo(usage: PsiElement, startOffset: Int, endOffset: Int): UsageInfo = new UsageInfo(usage)
              }, true);
            }
            return usages.isEmpty
          }
          case _ => false
        }
      }
      case _ => false
    }
  }
}