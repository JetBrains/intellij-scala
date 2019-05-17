package org.jetbrains.plugins.scala
package lang
package completion

import com.intellij.codeInsight.completion._
import com.intellij.openapi.actionSystem.{ActionManager, IdeActions}
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr._

import scala.collection.JavaConverters

/**
  * @author Alexander Podkhalyuzin
  */
final class ScalaGlobalMembersCompletionContributor extends ScalaCompletionContributor {

  import ScalaGlobalMembersCompletionContributor._

  extend(
    CompletionType.BASIC,
    PlatformPatterns.psiElement,
    new CompletionProvider[CompletionParameters] {
      def addCompletions(parameters: CompletionParameters,
                         context: ProcessingContext,
                         resultSet: CompletionResultSet): Unit = {
        val invocationCount = parameters.getInvocationCount
        if (invocationCount < 2) return

        positionFromParameters(parameters).getContext match {
          case refExpr: ScReferenceExpression if PsiTreeUtil.getContextOfType(refExpr, classOf[ScalaFile]) != null =>

            val qualifier = refExpr.qualifier.orElse(desugaredQualifier(refExpr))

            val globalFinder = qualifier match {
              case None       => StaticMembersFinder(refExpr, resultSet.getPrefixMatcher, accessAll = invocationCount >= 3)
              case Some(qual) => ExtensionMethodsFinder(qual)
            }

            globalFinder.foreach { finder =>

              val lookupItems = finder.lookupItems(parameters.getOriginalFile, refExpr)
              if (CompletionService.getCompletionService.getAdvertisementText != null &&
                lookupItems.exists(!_.shouldImport)) {
                hintString.foreach(resultSet.addLookupAdvertisement)
              }

              import JavaConverters._
              resultSet.addAllElements(lookupItems.asJava)
            }
          case _ =>
        }
      }
    }
  )
}

object ScalaGlobalMembersCompletionContributor {



  private def staticMembersFinder(place: ScReferenceExpression, prefixMatcher: PrefixMatcher, accessAll: Boolean): Option[StaticMembersFinder] =
    if (prefixMatcher.getPrefix.nonEmpty) Some(new StaticMembersFinder(place, prefixMatcher, accessAll))
    else None


  private def extensionMethodsFinder(qualifier: ScExpression): Option[ExtensionMethodsFinder] = {
    val qualifierType = qualifier.getTypeWithoutImplicits().toOption

    qualifierType.map(new ExtensionMethodsFinder(_, qualifier))
  }

  private def hintString: Option[String] =
    Option(ActionManager.getInstance.getAction(IdeActions.ACTION_SHOW_INTENTION_ACTIONS)).map { action =>
      "To import a method statically, press " + KeymapUtil.getFirstKeyboardShortcutText(action)
    }

  private def stringContextQualifier(lit: ScInterpolatedStringLiteral): Option[ScExpression] =
    lit.getStringContextExpression.flatMap {
      case ScMethodCall(ref: ScReferenceExpression, _) => ref.qualifier
      case _                                           => None
    }

  private def desugaredQualifier(refExpr: ScReferenceExpression): Option[ScExpression] =
    refExpr.getContext match {
      case ScSugarCallExpr(baseExpression, `refExpr`, _) => Option(baseExpression)
      case lit: ScInterpolatedStringLiteral              => stringContextQualifier(lit)
      case _                                             => None
    }
}