package org.jetbrains.plugins.scala.editor.documentationProvider.actions

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, CommonDataKeys}
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiUtilBase
import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiFile}
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.actions.ScalaActionUtil
import org.jetbrains.plugins.scala.editor.ScalaEditorBundle
import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocStubGenerator
import org.jetbrains.plugins.scala.editor.documentationProvider.actions.CreateScalaDocStubAction.createStub
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScDocCommentOwner, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createScalaDocCommentFromText
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment

import scala.collection.mutable

class CreateScalaDocStubAction extends AnAction(
  ScalaEditorBundle.message("create.scaladoc.stub.action.text"),
  ScalaEditorBundle.message("create.scaladoc.stub.action.description"),
  /* icon = */ null
) {
  override def update(e: AnActionEvent): Unit = {
    ScalaActionUtil enableAndShowIfInScalaFile e
  }

  override def actionPerformed(e: AnActionEvent): Unit = {
    val context = e.getDataContext
    val editor = CommonDataKeys.EDITOR.getData(context)

    if (editor == null) return
    val file = PsiUtilBase.getPsiFileInEditor(editor, CommonDataKeys.PROJECT.getData(context))
    if (!file.getLanguage.isKindOf(ScalaLanguage.INSTANCE)) return

    actionPerformedImpl(file, editor)
  }

  @TestOnly
  def actionPerformedImpl(file: PsiFile, editor: Editor): Unit =
    file.findElementAt(editor.getCaretModel.getOffset) match {
      case id: PsiElement if id.getNode.getElementType == ScalaTokenTypes.tIDENTIFIER =>
        id.getParent match {
          case docOwner: ScDocCommentOwner =>
            docOwner.docComment match {
              case Some(_) => recreateStub(docOwner, editor.getDocument)
              case None => createStub(docOwner, editor.getDocument)
            }
          case _ =>
        }
      case _ =>
    }

  private def recreateStub(docOwner: ScDocCommentOwner, psiDocument: Document): Unit = {
    val oldComment = docOwner.getDocComment.asInstanceOf[ScDocComment]
    val oldTags = oldComment findTagsByName (_ => true)

    def filterTags[T](groupName: String, newTags: mutable.HashMap[String, T]): Unit = {
      oldTags foreach {
        case tag if tag.getName == groupName => newTags remove tag.getValueElement.getText match {
          case Some(_) => //do nothing
          case None => tag.delete()
        }
        case _ =>
      }
    }

    @inline def convertToParamMap[T <: ScNamedElement](params: collection.immutable.Seq[T]) =
      mutable.HashMap(params.map(p => (p.getName, p)): _*)

    def processParams[T <: ScNamedElement](groupNames: List[String], params: List[Seq[T]]): Unit = {
      val paramMaps = groupNames zip params map {
        case (name, param) =>
          val paramMap = convertToParamMap(param.toSeq)
          filterTags(name, paramMap)
          paramMap
      }

      val tags = oldComment.getTags
      val firstAnchor = if (tags.nonEmpty) tags(tags.length - 1) else oldComment.getLastChild.getPrevSibling

      (groupNames zip paramMaps).foldLeft(firstAnchor.getTextRange.getEndOffset) {
        case (anchor, (name, paramMap)) => paramMap.foldLeft(anchor) {
          case (currentAnchor, param) =>
            val newTagText =
              if (psiDocument.getText(new TextRange(currentAnchor - 1, currentAnchor)) == "*") s"$name ${param._2.getName} \n"
              else s"* $name ${param._2.getName} \n"
            psiDocument.insertString(currentAnchor, newTagText)
            currentAnchor + newTagText.length
        }
      }
    }

    val project = docOwner.getProject
    CommandProcessor.getInstance().executeCommand(project, new Runnable {
      override def run(): Unit = {
        inWriteAction {
          docOwner match {
            case fun: ScFunctionDefinition =>
              processParams(List("@param", "@tparam"), List(fun.parameters, fun.typeParameters))
            case clazz: ScClass => processParams(List("@param", "@tparam"), List(clazz.parameters, clazz.typeParameters))
            case trt: ScTrait => processParams(List("@tparam"), List(trt.typeParameters))
            case alias: ScTypeAlias => processParams(List("@tparam"), List(alias.typeParameters))
            case _ =>
          }

          PsiDocumentManager getInstance project commitDocument psiDocument
          val range = docOwner.getDocComment.getTextRange
          CodeStyleManager getInstance project reformatText(docOwner.getContainingFile, range.getStartOffset, range.getEndOffset)
        }
      }
    }, ScalaEditorBundle.message("action.create.scaladoc.stub"), null, psiDocument)
  }
}

object CreateScalaDocStubAction {

  private[documentationProvider]
  def createStub(docOwner: ScDocCommentOwner, psiDocument: Document): Unit = {
    val stubText = ScalaDocStubGenerator.createScalaDocStub(docOwner).trim
    val newComment = createScalaDocCommentFromText(stubText)(docOwner.getManager)
    val project = docOwner.getProject
    val docCommentEnd = docOwner.getTextRange.getStartOffset

    val commandBody: Runnable = () => {
      inWriteAction {
        psiDocument.insertString(docCommentEnd, newComment.getText + "\n")
        PsiDocumentManager.getInstance(project).commitDocument(psiDocument)
      }

      docOwner.docComment match {
        case Some(docComment) =>
          val docRange = docComment.getTextRange
          inWriteAction {
            CodeStyleManager.getInstance(project).reformatText(docOwner.getContainingFile, docRange.getStartOffset, docRange.getEndOffset + 2)
          }
        case None => // I don't know when it could be the case, but just in case (see EA-246924)
      }
    }
    CommandProcessor.getInstance().executeCommand(project, commandBody, ScalaEditorBundle.message("action.create.scaladoc.stub"), null, psiDocument)
  }
}
