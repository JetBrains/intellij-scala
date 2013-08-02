package org.jetbrains.plugins.scala
package lang.refactoring.rename.inplace

import com.intellij.refactoring.util.TextOccurrencesUtil
import com.intellij.usageView.UsageInfo
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScEnumerator, ScGenerator, ScFunctionExpr}
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.{PsiNamedElement, PsiReference, PsiElement}
import com.intellij.util.Processor
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScEarlyDefinitions, ScNamedElement}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScVariable, ScValue}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameters, ScParameter, ScClassParameter}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import java.util

/**
 * User: Alexander Podkhalyuzin
 * Date: 23.11.2008
 */

object ScalaInplaceRenameUtil {

  def isLocallyDefined(elem: PsiNamedElement): Boolean = {
    try {
      ScalaPsiUtil.nameContext(elem) match {
        case v@(_: ScValue | _: ScVariable | _: ScFunction | _: ScCaseClause | _: ScGenerator | _: ScEnumerator)
          if !v.getParent.isInstanceOf[ScTemplateBody] &&
                  !v.isInstanceOf[ScClassParameter] && !v.getParent.isInstanceOf[ScEarlyDefinitions] => true
        case p: ScParameter if PsiTreeUtil.getParentOfType(p, classOf[ScParameters]).getParent.isInstanceOf[ScFunctionExpr] => true
        case _ => false
      }
    }
    catch {
      case e: Exception => false
    }
  }

  def myRenameInPlace(element: PsiElement, context: PsiElement): Boolean = {
    element match {
      case named: ScNamedElement if isLocallyDefined(named) =>
        val stringToSearch = named.name
        val usages = new util.ArrayList[UsageInfo]
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

      case _ => false
    }
  }
}