package org.jetbrains.plugins.scala
package annotator
package intention

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.daemon.impl.actions.AddImportAction
import com.intellij.codeInsight.hint.QuestionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.ui.popup.{JBPopupFactory, PopupStep}
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import com.intellij.util.ObjectUtils
import javax.swing.Icon
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScImportsHolder
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createDocLinkValue
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocResolvableCodeReference

sealed abstract class ScalaAddImportAction[Elem <: PsiElement](editor: Editor,
                                                               variants: Array[ElementToImport],
                                                               place: Elem,
                                                               popupPosition: PopupPosition = PopupPosition.best)
  extends QuestionAction {

  private implicit val project: Project = place.getProject

  protected def doAddImport(toImport: ElementToImport): Unit

  protected def alwaysAsk: Boolean = false

  override def execute(): Boolean = {
    val validVariants = variants.filter(_.isValid)

    if (validVariants.isEmpty)
      return false

    PsiDocumentManager.getInstance(project).commitAllDocuments()
    if (validVariants.length == 1 && !alwaysAsk) {
      addImport(validVariants(0))
    }
    else showChooser(validVariants)

    true
  }

  protected def chooserTitle(variants: Array[ElementToImport]): String =
    ElementToImport.messageByType(variants)(
      ScalaBundle.message("import.class.chooser.title"),
      ScalaBundle.message("import.package.chooser.title"),
      ScalaBundle.message("import.something.chooser.title")
    )


  private def showChooser(validVariants: Array[ElementToImport]): Unit = {
    val title = chooserTitle(validVariants)
    val firstPopupStep: BaseListPopupStep[ElementToImport] = new BaseListPopupStep[ElementToImport](title, validVariants: _*) {
      override def getIconFor(aValue: ElementToImport): Icon =
        aValue.element.getIcon(0)

      override def getTextFor(value: ElementToImport): String = {
        ObjectUtils.assertNotNull(value.qualifiedName)
      }

      override def isAutoSelectionEnabled: Boolean = false

      override def isSpeedSearchEnabled: Boolean = true

      import com.intellij.openapi.ui.popup.PopupStep.FINAL_CHOICE

      override def onChosen(selectedValue: ElementToImport, finalChoice: Boolean): PopupStep[_] = {
        if (selectedValue == null) {
          return FINAL_CHOICE
        }
        if (finalChoice) {
          PsiDocumentManager.getInstance(project).commitAllDocuments()
          addImport(selectedValue)
          return FINAL_CHOICE
        }
        val qname: String = selectedValue.qualifiedName
        if (qname == null) return FINAL_CHOICE
        val toExclude: java.util.List[String] = AddImportAction.getAllExcludableStrings(qname)
        new BaseListPopupStep[String](null, toExclude) {
          override def onChosen(selectedValue: String, finalChoice: Boolean): PopupStep[_] = {
            if (finalChoice) {
              AddImportAction.excludeFromImport(project, selectedValue)
            }
            super.onChosen(selectedValue, finalChoice)
          }

          override def getTextFor(value: String): String = {
            ScalaBundle.message("exclude.value.from.auto.import", value)
          }
        }
      }

      override def hasSubstep(selectedValue: ElementToImport): Boolean = {
        true
      }
    }
    val popup = JBPopupFactory.getInstance.createListPopup(firstPopupStep)
    popupPosition.showPopup(popup, editor)
  }


  private def addImport(toImport: ElementToImport): Unit = invokeLater {
    if (place.isValid && FileModificationService.getInstance.prepareFileForWrite(place.getContainingFile))
      executeWriteActionCommand(ScalaBundle.message("add.import.action")) {
        PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
        if (place.isValid) {
          doAddImport(toImport)
        }
      }
  }
}

object ScalaAddImportAction {

  def apply(editor: Editor,
            reference: ScReference,
            variants: Array[ElementToImport]): ScalaAddImportAction[_] = reference match {
    case reference: ScDocResolvableCodeReference => new ForScalaDoc(editor, variants, reference)
    case _ => new ForReference(editor, variants, reference)
  }

  private final class ForReference(editor: Editor, variants: Array[ElementToImport], ref: ScReference)
    extends ScalaAddImportAction[ScReference](editor, variants, ref) {

    override protected def doAddImport(toImport: ElementToImport): Unit = {
      toImport match {
        case PrefixPackageToImport(pack) => ref.bindToPackage(pack, addImport = true)
        case _ => ref.bindToElement(toImport.element)
      }
    }
  }

  private final class ForScalaDoc(editor: Editor, variants: Array[ElementToImport], ref: ScDocResolvableCodeReference)
    extends ScalaAddImportAction[ScDocResolvableCodeReference](editor, variants, ref) {

    //use fully qualified name instead of adding imports for scaladoc references
    override protected def doAddImport(toImport: ElementToImport): Unit = {
      ref.replace(createDocLinkValue(toImport.qualifiedName)(ref.getManager))
    }
  }

  def importImplicits(editor: Editor,
                      variants: Array[ElementToImport],
                      place: PsiElement,
                      title: String,
                      popupPosition: PopupPosition): ScalaAddImportAction[PsiElement] =
    new ImportImplicits(editor, variants, place, title, popupPosition)

  private final class ImportImplicits(editor: Editor,
                                      variants: Array[ElementToImport],
                                      place: PsiElement,
                                      title: String,
                                      popupPosition: PopupPosition)
    extends ScalaAddImportAction(editor, variants, place, popupPosition) {

    override protected def chooserTitle(variants: Array[ElementToImport]): String = title

    override protected def doAddImport(toImport: ElementToImport): Unit =
      ScImportsHolder(place).addImportForPath(toImport.qualifiedName)

    override protected def alwaysAsk: Boolean = true
  }
}

