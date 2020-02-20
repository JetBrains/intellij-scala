package org.jetbrains.plugins.scala
package lang
package completion

import com.intellij.codeInsight.completion._
import com.intellij.openapi.actionSystem.{ActionManager, IdeActions}
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr._

import scala.collection.JavaConverters

/**
 * @author Alexander Podkhalyuzin
 */
final class ScalaGlobalMembersCompletionContributor extends ScalaCompletionContributor {

  import ScalaGlobalMembersCompletionContributor._

  //extension methods with import
  extend(
    CompletionType.BASIC,
    PlatformPatterns.psiElement,
    new CompletionProvider[CompletionParameters] {
      def addCompletions(parameters: CompletionParameters,
                         context: ProcessingContext,
                         resultSet: CompletionResultSet): Unit = {
        val invocationCount = parameters.getInvocationCount
        if (invocationCount < 2) return

        for {
          refExpr <- findReference(parameters)
          qual    <- qualifier(refExpr)
          finder  <- ExtensionMethodsFinder(qual)
        } {
          val items = finder.lookupItems(parameters.getOriginalFile, refExpr)
          addGlobalCompletions(items, resultSet)
        }
      }
    }
  )

  //static members with import
  extend(
    CompletionType.BASIC,
    PlatformPatterns.psiElement,
    new CompletionProvider[CompletionParameters] {
      def addCompletions(parameters: CompletionParameters,
                         context: ProcessingContext,
                         resultSet: CompletionResultSet): Unit = {
        val invocationCount = parameters.getInvocationCount

        if (invocationCount < 2) return
        val accessAll = invocationCount >= 3

        for {
          refExpr <- findReference(parameters)
          if qualifier(refExpr).isEmpty
          finder  <- StaticMembersFinder(refExpr, resultSet.getPrefixMatcher, accessAll)
        } {
          val items = finder.lookupItems(parameters.getOriginalFile, refExpr)
          addGlobalCompletions(items, resultSet)
        }
      }
    }
  )

}

object ScalaGlobalMembersCompletionContributor {

  private def findReference(parameters: CompletionParameters): Option[ScReferenceExpression] =
    positionFromParameters(parameters).getContext match {
      case refExpr: ScReferenceExpression if PsiTreeUtil.getContextOfType(refExpr, classOf[ScalaFile]) != null => Some(refExpr)
      case _ => None
    }

  private def addGlobalCompletions(lookupItems: Seq[ScalaLookupItem], resultSet: CompletionResultSet): Unit = {
    if (lookupItems.exists(!_.shouldImport)) {
      hintString.foreach(resultSet.addLookupAdvertisement)
    }

    import JavaConverters._
    resultSet.addAllElements(lookupItems.asJava)
  }

  private def hintString: Option[String] =
    Option(ActionManager.getInstance.getAction(IdeActions.ACTION_SHOW_INTENTION_ACTIONS)).map { action =>
      ScalaBundle.message("to.import.method.statically.press.hotkey", KeymapUtil.getFirstKeyboardShortcutText(action))
    }

  private def qualifier(refExpr: ScReferenceExpression) = refExpr.qualifier.orElse(desugaredQualifier(refExpr))

  private def stringContextQualifier(lit: ScInterpolatedStringLiteral): Option[ScExpression] =
    lit.desugaredExpression.flatMap {
      case (reference: ScReferenceExpression, _) => reference.qualifier
      case _ => None
    }

  private def desugaredQualifier(refExpr: ScReferenceExpression): Option[ScExpression] =
    refExpr.getContext match {
      case ScSugarCallExpr(baseExpression, `refExpr`, _) => Option(baseExpression)
      case lit: ScInterpolatedStringLiteral              => stringContextQualifier(lit)
      case _                                             => None
    }
}