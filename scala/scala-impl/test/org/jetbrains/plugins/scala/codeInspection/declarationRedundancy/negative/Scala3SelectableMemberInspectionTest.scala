package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.negative

import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.Scala3UnusedDeclarationInspectionTestBase

/**
 * The members backing Scala 3's structural types (`scala.Selectable`) are invoked with the selected
 * member's name at the call site, so their own name never appears there.
 *
 * @see [[https://youtrack.jetbrains.com/issue/SCL-20304]]
 */
class Scala3SelectableMemberInspectionTest extends Scala3UnusedDeclarationInspectionTestBase {

  private def addUsageFile(text: String): Unit = myFixture.addFileToProject("Usage.scala", text)

  def test_select_dynamic(): Unit = {
    addUsageFile(
      """type Person = Record {
        |  val name: String
        |  val age: Int
        |}
        |
        |object Usage {
        |  val p = Record("name" -> "JetBrains", "age" -> 42).asInstanceOf[Person]
        |  println(s"${p.name} ${p.age}")
        |}""".stripMargin
    )
    checkTextHasNoErrors(
      """class Record(elems: (String, Any)*) extends Selectable {
        |  private val fields = elems.toMap
        |  def selectDynamic(name: String): Any = fields(name)
        |}""".stripMargin
    )
  }

  def test_apply_dynamic(): Unit = {
    addUsageFile(
      """type Greeter = Record { def greet(who: String): String }
        |
        |object Usage {
        |  val r = new Record().asInstanceOf[Greeter]
        |  println(r.greet("JetBrains"))
        |}""".stripMargin
    )
    checkTextHasNoErrors(
      """class Record extends Selectable {
        |  def applyDynamic(name: String, paramTypes: Class[?]*)(args: Any*): Any = (name, paramTypes, args)
        |}""".stripMargin
    )
  }
}
