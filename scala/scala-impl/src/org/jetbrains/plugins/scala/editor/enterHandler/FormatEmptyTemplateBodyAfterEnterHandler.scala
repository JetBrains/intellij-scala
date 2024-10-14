package org.jetbrains.plugins.scala.editor.enterHandler

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiFile, PsiWhiteSpace}
import org.jetbrains.plugins.scala.editor.EditorExt
import org.jetbrains.plugins.scala.extensions.{&, ObjectExt, Parent, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createWhitespace

final class FormatEmptyTemplateBodyAfterEnterHandler extends EnterHandlerDelegateAdapter {
  override def postProcessEnter(file: PsiFile, editor: Editor, dataContext: DataContext): Result = {
    if (!isApplicable(file)) return Result.Continue

    implicit val project: Project = file.getProject
    editor.commitDocument(project)

    val element = file.findElementAt(editor.getCaretModel.getOffset)
    element match {
      case (_: PsiWhiteSpace) &
        Parent((body: ScTemplateBody) & Parent(block: ScExtendsBlock))
        if body.isEmpty && block.prevSibling.exists(!_.is[PsiWhiteSpace]) =>

        val blockNode = block.getNode
        val parent = blockNode.getTreeParent
        parent.addChild(createWhitespace.getNode, blockNode)
      case _ =>
    }

    Result.Continue
  }

  private def isApplicable(file: PsiFile): Boolean =
    file.is[ScalaFile] && CodeInsightSettings.getInstance.SMART_INDENT_ON_ENTER
}
