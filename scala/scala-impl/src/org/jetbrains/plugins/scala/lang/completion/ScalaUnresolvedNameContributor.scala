package org.jetbrains.plugins.scala.lang.completion

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.daemon.impl.{DaemonCodeAnalyzerEx, HighlightInfo}
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementWeigher}
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile, PsiReference}
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, StringsExt, childOf}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScFieldId, ScReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDeclaration, ScTypeAliasDeclaration}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester
import org.jetbrains.plugins.scala.lang.resolve.ResolveTargets._

import scala.collection.mutable

/** Contributor adds unresolved names in current scope to completion list.
  * Unresolved reference name adds to completion list, according to [[ScReferenceElement.getKinds()]]
  *
  * Usages:
  * def <caret>
  * val/var <caret>
  * type <caret>
  * trait <caret>
  * [case] class/object <caret>
  *
  * For references with parameters(methods, classes), completion list contains parameters with types.
  * For references with parameters(methods, classes) completion after `object` keyword generates apply method with parameters.
  * Created by  Kate Ustyuzhanina on 17/03/2017.
  */
class ScalaUnresolvedNameContributor extends ScalaCompletionContributor {

  import ScalaUnresolvedNameContributor._

  extend(
    CompletionType.BASIC,
    PlatformPatterns.psiElement,
    new CompletionProvider[CompletionParameters] {

      override def addCompletions(parameters: CompletionParameters,
                                  processingContext: ProcessingContext,
                                  resultSet: CompletionResultSet): Unit = {
        positionFromParameters(parameters).getContext match {
          case owner: ScModifierListOwner if owner.hasModifierPropertyScala("override") =>
          case (_: ScFieldId) childOf (_ childOf (owner: ScModifierListOwner)) if owner.hasModifierPropertyScala("override") =>
          case declaration@(_: ScTypeDefinition | _: ScTypeAliasDeclaration | _: ScFunctionDeclaration | _: ScFieldId) =>
            val sorter = CompletionSorter.defaultSorter(parameters, resultSet.getPrefixMatcher)
              .weighAfter("prefix", ScalaTextLookupItem.Weigher)
            val newResultSet = resultSet.withRelevanceSorter(sorter)

            processHighlights(declaration, parameters.getOriginalFile) {
              handleReferencesInScope(_, declaration, newResultSet)
            }
          case _ =>
        }
      }
    })

}

object ScalaUnresolvedNameContributor {

  private val ReservedNames = Set(
    "+", "-", "*", "/", "%", ">", "<", "&&", "||", "&", "|", "==", "!=", "^", "<<", ">>", ">>>"
  )

  private def handleReferencesInScope(ref: ScReferenceElement,
                                      position: PsiElement,
                                      resultSet: CompletionResultSet): Unit = {
    def isKindAcceptable = {
      def refWithMethodCallParent = ref.parent.exists(_.isInstanceOf[ScMethodCall])

      def refKinds = ref.getKinds(incomplete = false)

      position match {
        case _: ScFieldId if !refWithMethodCallParent => refKinds.contains(VAL) || refKinds.contains(VAR)
        case _: ScObject => refKinds.contains(OBJECT)
        case cl: ScClass if cl.isCase && refWithMethodCallParent => refKinds.contains(OBJECT) || refKinds.contains(CLASS)
        case _: ScTypeDefinition | _: ScTypeAliasDeclaration => refKinds.contains(CLASS)
        case _: ScFunctionDeclaration => refKinds.contains(METHOD)
        case _ => false
      }
    }


    if (!ref.parent.exists(_.isInstanceOf[PsiReference]) &&
      !ReservedNames(ref.refName) &&
      isKindAcceptable) {

      val maybeArgumentList: Option[Seq[ScExpression]] = ref.getParent match {
        case MethodInvocation(`ref`, expressions) => Some(expressions) // TODO: List(1, 2) map myFunc -> to support
        case parent =>
          PsiTreeUtil.getNextSiblingOfType(parent, classOf[ScArgumentExprList]) match {
            case null => None
            case list => Some(list.exprs)
          }
      }

      val lookupElement = ScalaTextLookupItem(
        ref.refName,
        maybeArgumentList.map(createArguments).getOrElse(""),
        position
      )
      resultSet.addElement(lookupElement)
    }
  }

  private def processHighlights(position: PsiElement, originalFile: PsiFile)
                               (onReference: ScReferenceElement => Unit) = {
    val scope = position.scopes.toSeq.headOption.collect {
      case element: ScalaPsiElement => element.getSameElementInContext
    }.getOrElse(originalFile)

    val scopeRange = scope.getTextRange

    DaemonCodeAnalyzerEx.processHighlights(
      originalFile.getViewProvider.getDocument,
      position.getProject,
      HighlightSeverity.ERROR,
      scopeRange.getStartOffset,
      scopeRange.getEndOffset,
      (highlightInfo: HighlightInfo) => {
        val startOffset = highlightInfo.getStartOffset
        val maybeReference = originalFile.findReferenceAt(startOffset) match {
          case (_: ScReferenceElement) childOf ((_: ScAssignStmt) childOf (_: ScArgumentExprList)) => None
          case reference: ScReferenceElement if reference.getTextRange.containsRange(startOffset, highlightInfo.getEndOffset) => Some(reference)
          case _ => None
        }

        maybeReference.foreach(onReference)

        true
      })
  }


  private[this] def createArguments(argList: Seq[ScExpression]) = {
    case class Parameter(name: Option[String], `type`: Option[ScType]) {

      override def toString: String = name.zip(`type`).map {
        case (n, t) => s"$n: $t"
      }.headOption.getOrElse("")
    }

    def computeType(exprs: ScExpression): Option[ScType] = Option(exprs.`type`().getOrAny)

    val names: mutable.Map[String, Int] = mutable.HashMap.empty[String, Int]

    def suggestUniqueName(tp: ScType): String = {
      val name = NameSuggester.suggestNamesByType(tp).headOption.getOrElse("value")

      val result = if (names.contains(name)) name + names(name) else name
      names.put(name, names.getOrElse(name, 0) + 1)
      result
    }

    def handleOneExpression: ScExpression => Parameter = {
      case assign: ScAssignStmt =>
        Parameter(assign.assignName, assign.getRExpression.flatMap(computeType))
      case e =>
        val `type` = computeType(e)
        Parameter(`type`.map(suggestUniqueName), `type`)
    }

    argList.map(handleOneExpression)
      .map(_.toString)
      .commaSeparated(parenthesize = true)
  }
}

case class ScalaTextLookupItem(private val name: String,
                               private val args: String,
                               private val declarationType: PsiElement)
  extends LookupElement with Comparable[ScalaTextLookupItem] {

  override def compareTo(item: ScalaTextLookupItem): Int = name.compareTo(item.name)

  override def getLookupString: String = declarationType match {
    case _: ScObject => name
    case _ => name + args
  }

  override def handleInsert(context: InsertionContext): Unit = {
    declarationType match {
      case _: ScObject =>
        val text = s" {\n def apply$args: Any = ???\n}"

        Option(context.getFile.findElementAt(context.getStartOffset)).foreach { element =>
          context
            .getDocument
            .insertString(element.getTextRange.getEndOffset, text)

          CodeStyleManager.getInstance(context.getProject)
            .reformatText(context.getFile, context.getStartOffset, context.getSelectionEndOffset)

          context.commitDocument()
        }
      case _ => super.handleInsert(context)
    }
  }
}

object ScalaTextLookupItem {

  object Weigher extends LookupElementWeigher("unresolvedOnTop") {

    override def weigh(item: LookupElement): Comparable[_] = item match {
      case lookupItem: ScalaTextLookupItem => lookupItem
      case _ => null
    }
  }

}
