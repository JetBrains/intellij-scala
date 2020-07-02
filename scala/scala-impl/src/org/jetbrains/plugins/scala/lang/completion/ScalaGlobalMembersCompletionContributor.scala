package org.jetbrains.plugins.scala
package lang
package completion

import com.intellij.codeInsight.completion._
import com.intellij.openapi.actionSystem.{ActionManager, IdeActions}
import com.intellij.openapi.keymap.KeymapUtil.getFirstKeyboardShortcutText
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.util.PsiTreeUtil.getContextOfType
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
 * @author Alexander Podkhalyuzin
 */
final class ScalaGlobalMembersCompletionContributor extends ScalaCompletionContributor {

  import ScalaGlobalMembersCompletionContributor._
  import global._

  //extension methods with import
  extend(
    CompletionType.BASIC,
    psiElement,
    new CompletionProvider[CompletionParameters] {
      override def addCompletions(parameters: CompletionParameters,
                                  context: ProcessingContext,
                                  resultSet: CompletionResultSet): Unit = {
        val invocationCount = parameters.getInvocationCount
        if (!regardlessAccessibility(invocationCount)) return

        for {
          reference@Qualifier(place@TypeWithoutImplicits(originalType)) <- findReference(parameters)
        } {
          val items = new ExtensionMethodsFinder(originalType, place)
            .lookupItems(reference, parameters.getOriginalFile)
          addGlobalCompletions(items, resultSet)
        }
      }
    }
  )

  extend(
    CompletionType.BASIC,
    identifierWithParentPattern(classOf[ScReferenceExpression]),
    new CompletionProvider[CompletionParameters] {
      override def addCompletions(parameters: CompletionParameters,
                                  context: ProcessingContext,
                                  resultSet: CompletionResultSet): Unit = {
        val reference = positionFromParameters(parameters)
          .getContext
          .asInstanceOf[ScReferenceExpression]

        val invocationCount = parameters.getInvocationCount
        val requiresAdvertisement = regardlessAccessibility(invocationCount)

        val matcher = resultSet.getPrefixMatcher
        val maybeFinder = reference match {
          case Qualifier(qualifier) =>
            for {
              TypeWithoutImplicits(placeType) <- Some(qualifier)

              originalType = toValueType(placeType)

              ClassOrTrait(definition) <- originalType.extractClass
            } yield new CompanionObjectMembersFinder.ExtensionLike(originalType, definition, requiresAdvertisement)(_)
          case _ =>
            val finder = if (requiresAdvertisement && matcher.getPrefix.nonEmpty)
              new StaticMembersFinder(reference, accessAll(invocationCount))(_)
            else
              new CompanionObjectMembersFinder.Regular(reference, requiresAdvertisement)(_)
            Some(finder)
        }

        val items = maybeFinder.map {
          _.apply(matcher.prefixMatches)
        }.fold(Seq.empty[ScalaLookupItem]) {
          _.lookupItems(reference, parameters.getOriginalFile)
        }
        addGlobalCompletions(items, resultSet, requiresAdvertisement)
      }
    }
  )
}

object ScalaGlobalMembersCompletionContributor {

  private def findReference(parameters: CompletionParameters): Option[ScReferenceExpression] =
    positionFromParameters(parameters).getContext match {
      case refExpr: ScReferenceExpression if getContextOfType(refExpr, classOf[ScalaFile]) != null => Some(refExpr)
      case _ => None
    }

  private def addGlobalCompletions(lookupItems: Seq[ScalaLookupItem],
                                   resultSet: CompletionResultSet,
                                   requiresAdvertisement: Boolean = true): Unit = {
    if (requiresAdvertisement && !lookupItems.forall(_.shouldImport)) {
      hintString.foreach(resultSet.addLookupAdvertisement)
    }

    import collection.JavaConverters._
    resultSet.addAllElements(lookupItems.asJava)
  }

  private def hintString: Option[String] =
    Option(ActionManager.getInstance.getAction(IdeActions.ACTION_SHOW_INTENTION_ACTIONS))
      .map(getFirstKeyboardShortcutText)
      .map(ScalaBundle.message("to.import.method.statically.press.hotkey", _))

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

  private object TypeWithoutImplicits {

    def unapply(qualifier: ScExpression): Option[ScType] =
      qualifier.getTypeWithoutImplicits().toOption
  }
}