package org.jetbrains.plugins.scala
package refactoring.move

import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsJUnit4Runner, RunWithScalaVersions, TestScalaVersion}
import org.junit.Test
import org.junit.runner.RunWith

import java.nio.file.Path

@RunWith(classOf[MultipleScalaVersionsJUnit4Runner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_12,
  TestScalaVersion.Scala_2_13
))
final class ScalaMoveClassTest_Scala2Common extends ScalaMoveClassTestBase {

  override protected def getTestDataRoot: Path = super.getTestDataRoot / "scala2_common"

  @Test
  def testPackageObject(): Unit = {
    doTest(Seq("com.`package`"), "org")
  }

  @Test
  def testPackageObject2(): Unit = {
    doTest(Seq("com"), "org")
  }

  @Test
  def testSimple(): Unit = {
    doTest(Seq("com.A"), "org")
  }

  @Test
  def testSingleObject(): Unit = {
    doTest(Seq("com.A"), "org")
  }

  @Test
  def testcaseClass(): Unit = {
    doTest(Seq("com.A"), "org")
  }

  @Test
  def testScl2625(): Unit = {
    doTest(Seq("somepackage.Dummy", "somepackage.MoreBusiness", "somepackage.Business", "somepackage.AnotherEnum"), "dest")
  }

  @Test
  def testScl4623(): Unit = {
    doTest(Seq("moveRefactoring.foo.B"), "moveRefactoring.bar")
  }

  @Test
  def testScl4621(): Unit = {
    doTest(Seq("moveRefactoring.foo.O"), "moveRefactoring.bar")
  }

  @Test
  def testScl4619(): Unit = {
    doTest(Seq("foo.B"), "bar")
  }

  @Test
  def testScl4875(): Unit = {
    doTest(Seq("com.A"), "org")
  }

  @Test
  def testScl4894(): Unit = {
    doTest(Seq("moveRefactoring.foo.B", "moveRefactoring.foo.BB"), "moveRefactoring.bar")
  }

  @Test
  def testScl4972(): Unit = {
    doTest(Seq("moveRefactoring.foo.B"), "moveRefactoring.bar")
  }

  @Test
  def testScl5456(): Unit = {
    doTest(Seq("com.A"), "org", Kinds.onlyClasses)
  }

  @Test
  def testRemoveImport_WithoutSelectors(): Unit = {
    doTest(Seq("my.pkg.two.OtherThing"), "my.pkg.one")
  }

  //SCL-19764
  @Test
  def testRemoveImport_MultipleSelectors_MoveAll(): Unit =
    doTest(
      Seq(
        "org.example.CompileOrder",
        "org.example.DebuggingInfoLevel",
        "org.example.IncrementalityType",
        "org.example.SCompileOrder",
        "org.example.SDebuggingInfoLevel",
        "org.example.SIncrementalityType",
      ),
      "org.example.data"
    )

  //SCL-5036
  @Test
  def testRemoveImport_MultipleSelectors_MoveAllButOne(): Unit =
    doTest(
      Seq(
        "org.example.CompileOrder",
        "org.example.DebuggingInfoLevel",
        "org.example.SCompileOrder",
        "org.example.SDebuggingInfoLevel",
      ),
      "org.example.data"
    )

  //SCL-19771, SCL-19779
  @Test
  def testAllInOne(): Unit =
    doTest(
      Seq("org.example1.declaration.X"),
      "org.example1.declaration.data"
    )

  @Test
  def testAllInOne_1(): Unit =
    doTest(
      Seq("org.example1_1.declaration.X"),
      "org.example1_1.declaration.data"
    )

  @Test
  def testAllInOne_1_MoveXYZ(): Unit =
    doTest(
      Seq(
        "org.example1_1.declaration.X",
        "org.example1_1.declaration.Y",
        "org.example1_1.declaration.Z"
      ),
      "org.example1_1.declaration.data"
    )

  @Test
  def testAllInOne_LocalImports(): Unit =
    doTest(
      Seq("org.example2.declaration.U"),
      "org.example2.declaration.data"
    )

  @Test
  def testAllInOne_LocalImports_MultipleImportExprInSingleStmt(): Unit =
    doTest(
      Seq("org.example2.declaration.U"),
      "org.example2.declaration.data"
    )

  @Test
  def testMoveToSamePackageWithUsage(): Unit =
    doTest(
      Seq("org.example3.X"),
      "org.example3.data"
    )

  @Test
  def testMoveToSamePackageWithUsage_MoveAll(): Unit =
    doTest(
      Seq(
        "org.example3.X",
        "org.example3.Y",
        "org.example3.Z"
      ),
      "org.example3.data"
    )

  @Test
  def testSortOnlyModifiedImport_DoNotTouchOther(): Unit =
    doTest(
      Seq("org.example4.declaration.X"),
      "org.example4.declaration.data"
    )

  @Test
  def testSortOnlyModifiedImport_DoNotTouchOther_1(): Unit =
    doTest(
      Seq("org.example4_1.declaration.X"),
      "org.example4_1.declaration.data"
    )

  @Test
  def testMoveMultipleClasses_UsedInLocalImports(): Unit = {
    doTest(
      Seq(
        "org.example5.declaration.X",
        "org.example5.declaration.Y",
        "org.example5.declaration.Z",
      ),
      "org.example5.declaration.data"
    )
  }

  //SCL-4613
  @Test
  def testScl4613(): Unit = {
    doTest(Seq("moveRefactoring.foo.B"), "moveRefactoring.bar")
  }

  //SCL-4878
  @Test
  def testScl4878(): Unit = {
    doTest(Seq("org.B"), "com")
  }

  @Test
  def testWithCompanion(): Unit = {
    doTest(Seq("source.A"), "target", Kinds.onlyClasses)
  }

  @Test
  def testBothJavaAndScala(): Unit = {
    doTest(Seq("org.A", "org.J"), "com")
  }
}
