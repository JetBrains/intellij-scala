package org.jetbrains.plugins.scala.codeInspection.unused

class Scala2UsedGlobalSymbolInspectionTest extends ScalaUnusedSymbolInspectionTestBase {

  private def addFile(text: String): Unit = myFixture.addFileToProject("Foo.scala", text)
  
  def test_trait_extends_trait(): Unit = {
    addFile("trait Foo extends Bar")
    checkTextHasNoErrors("trait Bar")
  }

  def test_class_extends_trait(): Unit = {
    addFile("class Foo extends Bar")
    checkTextHasNoErrors("trait Bar")
  }

  def test_trait_extends_class(): Unit = {
    addFile("trait Foo extends Bar")
    checkTextHasNoErrors("class Bar")
  }

  def test_class_extends_class(): Unit = {
    addFile("class Foo extends Bar")
    checkTextHasNoErrors("class Bar")
  }

  def test_object_extends_trait(): Unit = {
    addFile("object Foo extends Bar")
    checkTextHasNoErrors("trait Bar")
  }

  def test_object_extends_class(): Unit = {
    addFile("object Foo extends Bar")
    checkTextHasNoErrors("class Bar")
  }

  def test_public_def(): Unit = {
    addFile("new Bar().fizz")
    checkTextHasNoErrors("class Bar { def fizz = 42 }")
  }

  def test_public_var(): Unit = {
    addFile("new Bar().fizz")
    checkTextHasNoErrors("class Bar { var fizz = 42 }")
  }

  def test_public_val(): Unit = {
    addFile("new Bar().fizz")
    checkTextHasNoErrors("class Bar { val fizz = 42 }")
  }

  def test_case_class_public_field_when_extracted_into_a_different_name(): Unit = {
    addFile("Bar(42) match { case Bar(extracted) => extracted }")
    checkTextHasNoErrors("case class Bar(fizz: Int)")
  }

  def test_case_class_private_field_when_extracted_into_a_different_name(): Unit = {
    addFile("Bar(42) match { case Bar(extracted) => extracted }")
    checkTextHasNoErrors("case class Bar(private val fizz: Int)")
  }

  def test_implicit_class(): Unit = {
    addFile("import Foo.Bar; 0.plus42")
    checkTextHasNoErrors("object Foo { implicit class Bar(x: Int) { def plus42 = x + 42 } }")
  }
}
