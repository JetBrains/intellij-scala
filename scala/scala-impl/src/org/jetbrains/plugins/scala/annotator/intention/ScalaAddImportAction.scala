package org.jetbrains.plugins.scala
package annotator
package intention

import java.awt.Point

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.daemon.impl.actions.AddImportAction
import com.intellij.codeInsight.hint.QuestionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.PopupStep.FINAL_CHOICE
import com.intellij.openapi.ui.popup._
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.Key
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.util.ui.JBUI
import javax.swing.event.ListSelectionEvent
import javax.swing.{Icon, JLabel, JList}
import org.jetbrains.annotations.{Nls, TestOnly}
import org.jetbrains.plugins.scala.annotator.quickfix.FoundImplicit
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScImportsHolder
import org.jetbrains.plugins.scala.lang.psi.api.ImplicitArgumentsOwner
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createDocLinkValue, createReferenceExpressionFromText}
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocResolvableCodeReference

sealed abstract class ScalaAddImportAction[Psi <: PsiElement, Elem <: ElementToImport](
  editor: Editor,
  variants: Seq[Elem],
  place: Psi,
  popupPosition: PopupPosition = PopupPosition.best
) extends QuestionAction {

  protected implicit val project: Project = place.getProject

  protected def doAddImport(toImport: Elem): Unit

  protected def importSingleOptionAutomatically: Boolean = true

  override def execute(): Boolean = {
    val validVariants = variants.filter(_.isValid)

    if (validVariants.isEmpty)
      return false

    PsiDocumentManager.getInstance(project).commitAllDocuments()
    if (validVariants.length == 1 && importSingleOptionAutomatically) {
      addImport(validVariants.head)
    }
    else {
      val model = editor.getCaretModel
      val carets = model.getCaretsAndSelections
      val offset = model.getOffset
      try {
        if (!place.getTextRange.contains(offset)) {
          model.moveToOffset(place.endOffset - 1)
        }
        showChooser(validVariants)
      } finally {
        model.setCaretsAndSelections(carets)
      }
    }

    true
  }

  @Nls
  protected def chooserTitle(variants: Seq[Elem]): String =
    ElementToImport.messageByType(variants)(
      ScalaBundle.message("import.class.chooser.title"),
      ScalaBundle.message("import.package.chooser.title"),
      ScalaBundle.message("import.something.chooser.title")
    )

  protected def separatorAbove(variant: Elem): ListSeparator = null

  private def showChooser(validVariants: Seq[Elem]): Unit = {
    val title = chooserTitle(validVariants)
    val firstPopupStep: BaseListPopupStep[Elem] = new BaseListPopupStep[Elem](title, validVariants: _*) {
      override def getIconFor(aValue: Elem): Icon =
        aValue.element.getIcon(0)

      override def getTextFor(value: Elem): String =
        value.presentation

      override def isAutoSelectionEnabled: Boolean = false

      override def isSpeedSearchEnabled: Boolean = true

      import com.intellij.openapi.ui.popup.PopupStep.FINAL_CHOICE

      override def getSeparatorAbove(value: Elem): ListSeparator = separatorAbove(value)

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
    customizePopup(popup, editor)
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

  protected def customizePopup(popup: ListPopup, editor: Editor): Unit = {}

  protected def addImport(toImport: Elem): Unit = invokeLater {
    if (place.isValid && FileModificationService.getInstance.prepareFileForWrite(place.getContainingFile)) {
      executeAddImportInCommand(toImport)
    }
  }

  @TestOnly
  final def addImportTestOnly(toImport: ElementToImport): Unit =
    executeAddImportInCommand(toImport.asInstanceOf[Elem])

  private def executeAddImportInCommand(toImport: Elem): Unit =
    executeWriteActionCommand(ScalaBundle.message("add.import.action")) {
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
      if (place.isValid) {
        doAddImport(toImport)
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

  private sealed class ForReference[Ref <: ScReference, ToImport <: ElementToImport](editor: Editor, variants: Seq[ToImport], ref: Ref)
    extends ScalaAddImportAction[Ref, ToImport](editor, variants, ref) {

    override protected def doAddImport(toImport: ToImport): Unit = {
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

  private class ImportMemberWithPrefix(editor: Editor, variants: Seq[MemberToImport], ref: ScReferenceExpression)
    extends ScalaAddImportAction(editor, variants, ref) {

    assert(ref.qualifier.isEmpty, "Cannot invoke import with prefix for already qualified reference")

    override protected def doAddImport(toImport: MemberToImport): Unit = {
      val withPrefix = toImport.owner.name + "." + toImport.name
      val newRef = ref.replace(createReferenceExpressionFromText(withPrefix)).asInstanceOf[ScReferenceExpression]
      newRef.qualifier.foreach {
        case qRef: ScReferenceExpression => qRef.bindToElement(toImport.owner)
      }
    }
  }

  private final class ImportImplicitConversionAction(editor: Editor, variants: Seq[MemberToImport], ref: ScReferenceExpression)
    extends ForReference[ScReferenceExpression, MemberToImport](editor, variants, ref) {

    override protected def chooserTitle(variants: Seq[MemberToImport]): String =
      ScalaBundle.message("import.conversion.chooser.title")

    override protected def doAddImport(toImport: MemberToImport): Unit = {
      ScImportsHolder(ref).addImportForPath(toImport.qualifiedName)
    }
  }

  def importImplicitConversion(editor: Editor,
                               variants: Seq[MemberToImport],
                               ref: ScReferenceExpression): ScalaAddImportAction[ScReferenceExpression, MemberToImport] =
    new ImportImplicitConversionAction(editor, variants, ref)

  def importImplicits(editor: Editor,
                      variants: Seq[ImplicitToImport],
                      place: ImplicitArgumentsOwner,
                      popupPosition: PopupPosition): ScalaAddImportAction[ImplicitArgumentsOwner, ImplicitToImport] =
    new ImportImplicits(editor, variants, place, popupPosition)

  def importWithPrefix(editor: Editor,
                       variants: Seq[MemberToImport],
                       ref: ScReferenceExpression): ScalaAddImportAction[ScReferenceExpression, MemberToImport] =
    new ImportMemberWithPrefix(editor, variants, ref)

  private final class ImportImplicits(editor: Editor,
                                      variants: Seq[ImplicitToImport],
                                      place: ImplicitArgumentsOwner,
                                      popupPosition: PopupPosition)
    extends ScalaAddImportAction[ImplicitArgumentsOwner, ImplicitToImport](editor, variants, place, popupPosition) {

    override protected def chooserTitle(variants: Seq[ImplicitToImport]): String =
      ScalaBundle.message("import.implicit.chooser.title")

    override protected def doAddImport(toImport: ImplicitToImport): Unit =
      ScImportsHolder(place).addImportForPath(toImport.qualifiedName)

    override protected def secondPopupStep(element: ImplicitToImport): PopupStep[_] =
      PopupStep.FINAL_CHOICE

    override protected def separatorAbove(variant: ImplicitToImport): ListSeparator = {
      val firstWithType = variants.find(_.found.scType == variant.found.scType).get

      if (firstWithType.found.instance != variant.found.instance) null
      else new ListSeparator(variant.found.scType.presentableText(place))
    }

    override protected def customizePopup(popup: ListPopup, editor: Editor): Unit = {
      popup.asInstanceOf[ListPopupImpl].getList.clearSelection()

      popup.addListSelectionListener { (e: ListSelectionEvent) =>
        val jList = e.getSource.asInstanceOf[JList[ImplicitToImport]]
        Option(jList.getSelectedValue).foreach { value =>
          if (value.found.path.size > 1)
            showDerivationPopup(value, editor, jList)
          else
            replaceDerivationPopup(editor, None)
        }
      }
      popup.addListener(new JBPopupListener {
        override def onClosed(event: LightweightWindowEvent): Unit = {
          replaceDerivationPopup(editor, None)
        }
      })
    }
  }

  private val derivationPopupKey: Key[JBPopup] = Key.create("derivation.popup.key")

  private def replaceDerivationPopup(editor: Editor, newPopup: Option[JBPopup]): Unit = {
    val current = Option(derivationPopupKey.get(editor))
    derivationPopupKey.set(editor, newPopup.orNull)
    current.foreach(_.cancel())
  }

  private def showDerivationPopup(variant: ImplicitToImport, editor: Editor, jList: JList[ImplicitToImport]): JBPopup = {
    val label = new JLabel(derivation(variant.found))

    label.setBorder(JBUI.Borders.empty(2))
    label.setFont(editor.getColorsScheme.getFont(EditorFontType.PLAIN))

    val popup: JBPopup =
      JBPopupFactory.getInstance().createComponentPopupBuilder(label, null)
        .setFocusable(false)
        .createPopup

    popup.getContent.setBackground(jList.getBackground)

    val index = jList.getSelectedIndex
    val cellBounds = jList.getCellBounds(index, index)

    val jListLocation = jList.getLocationOnScreen
    val x = jListLocation.x + cellBounds.width
    val y = jListLocation.y + cellBounds.y + cellBounds.height - label.getPreferredSize.height
    val location = new Point(x, y)
    popup.showInScreenCoordinates(editor.getContentComponent, location)

    replaceDerivationPopup(editor, Some(popup))
    popup
  }


  private def derivation(found: FoundImplicit): String = {
    val prefix = found.path.dropRight(1).map(_.name).mkString("", "(", "(")
    val suffix = ")" * (found.path.size - 1)
    val name = found.instance.named.name
    val params = found.instance.member match {
      case f: ScFunction if f.parameters.nonEmpty => "(...)"
      case _ => ""
    }

    html(s"${gray(prefix)}$name${gray(params)}${gray(suffix)}")
  }

  private def gray(text: String) = s"""<span style="color:gray">$text</span>"""

  private def html(text: String): String =
    s"""<html>$text</html>"""
}

