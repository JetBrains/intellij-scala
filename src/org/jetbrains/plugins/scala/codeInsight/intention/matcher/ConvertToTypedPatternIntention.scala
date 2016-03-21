package org.jetbrains.plugins.scala
package codeInsight
package intention
package matcher

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScConstructorPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.ScalaType
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult


class ConvertToTypedPatternIntention extends PsiElementBaseIntentionAction {
  def getFamilyName = "Convert to typed pattern"

  override def getText = getFamilyName

  def isAvailable(project: Project, editor: Editor, element: PsiElement) = {
    element match {
      case e @ Parent(Both(ref: ScStableCodeReferenceElement, Parent(_: ScConstructorPattern))) => true
        
      case _ => false
    }
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    val codeRef = element.getParent.asInstanceOf[ScStableCodeReferenceElement]
    val constrPattern = codeRef.getParent.asInstanceOf[ScConstructorPattern]
    val manager = codeRef.getManager
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
    // TODO replace references to the constructor pattern params with "value.param"
    val newPattern = ScalaPsiElementFactory.createPatternFromText("%s: %s".format(name, codeRef.getText), manager)
    constrPattern.replace(newPattern)
  }
}