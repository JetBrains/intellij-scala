package org.jetbrains.plugins.scala.codeInsight.intention.matcher

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScConstructorPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createPatternFromText
import org.jetbrains.plugins.scala.lang.psi.types.ScalaType
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult


class ConvertToTypedPatternIntention extends PsiElementBaseIntentionAction {
  override def getFamilyName: String = ScalaBundle.message("convert.to.typed.pattern")

  override def getText: String = ScalaBundle.message("convert.to.typed.pattern")

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    element match {
      case Parent((_: ScStableCodeReference) && Parent(_: ScConstructorPattern)) => true
        
      case _ => false
    }
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    val codeRef = element.getParent.asInstanceOf[ScStableCodeReference]
    val constrPattern = codeRef.getParent.asInstanceOf[ScConstructorPattern]
    val name = codeRef.bind() match {
      case Some( result @ ScalaResolveResult(fun: ScFunctionDefinition, _)) if fun.name == "unapply"=>
        // TODO follow aliases
        result.parentElement match {
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
    val typeText = constrPattern.`type`().toOption.fold(codeRef.getText)(_.presentableText(element))
    constrPattern.replace(createPatternFromText(s"$name: $typeText")(codeRef.getManager))
  }
}
