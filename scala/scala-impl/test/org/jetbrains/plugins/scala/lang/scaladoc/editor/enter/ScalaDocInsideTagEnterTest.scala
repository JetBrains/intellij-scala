package org.jetbrains.plugins.scala.lang.scaladoc.editor.enter

import com.intellij.openapi.util.text.StringUtil

class ScalaDocInsideTagEnterTest extends ScalaDocEnterTestBase {

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
         | * ${|}
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
         | * ${|}
         | */""".stripMargin,
    )


  def testEnterWithInvalidParam_1(): Unit =
    doTest(
      s"""/**
         | * @param   xxx   ${|}
         | */""".stripMargin,
      s"""/**
         | * @param   xxx
         | * ${|}
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

  def testEnterWithTagWithoutValue_1(): Unit =
    doTest(
      s"""/**
         | * @see        `something`   ${|}
         | */""".stripMargin,
      s"""/**
         | * @see        `something`
         | *             ${|}
         | */""".stripMargin
    )

  def testEnterWithTagWithoutValue_2(): Unit =
    doTest(
      s"""/**
         | * @see        [[something]]   ${|}
         | */""".stripMargin,
      s"""/**
         | * @see        [[something]]
         | *             ${|}
         | */""".stripMargin
    )
}