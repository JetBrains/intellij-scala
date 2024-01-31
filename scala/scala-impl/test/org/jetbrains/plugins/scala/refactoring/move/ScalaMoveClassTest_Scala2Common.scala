package org.jetbrains.plugins.scala
package refactoring.move

import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.junit.runner.RunWith

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_12,
  TestScalaVersion.Scala_2_13,
))
final class ScalaMoveClassTest_Scala2Common extends ScalaMoveClassTestBase {

  override protected def getTestDataRoot: String = super.getTestDataRoot + "/scala2_common/"

  def testPackageObject(): Unit = {
    doTest(Seq("com.`package`"), "org")
  }

  def testPackageObject2(): Unit = {
    doTest(Seq("com"), "org")
  }

  def testSimple(): Unit = {
    doTest(Seq("com.A"), "org")
  }

  def testSingleObject(): Unit = {
    doTest(Seq("com.A"), "org")
  }

  def testcaseClass(): Unit = {
    doTest(Seq("com.A"), "org")
  }

  def testScl2625(): Unit = {
    doTest(Seq("somepackage.Dummy", "somepackage.MoreBusiness", "somepackage.Business", "somepackage.AnotherEnum"), "dest")
  }

  def testScl4623(): Unit = {
    doTest(Seq("moveRefactoring.foo.B"), "moveRefactoring.bar")
  }

  def testScl4621(): Unit = {
    doTest(Seq("moveRefactoring.foo.O"), "moveRefactoring.bar")
  }

  def testScl4619(): Unit = {
    doTest(Seq("foo.B"), "bar")
  }

  def testScl4875(): Unit = {
    doTest(Seq("com.A"), "org")
  }

  def testScl4894(): Unit = {
    doTest(Seq("moveRefactoring.foo.B", "moveRefactoring.foo.BB"), "moveRefactoring.bar")
  }

  def testScl4972(): Unit = {
    doTest(Seq("moveRefactoring.foo.B"), "moveRefactoring.bar")
  }

  def testScl5456(): Unit = {
    doTest(Seq("com.A"), "org", Kinds.onlyClasses)
  }

  def testRemoveImport_WithoutSelectors(): Unit = {
    doTest(Seq("my.pkg.two.OtherThing"), "my.pkg.one")
  }

  //SCL-19764
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
  def testAllInOne(): Unit =
    doTest(
      Seq("org.example1.declaration.X"),
      "org.example1.declaration.data"
    )

  def testAllInOne_1(): Unit =
    doTest(
      Seq("org.example1_1.declaration.X"),
      "org.example1_1.declaration.data"
    )

  def testAllInOne_1_MoveXYZ(): Unit =
    doTest(
      Seq(
        "org.example1_1.declaration.X",
        "org.example1_1.declaration.Y",
        "org.example1_1.declaration.Z"
      ),
      "org.example1_1.declaration.data"
    )

  def testAllInOne_LocalImports(): Unit =
    doTest(
      Seq("org.example2.declaration.U"),
      "org.example2.declaration.data"
    )

  def testAllInOne_LocalImports_MultipleImportExprInSingleStmt(): Unit =
    doTest(
      Seq("org.example2.declaration.U"),
      "org.example2.declaration.data"
    )

  def testMoveToSamePackageWithUsage(): Unit =
    doTest(
      Seq("org.example3.X"),
      "org.example3.data"
    )

  def testMoveToSamePackageWithUsage_MoveAll(): Unit =
    doTest(
      Seq(
        "org.example3.X",
        "org.example3.Y",
        "org.example3.Z"
      ),
      "org.example3.data"
    )

  def testSortOnlyModifiedImport_DoNotTouchOther(): Unit =
    doTest(
      Seq("org.example4.declaration.X"),
      "org.example4.declaration.data"
    )

  def testSortOnlyModifiedImport_DoNotTouchOther_1(): Unit =
    doTest(
      Seq("org.example4_1.declaration.X"),
      "org.example4_1.declaration.data"
    )

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
  def testScl4613(): Unit = {
    doTest(Seq("moveRefactoring.foo.B"), "moveRefactoring.bar")
  }

  //SCL-4878
  def testScl4878(): Unit = {
    doTest(Seq("org.B"), "com")
  }

  def testWithCompanion(): Unit = {
    doTest(Seq("source.A"), "target", Kinds.onlyClasses)
  }

  def testBothJavaAndScala(): Unit = {
    doTest(Seq("org.A", "org.J"), "com")
  }
}