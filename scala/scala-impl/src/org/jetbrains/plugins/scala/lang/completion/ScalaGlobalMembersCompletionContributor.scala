package org.jetbrains.plugins.scala
package lang
package completion

import com.intellij.codeInsight.completion._
import com.intellij.openapi.actionSystem.{ActionManager, IdeActions}
import com.intellij.openapi.keymap.KeymapUtil.getFirstKeyboardShortcutText
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScReferenceExpression, ScSugarCallExpr}

/**
 * @author Alexander Podkhalyuzin
 */
final class ScalaGlobalMembersCompletionContributor extends ScalaCompletionContributor {

  import ScalaGlobalMembersCompletionContributor._
  import global._

  extend(
    CompletionType.BASIC,
    psiElement.withParent(classOf[ScReferenceExpression]),
    new CompletionProvider[CompletionParameters] {

      override def addCompletions(parameters: CompletionParameters,
                                  context: ProcessingContext,
                                  resultSet: CompletionResultSet): Unit = {
        val reference = positionFromParameters(parameters)
          .getContext
          .asInstanceOf[ScReferenceExpression]

        val invocationCount = parameters.getInvocationCount
        val requiresAdvertisement = regardlessAccessibility(invocationCount)

        val maybeFinder = reference match {
          case Qualifier(place) =>
            place
              .getTypeWithoutImplicits()
              .toOption
              .map {
                new ExtensionMethodsFinder(_, place, requiresAdvertisement)
              }
          case _ =>
            val matcher = resultSet.getPrefixMatcher
            val finder = if (requiresAdvertisement && matcher.getPrefix.nonEmpty)
              new StaticMembersFinder(reference, accessAll(invocationCount))(matcher.prefixMatches)
            else
              new CompanionObjectMembersFinder.Regular(reference, requiresAdvertisement)

            Some(finder)
        }

        val items = maybeFinder
          .toIterable
          .flatMap {
            _.lookupItems(reference, parameters.getOriginalFile)
          }

        if (requiresAdvertisement && !items.forall(_.shouldImport)) {
          addLookupAdvertisement(resultSet)
        }

        resultSet.addAllElements(items)
      }
    }
  )
}

object ScalaGlobalMembersCompletionContributor {

  private def addLookupAdvertisement(resultSet: CompletionResultSet): Unit =
    Option(ActionManager.getInstance.getAction(IdeActions.ACTION_SHOW_INTENTION_ACTIONS))
      .map(getFirstKeyboardShortcutText)
      .map(ScalaBundle.message("to.import.method.statically.press.hotkey", _))
      .foreach(resultSet.addLookupAdvertisement)

  private object Qualifier {

    def unapply(reference: ScReferenceExpression): Option[ScExpression] =
      reference.qualifier.orElse {
        desugaredQualifier(reference)
      }

    private[this] def stringContextQualifier(literal: ScInterpolatedStringLiteral) =
      literal.desugaredExpression.flatMap {
        case (reference: ScReferenceExpression, _) => reference.qualifier
        case _ => None
      }

    private[this] def desugaredQualifier(reference: ScReferenceExpression) =
      reference.getContext match {
        case ScSugarCallExpr(baseExpression, `reference`, _) => Option(baseExpression)
        case literal: ScInterpolatedStringLiteral => stringContextQualifier(literal)
        case _ => None
      }
  }
}