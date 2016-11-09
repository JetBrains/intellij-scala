package org.jetbrains.plugins.scala.lang.worksheet

import java.io.File

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.impl.VirtualFilePointerManagerImpl
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.openapi.vfs.{LocalFileSystem, VfsUtil}
import com.intellij.psi.{PsiDocumentManager, PsiFileFactory}
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.plugins.scala.ScalaFileType.WORKSHEET_EXTENSION
import org.jetbrains.plugins.scala.debugger.ScalaCompilerTestBase
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetSourceProcessor
import org.jetbrains.plugins.scala.{ScalaFileType, ScalaLanguage}

/**
  * User: Dmitry.Naydanov
  * Date: 12.07.16.
  */
abstract class WorksheetProcessorTestBase extends ScalaCompilerTestBase {

  override def setUp(): Unit = {
    super.setUp()
    addScalaSdk()
    addMacroPrinterDependency()
  }

  override protected def useCompileServer: Boolean = true

  import WorksheetProcessorTestBase._

  protected def addMacroPrinterDependency(): Unit = {
    val printerClazz = this.getClass.getClassLoader.loadClass("org.jetbrains.plugins.scala.worksheet.MacroPrinter")
    assert(printerClazz != null, s"Worksheet printer class $PRINTER_CLASS_NAME is null")

    val codeSource = printerClazz.getProtectionDomain.getCodeSource
    assert(codeSource != null, s"Code source for $PRINTER_CLASS_NAME is null")

    val url = codeSource.getLocation
    val rootFile = VfsUtil.findFileByURL(url)
    assert(rootFile != null, s"Cannot find $url . Vfs file is null")

    PsiTestUtil.addProjectLibrary(myModule, "WorksheetLibrary", VfsUtil.findFileByURL(url))
    VirtualFilePointerManager.getInstance.asInstanceOf[VirtualFilePointerManagerImpl].storePointers()
  }

  protected def doTest(text: String): Unit = {
    val psiFile = PsiFileFactory.getInstance(myProject).createFileFromText(defaultFileName(WORKSHEET_EXTENSION), ScalaLanguage.INSTANCE, text)
    val doc = PsiDocumentManager.getInstance(myProject).getDocument(psiFile)

    WorksheetSourceProcessor.processInner(psiFile.asInstanceOf[ScalaFile], Option(doc), 0) match {
      case Left((code, _)) =>
        val src = new File(getBaseDir.getCanonicalPath, "src")
        assert(src.exists(), "Cannot find src dir")

        val file = new File(src, defaultFileName(ScalaFileType.INSTANCE.getDefaultExtension))
        file.createNewFile()

        FileUtil.writeToFile(file, code)

        val vfile = LocalFileSystem.getInstance.refreshAndFindFileByPath(file.getCanonicalPath)
        assert(vfile != null, "Can't find created file")

        val messages = make()

        assert(messages.isEmpty, messages.mkString(" , "))
      case Right(errorElement) => assert(assertion = false, s"Compile error: $errorElement , ${errorElement.getText}")
    }

  }
}

object WorksheetProcessorTestBase {
  private val PRINTER_CLASS_NAME = "org.jetbrains.plugins.scala.worksheet.MacroPrinter"

  private def defaultFileName(extension: String) = s"dummy.$extension"
}
