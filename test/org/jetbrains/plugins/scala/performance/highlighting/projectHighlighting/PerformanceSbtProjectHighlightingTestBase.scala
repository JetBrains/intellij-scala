package org.jetbrains.plugins.scala.performance.highlighting.projectHighlighting

import com.intellij.psi.PsiManager
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.impl.file.impl.FileManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.ThrowableRunnable
import org.jetbrains.plugins.scala.annotator.{AnnotatorHolderMock, ScalaAnnotator}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.performance.DownloadingAndImportingTestCase

/**
  * Author: Svyatoslav Ilinskiy
  * Date: 10/23/15.
  */
abstract class PerformanceSbtProjectHighlightingTestBase extends DownloadingAndImportingTestCase {
  def doTest(filename: String, timeoutInMillis: Int): Unit = {
    val file = findFile(filename)
    val fileManager: FileManager = PsiManager.getInstance(myProject).asInstanceOf[PsiManagerEx].getFileManager
    PlatformTestUtil.startPerformanceTest(s"Performance test $filename", timeoutInMillis, new ThrowableRunnable[Nothing] {
      override def run(): Unit = {
        val annotator = new ScalaAnnotator
        file.refresh(true, false)
        val psiFile = fileManager.findFile(file)
        val mock = new AnnotatorHolderMock(psiFile)
        val visitor = new ScalaRecursiveElementVisitor {
          override def visitElement(element: ScalaPsiElement) {
            try {
              annotator.annotate(element, mock)
              super.visitElement(element)
            } catch {
              case ignored: Throwable => //this should be checked in AllProjectHighlightingTest
            }
          }
        }
        psiFile.accept(visitor)
        fileManager.cleanupForNextTest()
      }
    }).cpuBound().assertTiming()
  }
}

