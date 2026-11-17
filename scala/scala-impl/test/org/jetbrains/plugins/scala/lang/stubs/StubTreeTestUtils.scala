package org.jetbrains.plugins.scala.lang.stubs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.stubs.{StubElement, StubTree}
import org.jetbrains.plugins.scala.extensions._
import org.junit.Assert._

import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag

/** Shares unloaded-source lookup and typed traversal without reading source PSI text. */
private[stubs] object StubTreeTestUtils {

  /**
   * Finds the source PSI file and checks the initial unloaded-AST precondition for stub tests.
   *
   * Lookup and precondition failures identify the virtual-file path. Later lifecycle checks
   * remain with the caller; this helper establishes the starting state before stub access.
   *
   * @param project project whose PSI manager resolves the source file
   * @param virtualFile source file expected to have an unloaded AST
   * @return the resolved source PSI file after its implementation and unloaded AST are checked
   * @throws AssertionError if lookup fails, the PSI implementation is unexpected, or the AST is loaded
   */
  def findUnloadedSourceFile(
    project: Project,
    virtualFile: VirtualFile
  ): PsiFileImpl = {
    val context = s"stub-backed source `${virtualFile.getPath}`"
    val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
    assertNotNull(s"$context: PSI lookup must find a source file", psiFile)

    val actualPsiFileClassName = psiFile.getClass.getSimpleName
    val hasExpectedPsiImplementation = psiFile.isInstanceOf[PsiFileImpl]
    assertTrue(
      s"$context: expected PsiFileImpl, actual $actualPsiFileClassName",
      hasExpectedPsiImplementation
    )
    val file = psiFile.asInstanceOf[PsiFileImpl]

    val sourceAstLoaded = file.isContentsLoaded
    assertFalse(
      s"$context: source AST must be unloaded before reading stubs",
      sourceAstLoaded
    )
    file
  }

  /** Returns matching stubs, including an empty sequence when none have this type. */
  def stubsOfType[S <: StubElement[?] : ClassTag](
    tree: StubTree,
    context: String
  ): Seq[S] = {
    val requestedStubType = implicitly[ClassTag[S]].runtimeClass.getSimpleName
    assertNotNull(s"$context: cannot select $requestedStubType from a missing stub tree", tree)
    tree.getPlainList.asScala.toSeq.filterByType[S]
  }
}
