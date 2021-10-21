package org.jetbrains.plugins.scala.runner

import java.io.File

import com.intellij.lang.Language
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.testFramework.{PsiTestUtil, VfsTestUtil}
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.project.{ProjectExt, ScalaLanguageLevel}
import org.jetbrains.plugins.scala.{Scala3Language, ScalaLanguage}

/**
 * @todo this partially duplicates logic in [[org.jetbrains.plugins.scala.debugger.ScalaCompilerTestBase]]<br>
 *         extract some common base class / utility method / trait
 */
@ApiStatus.Experimental
abstract class ScalaFixtureTestCaseWithSourceFolder extends ScalaFixtureTestCase {

  override protected def setUp(): Unit = {
    super.setUp()
    addSrcRoot()
  }

  private def addSrcRoot(): Unit = inWriteAction {
    val srcRoot = getOrCreateChildDir("src")
    PsiTestUtil.addSourceRoot(myModule, srcRoot, false)
  }

  private def getOrCreateChildDir(name: String) = {
    val file = new File(getBaseDir.getCanonicalPath, name)
    if (!file.exists()) file.mkdir()
    LocalFileSystem.getInstance.refreshAndFindFileByPath(file.getCanonicalPath)
  }

  protected def getSourceRootDir: VirtualFile = getBaseDir.findChild("src")

  protected def scalaLanguage: Language =
    version.language

  protected def getBaseDir: VirtualFile = {
    getProject.baseDir.ensuring(_ != null, "project base directory is null")
  }

  protected def addFileToProjectSources(relativePath: String, text: String): VirtualFile = {
    val srcDir = getSourceRootDir
    val textFixed = StringUtil.convertLineSeparators(text)
    VfsTestUtil.createFile(srcDir, relativePath, textFixed)
  }
}
