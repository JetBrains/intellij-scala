package org.jetbrains.plugins.scala
package lang.refactoring.introduceField

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern

/**
 * Nikolay.Tropin
 * 7/3/13
 */
class ScalaElementToWorkOn private (val expression: ScExpression, val localVar: ScBindingPattern) {

}

object ScalaElementToWorkOn {
//  def processElementToWorkOn (editor: Editor,
//                              file: PsiFile,
//                              refactoringName: String,
//                              helpId: String,
//                              project: Project,
//                              processor: ElementToWorkOn.ElementsProcessor[ElementToWorkOn]): Unit = {
//
//    var expression: ScExpression = null
//    var localVar: ScBindingPattern = null
//    if (!editor.getSelectionModel.hasSelection) {
//      val leaf = file.findElementAt(editor.getCaretModel.getOffset)
//      ScalaPsiUtil.getParentOfType(leaf, classOf[ScExpression], classOf[ScBindingPattern]) match {
//        case ref: ScReferenceExpression =>
//          ref.resolve() match {
//            case bp: ScBindingPattern if !bp.isClassMember =>
//              expression = ref; localVar = bp
//            case _ =>
//          }
//        case bp: ScBindingPattern if !bp.isClassMember && bp.nameId == leaf && !bp.isWildcard =>
//        case _ =>
//      }
//    }
//
//  }

}