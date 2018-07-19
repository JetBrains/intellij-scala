package org.jetbrains.plugins.scala.lang.worksheet

import java.io.File

import com.intellij.openapi.module.Module
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.{LocalFileSystem, VfsUtil}
import com.intellij.psi.{PsiDocumentManager, PsiFileFactory}
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.plugins.scala.ScalaFileType.WORKSHEET_EXTENSION
import org.jetbrains.plugins.scala.base.libraryLoaders.ThirdPartyLibraryLoader
import org.jetbrains.plugins.scala.debugger.{ScalaCompilerTestBase, ScalaVersion}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetSourceProcessor
import org.jetbrains.plugins.scala.{ScalaFileType, ScalaLanguage}
import org.junit.Assert.assertNotNull

/**
  * User: Dmitry.Naydanov
  * Date: 12.07.16.
  */
abstract class WorksheetProcessorTestBase extends ScalaCompilerTestBase {

  override protected def useCompileServer: Boolean = true

  import WorksheetProcessorTestBase._

  override protected def additionalLibraries: Seq[ThirdPartyLibraryLoader] =
    Seq(WorksheetProcessorTestBase.MacroPrinterLoader(this.getClass.getClassLoader))

  protected def doTest(text: String): Unit = {
    val psiFile = PsiFileFactory.getInstance(myProject).createFileFromText(defaultFileName(WORKSHEET_EXTENSION), ScalaLanguage.INSTANCE, text)
    val doc = PsiDocumentManager.getInstance(myProject).getDocument(psiFile)

    WorksheetSourceProcessor.processDefault(psiFile.asInstanceOf[ScalaFile], Option(doc)) match {
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

  private def defaultFileName(extension: String) = s"dummy.$extension"

  case class MacroPrinterLoader(classLoader: ClassLoader) extends ThirdPartyLibraryLoader {

    override protected val name: String = "WorksheetLibrary"

    import MacroPrinterLoader.CLASS_NAME

    override def init(implicit module: Module, version: ScalaVersion): Unit = {
      val printerClazz = classLoader.loadClass(CLASS_NAME)
      assertNotNull(s"Worksheet printer class $CLASS_NAME is null", printerClazz)

      val codeSource = printerClazz.getProtectionDomain.getCodeSource
      assertNotNull(s"Code source for $CLASS_NAME is null", codeSource)

      val url = codeSource.getLocation
      val rootFile = VfsUtil.findFileByURL(url)
      assertNotNull(s"Cannot find $url. Vfs file is null", rootFile)

      PsiTestUtil.addProjectLibrary(module, name, rootFile)
    }

    override protected def path(implicit version: ScalaVersion): String =
      throw new UnsupportedOperationException
  }

  object MacroPrinterLoader {
    private val CLASS_NAME: String = "org.jetbrains.plugins.scala.worksheet.MacroPrinter"
  }

}
