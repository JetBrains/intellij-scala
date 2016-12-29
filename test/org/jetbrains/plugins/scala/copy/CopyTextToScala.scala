package org.jetbrains.plugins.scala.copy

import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

/**
  * Created by Kate Ustuyzhanina on 12/28/16.
  */
class CopyTextToScala extends CopyTestBase(Text(), Scala()) {
  override protected def setUp(): Unit = {
    super.setUp()
    ScalaProjectSettings.getInstance(getProject).setDontShowConversionDialog(true)
  }

  def testWrapWithExpression(): Unit ={
    val fromText = "<selection>new double[]{1.0, 2, 3};</selection>"

    val expected = "Array[Double](1.0, 2, 3)"

    doTestEmptyToFile(fromText, expected)
  }

  def testWrapWithFunction(): Unit ={
    val fromText = "<selection>assert true : \"Invocation of 'paste' operation for specific caret is not supported\";</selection>"

    val expected = "assert(true, \"Invocation of 'paste' operation for specific caret is not supported\")"
    doTestEmptyToFile(fromText, expected)
  }

  def testWrapWithClass(): Unit ={
    val fromText =
      """
        |<selection>public void doExecute() {
        |   assert true : "Invocation of 'paste' operation for specific caret is not supported";
        |}</selection>
      """.stripMargin

    val expected =
      """def doExecute() {
        |  assert(true, "Invocation of 'paste' operation for specific caret is not supported")
        |}""".stripMargin

    doTestEmptyToFile(fromText, expected)
  }

  def testAsFile(): Unit ={
    val fromText =
      """
        |<selection>import java.io.File;
        |public class Main {
        |	int func() {
        |		int qwe = 34;
        |		Boolean b = true;
        |		File f = new File("sdf");
        |		return 21;
        |	}
        |}</selection>
      """.stripMargin

    val expected =
      """import java.io.File
        |
        |class Main {
        |  def func: Int = {
        |    val qwe: Int = 34
        |    val b: Boolean = true
        |    val f: File = new File("sdf")
        |    21
        |  }
        |}""".stripMargin

    doTestEmptyToFile(fromText, expected)
  }
}
