package org.jetbrains.plugins.scala.lang.completion

import com.intellij.util.Consumer
import com.intellij.codeInsight.lookup.{LookupElementAction, Lookup, LookupElement, LookupActionProvider}
import com.intellij.psi.{PsiType, PsiClass}
import com.intellij.codeInsight.daemon.impl.actions.AddImportAction
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiUtil
import collection.JavaConversions
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils.ScalaLookupObject
import com.intellij.codeInsight.lookup.LookupElementAction.Result

/**
 * User: Alexander Podkhalyuzin
 * Date: 21.01.2010
 */

class ScalaExcludeFromCompletionLookupActionProvider extends LookupActionProvider {
  def fillActions(element: LookupElement, lookup: Lookup, consumer: Consumer[LookupElementAction]) {
    val o: Object = element.getObject
    var clazz: PsiClass = null
    o match {
      case cl: PsiClass => clazz = cl
      case _ => //do nothing, this way handled in ExludeFromCompletionLookupActionProvider
    }
    if (clazz != null && clazz.isValid) {
      val qname: String = clazz.getQualifiedName
      if (qname != null) {
        val project: Project = clazz.getProject
        import JavaConversions._
        for (s <- AddImportAction.getAllExcludableStrings(qname)) {
          consumer.consume(new ExcludeFromCompletionAction(project, s))
        }
      }
    }
  }

  private class ExcludeFromCompletionAction(project: Project, s: String) extends LookupElementAction(null, "Exclude '" + s + "' from completion") {
    def performLookupAction: Result = {
      AddImportAction.excludeFromImport(project, s)
      return Result.HIDE_LOOKUP
    }
  }
}