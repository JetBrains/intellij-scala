package org.jetbrains.plugins.scala.annotator.gutter

import _root_.scala.collection.immutable.HashSet
import com.intellij.codeInsight.navigation.NavigationUtil
import com.intellij.codeInsight.{CodeInsightActionHandler, CodeInsightBundle}
import com.intellij.ide.util.EditSourceUtil
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.psi.util.PsiTreeUtil
import lang.psi.api.statements.{ScFunction, ScValue, ScTypeAlias, ScVariable}
import lang.psi.api.toplevel.typedef.{ScTypeDefinition, ScTemplateDefinition}
import lang.psi.types.FullSignature
import ScalaMarkerType.ScCellRenderer

/**
 * User: Alexander Podkhalyuzin
 * Date: 08.11.2008
 */

class ScalaGoToSuperActionHandler extends CodeInsightActionHandler{
  def startInWriteAction = false

  def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
    val offset = editor.getCaretModel.getOffset
    val superElements = ScalaGoToSuperActionHandler.findSuperElements(file, offset);
    superElements.length match {
      case 0 => return
      case 1 => {
        superElements(0) match {
          case n: NavigatablePsiElement if n.canNavigate => n.navigate(true)
          case _ =>
        }
      }
      case _ => {
        val title = superElements(0) match {
          case _: PsiClass => ScalaBundle.message("goto.super.class.chooser.title")
          case _ => ScalaBundle.message("goto.super.member.chooser.title")
        }
        NavigationUtil.getPsiElementPopup[PsiElement](superElements, new ScCellRenderer, title, new PsiElementProcessor[PsiElement] {
          def execute(element: PsiElement): Boolean = {
            val descriptor = EditSourceUtil.getDescriptor(element)
            if (descriptor != null && descriptor.canNavigate) {
              descriptor.navigate(true)
            }
            return true
          }
        }).showInBestPositionFor(editor)
      }
    }
  }
}

private object ScalaGoToSuperActionHandler {
  val empty = Array[PsiElement]()

  def findSuperElements(file: PsiFile, offset: Int): Array[PsiElement] = {
    val element = file.findElementAt(offset)
    val e: PsiMember = PsiTreeUtil.getParentOfType(element, classOf[ScTemplateDefinition], false, classOf[ScFunction],
      classOf[ScValue], classOf[ScVariable], classOf[ScTypeAlias])

    e match {
      case template: ScTemplateDefinition => {
        val supers = template.supers.filter((x: PsiClass) => x.getQualifiedName != "java.lang.Object" && x.getQualifiedName != "scala.ScalaObject")
        return (HashSet[PsiClass](supers: _*)).toArray
      }
      case func: ScFunction => {
        val supers = func.superSignatures.map(_.element)
        return supers.toArray
      }
      case _ => empty//todo:
    }
  }
}