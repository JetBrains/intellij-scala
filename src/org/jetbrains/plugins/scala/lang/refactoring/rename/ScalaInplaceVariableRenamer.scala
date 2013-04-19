package org.jetbrains.plugins.scala
package lang
package refactoring
package rename

import com.intellij.refactoring.util.TextOccurrencesUtil
import com.intellij.usageView.UsageInfo
import java.util.ArrayList
import psi.api.base.patterns.ScCaseClause
import psi.api.statements.{ScFunction, ScValue, ScVariable}
import psi.api.toplevel.templates.ScTemplateBody
import psi.api.toplevel.{ScEarlyDefinitions, ScNamedElement}
import psi.ScalaPsiUtil
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScEnumerator, ScGenerator, ScFunctionExpr}
import psi.api.statements.params.{ScParameters, ScClassParameter, ScParameter}
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.{PsiReference, PsiElement}
import com.intellij.util.Processor

/**
 * User: Alexander Podkhalyuzin
 * Date: 23.11.2008
 */

object ScalaInplaceVariableRenamer {
  def myRenameInPlace(element: PsiElement, context: PsiElement): Boolean = {
    element match {
      case name: ScNamedElement => {
        def isOk(elem: PsiElement): Boolean = {
          elem match {
            case v@(_: ScValue | _: ScVariable | _: ScFunction | _: ScCaseClause | _: ScGenerator | _: ScEnumerator)
              if !v.getParent.isInstanceOf[ScTemplateBody] &&
                    !v.isInstanceOf[ScClassParameter] && !v.getParent.isInstanceOf[ScEarlyDefinitions] => true
            case p: ScParameter if PsiTreeUtil.getParentOfType(p, classOf[ScParameters]).getParent.isInstanceOf[ScFunctionExpr] => true
            case _ => false
          }
        }

        ScalaPsiUtil.nameContext(name) match {
          case elem if isOk(elem) => {
            val stringToSearch = name.name
            val usages = new ArrayList[UsageInfo]
            // See: SCL-4336
            var hasBackTickedRef = false
            if (stringToSearch != null) {
              TextOccurrencesUtil.addUsagesInStringsAndComments(element, stringToSearch, usages, new TextOccurrencesUtil.UsageInfoFactory {
                def createUsageInfo(usage: PsiElement, startOffset: Int, endOffset: Int): UsageInfo = new UsageInfo(usage)
              })
              ReferencesSearch.search(element).forEach(new Processor[PsiReference] {
                def process(t: PsiReference): Boolean = {
                  if (t.getElement.getText.contains("`")) {
                    hasBackTickedRef = true
                    false
                  } else true
                }
              })
            }
            usages.isEmpty && !hasBackTickedRef
          }
          case _ => false
        }
      }
      case _ => false
    }
  }
}