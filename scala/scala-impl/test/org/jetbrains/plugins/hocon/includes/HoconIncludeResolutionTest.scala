package org.jetbrains.plugins.hocon.includes

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.psi.{PsiFile, PsiManager}
import org.jetbrains.plugins.hocon.lexer.HoconTokenType
import org.jetbrains.plugins.hocon.psi.{HIncludeTarget, HoconPsiFile}
import org.jetbrains.plugins.hocon.ref.IncludedFileReference
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.junit.Assert._

/**
  * @author ghik
  */
trait HoconIncludeResolutionTest {

  protected def rootPath: String

  protected final def contentRoot: Option[VirtualFile] = {
    val fileSystem = LocalFileSystem.getInstance()
    Option(fileSystem.findFileByPath(rootPath))
  }

  private def findVirtualFile(path: String): Option[VirtualFile] =
    contentRoot.flatMap(file => Option(file.findFileByRelativePath(path)))

  protected def findHoconFile(path: String, project: Project): Option[HoconPsiFile] = {
    def toHoconFile(virtualFile: VirtualFile): Option[HoconPsiFile] =
      PsiManager.getInstance(project).findFile(virtualFile) match {
        case file: HoconPsiFile => Some(file)
        case _ => None
      }

    findVirtualFile(path).flatMap(toHoconFile)
  }

  protected def checkFile(path: String, project: Project): Unit = {
    val psiFile = findHoconFile(path, project).getOrElse(throw new RuntimeException)

    psiFile.depthFirst().foreach {
      case it: HIncludeTarget =>
        val parent = it.parent
        val prevComments = parent.map(_.parent.map(_.nonWhitespaceChildren).getOrElse(Iterator.empty)).getOrElse(Iterator.empty)
          .takeWhile(e => e.getNode.getElementType == HoconTokenType.HashComment)
          .toVector

        val references = it.getFileReferences

        def parentText = parent.map(_.getText).getOrElse("[No parent]")

        if (prevComments.nonEmpty) {
          assertTrue("No references in " + parentText, references.nonEmpty)
          val resolveResults = references.last.multiResolve(false)
          resolveResults.sliding(2).foreach {
            case Array(rr1, rr2) =>
              assertTrue(IncludedFileReference.ResolveResultOrdering.lteq(rr1, rr2))
            case _ =>
          }

          val expectedFiles = prevComments.flatMap(_.getText.stripPrefix("#").split(','))
            .map(_.trim)
            .filter(_.nonEmpty)
            .flatMap(findVirtualFile)

          val actualFiles = resolveResults.map(_.getElement)
            .collect {
              case file: PsiFile => file.getVirtualFile
            }

          assertEquals(parentText, expectedFiles.toSet, actualFiles.toSet)
        } else {
          assertTrue("Expected no references in " + parentText, references.isEmpty)
        }
      case _ =>
    }
  }

}

object HoconIncludeResolutionTest {

  private[includes] def inWriteAction[T](body: => T) =
    ApplicationManager.getApplication match {
      case application if application.isWriteAccessAllowed => body
      case application =>
        val computable: Computable[T] = () => body
        application.runWriteAction(computable)
    }
}