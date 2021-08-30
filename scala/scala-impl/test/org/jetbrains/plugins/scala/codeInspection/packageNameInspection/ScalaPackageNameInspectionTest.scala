package org.jetbrains.plugins.scala.codeInspection.packageNameInspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase
import org.jetbrains.plugins.scala.extensions._

class ScalaPackageNameInspectionTest extends ScalaInspectionTestBase {
  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[ScalaPackageNameInspection]

  override protected val description = "Description of ScalaPackageNameInspection"

  override protected def descriptionMatches(s: String): Boolean =
    s != null && s.startsWith("Package name")

  private var directory = Option.empty[String]
  private def inDirectory(dir: String)(body: => Unit): Unit = {
    assert(directory.isEmpty)
    directory = Some(dir)

    body

    assert(directory.nonEmpty)
    directory = None
  }
  override def onFileCreated(file: PsiFile): Unit = directory.foreach { directory =>
    inWriteCommandAction {
      val subDir = directory.split('/').foldLeft(file.getVirtualFile.getParent) {
        (dir, subDirName) => dir.createChildDirectory(this, subDirName)
      }
      subDir.findChild(file.getName).toOption.foreach { existingFile => existingFile.delete(this) }
      file.getVirtualFile.move(this, subDir)
    }(getProject)
  }

  def test_simple(): Unit = inDirectory("subdir") {
    checkTextHasNoErrors(
      """package subdir
        |
        |object Test {
        |  val test = 3
        |}
        |""".stripMargin
    )
  }

  def test_misspelled_package_decl(): Unit = inDirectory("subdir") {
    checkTextHasError(
      s"""package ${START}wrongName$END
         |
         |object Test
         |""".stripMargin
    )
  }

  def test_missing_package_decl(): Unit = inDirectory("subdir") {
    checkTextHasError(
      s"""object ${START}Test$END {
         |  val test = 3
         |}
         |""".stripMargin
    )
  }

  def test_in_parent_package_decl(): Unit = inDirectory("subdir") {
    checkTextHasError(
      s"""package ${START}subdir.wrong$END
         |
         |object Test {
         |  val test = 3
         |}
         |""".stripMargin
    )
  }

  def test_package_object(): Unit = inDirectory("subdir/subsubdir") {
    checkTextHasNoErrors(
      s"""package subdir
         |
         |package object subsubdir {
         |  val test = 3
         |}
         |""".stripMargin
    )
  }

  def test_package_object_on_root(): Unit = inDirectory("subdir") {
    checkTextHasNoErrors(
      s"""package object subdir {
         |  val test = 3
         |}
         |""".stripMargin
    )
  }

  def test_misspelled_package_object(): Unit = inDirectory("subdir/subsubdir") {
    checkTextHasError(
      s"""package ${START}subdir$END
         |
         |package object wrongName {
         |  val test = 3
         |}
         |""".stripMargin
    )
  }

  def test_package_object_in_wrong_parent_dir(): Unit = inDirectory("subdir/subsubdir") {
    checkTextHasError(
      s"""package ${START}wrongParent$END
         |
         |package object subsubdir {
         |  val test = 3
         |}
         |""".stripMargin
    )
  }

  def test_package_object_missing_parent_package(): Unit = inDirectory("subdir/subsubdir") {
    checkTextHasError(
      s"""package object ${START}subsubdir$END {
         |  val test = 3
         |}
         |""".stripMargin
    )
  }

  def test_package_object_missing_parent_dir(): Unit = inDirectory("subdir") {
    checkTextHasError(
      s"""package ${START}subdir$END
         |
         |package object subsubdir {
         |  val test = 3
         |}
         |""".stripMargin
    )
  }

  def test_package_object_missing_parent_root(): Unit =
    checkTextHasError(
      s"""package object ${START}subsubdir$END {
         |  val test = 3
         |}
         |""".stripMargin
    )

  def test_package_block(): Unit = inDirectory("subdir/subsubdir") {
    checkTextHasNoErrors(
      s"""package subdir
         |
         |package subsubdir {
         |  val test = 3
         |}
         |""".stripMargin
    )
  }

  def test_package_object_and_package(): Unit = inDirectory("subdir/subsubdir") {
    checkTextHasNoErrors(
      s"""package subdir
         |
         |package subsubdir {
         |  val x = 4
         |}
         |
         |package object subsubdir {
         |  val test = 3
         |}
         |""".stripMargin
    )
  }

  def test_should_be_in_multiple_packages(): Unit = inDirectory("subdir/subsubdir") {
    checkTextHasNoErrors(
      s"""package subdir
         |
         |class Test
         |
         |package object subsubdir {
         |  val test = 3
         |}
         |""".stripMargin
    )
  }
}
