package org.jetbrains.plugins.scala.codeInspection.packageNameInspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.AssertionMatchers
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase
import org.jetbrains.plugins.scala.extensions._

class ScalaPackageNameInspectionTest extends ScalaInspectionTestBase with AssertionMatchers {
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

  private def testMoveQuickfix(code: String, resultDir: String, hint: String): Unit = {
    testQuickFix(code, code.replace(CARET, ""), hint)
    val expectedPath = s"/src/$resultDir/${getFile.getName}".replace("//", "/")
    getFile.getVirtualFile.getPath shouldBe expectedPath
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

    testQuickFix(
      s"""package ${CARET}wrongName
         |
         |object Test
         |""".stripMargin,
      s"""package subdir
         |
         |object Test
         |""".stripMargin,
      "Set package name to 'subdir'"
    )

    testMoveQuickfix(
      s"""package ${CARET}wrongName
         |
         |object Test
         |""".stripMargin,
      resultDir = "wrongName",
      hint      = "Move to package 'wrongName'",
    )
  }

  def test_missing_package_decl(): Unit = inDirectory("subdir") {
    checkTextHasError(
      s"""object ${START}Test$END {
         |  val test = 3
         |}
         |""".stripMargin
    )

    testQuickFix(
      s"""object ${CARET}Test {
         |  val test = 3
         |}
         |""".stripMargin,
      s"""package subdir
         |
         |object Test {
         |  val test = 3
         |}
         |""".stripMargin,
      "Set package name to 'subdir'"
    )

    testMoveQuickfix(
      s"""object ${CARET}Test {
         |  val test = 3
         |}
         |""".stripMargin,
      resultDir = "",
      hint      = "Move to default package",
    )
  }

  def test_in_parent_package_decl(): Unit = inDirectory("subdir") {
    checkTextHasError(
      s"""package ${START}subdir.wrong$END
         |
         |object Test
         |""".stripMargin
    )

    testQuickFix(
      s"""package ${CARET}subdir.wrong
         |
         |object Test
         |""".stripMargin,
      s"""package subdir
         |
         |object Test
         |""".stripMargin,
      "Set package name to 'subdir'"
    )

    testMoveQuickfix(
      s"""package ${CARET}subdir.wrong
         |
         |object Test
         |""".stripMargin,
      resultDir = "subdir/wrong",
      hint      = "Move to package 'subdir.wrong'",
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
         |package object ${START}wrongName$END {
         |  val x = 3
         |}
         |""".stripMargin
    )

    testQuickFix(
      s"""package ${CARET}subdir
         |
         |package object wrongName {
         |  val x = 3
         |}
         |""".stripMargin,
      s"""package subdir
         |
         |package object subsubdir {
         |  val x = 3
         |}
         |""".stripMargin,
      "Set package name to 'subdir.subsubdir'"
    )

    testMoveQuickfix(
      s"""package ${CARET}subdir
         |
         |package object wrongName {
         |  val x = 3
         |}
         |""".stripMargin,
      resultDir = "subdir/wrongName",
      hint      = "Move to package 'subdir.wrongName'",
    )
  }

  def test_package_object_in_wrong_parent_dir(): Unit = inDirectory("subdir/subsubdir") {
    checkTextHasError(
      s"""package ${START}wrongParent$END
         |
         |package object ${START}subsubdir$END {
         |  val test = 3
         |}
         |""".stripMargin
    )


    testQuickFix(
      s"""package ${CARET}wrongParent
         |
         |package object subsubdir {
         |  val test = 3
         |}
         |""".stripMargin,
      s"""package subdir
         |
         |package object subsubdir {
         |  val test = 3
         |}
         |""".stripMargin,
      "Set package name to 'subdir.subsubdir'"
    )

    testMoveQuickfix(
      s"""package ${CARET}wrongParent
         |
         |package object subsubdir {
         |  val test = 3
         |}
         |""".stripMargin,
      resultDir = "wrongParent/subsubdir",
      hint      = "Move to package 'wrongParent.subsubdir'",
    )
  }

  def test_package_object_missing_parent_package(): Unit = inDirectory("subdir/subsubdir") {
    checkTextHasError(
      s"""package object ${START}subsubdir$END {
         |  val test = 3
         |}
         |""".stripMargin
    )

    testQuickFix(
      s"""package object ${CARET}subsubdir {
         |  val test = 3
         |}
         |""".stripMargin,
      s"""package subdir
         |
         |package object subsubdir {
         |  val test = 3
         |}
         |""".stripMargin,
      "Set package name to 'subdir.subsubdir'"
    )

    testMoveQuickfix(
      s"""package object ${CARET}subsubdir {
         |  val test = 3
         |}
         |""".stripMargin,
      resultDir = "subsubdir",
      hint      = "Move to package 'subsubdir'",
    )
  }

  def test_package_object_missing_parent_dir(): Unit = inDirectory("subdir") {
    checkTextHasError(
      s"""package ${START}subdir$END
         |
         |package object ${START}subsubdir$END {
         |  val test = 3
         |}
         |""".stripMargin
    )

    testQuickFix(
      s"""package ${CARET}subdir
         |
         |package object subsubdir {
         |  val test = 3
         |}
         |""".stripMargin,
      s"""package object subdir {
         |  val test = 3
         |}
         |""".stripMargin,
      "Set package name to 'subdir'"
    )

    testMoveQuickfix(
      s"""package ${CARET}subdir
         |
         |package object subsubdir {
         |  val test = 3
         |}
         |""".stripMargin,
      resultDir = "subdir/subsubdir",
      hint      = "Move to package 'subdir.subsubdir'",
    )
  }

  def test_package_object_missing_parent_root(): Unit = {
    checkTextHasError(
      s"""package object ${START}subsubdir$END {
         |  val test = 3
         |}
         |""".stripMargin
    )

    checkNotFixable(
      s"""package object ${CARET}subsubdir {
         |  val test = 3
         |}
         |""".stripMargin,
      hint => hint.startsWith("Set package name to") || hint == "Remove package statement"
    )
  }

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
