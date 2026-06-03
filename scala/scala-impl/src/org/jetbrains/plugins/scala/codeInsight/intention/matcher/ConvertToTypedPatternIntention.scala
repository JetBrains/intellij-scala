package org.jetbrains.plugins.scala.codeInsight.intention.matcher

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScExtractorPattern.ExtractorTarget
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScConstructorPattern, ScExtractorPattern}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createPatternFromText
import org.jetbrains.plugins.scala.lang.psi.types.{Context, ScalaType, TypePresentationContext}
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester


class ConvertToTypedPatternIntention extends PsiElementBaseIntentionAction {
  override def getFamilyName: String = ScalaBundle.message("convert.to.typed.pattern")

  override def getText: String = ScalaBundle.message("convert.to.typed.pattern")

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    element match {
      case Parent((_: ScStableCodeReference) & Parent(_: ScConstructorPattern)) => true

      case _ => false
    }
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    implicit val tpc: TypePresentationContext = TypePresentationContext(element)
    implicit val context: Context = Context(element)

    val codeRef = element.getParent.asInstanceOf[ScStableCodeReference]
    val constrPattern = codeRef.getParent.asInstanceOf[ScExtractorPattern]
    val name = constrPattern.targetFor(constrPattern.expectedType) match {
      case Some(target: ExtractorTarget.Unapply) =>
        // TODO follow aliases
        target.resolveResult.parentElement match {
          case Some(obj: ScObject) =>
            ScalaPsiUtil.getCompanionModule(obj) match {
              case Some(cls: ScClass) =>
                val tpe = ScalaType.designator(cls)
                val names = NameSuggester.suggestNamesByType(tpe)
                names.head
              case _ => "value"
            }
          case _ => "value"
        }
      case _ => "value"
    }
    val typeText = constrPattern.`type`().toOption.fold(codeRef.getText)(_.presentableText)
    constrPattern.replace(createPatternFromText(s"$name: $typeText", element)(codeRef.getManager))
  }
}
