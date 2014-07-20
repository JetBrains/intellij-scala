package intellijhocon

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import com.intellij.util.LocalTimeCounter

object HoconTestUtils {
  def createPseudoPhysicalHoconFile(project: Project, text: String) = {
    val tempFile = project.getBaseDir + "temp.conf"
    val fileType = FileTypeManager.getInstance.getFileTypeByFileName(tempFile)
    PsiFileFactory.getInstance(project).createFileFromText(
      tempFile, fileType, text, LocalTimeCounter.currentTime(), true)
  }

  implicit def asRunnable(code: => Unit): Runnable =
    new Runnable {
      def run() = code
    }
}
