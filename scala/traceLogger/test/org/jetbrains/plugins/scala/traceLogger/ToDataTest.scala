package org.jetbrains.plugins.scala.traceLogger

import junit.framework.TestCase
import org.junit.Assert

class ToDataTest extends TestCase {
  def test_int(): Unit = {
    val data = ToData(3)

    Assert.assertEquals("3", data)
  }

  def test_string(): Unit = {
    val data = ToData("str")

    Assert.assertEquals("\"str\"", data)
  }

  class CustomBase
  object CustomBase {
    implicit def toData[T <: CustomBase]: ToData[T] = _ => "[Custom]"
  }

  def test_customBase(): Unit = {
    val data = ToData(new CustomBase)

    Assert.assertEquals("[Custom]", data)
  }

  class CustomImplWithoutToData extends CustomBase

  def test_CustomImplWithoutToData(): Unit = {
    val data = ToData(new CustomImplWithoutToData)

    Assert.assertEquals("[Custom]", data)
  }

  class CustomImplWithToData extends CustomBase

  object CustomImplWithToData {
    implicit val toData: ToData[CustomImplWithToData] = _ => "[CustomImplWithToData]"
  }

  def test_CustomImplWithToData(): Unit = {
    val data = ToData(new CustomImplWithToData)

    Assert.assertEquals("[CustomImplWithToData]", data)
  }
}
