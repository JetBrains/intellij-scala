package org.jetbrains.plugins.scala
package lang.scaladoc

import com.intellij.openapi.util.text.StringUtil

class ScalaDocParamDescriptionAlignmentTest extends ScalaDocEnterActionTestBase {

  private val spaces = StringUtil.repeat(" ", 4)

  def testSimpleAlignment(): Unit =
    doTest(
      s"""/**
         | * @param i blah-blah${|}
         | */""".stripMargin,
      s"""/**
         | * @param i blah-blah
         | *          ${|}
         | */""".stripMargin
    )

  def testEmptyStringAlignment(): Unit =
    doTest(
      s"""/**
         | * @param i $spaces${|}
         | */""".stripMargin,
      s"""/**
         | * @param i $spaces
         | *          ${|}
         | */""".stripMargin,
    )

  def testMultiSpacesAlignment(): Unit =
    doTest(
      s"""/**
         | * @param i       blah blah${|}
         | */""".stripMargin,
      s"""/**
         | * @param i       blah blah
         | *                ${|}
         | */""".stripMargin,
    )

  def testEnterWithInvalidParam(): Unit =
    doTest(
      s"""/**
        | * @param ${|}
        | */""".stripMargin,
      s"""/**
         | * @param
         | *       ${|}
         | */""".stripMargin,
    )

  def testEnterWithTagWithoutValue(): Unit =
    doTest(
      s"""/**
         | * @see        something   ${|}
         | */""".stripMargin,
      s"""/**
         | * @see        something
         | *             ${|}
         | */""".stripMargin
    )
}

/**
 * @see          something
 *
 */