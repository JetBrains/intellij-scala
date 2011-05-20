package org.jetbrains.plugins.scala
package codeInsight
package intention
package matcher

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import com.intellij.codeInsight.CodeInsightUtilBase
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import lang.psi.api.expr.{ScExpression, ScMatchStmt}
import lang.psi.impl.ScalaPsiElementFactory
import com.intellij.psi._
import lang.psi.api.base.ScReferenceElement
import search.searches.ClassInheritorsSearch
import lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition, ScClass}
import lang.psi.ScalaPsiUtil
import lang.psi.types.result.TypingContext
import lang.psi.types.{ScType, ScSubstitutor}
import extensions._
import lang.refactoring.namesSuggester.NameSuggester
import lang.psi.api.base.patterns.{ScWildcardPattern, ScReferencePattern, ScPattern, ScCaseClause}

/**
  * Expands reference or wildcard pattern to a constructor/tuple pattern.
  */
// TODO avoid name clashes, avoid more FQNs with adjustTypes.
class ExpandPatternIntention extends PsiElementBaseIntentionAction {
  def getFamilyName: String = "Pattern Matching"

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    findReferencePattern(element) match {
      case Some((_, newPatternText)) =>
        setText("Expand to: " + newPatternText)
        true
      case _ => false
    }
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    findReferencePattern(element) match {
      case Some((origPattern, newPatternText)) =>
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        if (!CodeInsightUtilBase.prepareFileForWrite(element.getContainingFile)) return
        IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace()
        val newPattern = ScalaPsiElementFactory.createPatternFromText(newPatternText, element.getManager)
        val replaced = origPattern.replace(newPattern)
        ScalaPsiUtil.adjustTypes(replaced)
      case None =>
    }
  }

  private def findReferencePattern(element: PsiElement): Option[(ScPattern, String)] = {
    element.getParent match {
      case refPattern: ScReferencePattern =>
        val expectedType = refPattern.expectedType
        nestedPatternText(expectedType).map(patText => (refPattern, "%s @ %s".format(refPattern.getText, patText)))
      case wildcardPattern: ScWildcardPattern =>
        val expectedType = wildcardPattern.expectedType
        nestedPatternText(expectedType).map(patText => (wildcardPattern, patText))
      case _ => None
    }
  }


  def nestedPatternText(expectedType: Option[ScType]): Option[String] = {
    expectedType.flatMap(ScType.extractTupleType) match {
      case Some(tt) =>
        import NameSuggester.suggestNamesByType
        val names = tt.components.map(t => suggestNamesByType(t).head)
        val tuplePattern = names.mkParenString
        Some(tuplePattern)
      case _ =>
        expectedType.flatMap(ScType.extractDesignated).map(_._1) match {
          case Some(cls: ScClass) if cls.isCase =>
            val companionObj = ScalaPsiUtil.getCompanionModule(cls).get
            cls.constructor match {
              case Some(primaryConstructor) =>
                val parameters = primaryConstructor.effectiveFirstParameterSection
                val constructorParams = parameters.map(_.getName).mkParenString
                Some(cls.getQualifiedName + constructorParams)
              case None => None
            }
          case _ => None
        }
    }
  }
}