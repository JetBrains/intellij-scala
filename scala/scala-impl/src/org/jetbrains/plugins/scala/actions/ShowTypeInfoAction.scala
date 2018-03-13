package org.jetbrains.plugins.scala
package actions

import _root_.com.intellij.codeInsight.TargetElementUtil
import _root_.com.intellij.psi._
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, CommonDataKeys}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.util.{PsiTreeUtil, PsiUtilBase}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.types.api.ScTypePresentation
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt, TypePresentationContext}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil.getExpression
import org.jetbrains.plugins.scala.statistics.{FeatureKey, Stats}


/**
 * Pavel.Fatin, 16.04.2010
 */
class ShowTypeInfoAction extends AnAction(ScalaBundle.message("type.info")) {

  override def update(e: AnActionEvent) {
    ScalaActionUtil.enableAndShowIfInScalaFile(e)
  }

  def actionPerformed(e: AnActionEvent) {
    val context = e.getDataContext
    implicit val editor: Editor = CommonDataKeys.EDITOR.getData(context)
    if(editor == null) return

    val file = PsiUtilBase.getPsiFileInEditor(editor, CommonDataKeys.PROJECT.getData(context))
    if (!file.getLanguage.isKindOf(ScalaLanguage.INSTANCE)) return

    Stats.trigger(FeatureKey.showTypeInfo)

    val selectionModel = editor.getSelectionModel
    if (selectionModel.hasSelection) {
      def hintForPattern: Option[String] = {
        val pattern = PsiTreeUtil.findElementOfClassAtRange(file, selectionModel.getSelectionStart, selectionModel.getSelectionEnd, classOf[ScBindingPattern])
        ShowTypeInfoAction.typeInfoFromPattern(pattern).map("Type: " + _)
      }

      implicit val project: Project = file.getProject

      def hintForExpression(): Option[String] = {
        getExpression(file).map {
          case expr@Typeable(tpe) =>
            implicit val context: TypePresentationContext = expr
            val tpeText = tpe.presentableText
            val withoutAliases = Some(ScTypePresentation.withoutAliases(tpe))
            val tpeWithoutImplicits = expr.getTypeWithoutImplicits().toOption
            val tpeWithoutImplicitsText = tpeWithoutImplicits.map(_.presentableText)
            val expectedTypeText = expr.expectedType().map(_.presentableText)
            val nonSingletonTypeText = tpe.extractDesignatorSingleton.map(_.presentableText)

            val mainText = Seq("Type: " + tpeText)
            def additionalTypeText(typeText: Option[String], label: String) = typeText.filter(_ != tpeText).map(s"$label: " + _)

            val nonSingleton = additionalTypeText(nonSingletonTypeText, "Non-singleton")
            val simplified = additionalTypeText(withoutAliases, "Simplified")
            val orig = additionalTypeText(tpeWithoutImplicitsText, "Original")
            val expected = additionalTypeText(expectedTypeText, "Expected")
            val types = mainText ++ simplified.orElse(nonSingleton) ++ orig ++ expected

            if (types.size == 1) tpeText
            else types.mkString("\n")
          case _ => "Could not find type for selection"
        }
      }
      val hint = hintForPattern orElse hintForExpression
      hint.foreach(ScalaActionUtil.showHint(editor, _))

    } else {
      val offset = TargetElementUtil.adjustOffset(file, editor.getDocument,
        editor.logicalPositionToOffset(editor.getCaretModel.getLogicalPosition))
      ShowTypeInfoAction.getTypeInfoHint(editor, file, offset).foreach(ScalaActionUtil.showHint(editor, _))
    }
  }
}

object ShowTypeInfoAction {
  def getTypeInfoHint(editor: Editor, file: PsiFile, offset: Int): Option[String] = {
    val typeInfoFromRef = file.findReferenceAt(offset) match {
      case ResolvedWithSubst(e, subst) => typeTextOf(e, subst)
      case _ =>
        val element = file.findElementAt(offset)
        if (element == null) return None
        if (element.getNode.getElementType != ScalaTokenTypes.tIDENTIFIER) return None
        element match {
          case Parent(p) => typeTextOf(p, ScSubstitutor.empty)
          case _ => None
        }
    }
    val pattern = PsiTreeUtil.findElementOfClassAtOffset(file, offset, classOf[ScBindingPattern], false)
    typeInfoFromRef.orElse(typeInfoFromPattern(pattern))
  }

  def typeInfoFromPattern(p: ScBindingPattern): Option[String] = {
    p match {
      case null => None
      case _ => typeTextOf(p, ScSubstitutor.empty)
    }
  }

  val NO_TYPE: String = "No type was inferred"

  private[this] def typeTextOf(elem: PsiElement, subst: ScSubstitutor): Option[String] = {
    typeText(elem.ofNamedElement(subst))
  }

  private[this] def typeText(optType: Option[ScType], s: ScSubstitutor = ScSubstitutor.empty): Option[String] = {
    optType.map(ScTypePresentation.withoutAliases)
  }
}



