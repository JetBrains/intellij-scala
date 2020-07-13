package org.jetbrains.plugins.scala
package annotator
package gutter

import com.intellij.codeInsight.navigation.NavigationUtil
import com.intellij.ide.util.EditSourceUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.search.PsiElementProcessor
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition}

import scala.collection.mutable

class ScalaGoToSuperActionHandler extends ScalaCodeInsightActionHandler {

  override def startInWriteAction: Boolean = false

  override def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
    val offset = editor.getCaretModel.getOffset
    val (superClasses, superSignatureElements) = ScalaGoToSuperActionHandler.findSuperElements(file, offset)

    def popupChooser(superElements: Seq[PsiElement], @Nls title: String): Unit = {
      val processor: PsiElementProcessor[PsiElement] = (element: PsiElement) => {
        val descriptor = EditSourceUtil.getDescriptor(element)
        if (descriptor != null && descriptor.canNavigate) {
          descriptor.navigate(true)
        }
        true
      }
      val popup = NavigationUtil.getPsiElementPopup[PsiElement](superElements.toArray, ScalaMarkerType.newCellRenderer, title, processor)
      popup.showInBestPositionFor(editor)
    }

    (superClasses, superSignatureElements) match {
      case (Seq(), Seq()) =>
      case (Seq(c: NavigatablePsiElement), Seq()) if c.canNavigate => c.navigate(true)
      case (Seq(), Seq(c: NavigatablePsiElement)) if c.canNavigate => c.navigate(true)
      case (superClassElems, Seq()) =>
        popupChooser(superClassElems, ScalaBundle.message("goto.super.class.chooser.title"))
      case (Seq(), superSigElems) =>
        popupChooser(superSigElems, ScalaBundle.message("goto.super.member.chooser.title"))
      case (superClassElems, superSigElems) =>
        popupChooser(superClassElems ++ superSigElems, ScalaBundle.message("goto.super.class.or.member.chooser.title"))
    }
  }
}

private object ScalaGoToSuperActionHandler {
  val empty: Array[PsiElement] = Array[PsiElement]()

  def findSuperElements(file: PsiFile, offset: Int): (Seq[PsiElement], Seq[PsiElement]) = {
    var element = file.findElementAt(offset)
    def test(e: PsiElement): Boolean = e match {
      case _: ScTemplateDefinition | _: ScFunction | _: ScValue
           | _: ScVariable | _: ScTypeAlias | _: ScObject => true
      case _ => false
    }
    while (element != null && !test(element)) element = element.getParent

    def templateSupers(template: ScTemplateDefinition): Array[PsiElement] = {
      def ignored = Set("java.lang.Object", "scala.Any", "scala.AnyRef", "scala.AnyVal")
      val supers = template.supers.filterNot((x: PsiClass) => ignored.contains(x.qualifiedName))
      mutable.HashSet[PsiClass](supers: _*).toArray
    }

    // TODO refactor a bit more.
    def declaredElementHolderSupers(d: ScDeclaredElementsHolder): Array[PsiElement] = {
      var el = file.findElementAt(offset)
      val elOrig = el
      while (el != null && !(el.isInstanceOf[ScTypedDefinition] && el != elOrig)) el = el.getParent
      val elements = d.declaredElements
      if (elements.isEmpty) return empty
      val supers = mutable.HashSet[NavigatablePsiElement]((if (el != null && elements.contains(el.asInstanceOf[ScTypedDefinition])) {
        ScalaPsiUtil.superValsSignatures(el.asInstanceOf[ScTypedDefinition], withSelfType = true)
      } else ScalaPsiUtil.superValsSignatures(elements.head, withSelfType =  true)).flatMap(_.namedElement match {
        case n: NavigatablePsiElement => Some(n)
        case _ => None
      }): _*)
      supers.toArray
    }
    element match {
      case x: ScObject =>
        (templateSupers(x), declaredElementHolderSupers(x) ++ ScalaPsiUtil.superTypeMembers(x))
      case template: ScTemplateDefinition =>
        (templateSupers(template), ScalaPsiUtil.superTypeMembers(template))
      case func: ScFunction =>
        val supers = mutable.HashSet[NavigatablePsiElement](func.superSignaturesIncludingSelfType.flatMap(_.namedElement match {
          case n: NavigatablePsiElement => Some(n)
          case _ => None
        }): _*)
        (Seq(), supers.toSeq)
      case d: ScDeclaredElementsHolder =>
        (Seq(), declaredElementHolderSupers(d))
      case d: ScTypeAlias =>
        val superTypeMembers = ScalaPsiUtil.superTypeMembers(d, withSelfType = true)
        (Seq(), superTypeMembers)
      case _ => (Seq.empty, Seq.empty) //Case class synthetic companion object could also implement a value member.
    }
  }
}
