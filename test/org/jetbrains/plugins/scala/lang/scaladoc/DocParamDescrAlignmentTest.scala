package org.jetbrains.plugins.scala
package lang.scaladoc

import com.intellij.openapi.util.text.StringUtil

/**
 * User: Dmitry Naydanov
 * Date: 2/6/12
 */

class DocParamDescrAlignmentTest extends ScalaDocEnterActionTestBase {
  
  import org.jetbrains.plugins.scala.lang.scaladoc.DocParamDescrAlignmentTest._
  
  def testSimpleAlignment() {
    val header = "/**\n *" + docParamText + "blah-blah "
    val testText = "\n */"
    val stub = "/**\n * @param i blah-blah\n *" + StringUtil.repeat (" ", docParamText.length) + testText

    checkGeneratedTextFromString(header, testText, stub)
  }

  def testEmptyStringAlignment() {
    val spaces = StringUtil.repeat(" ", 10)
    val header = "/**\n *" + docParamText + " " + spaces
    val testText = "\n */"
    val stub = "/**\n *" + docParamText + spaces + "\n *" + StringUtil.repeat (" ", docParamText.length) + testText

    checkGeneratedTextFromString(header, testText, stub)
  }

  def testMultiSpacesAlignment() {
    val spaces = StringUtil.repeat(" ", 10)
    val header = "/**\n *" + docParamText + spaces + "blah-blah "
    val testText = "\n */"
    val stub = "/**\n *" + docParamText + spaces + "blah-blah\n *" + StringUtil.repeat(" ", docParamText.length) +
            spaces + testText

    checkGeneratedTextFromString(header, testText, stub)
  }

  def testEnterWithCar() {
    val header = "/**\n *" + docParamText + "blah  "
    val testText = "\n */"
    val stub = "/**\n *" + docParamText + "blah \n *" + StringUtil.repeat(" ", docParamText.length) + testText

    checkGeneratedTextFromString(header, testText, stub)
  }
  
  def testEnterWithInvalidParam() {
    val header = "/**\n * @param    "
    val testText = "\n */"
    val stub = "/**\n * @param   \n *" + StringUtil.repeat(" ", " @param".length) + testText

    checkGeneratedTextFromString(header, testText, stub)
  }

  def testEnterWithTagWithoutValue() {
    val descrText = "something"
    val spaces = StringUtil.repeat(" ", 4)
    val header = "/**\n * @see" + spaces + descrText + " "
    val testText = "\n */"
    val stub = "/**\n * @see" + spaces + descrText + "\n" + " *" + StringUtil.repeat(" ", " @see".length) + spaces + testText

    checkGeneratedTextFromString(header, testText, stub)
  }
}

object DocParamDescrAlignmentTest {
  private val docParamText = " @param i "
}