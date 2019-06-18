package org.jetbrains.plugins.scala.codeInsight.hints

import junit.framework.TestCase
import org.jetbrains.plugins.scala.codeInsight.hints.ScalaTypeHintsPass.isTypeObvious
import org.junit.Assert._

// SCL-14339
class TypeIsObviousTest extends TestCase {
  def testEmpty(): Unit = {
    assertFalse(isTypeObvious("", "", ""))
    assertFalse(isTypeObvious(" ", " ", " "))
    assertFalse(isTypeObvious("", "Color", ""))
  }

  // 1.1
  def testName(): Unit = {
    assertFalse(isTypeObvious("weight", "Color", ""))

    assertTrue(isTypeObvious("color", "color", ""))
    assertTrue(isTypeObvious("color", "Color", ""))
    assertTrue(isTypeObvious("Color", "color", ""))
    assertTrue(isTypeObvious("Color", "Color", ""))

    assertTrue(isTypeObvious("backgroundColor", "BackgroundColor", ""))
    assertFalse(isTypeObvious("backgroundColor", "Backgroundcolor", ""))
    assertFalse(isTypeObvious("backgroundcolor", "BackgroundColor", ""))
  }

  // 1.2
  def testNamePlural(): Unit = {
    assertFalse(isTypeObvious("weights", "Seq[Color]", ""))
    assertFalse(isTypeObvious("colorX", "Seq[Color]", ""))
    assertFalse(isTypeObvious("colorS", "Seq[Color]", ""))
    assertFalse(isTypeObvious("color", "Seq[Color]", ""))

    assertTrue(isTypeObvious("colors", "Seq[Color]", ""))
    assertTrue(isTypeObvious("colors", "Iterable[Color]", ""))
    assertTrue(isTypeObvious("colors", "Iterator[Color]", ""))
    assertTrue(isTypeObvious("colors", "List[Color]", ""))
    assertTrue(isTypeObvious("colors", "Vector[Color]", ""))
    assertTrue(isTypeObvious("colors", "Buffer[Color]", ""))

    assertFalse(isTypeObvious("colors", "Color", ""))
    assertTrue(isTypeObvious("colors", "Colors", ""))

    // 1.1
    assertTrue(isTypeObvious("Colors", "Buffer[Color]", ""))
    assertFalse(isTypeObvious("backgroundcolors", "Buffer[BackgroundColor]", ""))
  }

  // 1.3
  def testNameSuffix(): Unit = {
    assertFalse(isTypeObvious("weightIn", "Color", ""))

    assertTrue(isTypeObvious("colorIn", "Color", ""))
    assertTrue(isTypeObvious("colorOf", "Color", ""))
    assertTrue(isTypeObvious("colorFrom", "Color", ""))

    assertFalse(isTypeObvious("colorin", "Color", ""))
    assertFalse(isTypeObvious("colorBrightness", "Color", ""))

    assertTrue(isTypeObvious("colorFrom", "ColorFrom", ""))
    assertTrue(isTypeObvious("colorfrom", "Colorfrom", ""))

    // 1.1
    assertTrue(isTypeObvious("ColorIn", "ColorIn", ""))
    assertFalse(isTypeObvious("backgroundcolorsIn", "BackgroundColorIn", ""))

    // 1.2
    assertTrue(isTypeObvious("colorsIn", "Seq[Color]", ""))
  }

  // 1.4
  def testGetPrefix(): Unit = {
    assertFalse(isTypeObvious("getWeight", "Color", ""))

    assertTrue(isTypeObvious("getColor", "Color", ""))
    assertTrue(isTypeObvious("getT", "T", ""))

    assertFalse(isTypeObvious("getcolor", "Color", ""))
    assertFalse(isTypeObvious("GetColor", "Color", ""))

    assertTrue(isTypeObvious("getColor", "GetColor", ""))
    assertTrue(isTypeObvious("GetColor", "GetColor", ""))

    // 1.1
    assertTrue(isTypeObvious("getColor", "color", ""))
    assertTrue(isTypeObvious("getBackgroundColor", "BackgroundColor", ""))
    assertFalse(isTypeObvious("getBackgroundcolor", "BackgroundColor", ""))

    // 1.2
    assertTrue(isTypeObvious("getColors", "Seq[Color]", ""))
    assertFalse(isTypeObvious("getWeights", "Seq[Color]", ""))

    // 1.3
    assertTrue(isTypeObvious("getColorIn", "Color", ""))
    assertFalse(isTypeObvious("getWeightIn", "Color", ""))
  }

  // 1.5
  def testBooleanPrefix(): Unit = {
    assertFalse(isTypeObvious("color", "Boolean", ""))

    assertFalse(isTypeObvious("isColor", "Color", ""))

    assertTrue(isTypeObvious("isColor", "Boolean", ""))
    assertTrue(isTypeObvious("hasColor", "Boolean", ""))
    assertTrue(isTypeObvious("haveColor", "Boolean", ""))

    assertTrue(isTypeObvious("isT", "Boolean", ""))

    assertFalse(isTypeObvious("iscolor", "Boolean", ""))

    assertTrue(isTypeObvious("is", "Boolean", ""))

    assertTrue(isTypeObvious("IsColor", "IsColor", ""))
    assertTrue(isTypeObvious("isColor", "isColor", ""))
    assertTrue(isTypeObvious("iscolor", "iscolor", ""))
  }

  // 1.6
  def testOptionPrefix(): Unit = {
    assertFalse(isTypeObvious("maybeColor", "Color", ""))
    assertFalse(isTypeObvious("optionOfColor", "Color", ""))

    assertFalse(isTypeObvious("maybeWeight", "Option[Color]", ""))
    assertFalse(isTypeObvious("optionOfWeight", "Option[Color]", ""))

    assertTrue(isTypeObvious("maybeColor", "Option[Color]", ""))
    assertTrue(isTypeObvious("optionOfColor", "Option[Color]", ""))

    assertTrue(isTypeObvious("maybeColor", "Some[Color]", ""))
    assertTrue(isTypeObvious("optionOfColor", "Some[Color]", ""))

    assertFalse(isTypeObvious("color", "Option[Color]", ""))
    assertFalse(isTypeObvious("color", "Some[Color]", ""))

    assertFalse(isTypeObvious("maybe", "Option[Color]", ""))
    assertFalse(isTypeObvious("optionOf", "Option[Color]", ""))

    assertFalse(isTypeObvious("maybecolor", "Option[Color]", ""))
    assertFalse(isTypeObvious("optionofcolor", "Option[Color]", ""))
    assertFalse(isTypeObvious("optionofColor", "Option[Color]", ""))

    assertTrue(isTypeObvious("maybeColor", "MaybeColor", ""))
    assertTrue(isTypeObvious("maybecolor", "maybecolor", ""))
    assertTrue(isTypeObvious("MaybeColor", "MaybeColor", ""))

    assertTrue(isTypeObvious("maybe", "Maybe", ""))
    assertTrue(isTypeObvious("optionOf", "OptionOf", ""))

    // 1.1
    assertTrue(isTypeObvious("maybeBackgroundColor", "Option[BackgroundColor]", ""))
    assertFalse(isTypeObvious("maybeBackgroundcolor", "Option[BackgroundColor]", ""))

    // 1.2
    assertTrue(isTypeObvious("maybeColors", "Option[Seq[Color]]", ""))
    assertFalse(isTypeObvious("maybeColor", "Option[Seq[Color]]", ""))

    // 1.3
    assertTrue(isTypeObvious("maybeColorIn", "Option[Color]", ""))
    assertFalse(isTypeObvious("maybeColorIn", "Color", ""))

    // 1.4
    //    assertTrue(isTypeObvious("getOptionOfColor", "Option[Color]", "")) // TODO
  }

  // 1.7
  def testCodingConvention(): Unit = {
    assertFalse(isTypeObvious("i", "Color", ""))
    assertFalse(isTypeObvious("j", "Color", ""))
    assertFalse(isTypeObvious("k", "Color", ""))
    assertFalse(isTypeObvious("n", "Color", ""))
    assertFalse(isTypeObvious("color", "Int", ""))
    assertFalse(isTypeObvious("color", "Integer", ""))
    assertTrue(isTypeObvious("i", "Int", ""))
    assertTrue(isTypeObvious("j", "Int", ""))
    assertTrue(isTypeObvious("k", "Int", ""))
    assertTrue(isTypeObvious("n", "Int", ""))
    assertTrue(isTypeObvious("i", "Integer", ""))
    assertTrue(isTypeObvious("j", "Integer", ""))
    assertTrue(isTypeObvious("k", "Integer", ""))
    assertTrue(isTypeObvious("n", "Integer", ""))

    assertFalse(isTypeObvious("b", "Color", ""))
    assertFalse(isTypeObvious("bool", "Color", ""))
    assertFalse(isTypeObvious("color", "Boolean", ""))
    assertTrue(isTypeObvious("b", "Boolean", ""))
    assertTrue(isTypeObvious("bool", "Boolean", ""))

    assertFalse(isTypeObvious("o", "Color", ""))
    assertFalse(isTypeObvious("obj", "Color", ""))
    assertFalse(isTypeObvious("color", "Object", ""))
    assertTrue(isTypeObvious("o", "Object", ""))
    assertTrue(isTypeObvious("obj", "Object", ""))

    assertFalse(isTypeObvious("s", "Color", ""))
    assertFalse(isTypeObvious("color", "String", ""))
    assertTrue(isTypeObvious("s", "String", ""))

    assertFalse(isTypeObvious("str", "Color", ""))
    assertTrue(isTypeObvious("str", "String", ""))

    assertFalse(isTypeObvious("c", "Color", ""))
    assertFalse(isTypeObvious("color", "Char", ""))
    assertFalse(isTypeObvious("color", "Character", ""))
    assertTrue(isTypeObvious("c", "Char", ""))
    assertTrue(isTypeObvious("c", "Character", ""))

    assertFalse(isTypeObvious("char", "Color", ""))
    assertTrue(isTypeObvious("char", "Char", ""))
    assertTrue(isTypeObvious("char", "Character", ""))
  }

  // 1.8
  def testKnownThing(): Unit = {
    assertFalse(isTypeObvious("width", "Color", ""))
    assertTrue(isTypeObvious("width", "Int", ""))
    assertTrue(isTypeObvious("width", "Integer", ""))

    assertFalse(isTypeObvious("height", "Color", ""))
    assertTrue(isTypeObvious("height", "Int", ""))
    assertTrue(isTypeObvious("height", "Integer", ""))

    assertFalse(isTypeObvious("length", "Color", ""))
    assertTrue(isTypeObvious("length", "Int", ""))
    assertTrue(isTypeObvious("length", "Integer", ""))

    assertFalse(isTypeObvious("count", "Color", ""))
    assertTrue(isTypeObvious("count", "Int", ""))
    assertTrue(isTypeObvious("count", "Integer", ""))

    assertFalse(isTypeObvious("name", "Color", ""))
    assertTrue(isTypeObvious("name", "String", ""))
  }

  // 1.9 TODO

  // 1.10
  def testNumberSuffix(): Unit = {
    assertFalse(isTypeObvious("width2", "Color", ""))

    assertTrue(isTypeObvious("color2", "Color", ""))
    assertTrue(isTypeObvious("color0", "Color", ""))
    assertTrue(isTypeObvious("color123", "Color", ""))

    assertFalse(isTypeObvious("123", "Color", ""))

    assertTrue(isTypeObvious("Color2", "Color2", ""))

    // 1.1
    assertTrue(isTypeObvious("backgroundColor2", "BackgroundColor", ""))
    assertFalse(isTypeObvious("backgroundcolor2", "BackgroundColor", ""))

    // 1.2
    assertTrue(isTypeObvious("colors2", "Seq[Color]", ""))
    assertFalse(isTypeObvious("color2", "Seq[Color]", ""))

    // 1.3
    assertTrue(isTypeObvious("colorIn2", "Color", ""))
    assertFalse(isTypeObvious("weightIn2", "Color", ""))

    // 1.4
    assertTrue(isTypeObvious("getColor2", "Color", ""))
    assertFalse(isTypeObvious("getWeight2", "Color", ""))

    // 1.5
    assertTrue(isTypeObvious("isColor2", "Boolean", ""))
    assertFalse(isTypeObvious("isColor2", "Color", ""))

    // 1.6
    assertTrue(isTypeObvious("maybeColor2", "Option[Color]", ""))
    assertFalse(isTypeObvious("maybeColor2", "Color", ""))

    // 1.7
    assertTrue(isTypeObvious("i2", "Int", ""))
    assertFalse(isTypeObvious("i2", "Color", ""))

    // 1.8
    assertTrue(isTypeObvious("count2", "Int", ""))
    assertFalse(isTypeObvious("count2", "Color", ""))
  }

  // 1.11 TODO

  // 2.5
  def testRightHandSideIdentifier(): Unit = {
    assertTrue(isTypeObvious("color", "Color", ""))
    assertTrue(isTypeObvious("", "Color", "color"))

    assertTrue(isTypeObvious("color", "Color", "foo"))
    assertTrue(isTypeObvious("foo", "Color", "color"))

    assertTrue(isTypeObvious("color", "Color", "color"))

    assertFalse(isTypeObvious("foo", "Color", "foo"))

    // 1.1
    assertTrue(isTypeObvious("", "BackgroundColor", "backgroundColor2"))
    assertFalse(isTypeObvious("", "BackgroundColor", "backgroundcolor2"))

    // 1.2
    assertTrue(isTypeObvious("", "Seq[Color]", "colors2"))
    assertFalse(isTypeObvious("", "Seq[Color]", "color2"))

    // 1.3
    assertTrue(isTypeObvious("", "Color", "colorIn2"))
    assertFalse(isTypeObvious("", "Color", "weightIn2"))

    // 1.4
    assertTrue(isTypeObvious("", "Color", "getColor2"))
    assertFalse(isTypeObvious("", "Color", "getWeight2"))

    // 1.5
    assertTrue(isTypeObvious("", "Boolean", "isColor2"))
    assertFalse(isTypeObvious("", "Color", "isColor2"))

    // 1.6
    assertTrue(isTypeObvious("", "Option[Color]", "maybeColor2"))
    assertFalse(isTypeObvious("", "Color", "maybeColor2"))

    // 1.7
    assertTrue(isTypeObvious("", "Int", "i2"))
    assertFalse(isTypeObvious("", "Color", "i2"))

    // 1.8
    assertTrue(isTypeObvious("", "Int", "count2"))
    assertFalse(isTypeObvious("", "Color", "count2"))

    // 1.10
    assertFalse(isTypeObvious("", "Color", "width2"))
    assertTrue(isTypeObvious("", "Color", "color2"))
  }
}
