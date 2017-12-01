package org.jetbrains.plugins.scala.lang.completion

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx.processHighlights
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementWeigher}
import com.intellij.lang.annotation.HighlightSeverity.ERROR
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil.getNextSiblingOfType
import com.intellij.psi.{PsiElement, PsiFile, PsiReference}
import com.intellij.util.{ProcessingContext, Processor}
import org.jetbrains.plugins.scala.extensions.{Nullable, PsiElementExt, childOf}
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

/** Contributer adds unresolved names in current scope to completion list.
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
  extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new CompletionProvider[CompletionParameters] {
    override def addCompletions(completionParameters: CompletionParameters, processingContext: ProcessingContext, completionResultSet: CompletionResultSet): Unit = {
      val position = positionFromParameters(completionParameters)

      position.getContext match {
        case modOwner: ScModifierListOwner if modOwner.hasModifierPropertyScala("override") =>
        case (_: ScFieldId) childOf (_ childOf (modOwner: ScModifierListOwner)) if modOwner.hasModifierPropertyScala("override") =>
        case declaration@(_: ScTypeDefinition | _: ScTypeAliasDeclaration | _: ScFunctionDeclaration | _: ScFieldId) =>
          val result = addElementsOrderSorter(completionParameters, completionResultSet)
          handleReferencesInScope(declaration, result, completionParameters.getOriginalFile)
        case _ =>
      }
    }
  })

  def handleReferencesInScope(position: PsiElement, completionResultSet: CompletionResultSet, originalFile: PsiFile): Unit = {
    def isKindAcceptible(ref: ScReferenceElement) = {
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

    def argList(implicit ref: ScReferenceElement): Option[Seq[ScExpression]] =
      ref.parent.flatMap {
        case me: MethodInvocation if me.getInvokedExpr == ref => Option(me.argumentExpressions) // TODO: List(1, 2) map myFunc -> to support
        case psiElement => Option(getNextSiblingOfType(psiElement, classOf[ScArgumentExprList])).map(_.exprs)
      }

    def handleRef(ref: ScReferenceElement): Unit = {

      def parentIsRef = ref.parent.exists(_.isInstanceOf[PsiReference])

      if (!parentIsRef && !reservedNames.contains(ref.refName) && isKindAcceptible(ref)) {
        completionResultSet
          .addElement(createLookup(ref.refName, argList(ref), position))
      }
    }

    val document = originalFile.getViewProvider.getDocument
    val range = computeScope(position).map(_.getSameElementInContext).getOrElse(originalFile).getTextRange

    processHighlights(document, position.getProject, ERROR, range.getStartOffset, range.getEndOffset, new Processor[HighlightInfo] {
      override def process(highlightInfo: HighlightInfo): Boolean = {

        def isNamedArg(ref: ScReferenceElement): Boolean =
          ref.parent.exists(p =>
            p.isInstanceOf[ScAssignStmt] && p.parent.exists(_.isInstanceOf[ScArgumentExprList])
          )

        def maybeReferenceFromHighlight(info: HighlightInfo): Option[ScReferenceElement] =
          originalFile.findReferenceAt(info.getStartOffset)
            .asOptionOf[ScReferenceElement]
            .filter(_.getTextRange.containsRange(info.getStartOffset, info.getEndOffset))
            .filterNot(isNamedArg)

        maybeReferenceFromHighlight(highlightInfo).foreach(handleRef)

        true
      }
    })
  }

  protected def createLookup(name: String, args: Option[Seq[ScExpression]], position: PsiElement): ScalaTextLookupItem = {

    case class Parameter(name: Option[String], `type`: Option[ScType]) {
      override def toString: String = name.zip(`type`).map { case (n, t) => s"$n: $t" }.headOption.getOrElse("")
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

    val arguments = args.map(_.map(handleOneExpression).map(_.toString).mkString(", ")).map("(" + _ + ")").getOrElse("")

    position match {
      case _: ScObject => ScalaTextLookupItem(name, arguments, position)
      case _ => ScalaTextLookupItem(name, arguments, position)
    }
  }

  private def computeScope(position: PsiElement): Option[ScalaPsiElement] =
    position.scopes.toSeq.headOption.flatMap(_.asOptionOf[ScalaPsiElement])

  private def addElementsOrderSorter(parameters: CompletionParameters, result: CompletionResultSet): CompletionResultSet = {

    case class NameComparable(name: String) extends Comparable[NameComparable] {
      def compareTo(o: NameComparable): Int = {
        name.compareTo(o.name)
      }
    }

    class PreferByParamsOrder extends LookupElementWeigher("unresolvedOnTop") {
      override def weigh(item: LookupElement): Comparable[_] = {
        item match {
          case ScalaTextLookupItem(name, _, _) => NameComparable(name)
          case _ => null
        }
      }
    }

    var sorter = CompletionSorter.defaultSorter(parameters, result.getPrefixMatcher)
    sorter = sorter.weighAfter("prefix", new PreferByParamsOrder())
    result.withRelevanceSorter(sorter)
  }

  private val reservedNames =
    Seq(
      "+", "-", "*", "/", "%", ">", "<",
      "&&", "||", "&", "|", "==", "!=", "^",
      "<<", ">>", ">>>")
}

case class ScalaTextLookupItem(name: String, args: String, declarationType: PsiElement) extends LookupElement {
  override def getLookupString: String = {
    declarationType match {
      case _: ScObject => name
      case _ => name + args
    }
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


