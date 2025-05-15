package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase

class PrimitivesConformanceTestBase extends TypeInferenceTestBase {

  //SCL-5358
  def testSCL5358(): Unit = assertErrorsText(
      """final val x = 0
        |val y: Byte = x
        |""".stripMargin,
    """Error(x,Expression of type Int doesn't conform to expected type Byte)"""
  )

  //SCL-19295
  def testSCL19295(): Unit = {
    doTest(
      s"""val byte: Byte = 1
        |$START~byte$END
        |//Int
        |""".stripMargin
    )
  }

  def testByteCoercion(): Unit = assertErrorsText(
    """val byte0: Byte = 0
      |val byte1: Byte = 1
      |val byte127: Byte = 127
      |val byte128: Byte = 128
      |
      |val byte_m128: Byte = -128
      |val byte_m129: Byte = -129
      |
      |val byte_from_char: Byte = 1 : Char
      |val byte_from_short: Byte = 1 : Short
      |val byte_from_int: Byte = 1 : Int
      |val byte_from_long: Byte = 1L
      |
      |val byte1_0: Byte = 0.0
      |val byte1f: Byte = 1f
      |val byte1d: Byte = 1d
      |""".stripMargin,
    """Error(128,Expression of type Int doesn't conform to expected type Byte)
      |Error(-129,Expression of type Int doesn't conform to expected type Byte)
      |Error(Char,Expression of type Char doesn't conform to expected type Byte)
      |Error(Short,Expression of type Short doesn't conform to expected type Byte)
      |Error(Int,Expression of type Int doesn't conform to expected type Byte)
      |Error(1L,Expression of type Long doesn't conform to expected type Byte)
      |Error(0.0,Expression of type Double doesn't conform to expected type Byte)
      |Error(1f,Expression of type Float doesn't conform to expected type Byte)
      |Error(1d,Expression of type Double doesn't conform to expected type Byte)
      |""".stripMargin
  )

  def testCharCoercion(): Unit = assertErrorsText(
    """val char0: Char = 0
      |val char1: Char = 1
      |val char65536: Char = 65535
      |val char65537: Char = 65536
      |
      |val char_m1: Char = -1
      |
      |val char_from_byte: Char = 1 : Byte
      |val char_from_short: Char = 1 : Short
      |val char_from_int: Char = 1 : Int
      |val char_from_long: Char = 1L
      |
      |val char1_0: Char = 0.0
      |val char1f: Char = 1f
      |val char1d: Char = 1d
      |""".stripMargin,
    """Error(65536,Expression of type Int doesn't conform to expected type Char)
      |Error(-1,Expression of type Int doesn't conform to expected type Char)
      |Error(Byte,Expression of type Byte doesn't conform to expected type Char)
      |Error(Short,Expression of type Short doesn't conform to expected type Char)
      |Error(Int,Expression of type Int doesn't conform to expected type Char)
      |Error(1L,Expression of type Long doesn't conform to expected type Char)
      |Error(0.0,Expression of type Double doesn't conform to expected type Char)
      |Error(1f,Expression of type Float doesn't conform to expected type Char)
      |Error(1d,Expression of type Double doesn't conform to expected type Char)
      |""".stripMargin
  )

  def testShortCoercion(): Unit = assertErrorsText(
    """val short0: Short = 0
      |val short1: Short = 1
      |val short32767: Short = 32767
      |val short32768: Short = 32768
      |
      |val short_m32768: Short = -32768
      |val short_m32769: Short = -32769
      |
      |val short_from_byte: Short = 1 : Byte
      |val short_from_short: Short = 1 : Char
      |val short_from_int: Short = 1 : Int
      |val short_from_long: Short = 1L
      |
      |val short1_0: Short = 0.0
      |val short1f: Short = 1f
      |val short1d: Short = 1d
      |""".stripMargin,
    """Error(32768,Expression of type Int doesn't conform to expected type Short)
      |Error(-32769,Expression of type Int doesn't conform to expected type Short)
      |Error(Char,Expression of type Char doesn't conform to expected type Short)
      |Error(Int,Expression of type Int doesn't conform to expected type Short)
      |Error(1L,Expression of type Long doesn't conform to expected type Short)
      |Error(0.0,Expression of type Double doesn't conform to expected type Short)
      |Error(1f,Expression of type Float doesn't conform to expected type Short)
      |Error(1d,Expression of type Double doesn't conform to expected type Short)
      |""".stripMargin
  )

  def testIntCoercion(): Unit = assertErrorsText(
    """val int0: Int = 0
      |val int1: Int = 1
      |val int2147483647: Int = 2147483647
      |val int2147483648: Int = 2147483648
      |
      |val int_m2147483648: Int = -2147483648
      |val int_m2147483649: Int = -2147483649
      |
      |val int_from_byte: Int = 1 : Byte
      |val int_from_short: Int = 1 : Char
      |val int_from_int: Int = 1 : Int
      |val int_from_long: Int = 1L
      |
      |val int1_0: Int = 0.0
      |val int1f: Int = 1f
      |val int1d: Int = 1d
      |""".stripMargin,
    """Error(2147483648,Integer literal is out of range for type Int)
      |Error(-2147483649,Integer literal is out of range for type Int)
      |Error(1L,Expression of type Long doesn't conform to expected type Int)
      |Error(0.0,Expression of type Double doesn't conform to expected type Int)
      |Error(1f,Expression of type Float doesn't conform to expected type Int)
      |Error(1d,Expression of type Double doesn't conform to expected type Int)
      |""".stripMargin
  )

  def testLongCoercion(): Unit = assertErrorsText(
    """val long0: Long = 0
      |val long1: Long = 1
      |val long9223372036854775807: Long = 9223372036854775807l
      |val long9223372036854775808: Long = 9223372036854775808l
      |
      |val long_m9223372036854775808: Long = -9223372036854775808l
      |val long_m9223372036854775809: Long = -9223372036854775809l
      |
      |val long_from_byte: Long = 1 : Byte
      |val long_from_char: Long = 1 : Char
      |val long_from_short: Long = 1 : Short
      |val long_from_int: Long = 1 : Int
      |val long_from_long: Long = 1L
      |
      |val long1_0: Long = 0.0
      |val long1f: Long = 1f
      |val long1d: Long = 1d
      |""".stripMargin,
    """Error(9223372036854775808l,Integer number is out of range even for type Long)
      |Error(-9223372036854775809l,Integer number is out of range even for type Long)
      |Error(0.0,Expression of type Double doesn't conform to expected type Long)
      |Error(1f,Expression of type Float doesn't conform to expected type Long)
      |Error(1d,Expression of type Double doesn't conform to expected type Long)
      |""".stripMargin
  )

  def testDoubleCoercion(): Unit = assertNoErrors(
    """val double1: Double = 1.0f
      |val double2: Double = 1.0d
      |val double3: Double = 1
      |val double4: Double = 1.5
      |
      |val double_from_byte: Double = 1 : Byte
      |val double_from_char: Double = 1 : Char
      |val double_from_short: Double = 1 : Short
      |val double_from_int: Double = 1 : Int
      |val double_from_long: Double = 1L
      |""".stripMargin
  )
}

class PrimitivesConformanceTest_Scala2 extends TypeInferenceTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala2

  def testFloatCoercion(): Unit = assertErrorsText(
    """val double1: Float = 1.0f
      |val double2: Float = 1.0d
      |val double3: Float = 1
      |val double4: Double = 1.5
      |
      |val double_from_byte: Float = 1 : Byte
      |val double_from_char: Float = 1 : Char
      |val double_from_short: Float = 1 : Short
      |val double_from_int: Float = 1 : Int
      |val double_from_long: Float = 1L
      |""".stripMargin,
    """
      |Error(1.0d,Expression of type Double doesn't conform to expected type Float)
      |""".stripMargin
  )
}

class PrimitivesConformanceTest_Scala3 extends TypeInferenceTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  def testFloatCoercion(): Unit = assertErrorsText(
    """val double1: Float = 1.0f
      |val double2: Float = 1.0d
      |val double3: Float = 1
      |val double4: Double = 1.5
      |
      |val double_from_byte: Float = 1 : Byte
      |val double_from_char: Float = 1 : Char
      |val double_from_short: Float = 1 : Short
      |val double_from_int: Float = 1 : Int
      |val double_from_long: Float = 1L
      |""".stripMargin,
    """
      |Error(1.0d,Expression of type Double doesn't conform to expected type Float)
      |""".stripMargin
  )
}