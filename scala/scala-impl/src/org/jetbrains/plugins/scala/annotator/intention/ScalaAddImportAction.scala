package org.jetbrains.plugins.scala
package annotator
package intention

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.daemon.impl.actions.AddImportAction
import com.intellij.codeInsight.hint.QuestionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.PopupStep.FINAL_CHOICE
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import javax.swing.Icon
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScImportsHolder
import org.jetbrains.plugins.scala.lang.psi.api.ImplicitArgumentsOwner
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createDocLinkValue
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocResolvableCodeReference

sealed abstract class ScalaAddImportAction[Psi <: PsiElement, Elem <: ElementToImport](
  editor: Editor,
  variants: Seq[Elem],
  place: Psi,
  popupPosition: PopupPosition = PopupPosition.best
) extends QuestionAction {

  private implicit val project: Project = place.getProject

  protected def doAddImport(toImport: Elem): Unit

  protected def alwaysAsk: Boolean = false

  override def execute(): Boolean = {
    val validVariants = variants.filter(_.isValid)

    if (validVariants.isEmpty)
      return false

    PsiDocumentManager.getInstance(project).commitAllDocuments()
    if (validVariants.length == 1 && !alwaysAsk) {
      addImport(validVariants.head)
    }
    else showChooser(validVariants)

    true
  }

  protected def chooserTitle(variants: Seq[Elem]): String =
    ElementToImport.messageByType(variants)(
      ScalaBundle.message("import.class.chooser.title"),
      ScalaBundle.message("import.package.chooser.title"),
      ScalaBundle.message("import.something.chooser.title")
    )

  private def showChooser(validVariants: Seq[Elem]): Unit = {
    val title = chooserTitle(validVariants)
    val firstPopupStep: BaseListPopupStep[Elem] = new BaseListPopupStep[Elem](title, validVariants: _*) {
      override def getIconFor(aValue: Elem): Icon =
        aValue.element.getIcon(0)

      override def getTextFor(value: Elem): String =
        value.qualifiedName

      override def isAutoSelectionEnabled: Boolean = false

      override def isSpeedSearchEnabled: Boolean = true

      import com.intellij.openapi.ui.popup.PopupStep.FINAL_CHOICE

      override def onChosen(selectedValue: Elem, finalChoice: Boolean): PopupStep[_] = {
        if (selectedValue == null) {
          return FINAL_CHOICE
        }
        if (finalChoice) {
          addImport(selectedValue)
          return FINAL_CHOICE
        }
        secondPopupStep(selectedValue)
      }

      override def hasSubstep(selectedValue: Elem): Boolean =
        secondPopupStep(selectedValue) != FINAL_CHOICE
    }
    val popup = JBPopupFactory.getInstance.createListPopup(firstPopupStep)
    popupPosition.showPopup(popup, editor)
  }

  protected def secondPopupStep(element: Elem): PopupStep[_] = {
    val qname: String = element.qualifiedName
    if (qname == null)
      return FINAL_CHOICE

    val toExclude = AddImportAction.getAllExcludableStrings(element.qualifiedName)

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

  protected def addImport(toImport: Elem): Unit = invokeLater {
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
            variants: Seq[ElementToImport]): ScalaAddImportAction[_, _] = reference match {
    case reference: ScDocResolvableCodeReference => new ForScalaDoc(editor, variants, reference)
    case _ => new ForReference(editor, variants, reference)
  }

  private final class ForReference(editor: Editor, variants: Seq[ElementToImport], ref: ScReference)
    extends ScalaAddImportAction[ScReference, ElementToImport](editor, variants, ref) {

    override protected def doAddImport(toImport: ElementToImport): Unit = {
      toImport match {
        case PrefixPackageToImport(pack) => ref.bindToPackage(pack, addImport = true)
        case _ => ref.bindToElement(toImport.element)
      }
    }
  }

  private final class ForScalaDoc(editor: Editor, variants: Seq[ElementToImport], ref: ScDocResolvableCodeReference)
    extends ScalaAddImportAction[ScDocResolvableCodeReference, ElementToImport](editor, variants, ref) {

    //use fully qualified name instead of adding imports for scaladoc references
    override protected def doAddImport(toImport: ElementToImport): Unit = {
      ref.replace(createDocLinkValue(toImport.qualifiedName)(ref.getManager))
    }
  }

  def importImplicits(editor: Editor,
                      variants: Seq[ImplicitToImport],
                      place: ImplicitArgumentsOwner,
                      title: String,
                      popupPosition: PopupPosition): ScalaAddImportAction[ImplicitArgumentsOwner, ImplicitToImport] =
    new ImportImplicits(editor, variants, place, title, popupPosition)

  private final class ImportImplicits(editor: Editor,
                                      variants: Seq[ImplicitToImport],
                                      place: ImplicitArgumentsOwner,
                                      title: String,
                                      popupPosition: PopupPosition)
    extends ScalaAddImportAction[ImplicitArgumentsOwner, ImplicitToImport](editor, variants, place, popupPosition) {

    override protected def chooserTitle(variants: Seq[ImplicitToImport]): String = title

    override protected def doAddImport(toImport: ImplicitToImport): Unit =
      ScImportsHolder(place).addImportForPath(toImport.qualifiedName)

    override protected def secondPopupStep(element: ImplicitToImport): PopupStep[_] = {
      if (element.found.path.size <= 1)
        return PopupStep.FINAL_CHOICE

      new BaseListPopupStep[ImplicitToImport](null, element) {
        override def getTextFor(value: ImplicitToImport): String = element.derivation

        override def isSelectable(value: ImplicitToImport): Boolean = false

        override def hasSubstep(selectedValue: ImplicitToImport): Boolean = false
      }
    }

    override protected def alwaysAsk: Boolean = true
  }
}

