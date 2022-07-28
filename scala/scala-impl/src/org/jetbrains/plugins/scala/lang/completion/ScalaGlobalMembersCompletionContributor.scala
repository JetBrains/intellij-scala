package org.jetbrains.plugins.scala
package lang
package completion

import com.intellij.codeInsight.completion._
import com.intellij.openapi.actionSystem.{ActionManager, IdeActions}
import com.intellij.openapi.keymap.KeymapUtil.getFirstKeyboardShortcutText
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScReferenceExpression, ScSugarCallExpr}

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

        val finders = reference match {
          case Qualifier(place) =>
            place
              .getTypeWithoutImplicits()
              .toSeq
              .flatMap { originalType =>
                ByTypeGlobalMembersFinder(
                  originalType,
                  place,
                  invocationCount,
                )
              }
          case place =>
            ByPlaceGlobalMembersFinder(
              place,
              resultSet.getPrefixMatcher,
              invocationCount,
            )
        }

        val lookupItems = finders.flatMap(_.lookupItems)

        if (regardlessAccessibility(invocationCount) && !lookupItems.forall {
          case item: ScalaLookupItem => item.shouldImport
          case _ => false
        }) {
          addLookupAdvertisement(resultSet)
        }

        resultSet.addAllElements(lookupItems)
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