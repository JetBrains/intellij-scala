package org.jetbrains.plugins.scala.lang.completion3.command

import com.intellij.codeInsight.lookup.LookupElement
import org.junit.Test

final class ScalaCopyFQNCommandCompletionTest extends ScalaCommandCompletionTestBase {
  private val CopyReferencePredicate: LookupElement => Boolean = lookupStringStartsWith(_, "Copy reference")

  private def doCopyReferenceCommandCompletionTest(fileText: String, expectedCopiedReference: String): Unit = {
    doCommandCompletionTest(fileText, predicate = CopyReferencePredicate)
    scalaFixture.checkClipboardContent(expectedCopiedReference)
  }

  private def checkNoCopyReferenceCommandCompletion(fileText: String): Unit =
    checkNoCommandCompletion(fileText, predicate = CopyReferencePredicate)

  @Test
  def copyObjectFieldReferenceFromUsage(): Unit = doCopyReferenceCommandCompletionTest(
    fileText =
      s"""package org.example
         |
         |object Test {
         |  val field: Int = 1
         |
         |  def test(): Unit = {
         |    println(field + ${start}field$end..$CARET)
         |  }
         |}""".stripMargin,
    expectedCopiedReference = "org.example.Test.field"
  )

  @Test
  def copyObjectFieldReferenceFromDeclaration(): Unit = doCopyReferenceCommandCompletionTest(
    fileText =
      s"""package org.example
         |
         |object Test {
         |  val ${start}field$end..$CARET: Int = 1
         |
         |  def test(): Unit = {
         |    println(field + field)
         |  }
         |}""".stripMargin,
    expectedCopiedReference = "org.example.Test.field"
  )

  @Test
  def copyObjectMethodReferenceFromDeclaration(): Unit = doCopyReferenceCommandCompletionTest(
    fileText =
      s"""package org.example
         |
         |object Test {
         |  def ${start}myMethod$end..$CARET(): Unit = {}
         |}""".stripMargin,
    expectedCopiedReference = "org.example.Test.myMethod"
  )

  @Test
  def copyObjectMethodReferenceFromUsage(): Unit = doCopyReferenceCommandCompletionTest(
    fileText =
      s"""package org.example
         |
         |object Test {
         |  def myMethod(): Unit = {}
         |
         |  def test(): Unit = {
         |    ${start}myMethod$end..$CARET()
         |  }
         |}""".stripMargin,
    expectedCopiedReference = "org.example.Test.myMethod"
  )

  @Test
  def copyClassReference(): Unit = doCopyReferenceCommandCompletionTest(
    fileText =
      s"""package org.example
         |
         |class ${start}MyClass$end..$CARET {
         |}""".stripMargin,
    expectedCopiedReference = "org.example.MyClass"
  )

  @Test
  def noCompletionOutsideOfClassIdentifier(): Unit = checkNoCopyReferenceCommandCompletion(
    fileText =
      s"""package org.example
         |
         |class MyClass {
         |}..$CARET""".stripMargin,
  )

  @Test
  def noCompletionOutsideOfClassIdentifier2(): Unit = checkNoCopyReferenceCommandCompletion(
    fileText =
      s"""package org.example
         |
         |class MyClass ..$CARET {
         |}""".stripMargin,
  )

  @Test
  def copyTraitReference(): Unit = doCopyReferenceCommandCompletionTest(
    fileText =
      s"""package org.example
         |
         |trait ${start}MyTrait$end..$CARET {
         |}""".stripMargin,
    expectedCopiedReference = "org.example.MyTrait"
  )

  @Test
  def copyObjectReference(): Unit = doCopyReferenceCommandCompletionTest(
    fileText =
      s"""package org.example
         |
         |object ${start}MyObject$end..$CARET {
         |}""".stripMargin,
    expectedCopiedReference = "org.example.MyObject"
  )

  @Test
  def copyCaseClassReference(): Unit = doCopyReferenceCommandCompletionTest(
    fileText =
      s"""package org.example
         |
         |case class ${start}MyCase$end..$CARET(x: Int)""".stripMargin,
    expectedCopiedReference = "org.example.MyCase"
  )

  @Test
  def copyInnerClassReference(): Unit = doCopyReferenceCommandCompletionTest(
    fileText =
      s"""package org.example
         |
         |class Outer {
         |  class ${start}Inner$end..$CARET {
         |  }
         |}""".stripMargin,
    expectedCopiedReference = "org.example.Outer.Inner"
  )

  @Test
  def copyInnerClassMethodReference(): Unit = doCopyReferenceCommandCompletionTest(
    fileText =
      s"""package org.example
         |
         |class Outer {
         |  class Inner {
         |    def ${start}foo$end..$CARET(): Unit = {}
         |  }
         |}""".stripMargin,
    expectedCopiedReference = "org.example.Outer.Inner#foo"
  )

  @Test
  def copyInnerObjectReference(): Unit = doCopyReferenceCommandCompletionTest(
    fileText =
      s"""package org.example
         |
         |class Outer {
         |  object ${start}Inner$end..$CARET {
         |  }
         |}""".stripMargin,
    expectedCopiedReference = "org.example.Outer.Inner"
  )

  @Test
  def copyInnerClassReferenceFromUsage(): Unit = doCopyReferenceCommandCompletionTest(
    fileText =
      s"""package org.example
         |
         |class Outer {
         |  class Inner
         |
         |  def test(): Unit = {
         |    val x: ${start}Inner$end..$CARET = new Inner()
         |  }
         |}""".stripMargin,
    expectedCopiedReference = "org.example.Outer.Inner"
  )

  @Test
  def copyCalledMethodReference(): Unit = doCopyReferenceCommandCompletionTest(
    fileText =
      s"""object Test {
         |  def foo(): Unit = {
         |    ${start}println$end..$CARET()
         |  }
         |}""".stripMargin,
    expectedCopiedReference = "scala.Predef.println"
  )

  @Test
  def noCompletionOutsideOfCalledMethodIdentifier(): Unit = checkNoCopyReferenceCommandCompletion(
    fileText =
      s"""object Test {
         |  def foo(): Unit = {
         |    println()..$CARET
         |  }
         |}""".stripMargin
  )

  @Test
  def copyLocalVariableReference(): Unit = doCopyReferenceCommandCompletionTest(
    fileText =
      s"""object Test {
         |  def foo(): Unit = {
         |    val localVar = 1
         |    localVar..$CARET
         |  }
         |}""".stripMargin,
    expectedCopiedReference = "localVar"
  )

  @Test
  def copyMethodParameterReference(): Unit = doCopyReferenceCommandCompletionTest(
    fileText =
      s"""object Test {
         |  def foo(param: Int): Unit = {
         |    param..$CARET
         |  }
         |}""".stripMargin,
    expectedCopiedReference = "param"
  )

  @Test
  def noCompletionForLiteral(): Unit = checkNoCopyReferenceCommandCompletion(
    fileText =
      s"""object Test {
         |  def foo(param: Int): Unit = {
         |    println("hi"..$CARET)
         |  }
         |}""".stripMargin
  )
}
