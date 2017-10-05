package org.jetbrains.plugins.hocon.includes

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiFile, PsiManager}
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.plugins.hocon.lexer.HoconTokenType
import org.jetbrains.plugins.hocon.psi.{HIncludeTarget, HoconPsiFile}
import org.jetbrains.plugins.hocon.ref.IncludedFileReference
import org.jetbrains.plugins.scala.extensions._
import org.junit.Assert._

/**
 * @author ghik
 */
trait HoconIncludeResolutionTest {
  this: UsefulTestCase =>

  protected def project: Project

  protected def contentRoots: Array[VirtualFile]

  private def findFile(path: String): VirtualFile =
    contentRoots.iterator.flatMap(_.findFileByRelativePath(path).toOption)
      .toStream.headOption.getOrElse(throw new Exception("Could not find file " + path))

  protected def findHoconFile(path: String): PsiFile =
    PsiManager.getInstance(project).findFile(findFile(path)).asOptionOf[HoconPsiFile]
      .getOrElse(throw new Exception("Could not find HOCON file " + path))

  protected def checkFile(path: String): Unit = {
    val psiFile = findHoconFile(path)

    psiFile.depthFirst().foreach {
      case it: HIncludeTarget =>
        val prevComments = it.parent.map(_.parent.map(_.nonWhitespaceChildren).getOrElse(Iterator.empty)).getOrElse(Iterator.empty)
          .takeWhile(e => e.getNode.getElementType == HoconTokenType.HashComment)
          .toVector

        val references = it.getFileReferences
        @inline def parentText = it.parent.map(_.getText).getOrElse("[No parent]")

        if (prevComments.nonEmpty) {
          assertTrue("No references in " + parentText, references.nonEmpty)
          val resolveResults = references.last.multiResolve(false)
          resolveResults.sliding(2).foreach {
            case Array(rr1, rr2) =>
              assertTrue(IncludedFileReference.ResolveResultOrdering.lteq(rr1, rr2))
            case _ =>
          }

          val expectedFiles = prevComments.map(_.getText.stripPrefix("#")).mkString(",")
            .split(',').iterator.map(_.trim).filter(_.nonEmpty).map(findFile).toSet

          val actualFiles = resolveResults.iterator
            .map(_.getElement.asInstanceOf[PsiFile].getVirtualFile).toSet

          assertEquals(parentText, expectedFiles, actualFiles)
        } else {
          assertTrue("Expected no references in " + parentText, references.isEmpty)
        }
      case _ =>
    }
  }

}
