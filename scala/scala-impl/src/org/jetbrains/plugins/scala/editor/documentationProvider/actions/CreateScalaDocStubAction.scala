package org.jetbrains.plugins.scala.editor.documentationProvider.actions

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.openapi.actionSystem.{ActionUpdateThread, AnAction, AnActionEvent, CommonDataKeys}
import com.intellij.openapi.application.ApplicationManager
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
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScEnumCase, ScEnumCases, ScFunctionDefinition, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScDocCommentOwner, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createScalaDocCommentFromText
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing.TagNames
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment
import org.jetbrains.plugins.scala.util.IndentUtil

import scala.collection.mutable

class CreateScalaDocStubAction extends AnAction(
  ScalaEditorBundle.message("create.scaladoc.stub.action.text"),
  ScalaEditorBundle.message("create.scaladoc.stub.action.description"),
  /* icon = */ null
) {
  override def update(e: AnActionEvent): Unit =
    ScalaActionUtil enableAndShowIfInScalaFile e

  override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.BGT

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
            val location = docOwner match {
              case (_: ScEnumCase) & Parent(cses: ScEnumCases) =>
                if (cses.declaredElements.length > 1) return else cses
              case _ => docOwner
            }

            docOwner.docComment match {
              case Some(_) => recreateStub(location, docOwner, editor.getDocument)
              case None => createStub(location, docOwner, editor.getDocument)
            }
          case _ =>
        }
      case _ =>
    }

  private def recreateStub(docLocation: ScDocCommentOwner, docOwner: ScDocCommentOwner, psiDocument: Document): Unit = {
    val oldComment = docLocation.getDocComment.asInstanceOf[ScDocComment]
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
        case (anchor, (tagName, paramMap)) => paramMap.foldLeft(anchor) {
          case (currentAnchor, param) =>
            val paramName = param._2.getName
            val alreadyHasAsterisk = psiDocument.getText(new TextRange(currentAnchor - 1, currentAnchor)) == "*"
            val newTagText =
              if (alreadyHasAsterisk)
                s"@$tagName $paramName \n"
              else
                s"* @$tagName $paramName \n"
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
              processParams(TagNames.ParamOrTParamSet.toList, List(fun.parameters, fun.typeParameters))
            case clazz: ScClass =>
              processParams(TagNames.ParamOrTParamSet.toList, List(clazz.parameters, clazz.typeParameters))
            case trt: ScTrait =>
              processParams(List(TagNames.TypeParam), List(trt.typeParameters))
            case alias: ScTypeAlias =>
              processParams(List(TagNames.TypeParam), List(alias.typeParameters))
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
  def createStub(docLocation: ScDocCommentOwner, docOwner: ScDocCommentOwner, psiDocument: Document): Unit = {
    val stubText = ScalaDocStubGenerator.createScalaDocStub(docOwner).trim
    val newComment = createScalaDocCommentFromText(stubText)(docOwner.getManager)
    val project = docOwner.getProject
    val docCommentEnd = docLocation.getTextRange.getStartOffset

    val tabSize = CodeStyle.getSettings(project).getTabSize(docLocation.getLanguage.getAssociatedFileType)
    val indent = IndentUtil.calcIndent(docLocation, tabSize)

    val newIndentedCommentText =
      if (indent <= 0) newComment.getText + "\n"
      else {
        val indentedLineBreak = "\n" + (" " * indent)
        newComment.getText.replaceAll("\n", indentedLineBreak) + indentedLineBreak
      }

    val commandBody: Runnable = () => {
      IntentionPreviewUtils.write { () =>
        psiDocument.insertString(docCommentEnd, newIndentedCommentText)
        PsiDocumentManager.getInstance(project).commitDocument(psiDocument)
      }

      docLocation.docComment match {
        case Some(docComment) =>
          val docRange = docComment.getTextRange
          IntentionPreviewUtils.write { () =>
            CodeStyleManager.getInstance(project).reformatText(docLocation.getContainingFile, docRange.getStartOffset, docRange.getEndOffset + 2)
          }
        case None => // I don't know when it could be the case, but just in case (see EA-246924)
      }
    }

    if (!ApplicationManager.getApplication.isWriteIntentLockAcquired || IntentionPreviewUtils.isIntentionPreviewActive) {
      commandBody.run()
    } else {
      CommandProcessor.getInstance().executeCommand(project, commandBody, ScalaEditorBundle.message("action.create.scaladoc.stub"), null, psiDocument)
    }
  }
}
