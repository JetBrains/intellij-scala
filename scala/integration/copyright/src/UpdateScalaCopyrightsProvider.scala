package org.jetbrains.plugins.scala.copyright

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiComment, PsiFile, PsiWhiteSpace, SyntaxTraverser}
import com.intellij.util.containers.TreeTraversal
import com.maddyhome.idea.copyright.CopyrightProfile
import com.maddyhome.idea.copyright.psi.{UpdateCopyrightsProvider, UpdatePsiFileCopyright}
import org.jetbrains.plugins.scala.extensions.{&, ObjectExt, Parent, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScCommentOwner
import org.jetbrains.plugins.scalaDirective.psi.api.{ScDirective, ScDirectiveToken}

import scala.jdk.CollectionConverters.{IterableHasAsScala, SeqHasAsJava}

final class UpdateScalaCopyrightsProvider extends UpdateCopyrightsProvider {

  override def createInstance(project: Project,
                              module: Module,
                              file: VirtualFile,
                              base: FileType,
                              options: CopyrightProfile): UpdateScalaCopyright =
    new UpdateScalaCopyright(project, module, file, options)
}

private final class UpdateScalaCopyright(
  project: Project,
  module: Module,
  file: VirtualFile,
  options: CopyrightProfile
) extends UpdatePsiFileCopyright(project, module, file, options) {
  override def accept(): Boolean = getFile.is[ScalaFile]

  override def scanFile(): Unit = {
    val file = getFile
    val comments = UpdateScalaCopyright.getExistingComments(file)
    checkComments(comments.lastOption.orNull, true, comments.asJava)
  }
}

private object UpdateScalaCopyright {
  private def getExistingComments(file: PsiFile): List[PsiComment] = SyntaxTraverser.psiTraverser(file)
    .withTraversal(TreeTraversal.LEAVES_DFS)
    .traverse()
    .takeWhile { e =>
      e.is[PsiWhiteSpace, ScDirectiveToken] ||
        e.is[PsiComment] && !e.parent.exists(_.isInstanceOf[ScCommentOwner]) ||
        e.getText.isEmpty
    }
    .asScala
    .toList
    .collect {
      case comment: PsiComment => comment
      case (_: ScDirectiveToken) & Parent(directive: ScDirective) => directive
    }
    .distinct
}
