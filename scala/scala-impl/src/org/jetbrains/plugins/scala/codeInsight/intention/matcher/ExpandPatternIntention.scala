package org.jetbrains.plugins.scala.codeInsight.intention.matcher

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.project.Project
import com.intellij.psi._
import org.apache.commons.lang3.StringUtils
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.{ObjectExt, _}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScPattern, ScTypePattern, ScTypedPatternLike}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createPatternFromText
import org.jetbrains.plugins.scala.lang.psi.types.api.TupleType
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt}

/**
  * Expands reference or wildcard pattern to a constructor/tuple pattern.
  */
// TODO avoid name clashes, avoid more FQNs with adjustTypes.
class ExpandPatternIntention extends PsiElementBaseIntentionAction {
  override def getFamilyName: String = ScalaBundle.message("family.name.expand.to.constructor.pattern")

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    findReferencePattern(element) match {
      case Some((_, newPatternText)) =>
        setText(ScalaBundle.message("expand.to.new.pattern", StringUtils.abbreviate(newPatternText, 25)))
        true
      case _ => false
    }
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    findReferencePattern(element) match {
      case Some((origPattern, newPatternText)) =>
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        if (!FileModificationService.getInstance.prepareFileForWrite(element.getContainingFile)) return
        IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace()
        val newPattern = createPatternFromText(newPatternText, element)(element.getManager)
        val replaced = origPattern.replace(newPattern)
        ScalaPsiUtil.adjustTypes(replaced)
      case None =>
    }
  }

  override def generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo = {
    val element = file.findElementAt(editor.getCaretModel.getOffset)
    findReferencePattern(element) match {
      case Some((origPattern, newPatternText)) =>
        val newPattern = createPatternFromText(newPatternText, element)(element.getManager)
        val replaced = origPattern.replace(newPattern)
        ScalaPsiUtil.adjustTypes(replaced)
        IntentionPreviewInfo.DIFF
      case _ => IntentionPreviewInfo.EMPTY
    }
  }

  private def findReferencePattern(element: PsiElement): Option[(ScPattern, String)] =
    element.parents
      .takeWhile(_.is[ScPattern, ScTypeElement, ScTypePattern, ScReference])
      .flatMap {
        case typedPattern@ScTypedPatternLike.withNameId(typePattern, nameId) =>
          val patText = typePattern.typeElement
            .`type`().toOption
            .flatMap(nestedPatternText)

          patText.map { patText =>
            nameId.getText match {
              case "_"  => (typedPattern, patText)
              case name => (typedPattern, s"$name@$patText")
            }
          }
        case _ => None
      }
      .nextOption()

  private def nestedPatternText(expectedType: ScType): Option[String] = {
    expectedType match {
      case TupleType(comps) =>
        import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester.suggestNamesByType
        val names = comps.map(t => suggestNamesByType(t).head)
        val tuplePattern = names.mkParenString
        Some(tuplePattern)
      case _ =>
        expectedType.extractDesignated(expandAliases = true) match {
          case Some(cls: ScClass) if cls.isCase => // TODO: SCALA 3 has enum classes, which should work here, too
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
