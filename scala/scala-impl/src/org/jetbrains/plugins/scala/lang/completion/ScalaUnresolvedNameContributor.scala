package org.jetbrains.plugins.scala
package lang
package completion

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
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
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
          case OverrideAnnotationOwner() |
               (_: ScFieldId) childOf (_ childOf OverrideAnnotationOwner()) =>
          case declaration@(_: ScTypeDefinition
                            | _: ScTypeAliasDeclaration
                            | _: ScFunctionDeclaration
                            | _: ScFieldId) =>
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

  private object OverrideAnnotationOwner {

    def unapply(owner: ScModifierListOwner): Boolean =
      owner.hasModifierPropertyScala("override")
  }

  private def handleReferencesInScope(reference: ScReferenceElement,
                                      position: PsiElement,
                                      resultSet: CompletionResultSet): Unit = {
    def isKindAcceptable = {
      def refWithMethodCallParent = reference.parent.exists(_.isInstanceOf[ScMethodCall])

      def refKinds = reference.getKinds(incomplete = false)

      position match {
        case _: ScFieldId if !refWithMethodCallParent => refKinds.contains(VAL) || refKinds.contains(VAR)
        case _: ScObject => refKinds.contains(OBJECT)
        case cl: ScClass if cl.isCase && refWithMethodCallParent => refKinds.contains(OBJECT) || refKinds.contains(CLASS)
        case _: ScTypeDefinition | _: ScTypeAliasDeclaration => refKinds.contains(CLASS)
        case _: ScFunctionDeclaration => refKinds.contains(METHOD)
        case _ => false
      }
    }


    if (!reference.parent.exists(_.isInstanceOf[PsiReference]) &&
      !ReservedNames(reference.refName) &&
      isKindAcceptable) {

      val lookupElement = position match {
        case _: ScObject => new ScalaTextLookupItem.Object(reference)
        case _ => new ScalaTextLookupItem.Regular(reference)
      }

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
}

sealed abstract class ScalaTextLookupItem(protected val reference: ScReferenceElement)
  extends LookupElement with Comparable[ScalaTextLookupItem] {

  private val name: String = reference.refName

  private val maybeArguments = reference.getParent match {
    case MethodInvocation(`reference`, expressions) => Some(expressions) // TODO: List(1, 2) map myFunc -> to support
    case parent =>
      PsiTreeUtil.getNextSiblingOfType(parent, classOf[ScArgumentExprList]) match {
        case null => None
        case list => Some(list.exprs)
      }
  }

  override def equals(other: Any): Boolean = other match {
    case that: ScalaTextLookupItem => name == that.name && arguments == that.arguments
    case _ => false
  }

  override def hashCode(): Int = 31 * name.hashCode + arguments.hashCode

  override def compareTo(item: ScalaTextLookupItem): Int = name.compareTo(item.name)

  override def getLookupString: String = name

  protected val arguments: String = maybeArguments.fold("")(createParameters)

  private[this] def createParameters(arguments: Seq[ScExpression]) = {
    import NameSuggester._
    val suggester = new UniqueNameSuggester()

    def createParameter: ScExpression => (String, ScType) = {
      case assign@ScAssignStmt(_, Some(assignment)) =>
        suggester(assign.assignName) -> assignment.`type`().getOrAny
      case expression =>
        val `type` = expression.`type`().getOrAny
        suggester(`type`) -> `type`
    }

    arguments.map(createParameter).map {
      case (parameterName, scType) => s"$parameterName${ScalaTokenTypes.tCOLON} ${scType.presentableText}"
    }.commaSeparated(parenthesize = true)
  }
}

object ScalaTextLookupItem {

  class Regular(override protected val reference: ScReferenceElement) extends ScalaTextLookupItem(reference) {

    override def getLookupString: String = super.getLookupString + arguments

    override def equals(other: Any): Boolean = other match {
      case regular: Regular => super.equals(regular)
      case _ => false
    }
  }

  class Object(override protected val reference: ScReferenceElement) extends ScalaTextLookupItem(reference) {

    override def equals(other: Any): Boolean = other match {
      case obj: Object => super.equals(obj)
      case _ => false
    }


    override def handleInsert(context: InsertionContext): Unit = {
      val startOffset = context.getStartOffset
      context.getFile.findElementAt(startOffset) match {
        case null =>
        case element =>
          context.getDocument.insertString(
            element.getTextRange.getEndOffset,
            s" {\n def apply$arguments: Any = ???\n}"
          )

          CodeStyleManager.getInstance(context.getProject).reformatText(
            context.getFile,
            startOffset,
            context.getSelectionEndOffset
          )

          context.commitDocument()
      }
    }
  }

  object Weigher extends LookupElementWeigher("unresolvedOnTop") {

    override def weigh(item: LookupElement): Comparable[_] = item match {
      case lookupItem: ScalaTextLookupItem => lookupItem
      case _ => null
    }
  }

}
