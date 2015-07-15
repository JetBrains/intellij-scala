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
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiNamedElement, PsiElement, PsiReference}
import com.intellij.refactoring.HelpID
import com.intellij.refactoring.util.{CommonRefactoringUtil, RefactoringMessageDialog}
import com.intellij.usageView.UsageInfo
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScStableReferenceElementPattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScStableCodeReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScReferenceExpression, ScExpression, ScMethodCall}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.ScFunctionType
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
    def removeChilds(value: PsiElement) = {
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
          case v@(_: ScValue | _: ScVariable) if v.declaredElements.length == 1 => {
            removeChilds(v)
          }
          case _ =>
        }
      case funDef: ScFunctionDefinition => CodeEditUtil.removeChild(funDef.getParent.getNode, funDef.getNode)
      case typeAlias: ScTypeAliasDefinition =>
        removeChilds(typeAlias)
      case _ =>
    }
  }

  def createInliner(element: PsiElement, settings: InlineHandler.Settings): InlineHandler.Inliner = {
    val expr = ScalaRefactoringUtil.unparExpr(element match {
      case rp: ScBindingPattern => {
        PsiTreeUtil.getParentOfType(rp, classOf[ScDeclaredElementsHolder]) match {
          case v@ScPatternDefinition.expr(e) if v.declaredElements == Seq(element) => e
          case v@ScVariableDefinition.expr(e) if v.declaredElements == Seq(element) => e
          case _ => return null
        }
      }
      case funDef: ScFunctionDefinition if funDef.parameters.isEmpty =>
        funDef.body.orNull
      case typeAlias: ScTypeAliasDefinition =>
        ScalaPsiElementFactory.createExpressionFromText(typeAlias.aliasedType.get.presentableText, typeAlias.getManager)
      case _ => return null
    })
    new InlineHandler.Inliner {
      def inlineUsage(usage: UsageInfo, referenced: PsiElement) {
        val reference = usage.getReference
        reference match {
          case referenceExpression: ScReferenceExpression =>
            val expressionOpt = reference.getElement match {
              case Parent(call: ScMethodCall) if call.argumentExpressions.isEmpty => Some(call)
              case e: ScExpression => Some(e)
              //              case typeAlias: ScStableCodeReferenceElement =>Some(typeAlias)
              case _ => None
            }

            expressionOpt.foreach { expression =>
              val replacement = expression match {
                case _ childOf (_: ScInterpolatedStringLiteral) =>
                  ScalaPsiElementFactory.createExpressionFromText(s"{" + expr.getText + "}", expression.getManager)
                case _ => expr
              }
              //change replaceExpression -> replace?
              val newExpr = expression.replaceExpression(replacement, removeParenthesis = true)
              val project = newExpr.getProject
              val manager = FileEditorManager.getInstance(project)
              val editor = manager.getSelectedTextEditor
              occurrenceHighlighters = ScalaRefactoringUtil.highlightOccurrences(project, Array[PsiElement](newExpr), editor)
              CodeStyleManager.getInstance(project).reformatRange(newExpr.getContainingFile, newExpr.getTextRange.getStartOffset - 1,
                newExpr.getTextRange.getEndOffset + 1) //to prevent situations like this 2 ++2 (+2 was inlined)
            }
          case codeReference: ScStableCodeReferenceElement =>
            val newExpr = codeReference.replace(expr)
            val project = newExpr.getProject
            val manager = FileEditorManager.getInstance(project)
            val editor = manager.getSelectedTextEditor
            occurrenceHighlighters = ScalaRefactoringUtil.highlightOccurrences(project, Array[PsiElement](newExpr), editor)
            CodeStyleManager.getInstance(project).reformatRange(newExpr.getContainingFile, newExpr.getTextRange.getStartOffset - 1,
              newExpr.getTextRange.getEndOffset + 1) //to prevent situations like this 2 ++2 (+2 was inlined)
          case _ =>
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
      if (refs.size == 0)
        showErrorHint(ScalaBundle.message("cannot.inline.never.used"), inlineTitleSuffix)
      else if (!psiNamedElement.isInstanceOf[ScTypeAliasDefinition] && refs.exists(ref =>
        ScalaPsiUtil.getParentOfType(ref.getElement, classOf[ScStableCodeReferenceElement], classOf[ScStableReferenceElementPattern]) != null))
        showErrorHint(ScalaBundle.message("cannot.inline.stable.reference"), inlineTitleSuffix)
      else if (!ApplicationManager.getApplication.isUnitTestMode) {
        val occurences = refs.size match {
          case 1 => "(1 occurrence)"
          case n => s"($n occurrences)"
        }

        val question = s"Inline $inlineDescriptionSuffix ${psiNamedElement.name}? $occurences"
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

    def isSimpleTypeAlias(typeAlias: ScTypeAlias): Boolean =
      typeAlias.typeParameters.length > 0


    UsageTrigger.trigger(ScalaBundle.message("inline.id"))

    element match {
      case typedDef: ScTypedDefinition if ScFunctionType.unapply(typedDef.getType().getOrAny).exists(_._2.length > 0) =>
        showErrorHint(ScalaBundle.message("cannot.inline.anonymous.function"), "element")
      case named: ScNamedElement if !usedInSameClassOnly(named) =>
        showErrorHint(ScalaBundle.message("cannot.inline.used.outside.class"), "member")
      case bp: ScBindingPattern =>
        PsiTreeUtil.getParentOfType(bp, classOf[ScPatternDefinition], classOf[ScVariableDefinition]) match {
          case definition: ScPatternDefinition if !definition.isSimple =>
            showErrorHint(ScalaBundle.message("cannot.inline.not.simple.pattern"), "value")
          case definition: ScVariableDefinition if !definition.isSimple =>
            showErrorHint(ScalaBundle.message("cannot.inline.not.simple.pattern"), "variable")
          case parent if parent != null && parent.declaredElements == Seq(element) =>
            if (parent.isLocal) getSettings(parent.declaredElements.apply(0), "Variable", "local variable")
            else getSettings(parent.declaredElements.apply(0), "Variable", "variable")
          case _ => null
        }
      case funDef: ScFunctionDefinition if funDef.recursionType != RecursionType.NoRecursion =>
        showErrorHint(ScalaBundle.message("cannot.inline.recursive.function"), "method")
      case funDef: ScFunctionDefinition if funDef.body.isDefined && funDef.parameters.isEmpty =>
        if (funDef.isLocal) getSettings(funDef, "Method", "local method")
        else getSettings(funDef, "Method", "method")
      case typeAlias: ScTypeAliasDefinition if (isSimpleTypeAlias(typeAlias)) =>
        showErrorHint(ScalaBundle.message("cannot.inline.recursive.function"), "TypeAlias")
      case typeAlias: ScTypeAliasDefinition =>
        getSettings(typeAlias, "TypeAlias", "typeAlias")
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
