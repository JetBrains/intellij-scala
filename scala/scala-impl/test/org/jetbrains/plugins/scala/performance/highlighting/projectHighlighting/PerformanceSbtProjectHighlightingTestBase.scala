package org.jetbrains.plugins.scala.performance.highlighting.projectHighlighting

import com.intellij.psi.PsiManager
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.impl.file.impl.FileManager
import com.intellij.testFramework.PlatformTestUtil
import org.jetbrains.plugins.scala.annotator.{AnnotatorHolderMock, ScalaAnnotator}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaPsiElement, ScalaRecursiveElementVisitor}
import org.jetbrains.plugins.scala.performance.DownloadingAndImportingTestCase
import org.jetbrains.sbt.Sbt

abstract class PerformanceSbtProjectHighlightingTestBase extends DownloadingAndImportingTestCase {
  def doTest(filename: String, timeoutInMillis: Int): Unit = {
    val file = findFile(filename)
    val fileManager: FileManager = PsiManager.getInstance(myProject).asInstanceOf[PsiManagerEx].getFileManager
    PlatformTestUtil.startPerformanceTest(s"Performance test $filename", timeoutInMillis, () => {
      val annotator = ScalaAnnotator.forProject(myProject)
      file.refresh(true, false)
      val psiFile = fileManager.findFile(file)
      val mock = new AnnotatorHolderMock(psiFile)
      val visitor = new ScalaRecursiveElementVisitor {
        override def visitScalaElement(element: ScalaPsiElement): Unit = {
          try {
            annotator.annotate(element)(mock)
            super.visitScalaElement(element)
          } catch {
            case _: Throwable => //this should be checked in AllProjectHighlightingTest
          }
        }
      }
      psiFile.accept(visitor)
      fileManager.cleanupForNextTest()
    }).assertTiming()
  }

  override protected def getExternalSystemConfigFileName = Sbt.BuildFile
}

