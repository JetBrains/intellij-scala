package org.jetbrains.plugins.scala
package refactoring.move

import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.experimental.categories.Category

//NOTE: these tests are currently valid for Scala 2.10 !!!
//They may fail when upgrading to e.g. 2.13, e.g. due to this change between 2.12 & 2.13 SCL-16305
class ScalaMoveClassTest extends ScalaMoveClassTestBase {

  override protected def testDataRoot = TestUtils.getTestDataPath + "/move/"

  def testPackageObject(): Unit = {
    doTest(Array("com.`package`"), "org")
  }

  def testPackageObject2(): Unit = {
    doTest(Array("com"), "org")
  }

  def testSimple(): Unit = {
    doTest(Array("com.A"), "org")
  }

  def testSingleObject(): Unit = {
    doTest(Array("com.A"), "org")
  }

  def testcaseClass(): Unit = {
    doTest(Array("com.A"), "org")
  }

  def testScl2625(): Unit = {
    doTest(Array("somepackage.Dummy", "somepackage.MoreBusiness", "somepackage.Business", "somepackage.AnotherEnum"), "dest")
  }

  def testScl4623(): Unit = {
    doTest(Array("moveRefactoring.foo.B"), "moveRefactoring.bar")
  }

  def testScl4621(): Unit = {
    doTest(Array("moveRefactoring.foo.O"), "moveRefactoring.bar")
  }

  def testScl4619(): Unit = {
    doTest(Array("foo.B"), "bar")
  }

  def testScl4875(): Unit = {
    doTest(Array("com.A"), "org")
  }

  def testScl4894(): Unit = {
    doTest(Array("moveRefactoring.foo.B", "moveRefactoring.foo.BB"), "moveRefactoring.bar")
  }

  def testScl4972(): Unit = {
    doTest(Array("moveRefactoring.foo.B"), "moveRefactoring.bar")
  }

  def testScl5456(): Unit = {
    doTest(Array("com.A"), "org", Kinds.onlyClasses)
  }

  def testRemoveImport_WithoutSelectors(): Unit = {
    doTest(Array("my.pkg.two.OtherThing"), "my.pkg.one")
  }

  //SCL-19764
  def testRemoveImport_MultipleSelectors_MoveAll(): Unit =
    doTest(
      Array(
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
      Array(
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
      Array("org.example1.declaration.X"),
      "org.example1.declaration.data"
    )

  def testAllInOne_1(): Unit =
    doTest(
      Array("org.example1_1.declaration.X"),
      "org.example1_1.declaration.data"
    )

  def testAllInOne_1_MoveXYZ(): Unit =
    doTest(
      Array(
        "org.example1_1.declaration.X",
        "org.example1_1.declaration.Y",
        "org.example1_1.declaration.Z"
      ),
      "org.example1_1.declaration.data"
    )

  def testAllInOne_LocalImports(): Unit =
    doTest(
      Array("org.example2.declaration.U"),
      "org.example2.declaration.data"
    )

  def testAllInOne_LocalImports_MultipleImportExprInSingleStmt(): Unit =
    doTest(
      Array("org.example2.declaration.U"),
      "org.example2.declaration.data"
    )

  def testMoveToSamePackageWithUsage(): Unit =
    doTest(
      Array("org.example3.X"),
      "org.example3.data"
    )

  def testMoveToSamePackageWithUsage_MoveAll(): Unit =
    doTest(
      Array(
        "org.example3.X",
        "org.example3.Y",
        "org.example3.Z"
      ),
      "org.example3.data"
    )

  def testSortOnlyModifiedImport_DoNotTouchOther(): Unit =
    doTest(
      Array("org.example4.declaration.X"),
      "org.example4.declaration.data"
    )

  def testSortOnlyModifiedImport_DoNotTouchOther_1(): Unit =
    doTest(
      Array("org.example4_1.declaration.X"),
      "org.example4_1.declaration.data"
    )

  def testMoveMultipleClasses_UsedInLocalImports(): Unit = {
    doTest(
      Array(
        "org.example5.declaration.X",
        "org.example5.declaration.Y",
        "org.example5.declaration.Z",
      ),
      "org.example5.declaration.data"
    )
  }

  //SCL-19801 (2.10)
  def testMoveClass_NameClashesWithOtherNamesImportedFromOtherPackageWithWithWildcard(): Unit = {
    doTest(
      Array(
        "org.example.declaration.Random",
        "org.example.declaration.X",
      ),
      "org.example.declaration.data"
    )
  }
}

class ScalaMoveClassScala213Test extends ScalaMoveClassTestBase {

  override protected def testDataRoot = TestUtils.getTestDataPath + "/moveScala213/"

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_2_13

  //SCL-19801 (2.13)
  def testMoveClass_NameClashesWithOtherNamesImportedFromOtherPackageWithWithWildcard(): Unit = {
    doTest(
      Array(
        "org.example.declaration.Random",
        "org.example.declaration.X",
      ),
      "org.example.declaration.data"
    )
  }

  //SCL-4613
  def testScl4613(): Unit = {
    doTest(Array("moveRefactoring.foo.B"), "moveRefactoring.bar")
  }

  //SCL-4878
  def testScl4878(): Unit = {
    doTest(Array("org.B"), "com")
  }
}

@Category(Array(classOf[FlakyTests]))
class ScalaMoveClassTestIgnored extends ScalaMoveClassTestBase {

  override protected def testDataRoot = TestUtils.getTestDataPath + "/move/"

  def testWithCompanion(): Unit = {
    doTest(Array("source.A"), "target", Kinds.onlyClasses)
  }

  def testBothJavaAndScala(): Unit = {
    doTest(Array("org.A", "org.J"), "com")
  }
}