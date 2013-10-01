package org.jetbrains.plugins.scala
package editor.documentationProvider

import com.intellij.openapi.actionSystem.{CommonDataKeys, AnActionEvent, AnAction}
import org.jetbrains.plugins.scala.actions.ScalaActionUtil
import com.intellij.psi.util.PsiUtilBase
import lang.psi.api.toplevel.typedef.{ScTrait, ScClass, ScDocCommentOwner}
import lang.lexer.ScalaTokenTypes
import lang.psi.impl.ScalaPsiElementFactory
import com.intellij.openapi.editor.Document
import com.intellij.psi.{PsiElement, PsiDocumentManager}
import com.intellij.psi.codeStyle.CodeStyleManager
import lang.psi.api.statements.{ScTypeAlias, ScFunctionDefinition}
import lang.scaladoc.psi.api.ScDocComment
import collection.mutable
import lang.psi.api.toplevel.ScNamedElement
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.util.TextRange

/**
 * User: Dmitry Naydanov
 * Date: 11/14/12
 */
class CreateScalaDocStubAction extends AnAction(ScalaBundle message "create.scaladoc.stub.action") {
  override def update(e: AnActionEvent) {
    ScalaActionUtil enableAndShowIfInScalaFile e
  }

  def actionPerformed(e: AnActionEvent) {
    val context = e.getDataContext
    val editor = CommonDataKeys.EDITOR.getData(context)

    if(editor == null) return
    val file = PsiUtilBase.getPsiFileInEditor(editor, CommonDataKeys.PROJECT.getData(context))
    if (file.getLanguage != ScalaFileType.SCALA_LANGUAGE) return
    
    file findElementAt editor.getCaretModel.getOffset match {
      case id: PsiElement if id.getNode.getElementType == ScalaTokenTypes.tIDENTIFIER =>
        id.getParent match {
          case docOwner: ScDocCommentOwner =>
            docOwner.docComment match {
              case Some(comment) => recreateStub(docOwner, editor.getDocument)
              case None => createStub(docOwner, editor.getDocument) 
            }
          case _ =>
        }
      case _ => 
    }
  }
  
  private def createStub(docOwner: ScDocCommentOwner, psiDocument: Document) {
    val newComment = ScalaPsiElementFactory.createDocCommentFromText(
      ScalaDocumentationProvider createScalaDocStub docOwner trim(), docOwner.getManager)
    val project = docOwner.getProject
    val docCommentEnd = docOwner.getTextRange.getStartOffset - 1
    
    CommandProcessor.getInstance().executeCommand(project, new Runnable {
      def run() {
        extensions inWriteAction {
          psiDocument insertString (docCommentEnd, newComment.getText + "\n")
          PsiDocumentManager getInstance project commitDocument psiDocument
        }
        
        val docRange = docOwner.getDocComment.getTextRange
        extensions inWriteAction {
          CodeStyleManager getInstance project reformatText (docOwner.getContainingFile, docRange.getStartOffset, docRange.getEndOffset + 2)
        }
      }
    }, "Create ScalaDoc stub", null, psiDocument)
  }
  
  private def recreateStub(docOwner: ScDocCommentOwner, psiDocument: Document) {
    val oldComment = docOwner.getDocComment.asInstanceOf[ScDocComment]
    val oldTags = oldComment findTagsByName (_ => true)
    
    def filterTags[T](groupName: String, newTags: mutable.HashMap[String, T]) {
      oldTags foreach {
        case tag if tag.getName == groupName => newTags remove tag.getValueElement.getText match {
          case Some(elem) => //do nothing
          case None => tag.delete()
        }
        case _ =>
      }
    }

    @inline def convertToParamMap[T <: ScNamedElement](params: Seq[T]) = mutable.HashMap(params map (p => (p.getName, p)): _*)

    def processParams[T <: ScNamedElement](groupNames: List[String], params: List[Seq[T]]) {
      val paramMaps = groupNames zip params map {
        case (name, param) =>
          val paramMap = convertToParamMap(param)
          filterTags(name, paramMap)
          paramMap
      }

      val tags = oldComment.getTags
      val firstAnchor = if (tags.length > 0) tags(tags.length - 1) else oldComment.getLastChild.getPrevSibling

      (firstAnchor.getTextRange.getEndOffset /: (groupNames zip paramMaps)) {
        case (anchor, (name, paramMap)) => (anchor /: paramMap) {
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
      def run() {
        extensions inWriteAction {
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
    }, "Create ScalaDoc Stub", null, psiDocument)
  }
}
