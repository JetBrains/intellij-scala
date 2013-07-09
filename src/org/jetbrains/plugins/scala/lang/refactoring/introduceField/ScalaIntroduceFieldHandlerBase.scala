package org.jetbrains.plugins.scala
package lang.refactoring.introduceField

import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil._
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import com.intellij.codeInsight.navigation.NavigationUtil
import com.intellij.ide.util.PsiClassListCellRenderer
import com.intellij.psi.search.PsiElementProcessor
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

/**
 * Nikolay.Tropin
 * 6/28/13
 */
abstract class ScalaIntroduceFieldHandlerBase extends RefactoringActionHandler{

  val REFACTORING_NAME = ScalaBundle.message("introduce.field.title")

  def afterClassChoosing[T <: PsiElement](elem: T, types: Array[ScType], project: Project, editor: Editor, file: PsiFile, title: String)
                                         (action: (T, Array[ScType], ScTemplateDefinition, Project, Editor, PsiFile) => Unit) {
    try {
      val classes = ScalaPsiUtil.getParents(elem, file).collect{ case t: ScTemplateDefinition if t != elem => t}.toArray[PsiClass]
      classes.size match {
        case 0 =>
        case 1 => action(elem, types, classes(0).asInstanceOf[ScTemplateDefinition], project, editor, file)
        case _ =>
          val selection = classes(0)
          val processor = new PsiElementProcessor[PsiClass] {
            def execute(aClass: PsiClass): Boolean = {
              action(elem, types, aClass.asInstanceOf[ScTemplateDefinition], project, editor, file)
              false
            }
          }
          NavigationUtil.getPsiElementPopup(classes, new PsiClassListCellRenderer() {
            override def getElementText(element: PsiClass): String = super.getElementText(element).replace("$", "")
          }, title, processor, selection).showInBestPositionFor(editor)
      }
    }
    catch {
      case _: IntroduceException => return
    }
    }

  class Settings(val name: String, val varType: ScType) {
    private val scalaSettings = ScalaApplicationSettings.getInstance()
    val isVar = scalaSettings.INTRODUCE_FIELD_IS_VAR
    val replaceAll = scalaSettings.INTRODUCE_FIELD_REPLACE_ALL
    val accessLevel = scalaSettings.INTRODUCE_FIELD_MODIFIER
    val explicitType = scalaSettings.INTRODUCE_FIELD_EXPLICIT_TYPE
  }

}
