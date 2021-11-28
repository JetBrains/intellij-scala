package org.jetbrains.plugins.scala
package refactoring.move

import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.experimental.categories.Category

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
}

@Category(Array(classOf[FlakyTests]))
class ScalaMoveClassTestIgnored extends ScalaMoveClassTestBase {

  override protected def testDataRoot = TestUtils.getTestDataPath + "/move/"

  def testScl4613(): Unit = {
    doTest(Array("moveRefactoring.foo.B"), "moveRefactoring.bar")
  }

  def testScl4878(): Unit = {
    doTest(Array("org.B"), "com")
  }

  def testWithCompanion(): Unit = {
    doTest(Array("source.A"), "target", Kinds.onlyClasses)
  }

  def testBothJavaAndScala(): Unit = {
    doTest(Array("org.A", "org.J"), "com")
  }

  // wait for fix Scl-6316
  def testWithoutCompanion(): Unit = {
    doTest(Array("source.A"), "target", Kinds.onlyObjects, moveCompanion = false)
  }
}