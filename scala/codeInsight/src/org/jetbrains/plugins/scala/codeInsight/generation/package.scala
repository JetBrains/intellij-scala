package org.jetbrains.plugins.scala
package codeInsight

import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiDocumentManager, PsiElement, PsiFile}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValueOrVariable, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._

import scala.collection.mutable

package object generation {

  private def findAnchor(aClass: PsiClass): Option[PsiElement] = aClass match {
    case cl: ScTemplateDefinition =>
      cl.extendsBlock match {
        case ScExtendsBlock.TemplateBody(body) => body.lastChild
        case _ => None
      }
    case _ => None
  }

  def addMembers(aClass: ScTemplateDefinition, members: Seq[ScMember], document: Document, anchor: Option[PsiElement] = None): Unit = {
    val addedMembers = mutable.ListBuffer[PsiElement]()
    val psiDocManager = PsiDocumentManager.getInstance(aClass.getProject)
    for {
      anch <- anchor.orElse(findAnchor(aClass))
      parent <- Option(anch.getParent)
    } {
      members.foldLeft(anch) {
        (anchor, member) =>
          val added = parent.addBefore(member, anchor)
          addedMembers += added
          added
      }
    }

    if (addedMembers.nonEmpty) {
      psiDocManager.doPostponedOperationsAndUnblockDocument(document)
      val styleManager = CodeStyleManager.getInstance(aClass.getProject)
      val ranges = addedMembers.map(_.getTextRange)
      val minOffset = ranges.map(_.getStartOffset).min
      val maxOffset = ranges.map(_.getEndOffset).max
      val minLine = document.getLineNumber(minOffset)
      val maxLine = document.getLineNumber(maxOffset)
      for {
        file <- aClass.containingFile
        line <- minLine to maxLine
      } {
        psiDocManager.commitDocument(document)
        styleManager.adjustLineIndent(file, document.getLineStartOffset(line))
      }
    }
  }

  def isVar(elem: ScNamedElement): Boolean = ScalaPsiUtil.nameContext(elem) match {
    case _: ScVariable => true
    case param: ScClassParameter if param.isVar => true
    case _ => false
  }

  def fields(definition: ScTypeDefinition): collection.Seq[ScNamedElement] =
    members(definition).flatMap {
      case classParam: ScClassParameter if classParam.isVal || classParam.isVar => Seq(classParam)
      case property: ScValueOrVariable => property.declaredElements
      case _ => Seq.empty
    }

  def parameterlessMethods(definition: ScTypeDefinition): collection.Seq[ScNamedElement] =
    members(definition).flatMap {
      case method: ScFunction if method.parameters.isEmpty => method.declaredElements
      case _ => Seq.empty
    }

  def elementOfTypeAtCaret[T <: PsiElement](types: Class[_ <: T]*)
                                           (implicit editor: Editor, file: PsiFile): Option[T] = {
    val element = file.findElementAt(editor.getCaretModel.getOffset)
    Option(PsiTreeUtil.getParentOfType(element, types: _*))
  }

  private def members(definition: ScTypeDefinition): collection.Seq[ScMember] = {
    val constructorParameters = definition match {
      case scClass: ScClass => scClass.constructor.toSeq.flatMap(_.parameters)
      case _ => Seq.empty
    }

    definition.members ++ constructorParameters
  }
}
