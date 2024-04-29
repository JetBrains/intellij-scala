package org.jetbrains.plugins.scala.testingSupport.munit

import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.{PsiDirectory, PsiManager}
import org.jetbrains.plugins.scala.configurations.TestLocation.{CaretLocation, PackageLocation, PsiElementLocation}
import org.jetbrains.plugins.scala.extensions.inReadAction

class MUnitShouldReplaceJUnit extends MUnitTestCase {

  private val ClassName = "MyTestClass"
  private val FileName = "org/example/MyTestClass.scala"

  addSourceFile(FileName,
    s"""package org.example
       |
       |import munit.ScalaCheckSuite
       |
       |class $ClassName extends ScalaCheckSuite {
       |  test("simple test") {
       |  }
       |}
       |""".stripMargin
  )

  /**
   * NOTE: we just create configuration and expect that [[getSingleConfigurationFromContext]]
   * will ensure that there was only 1 configuration produced by all producers
   */
  def testCreateForCaretAtClass(): Unit = {
    createTestFromLocation(CaretLocation(FileName, 4, 0))
  }

  def testCreateForCaretAtSingleTest(): Unit = {
    createTestFromLocation(CaretLocation(FileName, 5, 5))
  }

  def testCreateForPackage(): Unit = {
    createTestFromLocation(PackageLocation("org.example"))
  }

  def testCreateForDirectory_SrcRoot(): Unit = {
    createTestFromLocation(PsiElementLocation(getSrcPsiDirectory))
  }

  def testCreateForDirectory_Package(): Unit = {
    val packageDir = inReadAction {
      val dir = getSrcPsiDirectory.getFirstChild.asInstanceOf[PsiDirectory]
      assertEquals("org", dir.getName)
      dir
    }
    createTestFromLocation(PsiElementLocation(packageDir))
  }

  private def getSrcPsiDirectory: PsiDirectory = inReadAction {
    val srcDir = VirtualFileManager.getInstance.findFileByNioPath(srcPath)
    PsiManager.getInstance(myProject).findDirectory(srcDir)
  }
}
