package org.jetbrains.plugins.scala
package lang
package refactoring
package inline


import com.intellij.internal.statistic.UsageTrigger
import com.intellij.lang.refactoring.InlineHandler
import com.intellij.lang.refactoring.InlineHandler.Settings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi._
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.HelpID
import com.intellij.refactoring.util.{CommonRefactoringUtil, RefactoringMessageDialog}
import com.intellij.usageView.UsageInfo
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScStableReferenceElementPattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScStableCodeReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScMethodCall}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.{ScFunctionType, ScProjectionType, ScTypeParameterType}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil

import scala.collection.JavaConverters.iterableAsScalaIterableConverter
import scala.collection.mutable.ArrayBuffer

/**
 * User: Alexander Podkhalyuzin
 * Date: 08.06.2009
 */

class ScalaInlineHandler extends InlineHandler {

  private var occurrenceHighlighters: Seq[RangeHighlighter] = Seq.empty

  def removeDefinition(element: PsiElement, settings: InlineHandler.Settings) {
    def removeElementWithNonSignificantSibilings(value: PsiElement) = {
      val children = new ArrayBuffer[PsiElement]
      var psiElement = value.getNextSibling
      while (psiElement != null && (psiElement.getNode.getElementType == ScalaTokenTypes.tSEMICOLON || psiElement.getText.trim == "")) {
        children += psiElement
        psiElement = psiElement.getNextSibling
      }
      for (child <- children) {
        child.getParent.getNode.removeChild(child.getNode)
      }
      value.getParent.getNode.removeChild(value.getNode)
    }

    element match {
      case rp: ScBindingPattern =>
        PsiTreeUtil.getParentOfType(rp, classOf[ScDeclaredElementsHolder]) match {
          case v@(_: ScValue | _: ScVariable) if v.declaredElements.length == 1 =>
            removeElementWithNonSignificantSibilings(v)
          case _ =>
        }
      case funDef: ScFunctionDefinition => CodeEditUtil.removeChild(funDef.getParent.getNode, funDef.getNode)
      case typeAlias: ScTypeAliasDefinition =>
        removeElementWithNonSignificantSibilings(typeAlias)
      case _ =>
    }
  }

  def createInliner(element: PsiElement, settings: InlineHandler.Settings): InlineHandler.Inliner = {
    val replacementValue = element match {
      case rp: ScBindingPattern =>
        PsiTreeUtil.getParentOfType(rp, classOf[ScDeclaredElementsHolder]) match {
          case v@ScPatternDefinition.expr(e) if v.declaredElements == Seq(element) => e.getText
          case v@ScVariableDefinition.expr(e) if v.declaredElements == Seq(element) => e.getText
          case _ => return null
        }
      case funDef: ScFunctionDefinition if funDef.parameters.isEmpty =>
        funDef.body.orNull.getText
      case typeAlias: ScTypeAliasDefinition =>
        typeAlias.aliasedTypeElement.getText
      case _ => return null
    }
    new InlineHandler.Inliner {
      def inlineUsage(usage: UsageInfo, referenced: PsiElement) {
        val reference = usage.getReference
        val replacementOpt = reference.getElement match {
          case Parent(call: ScMethodCall) if call.argumentExpressions.isEmpty => Some(call)
          case e: ScExpression => Some(e)
          case Parent(reference: ScTypeElement) =>
            Some(reference)
          case _ => None
        }
        replacementOpt.foreach { replacement =>
          val newValue = replacement match {
            case expression: ScExpression =>
              val oldValue = expression match {
                case _ childOf (_: ScInterpolatedStringLiteral) =>
                  s"{" + replacementValue + "}"
                case _ =>
                  replacementValue
              }
              expression.replaceExpression(ScalaPsiElementFactory.createExpressionFromText(oldValue, replacement.getManager), removeParenthesis = true)
            case typeElement: ScTypeElement =>
              replacement.replace(ScalaPsiElementFactory.createTypeElementFromText(replacementValue, replacement.getManager))
          }

          val project = newValue.getProject
          val manager = FileEditorManager.getInstance(project)
          val editor = manager.getSelectedTextEditor
          occurrenceHighlighters = ScalaRefactoringUtil.highlightOccurrences(project, Array[PsiElement](newValue), editor)
          CodeStyleManager.getInstance(project).reformatRange(newValue.getContainingFile, newValue.getTextRange.getStartOffset - 1,
            newValue.getTextRange.getEndOffset + 1) //to prevent situations like this 2 ++2 (+2 was inlined)
        }
      }

      def getConflicts(reference: PsiReference, referenced: PsiElement): com.intellij.util.containers.MultiMap[PsiElement, String] =
        new com.intellij.util.containers.MultiMap[PsiElement, String]()
    }
  }


  def prepareInlineElement(element: PsiElement, editor: Editor, invokedOnReference: Boolean): InlineHandler.Settings = {
    def title(suffix: String) = "Scala Inline " + suffix

    def showErrorHint(message: String, titleSuffix: String): InlineHandler.Settings = {
      val inlineTitle = title(titleSuffix)
      CommonRefactoringUtil.showErrorHint(element.getProject, editor, message, inlineTitle, HelpID.INLINE_VARIABLE)
      Settings.CANNOT_INLINE_SETTINGS
    }

    def getSettings(psiNamedElement: PsiNamedElement, inlineTitleSuffix: String, inlineDescriptionSuffix: String): InlineHandler.Settings = {
      val refs = ReferencesSearch.search(psiNamedElement, psiNamedElement.getUseScope).findAll.asScala
      val inlineTitle = title(inlineTitleSuffix)
      occurrenceHighlighters = ScalaRefactoringUtil.highlightOccurrences(element.getProject, refs.map(_.getElement).toArray, editor)
      val settings = new InlineHandler.Settings {
        def isOnlyOneReferenceToInline: Boolean = false
      }
      if (refs.isEmpty)
        showErrorHint(ScalaBundle.message("cannot.inline.never.used"), inlineTitleSuffix)
      else if (!psiNamedElement.isInstanceOf[ScTypeAliasDefinition] && refs.exists(ref =>
        ScalaPsiUtil.getParentOfType(ref.getElement, classOf[ScStableCodeReferenceElement], classOf[ScStableReferenceElementPattern]) != null))
        showErrorHint(ScalaBundle.message("cannot.inline.stable.reference"), inlineTitleSuffix)
      else if (!ApplicationManager.getApplication.isUnitTestMode) {
        val occurences = refs.size match {
          case 1 => "(1 occurrence)"
          case n => s"($n occurrences)"
        }

        val question = s"Inline $inlineDescriptionSuffix ${
          psiNamedElement.name
        }? $occurences"
        val dialog = new RefactoringMessageDialog(
          inlineTitle,
          question,
          HelpID.INLINE_VARIABLE,
          "OptionPane.questionIcon",
          true,
          element.getProject)
        dialog.show()
        if (!dialog.isOK) {
          occurrenceHighlighters.foreach(_.dispose())
          occurrenceHighlighters = Seq.empty
          InlineHandler.Settings.CANNOT_INLINE_SETTINGS
        } else settings
      } else settings
    }

    def isSimpleTypeAlias(typeAlias: ScTypeAliasDefinition): Boolean = {
      typeAlias.aliasedTypeElement.depthFirst.forall {
        case t: ScTypeElement =>
          t.calcType match {
            case part: ScTypeParameterType => false
            case part: ScProjectionType if !ScalaPsiUtil.hasStablePath(part.element) =>
              false
            case _ => true
          }
        case _ => true
      }
    }

    def isParametrizedTypeAlias(typeAlias: ScTypeAliasDefinition): Boolean =
      !typeAlias.typeParameters.isEmpty

    UsageTrigger.trigger(ScalaBundle.message("inline.id"))

    implicit val typeSystem = element.typeSystem
    element match {
      case typedDef: ScTypedDefinition if ScFunctionType.unapply(typedDef.getType().getOrAny).exists(_._2.nonEmpty) =>
        val message = typedDef match {
          case _: ScFunctionDeclaration | _: ScFunctionDefinition => ScalaBundle.message("cannot.inline.function.with.parameters")
          case _ => ScalaBundle.message("cannot.inline.value.functional.type")
        }
        showErrorHint(message, "element")
      case named: ScNamedElement if named.getContainingFile != PsiDocumentManager.getInstance(editor.getProject).getPsiFile(editor.getDocument) =>
        showErrorHint(ScalaBundle.message("cannot.inline.different.files"), "element")
      case named: ScNamedElement if !usedInSameClassOnly(named) =>
        showErrorHint(ScalaBundle.message("cannot.inline.used.outside.class"), "member")
      case bp: ScBindingPattern =>
        PsiTreeUtil.getParentOfType(bp, classOf[ScPatternDefinition], classOf[ScVariableDefinition]) match {
          case definition: ScPatternDefinition if !definition.isSimple =>
            showErrorHint(ScalaBundle.message("cannot.inline.not.simple.pattern"), "value")
          case definition: ScVariableDefinition if !definition.isSimple =>
            showErrorHint(ScalaBundle.message("cannot.inline.not.simple.pattern"), "variable")
          case parent if parent != null && parent.declaredElements == Seq(element) =>
            if (parent.isLocal) getSettings(parent.declaredElements.head, "Variable", "local variable")
            else getSettings(parent.declaredElements.head, "Variable", "variable")
          case _ => null
        }
      case funDef: ScFunctionDefinition if funDef.recursionType != RecursionType.NoRecursion =>
        showErrorHint(ScalaBundle.message("cannot.inline.recursive.function"), "method")
      case funDef: ScFunctionDefinition if funDef.body.isDefined && funDef.parameters.isEmpty =>
        if (funDef.isLocal) getSettings(funDef, "Method", "local method")
        else getSettings(funDef, "Method", "method")
      case typeAlias: ScTypeAliasDefinition if isParametrizedTypeAlias(typeAlias) || !isSimpleTypeAlias(typeAlias) =>
        showErrorHint(ScalaBundle.message("cannot.inline.notsimple.typealias"), "Type Alias")
      case typeAlias: ScTypeAliasDefinition =>
        getSettings(typeAlias, "Type Alias", "type alias")
      case _ => null
    }
  }

  private def usedInSameClassOnly(named: ScNamedElement): Boolean = {
    ScalaPsiUtil.nameContext(named) match {
      case member: ScMember =>
        ReferencesSearch.search(named, named.getUseScope).findAll.asScala.forall {
          ref => member.containingClass == null || PsiTreeUtil.isAncestor(member.containingClass, ref.getElement, true)
        }
      case _ => true
    }
  }
}
