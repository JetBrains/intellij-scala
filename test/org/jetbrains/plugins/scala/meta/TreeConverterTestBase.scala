package org.jetbrains.plugins.scala.meta

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.{PsiFile, PsiManager}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.meta.trees.TreeConverter
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion

class TreeConverterTestBase extends ScalaLightCodeInsightFixtureTestAdapter with TreeConverterTestUtils {

  def fixture = myFixture

  val converter: TreeConverter = new TreeConverter {
    override def findFileByPath(path: String): PsiFile = {
      val virtualFile = VirtualFileManager.getInstance().findFileByUrl(path)
      PsiManager.getInstance(myFixture.getProject).findFile(virtualFile)
    }

    override def getCurrentProject: Project = myFixture.getProject
  }
  def testOk() = () // to get rid of no tests found spam in IDEA junit runner
}

class TreeConverterTestBaseNoLibrary extends TreeConverterTestBase {
  override def loadScalaLibrary = false
  override def testOk() = () // to get rid of no tests found spam in IDEA junit runner
}

class TreeConverterTestBaseWithLibrary extends TreeConverterTestBase {
  override protected def libVersion: ScalaSdkVersion = ScalaSdkVersion._2_11
  override def testOk() = () // to get rid of no tests found spam in IDEA junit runner
}
