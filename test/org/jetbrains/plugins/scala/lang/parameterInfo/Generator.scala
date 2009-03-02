import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil
import java.io.File
import com.intellij.vcsUtil.VcsUtil
import org.jetbrains.plugins.scala.util.TestUtils

val testDataPath = TestUtils.getTestDataPath
val testPaths = testDataPath + "/parameterInfo"

val files = new java.util.ArrayList[VirtualFile]()
VcsUtil.collectFiles(LocalFileSystem.getInstance.findFileByPath(testPaths.replace(File.separatorChar, '/')), files, true, false)

for (file: VirtualFile <- files.toArray(Array[VirtualFile]())) {
  print("  def test" + file.getNameWithoutExtension + "{\n")
  val path = file.getPath
  print("    testPath = " + path.substring(path.indexOf("parameterInfo") + 13, path.indexOf(".scala")) + "\n")
  print("    realOutput = \"\"\"\n")
  print("text\n\"\"\"\n")
  print("    realOutput = realOutput.trim\n")
  print("    playTest\n  }\n\n")
}