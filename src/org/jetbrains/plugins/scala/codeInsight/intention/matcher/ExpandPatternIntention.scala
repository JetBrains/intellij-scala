package org.jetbrains.plugins.scala
package codeInsight
package intention
package matcher

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.project.Project
import com.intellij.psi._
import org.apache.commons.lang.StringUtils
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScPattern, ScReferencePattern, ScWildcardPattern}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.psi.types.{ScTupleType, ScType, ScalaType}
import org.jetbrains.plugins.scala.project.ProjectExt

/**
  * Expands reference or wildcard pattern to a constructor/tuple pattern.
  */
// TODO avoid name clashes, avoid more FQNs with adjustTypes.
class ExpandPatternIntention extends PsiElementBaseIntentionAction {
  def getFamilyName: String = "Expand to Constructor pattern"

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    findReferencePattern(element)(project.typeSystem) match {
      case Some((_, newPatternText)) =>
        setText("Expand to: " + StringUtils.abbreviate(newPatternText, 25))
        true
      case _ => false
    }
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    findReferencePattern(element)(project.typeSystem) match {
      case Some((origPattern, newPatternText)) =>
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        if (!FileModificationService.getInstance.prepareFileForWrite(element.getContainingFile)) return
        IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace()
        val newPattern = ScalaPsiElementFactory.createPatternFromText(newPatternText, element.getManager)
        val replaced = origPattern.replace(newPattern)
        ScalaPsiUtil.adjustTypes(replaced)
      case None =>
    }
  }

  private def findReferencePattern(element: PsiElement)
                                  (implicit typeSystem: TypeSystem): Option[(ScPattern, String)] = {
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


  def nestedPatternText(expectedType: Option[ScType])
                       (implicit typeSystem: TypeSystem): Option[String] = {
    expectedType match {
      case Some(ScTupleType(comps)) =>
        import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester.suggestNamesByType
        val names = comps.map(t => suggestNamesByType(t).head)
        val tuplePattern = names.mkParenString
        Some(tuplePattern)
      case _ =>
        expectedType.flatMap(ScalaType.extractDesignated(_, withoutAliases = true)).map(_._1) match {
          case Some(cls: ScClass) if cls.isCase =>
            val companionObj = ScalaPsiUtil.getCompanionModule(cls).get
            cls.constructor match {
              case Some(primaryConstructor) =>
                val parameters = primaryConstructor.effectiveFirstParameterSection
                val constructorParams = parameters.map(_.name).mkParenString
                Some(cls.qualifiedName + constructorParams)
              case None => None
            }
          case _ => None
        }
    }
  }
}