package org.jetbrains.plugins.scala.codeInspection.packageNameInspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.SharedTestProjectToken
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.util.assertions.AssertionMatchers

abstract class ScalaPackageNameInspectionTestBase extends ScalaInspectionTestBase with AssertionMatchers {
  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[ScalaPackageNameInspection]

  override protected val description = "Description of ScalaPackageNameInspection"

  override protected def descriptionMatches(s: String): Boolean =
    s != null && s.startsWith("Package name")

  private var directory = Option.empty[String]

  protected def inDirectory(dir: String)(body: => Unit): Unit = {
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

  protected def testMoveQuickfix(code: String, resultDir: String, hint: String): Unit = {
    testQuickFix(code, code.replace(CARET, ""), hint)
    val expectedPath = s"/src/$resultDir/${getFile.getName}".replace("//", "/")
    getFile.getVirtualFile.getPath shouldBe expectedPath
  }

}

class ScalaPackageNameInspectionTest_Scala2 extends ScalaPackageNameInspectionTestBase {
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

  def test_package_with_backticks(): Unit = inDirectory("subdir/sub.subdir") {
    checkTextHasNoErrors(
      s"""package subdir.`sub.subdir`
         |
         |class Test
         |""".stripMargin
    )
  }

  def test_package_object_with_backticks(): Unit = inDirectory("subdir/sub.subdir") {
    checkTextHasNoErrors(
      s"""package subdir
         |
         |package object `sub.subdir` {
         |  val test = 3
         |}
         |""".stripMargin
    )
  }

  def test_move_package_with_backticks(): Unit = inDirectory("subdir/sub.subdir") {
    checkTextHasError(
      s"""package ${START}subdir.`something.wrong`$END
         |
         |class Test
         |""".stripMargin
    )

    testQuickFix(
      s"""package ${CARET}subdir.`something.wrong`
         |
         |class Test
         |""".stripMargin,
      s"""package subdir.`sub.subdir`
         |
         |class Test
         |""".stripMargin,
      "Set package name to 'subdir.`sub.subdir`'"
    )

    // doesn't work when there are escaped dots in the package name... we are using too many java utils
    testMoveQuickfix(
      s"""package ${CARET}subdir.`something wrong`
         |
         |class Test
         |""".stripMargin,
      resultDir = "subdir/something wrong",
      hint      = "Move to package 'subdir.`something wrong`'",
    )
  }

  def test_legacy_package_object_no_warnings(): Unit = inDirectory("org/example") {
    checkTextHasNoErrors(
      """package org.example
        |
        |object `package` {
        |  def foo: String = "42"
        |}
        |""".stripMargin
    )
  }

  def test_legacy_package_object_set_correct_name(): Unit = inDirectory("org/example") {
    checkTextHasError(
      s"""package ${START}org.example.inner$END
         |
         |object $START`package`$END {}
         |""".stripMargin
    )

    testQuickFix(
      s"""package ${CARET}org.example.inner
         |
         |object `package` {}
         |""".stripMargin,
      s"""package org.example
         |
         |object `package` {}
         |""".stripMargin,
      "Set package name to 'org.example'"
    )
  }
}

class ScalaPackageNameInspectionTest_Scala3 extends ScalaPackageNameInspectionTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >=  ScalaVersion.Latest.Scala_3_0

  val toplevels = Seq(
    "def test = 3",
    "val test = 3",
    "type Typ = Int"
  )

  def test_single_val(): Unit = inDirectory("subdir") {
    for (toplevel <- toplevels)
      checkTextHasNoErrors(
        s"""package subdir
           |
           |$toplevel
           |""".stripMargin
      )
  }

  def test_single_toplevel_in_wrong_dir(): Unit = {
    for (toplevel <- toplevels)
      inDirectory("subdir") {
        checkTextHasError(
          s"""package ${START}wrong$END
             |
             |$toplevel
             |""".stripMargin
        )

        testQuickFix(
          s"""package ${CARET}wrong
             |
             |$toplevel
             |""".stripMargin,
          s"""package subdir
             |
             |$toplevel
             |""".stripMargin,
          "Set package name to 'subdir'"
        )
      }
  }

  def test_move_single_def(): Unit =
    testMoveQuickfix(
      s"""package ${CARET}wrong
         |
         |def test = 3
         |""".stripMargin,
      resultDir = "wrong",
      hint      = "Move to package 'wrong'",
    )

  def test_move_single_val(): Unit =
    testMoveQuickfix(
      s"""package ${CARET}wrong
         |
         |val test = 3
         |""".stripMargin,
      resultDir = "wrong",
      hint      = "Move to package 'wrong'",
    )

  def test_move_single_type(): Unit =
    testMoveQuickfix(
      s"""package ${CARET}wrong
         |
         |type X = Int
         |""".stripMargin,
      resultDir = "wrong",
      hint      = "Move to package 'wrong'",
    )
}

class ScalaPackageNameInspectionPackagePrefixTest extends ScalaPackageNameInspectionTestBase {

  // This test needs to be in a separate test class, or else the package prefix may propagate to other tests.
  // We make sure the test has its own project descriptor by returning `SharedTestProjectToken.DoNotShare` below.
  override protected def sharedProjectToken: SharedTestProjectToken = SharedTestProjectToken.DoNotShare

  def test_package_prefix(): Unit = {
    ModuleRootModificationUtil.updateModel(myFixture.getModule, model => {
      model.getContentEntries.flatMap(_.getSourceFolders).foreach(_.setPackagePrefix("org.example"))
    })
    checkTextHasNoErrors("package org.example\nclass Foo")
  }
}
