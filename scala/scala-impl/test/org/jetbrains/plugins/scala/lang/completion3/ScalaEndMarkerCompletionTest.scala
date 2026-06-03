package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.codeInsight.completion.CompletionType
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase
import org.jetbrains.plugins.scala.util.runners.{RunWithScalaVersions, TestScalaVersion}
import org.junit.{Assert, Test}

@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_Latest))
class ScalaEndMarkerCompletionTest extends ScalaCompletionTestBase {

  import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase._

  private def checkLookupElement(fileText: String,
                                 resultText: String,
                                 lookupStr: String,
                                 presentationText: String = null,
                                 typeText: String = null,
                                 completionType: CompletionType = CompletionType.BASIC): Unit =
    doRawCompletionTest(fileText, resultText, completionType = completionType) { lookup =>
      val actualPresentation = createPresentation(lookup)
      val actualPresentationText = actualPresentation.getItemText + Option(actualPresentation.getTailText).getOrElse("")
      val actualTypeText = actualPresentation.getTypeText

      hasLookupString(lookup, lookupStr) &&
        Option(presentationText).getOrElse(lookupStr) == actualPresentationText &&
        actualTypeText == typeText
    }

  private def checkLookupElementsOrder(fileText: String, expectedItems: List[String]): Unit = {
    val (_, items) = activeLookupWithItems(fileText, CompletionType.BASIC, DefaultInvocationCount)
    val actualItems = items.toList.map(_.getLookupString).filter(_.startsWith("end "))

    Assert.assertArrayEquals(expectedItems.toArray[AnyRef], actualItems.toArray[AnyRef])
  }

  private def checkNoCompletion(fileText: String): Unit =
    super.checkNoCompletion(fileText) { lookup =>
      lookup.getLookupString.startsWith("end ") ||
        createPresentation(lookup).getItemText.startsWith("end ")
    }

  private def checkNoCompletionFor(fileText: String, item: String): Unit =
    super.checkNoCompletion(fileText) { lookup =>
      hasLookupString(lookup, item)
    }

  @Test
  def testNoCompletionAfterDot(): Unit = checkNoCompletion(
    fileText =
      s"""def foo =
         |  1
         |  2
         |  3.e$CARET
         |""".stripMargin
  )

  /// anonymous class

  @Test
  def testAnonClass(): Unit = checkLookupElement(
    fileText =
      s"""class C
         |
         |new C:
         |  def foo = true
         |e$CARET
         |""".stripMargin,
    resultText =
      s"""class C
         |
         |new C:
         |  def foo = true
         |end new
         |$CARET
         |""".stripMargin,
    lookupStr = "end new",
    typeText = "C"
  )

  @Test
  def testAnonClassComplexTypeText(): Unit = checkLookupElement(
    fileText =
      s"""class SomeClass
         |class AnotherClass
         |
         |new SomeClass with AnotherClass:
         |  def foo = true
         |e$CARET
         |""".stripMargin,
    resultText =
      s"""class SomeClass
         |class AnotherClass
         |
         |new SomeClass with AnotherClass:
         |  def foo = true
         |end new
         |$CARET
         |""".stripMargin,
    lookupStr = "end new",
    typeText = "SomeClass with ..."
  )

  @Test
  def testAnonClassComplexTypeText2(): Unit = checkLookupElement(
    fileText =
      s"""class SomeClass
         |class AnotherClass
         |
         |new SomeClass
         |  with AnotherClass:
         |  def foo = true
         |e$CARET
         |""".stripMargin,
    resultText =
      s"""class SomeClass
         |class AnotherClass
         |
         |new SomeClass
         |  with AnotherClass:
         |  def foo = true
         |end new
         |$CARET
         |""".stripMargin,
    lookupStr = "end new",
    typeText = "SomeClass with ..."
  )

  @Test
  def testAnonClassWithoutInput(): Unit = checkLookupElement(
    fileText =
      s"""class C
         |
         |new C:
         |  def foo = true
         |$CARET
         |""".stripMargin,
    resultText =
      s"""class C
         |
         |new C:
         |  def foo = true
         |end new
         |$CARET
         |""".stripMargin,
    lookupStr = "end new",
    typeText = "C"
  )

  @Test
  def testAnonClassAfterEndKeyword(): Unit = checkLookupElement(
    fileText =
      s"""class C
         |
         |new C:
         |  def foo = true
         |end $CARET
         |""".stripMargin,
    resultText =
      s"""class C
         |
         |new C:
         |  def foo = true
         |end new
         |$CARET
         |""".stripMargin,
    lookupStr = "new",
    presentationText = "new",
    typeText = "C"
  )

  @Test
  def testEmptyAnonClass(): Unit = checkLookupElement(
    fileText =
      s"""class C
         |
         |new C:
         |e$CARET
         |""".stripMargin,
    resultText =
      s"""class C
         |
         |new C:
         |end new
         |$CARET
         |""".stripMargin,
    lookupStr = "end new",
    typeText = "C"
  )

  @Test
  def testNoCompletionForAnonClassWithoutTemplateBody(): Unit = checkNoCompletion(
    fileText =
      s"""class C
         |
         |new C
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForAnonClassWithoutTemplateBodyAfterEndKeyword(): Unit = checkNoCompletion(
    fileText =
      s"""class C
         |
         |new C
         |end $CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForAnonClassWithBraces(): Unit = checkNoCompletion(
    fileText =
      s"""class C
         |
         |new C {
         |  def foo = true
         |}
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForAnonClassWithEndMarker(): Unit = checkNoCompletion(
    fileText =
      s"""class C
         |
         |new C:
         |  def foo = true
         |end new
         |e$CARET
         |""".stripMargin
  )

  /// class

  @Test
  def testClass(): Unit = checkLookupElement(
    fileText =
      s"""class C:
         |  def foo = true
         |e$CARET
         |""".stripMargin,
    resultText =
      s"""class C:
         |  def foo = true
         |end C
         |$CARET
         |""".stripMargin,
    lookupStr = "end C"
  )

  @Test
  def testClassWithoutInput(): Unit = checkLookupElement(
    fileText =
      s"""class C:
         |  def foo = true
         |$CARET
         |""".stripMargin,
    resultText =
      s"""class C:
         |  def foo = true
         |end C
         |$CARET
         |""".stripMargin,
    lookupStr = "end C"
  )

  @Test
  def testClassAfterEndKeyword(): Unit = checkLookupElement(
    fileText =
      s"""class C:
         |  def foo = true
         |end $CARET
         |""".stripMargin,
    resultText =
      s"""class C:
         |  def foo = true
         |end C
         |$CARET
         |""".stripMargin,
    lookupStr = "C",
    presentationText = "C"
  )

  @Test
  def testEmptyClass(): Unit = checkLookupElement(
    fileText =
      s"""class C:
         |e$CARET
         |""".stripMargin,
    resultText =
      s"""class C:
         |end C
         |$CARET
         |""".stripMargin,
    lookupStr = "end C"
  )

  @Test
  def testNoCompletionForClassWithoutTemplateBody(): Unit = checkNoCompletion(
    fileText =
      s"""class C
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForClassWithoutTemplateBodyAfterEndKeyword(): Unit = checkNoCompletion(
    fileText =
      s"""class C
         |end $CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForClassWithBraces(): Unit = checkNoCompletion(
    fileText =
      s"""class C {
         |  def foo = true
         |}
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForClassWithEndMarker(): Unit = checkNoCompletion(
    fileText =
      s"""class C:
         |  def foo = true
         |end C
         |e$CARET
         |""".stripMargin
  )

  /// trait

  @Test
  def testTrait(): Unit = checkLookupElement(
    fileText =
      s"""trait T:
         |  def foo
         |e$CARET
         |""".stripMargin,
    resultText =
      s"""trait T:
         |  def foo
         |end T
         |$CARET
         |""".stripMargin,
    lookupStr = "end T"
  )

  @Test
  def testTraitWithoutInput(): Unit = checkLookupElement(
    fileText =
      s"""trait T:
         |  def foo
         |$CARET
         |""".stripMargin,
    resultText =
      s"""trait T:
         |  def foo
         |end T
         |$CARET
         |""".stripMargin,
    lookupStr = "end T"
  )

  @Test
  def testTraitAfterEndKeyword(): Unit = checkLookupElement(
    fileText =
      s"""trait T:
         |  def foo
         |end $CARET
         |""".stripMargin,
    resultText =
      s"""trait T:
         |  def foo
         |end T
         |$CARET
         |""".stripMargin,
    lookupStr = "T",
    presentationText = "T"
  )

  @Test
  def testEmptyTrait(): Unit = checkLookupElement(
    fileText =
      s"""trait T:
         |e$CARET
         |""".stripMargin,
    resultText =
      s"""trait T:
         |end T
         |$CARET
         |""".stripMargin,
    lookupStr = "end T"
  )

  @Test
  def testNoCompletionForTraitWithoutTemplateBody(): Unit = checkNoCompletion(
    fileText =
      s"""trait T
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForTraitWithoutTemplateBodyAfterEndKeyword(): Unit = checkNoCompletion(
    fileText =
      s"""trait T
         |end $CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForTraitWithBraces(): Unit = checkNoCompletion(
    fileText =
      s"""trait T {
         |  def foo
         |}
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForTraitWithEndMarker(): Unit = checkNoCompletion(
    fileText =
      s"""trait T:
         |  def foo
         |end T
         |e$CARET
         |""".stripMargin
  )

  /// object

  @Test
  def testObject(): Unit = checkLookupElement(
    fileText =
      s"""object O:
         |  def foo = true
         |e$CARET
         |""".stripMargin,
    resultText =
      s"""object O:
         |  def foo = true
         |end O
         |$CARET
         |""".stripMargin,
    lookupStr = "end O"
  )

  @Test
  def testObjectWithoutInput(): Unit = checkLookupElement(
    fileText =
      s"""object O:
         |  def foo = true
         |$CARET
         |""".stripMargin,
    resultText =
      s"""object O:
         |  def foo = true
         |end O
         |$CARET
         |""".stripMargin,
    lookupStr = "end O"
  )

  @Test
  def testObjectAfterEndKeyword(): Unit = checkLookupElement(
    fileText =
      s"""object O:
         |  def foo = true
         |end $CARET
         |""".stripMargin,
    resultText =
      s"""object O:
         |  def foo = true
         |end O
         |$CARET
         |""".stripMargin,
    lookupStr = "O",
    presentationText = "O"
  )

  @Test
  def testEmptyObject(): Unit = checkLookupElement(
    fileText =
      s"""object O:
         |e$CARET
         |""".stripMargin,
    resultText =
      s"""object O:
         |end O
         |$CARET
         |""".stripMargin,
    lookupStr = "end O"
  )

  @Test
  def testNoCompletionForObjectWithoutTemplateBody(): Unit = checkNoCompletion(
    fileText =
      s"""object O
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForObjectWithoutTemplateBodyAfterEndKeyword(): Unit = checkNoCompletion(
    fileText =
      s"""object O
         |end $CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForObjectWithBraces(): Unit = checkNoCompletion(
    fileText =
      s"""object O {
         |  def foo = true
         |}
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForObjectWithEndMarker(): Unit = checkNoCompletion(
    fileText =
      s"""object O:
         |  def foo = true
         |end O
         |e$CARET
         |""".stripMargin
  )

  /// enum

  @Test
  def testEnum(): Unit = checkLookupElement(
    fileText =
      s"""enum E:
         |  case C
         |e$CARET
         |""".stripMargin,
    resultText =
      s"""enum E:
         |  case C
         |end E
         |$CARET
         |""".stripMargin,
    lookupStr = "end E"
  )

  @Test
  def testEnumWithoutInput(): Unit = checkLookupElement(
    fileText =
      s"""enum E:
         |  case C
         |$CARET
         |""".stripMargin,
    resultText =
      s"""enum E:
         |  case C
         |end E
         |$CARET
         |""".stripMargin,
    lookupStr = "end E"
  )

  @Test
  def testEnumAfterEndKeyword(): Unit = checkLookupElement(
    fileText =
      s"""enum E:
         |  case C
         |end $CARET
         |""".stripMargin,
    resultText =
      s"""enum E:
         |  case C
         |end E
         |$CARET
         |""".stripMargin,
    lookupStr = "E",
    presentationText = "E"
  )

  @Test
  def testEmptyEnum(): Unit = checkLookupElement(
    fileText =
      s"""enum E:
         |e$CARET
         |""".stripMargin,
    resultText =
      s"""enum E:
         |end E
         |$CARET
         |""".stripMargin,
    lookupStr = "end E"
  )

  @Test
  def testNoCompletionForEnumWithoutTemplateBody(): Unit = checkNoCompletion(
    fileText =
      s"""enum E
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForEnumWithoutTemplateBodyAfterEndKeyword(): Unit = checkNoCompletion(
    fileText =
      s"""enum E
         |end $CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForEnumWithBraces(): Unit = checkNoCompletion(
    fileText =
      s"""enum E {
         |  case C
         |}
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForEnumWithEndMarker(): Unit = checkNoCompletion(
    fileText =
      s"""enum E:
         |  case C
         |end E
         |e$CARET
         |""".stripMargin
  )

  /// constructor

  @Test
  def testConstructor(): Unit = checkLookupElement(
    fileText =
      s"""class C(i: Int):
         |  def this(i: Int, s: String) =
         |    this(i)
         |    println("multiline")
         |  e$CARET
         |""".stripMargin,
    resultText =
      s"""class C(i: Int):
         |  def this(i: Int, s: String) =
         |    this(i)
         |    println("multiline")
         |  end this
         |  $CARET
         |""".stripMargin,
    lookupStr = "end this"
  )

  @Test
  def testConstructorWithoutInput(): Unit = checkLookupElement(
    fileText =
      s"""class C(i: Int):
         |  def this(i: Int, s: String) =
         |    this(i)
         |    println("multiline")
         |  $CARET
         |""".stripMargin,
    resultText =
      s"""class C(i: Int):
         |  def this(i: Int, s: String) =
         |    this(i)
         |    println("multiline")
         |  end this
         |  $CARET
         |""".stripMargin,
    lookupStr = "end this"
  )

  @Test
  def testConstructorAfterEndKeyword(): Unit = checkLookupElement(
    fileText =
      s"""class C(i: Int):
         |  def this(i: Int, s: String) =
         |    this(i)
         |    println("multiline")
         |  end $CARET
         |""".stripMargin,
    resultText =
      s"""class C(i: Int):
         |  def this(i: Int, s: String) =
         |    this(i)
         |    println("multiline")
         |  end this
         |  $CARET
         |""".stripMargin,
    lookupStr = "this",
    presentationText = "this"
  )

  @Test
  def testNoCompletionForConstructorWithoutBody(): Unit = checkNoCompletionFor(
    fileText =
      s"""class C(i: Int):
         |  def this(i: Int, s: String)
         |  e$CARET
         |""".stripMargin,
    item = "end this"
  )

  @Test
  def testNoCompletionForConstructorIfIndentIsLessThanConstructorIndent(): Unit = checkNoCompletionFor(
    fileText =
      s"""class C(i: Int):
         |  def this(i: Int, s: String) =
         |    this(i)
         | e$CARET
         |""".stripMargin,
    item = "end this"
  )

  @Test
  def testNoCompletionForConstructorWithBraces(): Unit = checkNoCompletionFor(
    fileText =
      s"""class C(i: Int):
         |  def this(i: Int, s: String) = {
         |    this(i)
         |    println("multiline")
         |  }
         |  e$CARET
         |""".stripMargin,
    item = "end this"
  )

  @Test
  def testNoCompletionForConstructorWithEndMarker(): Unit = checkNoCompletionFor(
    fileText =
      s"""class C(i: Int):
         |  def this(i: Int, s: String) =
         |    this(i)
         |    println("multiline")
         |  end this
         |  e$CARET
         |""".stripMargin,
    item = "end this"
  )

  @Test
  def testClassIfIndentIsGreaterThanClassIndent(): Unit = checkLookupElement(
    fileText =
      s"""class C(i: Int):
         |  def this(i: Int, s: String) =
         |    this(i)
         |    println("multiline")
         | e$CARET
         |""".stripMargin,
    resultText =
      s"""class C(i: Int):
         |  def this(i: Int, s: String) =
         |    this(i)
         |    println("multiline")
         |end C
         |$CARET
         |""".stripMargin,
    lookupStr = "end C"
  )

  /// value

  @Test
  def testValue(): Unit = checkLookupElement(
    fileText =
      s"""val v =
         |  1 +
         |    41
         |e$CARET
         |""".stripMargin,
    resultText =
      s"""val v =
         |  1 +
         |    41
         |end v
         |$CARET
         |""".stripMargin,
    lookupStr = "end v"
  )

  @Test
  def testValueWithoutInput(): Unit = checkLookupElement(
    fileText =
      s"""val v =
         |  1 + 2 match
         |    case 3 => 0
         |    case _ => 1
         |$CARET
         |""".stripMargin,
    resultText =
      s"""val v =
         |  1 + 2 match
         |    case 3 => 0
         |    case _ => 1
         |end v
         |$CARET
         |""".stripMargin,
    lookupStr = "end v"
  )

  @Test
  def testValueAfterEndKeyword(): Unit = checkLookupElement(
    fileText =
      s"""val v =
         |  0
         |  42
         |end $CARET
         |""".stripMargin,
    resultText =
      s"""val v =
         |  0
         |  42
         |end v
         |$CARET
         |""".stripMargin,
    lookupStr = "v",
    presentationText = "v"
  )

  @Test
  def testNoCompletionForValueWithOneLinerBody(): Unit = checkNoCompletion(
    fileText =
      s"""val v =
         |  42
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForValueWithBraces(): Unit = checkNoCompletion(
    fileText =
      s"""val v = {
         |  0
         |  42
         |}
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForValueWithEndMarker(): Unit = checkNoCompletion(
    fileText =
      s"""val v =
         |  1 +
         |    41
         |end v
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForAbstractValue(): Unit = checkNoCompletion(
    fileText =
      s"""val v
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForAbstractValueAfterEndKeyword(): Unit = checkNoCompletion(
    fileText =
      s"""val v
         |end $CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForValueIfEndIsNotOnTheNewLine(): Unit = checkNoCompletion(
    fileText =
      s"""val v =
         |  0
         |  42 e$CARET
         |""".stripMargin
  )

  /// variable

  @Test
  def testVariable(): Unit = checkLookupElement(
    fileText =
      s"""var v =
         |  0
         |  42
         |e$CARET
         |""".stripMargin,
    resultText =
      s"""var v =
         |  0
         |  42
         |end v
         |$CARET
         |""".stripMargin,
    lookupStr = "end v"
  )

  @Test
  def testVariableWithoutInput(): Unit = checkLookupElement(
    fileText =
      s"""var v =
         |  0
         |  42
         |$CARET
         |""".stripMargin,
    resultText =
      s"""var v =
         |  0
         |  42
         |end v
         |$CARET
         |""".stripMargin,
    lookupStr = "end v"
  )

  @Test
  def testVariableAfterEndKeyword(): Unit = checkLookupElement(
    fileText =
      s"""var v =
         |  0
         |  42
         |end $CARET
         |""".stripMargin,
    resultText =
      s"""var v =
         |  0
         |  42
         |end v
         |$CARET
         |""".stripMargin,
    lookupStr = "v",
    presentationText = "v"
  )

  @Test
  def testNoCompletionForVariableWithOneLinerBody(): Unit = checkNoCompletion(
    fileText =
      s"""var v =
         |  42
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForVariableWithBraces(): Unit = checkNoCompletion(
    fileText =
      s"""var v = {
         |  0
         |  42
         |}
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForVariableWithEndMarker(): Unit = checkNoCompletion(
    fileText =
      s"""var v =
         |  0
         |  42
         |end v
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForAbstractVariable(): Unit = checkNoCompletion(
    fileText =
      s"""var v
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForAbstractVariableAfterEndKeyword(): Unit = checkNoCompletion(
    fileText =
      s"""var v
         |end $CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForVariableIfEndIsNotOnTheNewLine(): Unit = checkNoCompletion(
    fileText =
      s"""var v =
         |  0
         |  42 e$CARET
         |""".stripMargin
  )

  /// value binding pattern

  @Test
  def testValueBinding(): Unit = checkLookupElement(
    fileText =
      s"""val h :: t =
         |  List(1,
         |    2, 3)
         |e$CARET
         |""".stripMargin,
    resultText =
      s"""val h :: t =
         |  List(1,
         |    2, 3)
         |end val
         |$CARET
         |""".stripMargin,
    lookupStr = "end val"
  )

  @Test
  def testValueBindingWithoutInput(): Unit = checkLookupElement(
    fileText =
      s"""val h :: t =
         |  List(1,
         |    2, 3)
         |$CARET
         |""".stripMargin,
    resultText =
      s"""val h :: t =
         |  List(1,
         |    2, 3)
         |end val
         |$CARET
         |""".stripMargin,
    lookupStr = "end val"
  )

  @Test
  def testValueBindingAfterEndKeyword(): Unit = checkLookupElement(
    fileText =
      s"""val h :: t =
         |  List(1,
         |    2, 3)
         |end $CARET
         |""".stripMargin,
    resultText =
      s"""val h :: t =
         |  List(1,
         |    2, 3)
         |end val
         |$CARET
         |""".stripMargin,
    lookupStr = "val",
    presentationText = "val"
  )

  @Test
  def testNoCompletionForValueBindingWithOneLinerBody(): Unit = checkNoCompletion(
    fileText =
      s"""val h :: t =
         |  List(1, 2, 3)
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForValueBindingWithoutAssign(): Unit = checkNoCompletion(
    fileText =
      s"""val h :: t
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForValueBindingWithoutAssignAfterEndKeyword(): Unit = checkNoCompletion(
    fileText =
      s"""val h :: t
         |end $CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForValueBindingWithBraces(): Unit = checkNoCompletion(
    fileText =
      s"""val h :: t = {
         |  List(1,
         |    2, 3)
         |}
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForValueBindingWithEndMarker(): Unit = checkNoCompletion(
    fileText =
      s"""val h :: t =
         |  List(1,
         |    2, 3)
         |end val
         |e$CARET
         |""".stripMargin
  )

  /// variable binding pattern

  @Test
  def testNoCompletionForVariableBinding(): Unit = checkNoCompletion(
    fileText =
      s"""var h :: t =
         |  List(1,
         |    2, 3)
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForVariableBindingAfterEndKeyword(): Unit = checkNoCompletion(
    fileText =
      s"""var h :: t =
         |  List(1,
         |    2, 3)
         |end $CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForVariableBindingWithOneLinerBody(): Unit = checkNoCompletion(
    fileText =
      s"""var h :: t =
         |  List(1, 2, 3)
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForVariableBindingWithoutAssign(): Unit = checkNoCompletion(
    fileText =
      s"""var h :: t
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForVariableBindingWithoutAssignAfterEndKeyword(): Unit = checkNoCompletion(
    fileText =
      s"""var h :: t
         |end $CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForVariableBindingWithBraces(): Unit = checkNoCompletion(
    fileText =
      s"""var h :: t = {
         |  List(1,
         |    2, 3)
         |}
         |e$CARET
         |""".stripMargin
  )

  /// given

  @Test
  def testAnonymousGivenAlias(): Unit = checkLookupElement(
    fileText =
      s"""given Int =
         |  0
         |  42
         |e$CARET
         |""".stripMargin,
    resultText =
      s"""given Int =
         |  0
         |  42
         |end given
         |$CARET
         |""".stripMargin,
    lookupStr = "end given"
  )

  @Test
  def testAnonymousGivenAliasWithoutInput(): Unit = checkLookupElement(
    fileText =
      s"""given Int =
         |  0
         |  42
         |$CARET
         |""".stripMargin,
    resultText =
      s"""given Int =
         |  0
         |  42
         |end given
         |$CARET
         |""".stripMargin,
    lookupStr = "end given"
  )

  @Test
  def testAnonymousGivenAliasAfterEndKeyword(): Unit = checkLookupElement(
    fileText =
      s"""given Int =
         |  0
         |  42
         |end $CARET
         |""".stripMargin,
    resultText =
      s"""given Int =
         |  0
         |  42
         |end given
         |$CARET
         |""".stripMargin,
    lookupStr = "given",
    presentationText = "given"
  )

  @Test
  def testNoCompletionForAnonymousGivenAliasWithOneLinerBody(): Unit = checkNoCompletion(
    fileText =
      s"""given Int =
         |  42
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForAnonymousGivenAliasWithoutAssign(): Unit = checkNoCompletion(
    fileText =
      s"""given Int
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForAnonymousGivenAliasWithoutAssignAfterEndKeyword(): Unit = checkNoCompletion(
    fileText =
      s"""given Int
         |end $CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForAnonymousGivenAliasWithBraces(): Unit = checkNoCompletion(
    fileText =
      s"""given Int = {
         |  0
         |  42
         |}
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForAnonymousGivenAliasWithEndMarker(): Unit = checkNoCompletion(
    fileText =
      s"""given Int =
         |  0
         |  42
         |end given
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testGivenAlias(): Unit = checkLookupElement(
    fileText =
      s"""given someGiven: Int =
         |  0
         |  42
         |e$CARET
         |""".stripMargin,
    resultText =
      s"""given someGiven: Int =
         |  0
         |  42
         |end someGiven
         |$CARET
         |""".stripMargin,
    lookupStr = "end someGiven"
  )

  @Test
  def testGivenAliasWithoutInput(): Unit = checkLookupElement(
    fileText =
      s"""given someGiven: Int =
         |  0
         |  42
         |$CARET
         |""".stripMargin,
    resultText =
      s"""given someGiven: Int =
         |  0
         |  42
         |end someGiven
         |$CARET
         |""".stripMargin,
    lookupStr = "end someGiven"
  )

  @Test
  def testGivenAliasAfterEndKeyword(): Unit = checkLookupElement(
    fileText =
      s"""given someGiven: Int =
         |  0
         |  42
         |end $CARET
         |""".stripMargin,
    resultText =
      s"""given someGiven: Int =
         |  0
         |  42
         |end someGiven
         |$CARET
         |""".stripMargin,
    lookupStr = "someGiven",
    presentationText = "someGiven"
  )

  @Test
  def testNoCompletionForGivenAliasWithOneLinerBody(): Unit = checkNoCompletion(
    fileText =
      s"""given someGiven: Int =
         |  42
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForGivenAliasWithoutAssign(): Unit = checkNoCompletion(
    fileText =
      s"""given someGiven: Int
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForGivenAliasWithoutAssignAfterEndKeyword(): Unit = checkNoCompletion(
    fileText =
      s"""given someGiven: Int
         |end $CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForGivenAliasWithBraces(): Unit = checkNoCompletion(
    fileText =
      s"""given someGiven: Int = {
         |  0
         |  42
         |}
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForGivenAliasWithEndMarker(): Unit = checkNoCompletion(
    fileText =
      s"""given someGiven: Int =
         |  0
         |  42
         |end someGiven
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testAnonymousGivenDefinition(): Unit = checkLookupElement(
    fileText =
      s"""trait Ord[T]:
         |  def compare(x: T, y: T): Int
         |given Ord[Int] with
         |  def compare(x: Int, y: Int): Int =
         |    x.compareTo(y)
         |e$CARET
         |""".stripMargin,
    resultText =
      s"""trait Ord[T]:
         |  def compare(x: T, y: T): Int
         |given Ord[Int] with
         |  def compare(x: Int, y: Int): Int =
         |    x.compareTo(y)
         |end given
         |$CARET
         |""".stripMargin,
    lookupStr = "end given"
  )

  @Test
  def testAnonymousGivenDefinitionAfterEndKeyword(): Unit = checkLookupElement(
    fileText =
      s"""trait Ord[T]:
         |  def compare(x: T, y: T): Int
         |given Ord[Int] with
         |  def compare(x: Int, y: Int): Int =
         |    x.compareTo(y)
         |end $CARET
         |""".stripMargin,
    resultText =
      s"""trait Ord[T]:
         |  def compare(x: T, y: T): Int
         |given Ord[Int] with
         |  def compare(x: Int, y: Int): Int =
         |    x.compareTo(y)
         |end given
         |$CARET
         |""".stripMargin,
    lookupStr = "given",
    presentationText = "given"
  )

  @Test
  def testNoCompletionForAnonymousGivenDefinitionWithOneLinerBody(): Unit = checkNoCompletion(
    fileText =
      s"""trait Ord[T]:
         |  def compare(x: T, y: T): Int
         |given Ord[Int] with
         |  def compare(x: Int, y: Int): Int = x.compareTo(y)
         |e$CARET
         |""".stripMargin,
  )

  @Test
  def testNoCompletionForAnonymousGivenDefinitionWithBraces(): Unit = checkNoCompletion(
    fileText =
      s"""trait Ord[T]:
         |  def compare(x: T, y: T): Int
         |given Ord[Int] with {
         |  def compare(x: Int, y: Int): Int =
         |    x.compareTo(y)
         |}
         |e$CARET
         |""".stripMargin,
  )

  @Test
  def testNoCompletionForAnonymousGivenDefinitionWithEndMarker(): Unit = checkNoCompletion(
    fileText =
      s"""trait Ord[T]:
         |  def compare(x: T, y: T): Int
         |given Ord[Int] with
         |  def compare(x: Int, y: Int): Int =
         |    x.compareTo(y)
         |end given
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testGivenDefinition(): Unit = checkLookupElement(
    fileText =
      s"""trait Ord[T]:
         |  def compare(x: T, y: T): Int
         |given intOrd: Ord[Int] with
         |  def compare(x: Int, y: Int): Int =
         |    if x < y then -1 else if x > y then +1 else 0
         |e$CARET
         |""".stripMargin,
    resultText =
      s"""trait Ord[T]:
         |  def compare(x: T, y: T): Int
         |given intOrd: Ord[Int] with
         |  def compare(x: Int, y: Int): Int =
         |    if x < y then -1 else if x > y then +1 else 0
         |end intOrd
         |$CARET
         |""".stripMargin,
    lookupStr = "end intOrd"
  )

  @Test
  def testGivenDefinitionAfterEndKeyword(): Unit = checkLookupElement(
    fileText =
      s"""trait Ord[T]:
         |  def compare(x: T, y: T): Int
         |given intOrd: Ord[Int] with
         |  def compare(x: Int, y: Int): Int =
         |    if x < y then -1 else if x > y then +1 else 0
         |end $CARET
         |""".stripMargin,
    resultText =
      s"""trait Ord[T]:
         |  def compare(x: T, y: T): Int
         |given intOrd: Ord[Int] with
         |  def compare(x: Int, y: Int): Int =
         |    if x < y then -1 else if x > y then +1 else 0
         |end intOrd
         |$CARET
         |""".stripMargin,
    lookupStr = "intOrd",
    presentationText = "intOrd"
  )

  @Test
  def testNoCompletionForGivenDefinitionWithOneLinerBody(): Unit = checkNoCompletion(
    fileText =
      s"""trait Ord[T]:
         |  def compare(x: T, y: T): Int
         |given intOrd: Ord[Int] with
         |  def compare(x: Int, y: Int): Int = if x < y then -1 else if x > y then +1 else 0
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForGivenDefinitionWithBraces(): Unit = checkNoCompletion(
    fileText =
      s"""trait Ord[T]:
         |  def compare(x: T, y: T): Int
         |given intOrd: Ord[Int] with {
         |  def compare(x: Int, y: Int): Int =
         |    if x < y then -1 else if x > y then +1 else 0
         |}
         |e$CARET
         |""".stripMargin,
  )

  @Test
  def testNoCompletionForGivenDefinitionWithEndMarker(): Unit = checkNoCompletion(
    fileText =
      s"""trait Ord[T]:
         |  def compare(x: T, y: T): Int
         |given intOrd: Ord[Int] with
         |  def compare(x: Int, y: Int): Int =
         |    if x < y then -1 else if x > y then +1 else 0
         |end intOrd
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForPatternBoundGiven(): Unit = checkNoCompletionFor(
    fileText =
      s"""for
         |  given Int <- List(1, 2, 3)
         |  e$CARET
         |do ()
         |""".stripMargin,
    item = "end given"
  )

  /// extension

  @Test
  def testExtension(): Unit = checkLookupElement(
    fileText =
      s"""extension (x: String)
         |  def < (y: String): Boolean =
         |    ???
         |e$CARET
         |""".stripMargin,
    resultText =
      s"""extension (x: String)
         |  def < (y: String): Boolean =
         |    ???
         |end extension
         |$CARET
         |""".stripMargin,
    lookupStr = "end extension"
  )

  @Test
  def testExtensionWithoutInput(): Unit = checkLookupElement(
    fileText =
      s"""extension (x: String)
         |  def < (y: String): Boolean =
         |    ???
         |e$CARET
         |""".stripMargin,
    resultText =
      s"""extension (x: String)
         |  def < (y: String): Boolean =
         |    ???
         |end extension
         |$CARET
         |""".stripMargin,
    lookupStr = "end extension"
  )

  @Test
  def testExtensionAfterEndKeyword(): Unit = checkLookupElement(
    fileText =
      s"""extension (x: String)
         |  def < (y: String): Boolean =
         |    ???
         |end $CARET
         |""".stripMargin,
    resultText =
      s"""extension (x: String)
         |  def < (y: String): Boolean =
         |    ???
         |end extension
         |$CARET
         |""".stripMargin,
    lookupStr = "extension",
    presentationText = "extension"
  )

  @Test
  def testNoCompletionForExtensionWithOneLinerBody(): Unit = checkNoCompletion(
    fileText =
      s"""extension (x: String)
         |  def < (y: String): Boolean = ???
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForExtensionWithBraces(): Unit = checkNoCompletion(
    fileText =
      s"""extension (x: String) {
         |  def < (y: String): Boolean =
         |    ???
         |}
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForExtensionWithEndMarker(): Unit = checkNoCompletion(
    fileText =
      s"""extension (x: String)
         |  def < (y: String): Boolean =
         |    ???
         |end extension
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForExtensionWithOneLinerFunctionOnTheSameLine(): Unit = checkNoCompletion(
    fileText =
      s"""extension (i: Int) def isZero: Boolean = i == 0
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testExtensionWithMultilineFunctionOnTheSameLine(): Unit = checkLookupElement(
    fileText =
      s"""extension (i: Int) def isZero: Boolean =
         |  i == 0
         |e$CARET
         |""".stripMargin,
    resultText =
      s"""extension (i: Int) def isZero: Boolean =
         |  i == 0
         |end extension
         |$CARET
         |""".stripMargin,
    lookupStr = "end extension"
  )

  @Test
  def testExtensionWithMultilineFunctionOnTheSameLineAfterEndKeyword(): Unit = checkLookupElement(
    fileText =
      s"""extension (i: Int) def isZero: Boolean =
         |  i == 0
         |end $CARET
         |""".stripMargin,
    resultText =
      s"""extension (i: Int) def isZero: Boolean =
         |  i == 0
         |end extension
         |$CARET
         |""".stripMargin,
    lookupStr = "extension",
    presentationText = "extension"
  )

  @Test
  def testNoCompletionForMultilineExtensionFunctionOnTheSameLine(): Unit = checkNoCompletionFor(
    fileText =
      s"""extension (i: Int) def isZero: Boolean =
         |  i == 0
         |e$CARET
         |""".stripMargin,
    item = "end isZero"
  )

  // todo: uncomment when SCL-19689 is resolved
//  @Test
//  def testNoCompletionForExtensionWithMultilineFunctionOnTheSameLineWithEndMarker(): Unit = checkNoCompletion(
//    fileText =
//      s"""extension (i: Int) def isZero: Boolean =
//         |  i == 0
//         |end extension
//         |e$CARET
//         |""".stripMargin
//  )

  @Test
  def testNoCompletionForExtensionWithoutFunctions(): Unit = checkNoCompletion(
    fileText =
      s"""extension (ss: Seq[String])
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForExtensionWithoutFunctionsAfterEndKeyword(): Unit = checkNoCompletion(
    fileText =
      s"""extension (ss: Seq[String])
         |end $CARET
         |""".stripMargin
  )

  /// function

  @Test
  def testFunction(): Unit = checkLookupElement(
    fileText =
      s"""def largeMethod(n: Int) =
         |  val x = n / 2
         |  if x * 2 == n then
         |    x
         |  else
         |    x + 1
         |e$CARET
         |""".stripMargin,
    resultText =
      s"""def largeMethod(n: Int) =
         |  val x = n / 2
         |  if x * 2 == n then
         |    x
         |  else
         |    x + 1
         |end largeMethod
         |$CARET
         |""".stripMargin,
    lookupStr = "end largeMethod"
  )

  @Test
  def testFunctionWithoutInput(): Unit = checkLookupElement(
    fileText =
      s"""def largeMethod(n: Int) =
         |  val x = n / 2
         |  if x * 2 == n then
         |    x
         |  else
         |    x + 1
         |$CARET
         |""".stripMargin,
    resultText =
      s"""def largeMethod(n: Int) =
         |  val x = n / 2
         |  if x * 2 == n then
         |    x
         |  else
         |    x + 1
         |end largeMethod
         |$CARET
         |""".stripMargin,
    lookupStr = "end largeMethod"
  )

  @Test
  def testFunctionAfterEndKeyword(): Unit = checkLookupElement(
    fileText =
      s"""def largeMethod(n: Int) =
         |  val x = n / 2
         |  if x * 2 == n then
         |    x
         |  else
         |    x + 1
         |end $CARET
         |""".stripMargin,
    resultText =
      s"""def largeMethod(n: Int) =
         |  val x = n / 2
         |  if x * 2 == n then
         |    x
         |  else
         |    x + 1
         |end largeMethod
         |$CARET
         |""".stripMargin,
    lookupStr = "largeMethod",
    presentationText = "largeMethod"
  )

  @Test
  def testNoCompletionForFunctionWithOneLinerBody(): Unit = checkNoCompletion(
    fileText =
      s"""def foo(str: String) =
         |  str.length
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForFunctionWithoutBody(): Unit = checkNoCompletionFor(
    fileText =
      s"""def foo: Int
         |e$CARET
         |""".stripMargin,
    item = "end foo"
  )

  @Test
  def testNoCompletionForFunctionWithoutBodyAfterEndKeyword(): Unit = checkNoCompletionFor(
    fileText =
      s"""def foo: Int
         |end $CARET
         |""".stripMargin,
    item = "foo"
  )

  @Test
  def testNoCompletionForFunctionWithBraces(): Unit = checkNoCompletion(
    fileText =
      s"""def largeMethod(n: Int) = {
         |  val x = n / 2
         |  if x * 2 == n then
         |    x
         |  else
         |    x + 1
         |}
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForFunctionWithEndMarker(): Unit = checkNoCompletion(
    fileText =
      s"""def largeMethod(n: Int) =
         |  val x = n / 2
         |  if x * 2 == n then
         |    x
         |  else
         |    x + 1
         |end largeMethod
         |e$CARET
         |""".stripMargin
  )

  /// package

  @Test
  def testPackage(): Unit = checkLookupElement(
    fileText =
      s"""package p1.p2.p3:
         |e$CARET
         |""".stripMargin,
    resultText =
      s"""package p1.p2.p3:
         |end p3
         |$CARET
         |""".stripMargin,
    lookupStr = "end p3"
  )

  @Test
  def testPackageWithoutInput(): Unit = checkLookupElement(
    fileText =
      s"""package p1.p2.p3:
         |$CARET
         |""".stripMargin,
    resultText =
      s"""package p1.p2.p3:
         |end p3
         |$CARET
         |""".stripMargin,
    lookupStr = "end p3"
  )

  @Test
  def testPackageAfterEndKeyword(): Unit = checkLookupElement(
    fileText =
      s"""package p1.p2.p3:
         |end $CARET
         |""".stripMargin,
    resultText =
      s"""package p1.p2.p3:
         |end p3
         |$CARET
         |""".stripMargin,
    lookupStr = "p3",
    presentationText = "p3"
  )

  @Test
  def testNoCompletionForNonExplicitPackage(): Unit = checkNoCompletion(
    s"""package p1.p2.p3
       |e$CARET""".stripMargin
  )

  @Test
  def testNoCompletionForNonExplicitPackageAfterEndKeyword(): Unit = checkNoCompletion(
    s"""package p1.p2.p3
       |end $CARET""".stripMargin
  )

  @Test
  def testNoCompletionForPackageWithEndMarker(): Unit = checkNoCompletion(
    fileText =
      s"""package p1.p2.p3:
         |end p3
         |e$CARET
         |""".stripMargin
  )

  /// if

  @Test
  def testIf(): Unit = checkLookupElement(
    fileText =
      s"""if 1 > 2 then
         |  println("wow")
         |  println("impossible")
         |e$CARET
         |""".stripMargin,
    resultText =
      s"""if 1 > 2 then
         |  println("wow")
         |  println("impossible")
         |end if
         |$CARET
         |""".stripMargin,
    lookupStr = "end if"
  )

  @Test
  def testIfOldStyle(): Unit = checkLookupElement(
    fileText =
      s"""if (1 > 2)
         |  println("wow")
         |  println("impossible")
         |e$CARET
         |""".stripMargin,
    resultText =
      s"""if (1 > 2)
         |  println("wow")
         |  println("impossible")
         |end if
         |$CARET
         |""".stripMargin,
    lookupStr = "end if"
  )

  @Test
  def testIfOldStyleWithElse(): Unit = checkLookupElement(
    fileText =
      s"""if (1 > 2)
         |  println("wow")
         |else
         |  println()
         |  println("ok")
         |e$CARET
         |""".stripMargin,
    resultText =
      s"""if (1 > 2)
         |  println("wow")
         |else
         |  println()
         |  println("ok")
         |end if
         |$CARET
         |""".stripMargin,
    lookupStr = "end if"
  )

  @Test
  def testIfOldStyleWithElseWithoutConditionAndThenExpr(): Unit = checkLookupElement(
    fileText =
      s"""if else
         |  println()
         |  println("ok")
         |e$CARET
         |""".stripMargin,
    resultText =
      s"""if else
         |  println()
         |  println("ok")
         |end if
         |$CARET
         |""".stripMargin,
    lookupStr = "end if"
  )

  @Test
  def testIfWithBracesAroundThenExpr(): Unit = checkLookupElement(
    fileText =
      s"""if (1 > 2) {
         |  println("wow")
         |} else
         |  println()
         |  println("ok")
         |e$CARET
         |""".stripMargin,
    resultText =
      s"""if (1 > 2) {
         |  println("wow")
         |} else
         |  println()
         |  println("ok")
         |end if
         |$CARET
         |""".stripMargin,
    lookupStr = "end if"
  )

  @Test
  def testIfWithMultilineElse(): Unit = checkLookupElement(
    fileText =
      s"""if 1 > 2 then println("wow")
         |else
         |  println("ok")
         |  println(1 - 2)
         |e$CARET
         |""".stripMargin,
    resultText =
      s"""if 1 > 2 then println("wow")
         |else
         |  println("ok")
         |  println(1 - 2)
         |end if
         |$CARET
         |""".stripMargin,
    lookupStr = "end if"
  )

  @Test
  def testIfWithoutInput(): Unit = checkLookupElement(
    fileText =
      s"""if 1 > 2 then
         |  println("wow")
         |  println("impossible")
         |$CARET
         |""".stripMargin,
    resultText =
      s"""if 1 > 2 then
         |  println("wow")
         |  println("impossible")
         |end if
         |$CARET
         |""".stripMargin,
    lookupStr = "end if"
  )

  @Test
  def testIfAfterEndKeyword(): Unit = checkLookupElement(
    fileText =
      s"""if 1 > 2 then
         |  println("wow")
         |  println("impossible")
         |end $CARET
         |""".stripMargin,
    resultText =
      s"""if 1 > 2 then
         |  println("wow")
         |  println("impossible")
         |end if
         |$CARET
         |""".stripMargin,
    lookupStr = "if",
    presentationText = "if"
  )

  @Test
  def testNoCompletionForIfWithBraces1(): Unit = checkNoCompletion(
    fileText =
      s"""if (1 > 2) {
         |  println("wow")
         |  println("impossible")
         |}
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForIfWithBraces2(): Unit = checkNoCompletion(
    fileText =
      s"""if (1 > 2)
         |  println("wow")
         |  println("impossible")
         |else {
         |  println("ok")
         |}
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForIfWithEndMarker(): Unit = checkNoCompletion(
    fileText =
      s"""if 1 > 2 then
         |  println("wow")
         |  println("impossible")
         |end if
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForIfWithOneLinerThenWithoutElse(): Unit = checkNoCompletion(
    fileText =
      s"""if 1 > 2 then
         |  println("wow")
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForIfWithOneLinerThenAndElse(): Unit = checkNoCompletion(
    fileText =
      s"""if 1 > 2 then
         |  println("wow")
         |else
         |  println("ok")
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNestedIf(): Unit = checkLookupElement(
    fileText =
      s"""if 1 > 2 then
         |  if 2 > 3 then
         |    println("wow")
         |    println(2 - 3)
         |  $CARET
         |  println("impossible")
         |end if
         |""".stripMargin,
    resultText =
      s"""if 1 > 2 then
         |  if 2 > 3 then
         |    println("wow")
         |    println(2 - 3)
         |  end if
         |  $CARET
         |  println("impossible")
         |end if
         |""".stripMargin,
    lookupStr = "end if"
  )

  @Test
  def testNoCompletionForIfOnTheSameLineAsValueDefinition(): Unit = checkNoCompletionFor(
    fileText =
      s"""val v = if 1 > 2 then
         |  println("hmm")
         |  3
         |else 4
         |e$CARET
         |""".stripMargin,
    item = "end if"
  )

  /// while

  @Test
  def testWhile(): Unit = checkLookupElement(
    fileText =
      s"""var x = 5
         |while x > 0 do
         |  x -= 2
         |  x += 1
         |e$CARET
         |""".stripMargin,
    resultText =
      s"""var x = 5
         |while x > 0 do
         |  x -= 2
         |  x += 1
         |end while
         |$CARET
         |""".stripMargin,
    lookupStr = "end while"
  )

  @Test
  def testWhileOldStyle(): Unit = checkLookupElement(
    fileText =
      s"""var x = 5
         |while (x > 0)
         |  x -= 2
         |  x += 1
         |e$CARET
         |""".stripMargin,
    resultText =
      s"""var x = 5
         |while (x > 0)
         |  x -= 2
         |  x += 1
         |end while
         |$CARET
         |""".stripMargin,
    lookupStr = "end while"
  )

  @Test
  def testWhileWithoutInput(): Unit = checkLookupElement(
    fileText =
      s"""var x = 5
         |while x > 0 do
         |  x -= 2
         |  x += 1
         |$CARET
         |""".stripMargin,
    resultText =
      s"""var x = 5
         |while x > 0 do
         |  x -= 2
         |  x += 1
         |end while
         |$CARET
         |""".stripMargin,
    lookupStr = "end while"
  )

  @Test
  def testWhileAfterEndKeyword(): Unit = checkLookupElement(
    fileText =
      s"""var x = 5
         |while x > 0 do
         |  x -= 2
         |  x += 1
         |end $CARET
         |""".stripMargin,
    resultText =
      s"""var x = 5
         |while x > 0 do
         |  x -= 2
         |  x += 1
         |end while
         |$CARET
         |""".stripMargin,
    lookupStr = "while",
    presentationText = "while"
  )

  @Test
  def testNoCompletionForWhileWithOneLinerBody(): Unit = checkNoCompletion(
    fileText =
      s"""var x = 5
         |while x > 0 do
         |  x -= 1
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForWhileWithBraces1(): Unit = checkNoCompletion(
    fileText =
      s"""var x = 5
         |while x > 0 do {
         |  x -= 2
         |  x += 1
         |}
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForWhileWithBraces2(): Unit = checkNoCompletion(
    fileText =
      s"""var x = 5
         |while (x > 0) {
         |  x -= 2
         |  x += 1
         |}
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForWhileWithEndMarker(): Unit = checkNoCompletion(
    fileText =
      s"""var x = 5
         |while x > 0 do
         |  x -= 2
         |  x += 1
         |end while
         |e$CARET
         |""".stripMargin
  )

  /// for

  @Test
  def testFor(): Unit = checkLookupElement(
    fileText =
      s"""for x <- 0 to 5 do
         |  println(x)
         |  println(x * 2)
         |e$CARET
         |""".stripMargin,
    resultText =
      s"""for x <- 0 to 5 do
         |  println(x)
         |  println(x * 2)
         |end for
         |$CARET
         |""".stripMargin,
    lookupStr = "end for"
  )

  @Test
  def testForOldStyle(): Unit = checkLookupElement(
    fileText =
      s"""for (x <- 0 to 5)
         |  println(x)
         |  println(x * 2)
         |e$CARET
         |""".stripMargin,
    resultText =
      s"""for (x <- 0 to 5)
         |  println(x)
         |  println(x * 2)
         |end for
         |$CARET
         |""".stripMargin,
    lookupStr = "end for"
  )

  @Test
  def testForWithParensAroundEnumerators(): Unit = checkLookupElement(
    fileText =
      s"""for (x <- 0 to 5)
         |  println(x)
         |  println(x * 2)
         |e$CARET
         |""".stripMargin,
    resultText =
      s"""for (x <- 0 to 5)
         |  println(x)
         |  println(x * 2)
         |end for
         |$CARET
         |""".stripMargin,
    lookupStr = "end for"
  )

  @Test
  def testForWithBracesAroundEnumerators(): Unit = checkLookupElement(
    fileText =
      s"""for {
         |  x <- 0 to 5
         |} do
         |  println(x)
         |  println(x * 2)
         |e$CARET
         |""".stripMargin,
    resultText =
      s"""for {
         |  x <- 0 to 5
         |} do
         |  println(x)
         |  println(x * 2)
         |end for
         |$CARET
         |""".stripMargin,
    lookupStr = "end for"
  )

  @Test
  def testForWithoutInput(): Unit = checkLookupElement(
    fileText =
      s"""for x <- 0 to 5 do
         |  println(x)
         |  println(x * 2)
         |$CARET
         |""".stripMargin,
    resultText =
      s"""for x <- 0 to 5 do
         |  println(x)
         |  println(x * 2)
         |end for
         |$CARET
         |""".stripMargin,
    lookupStr = "end for"
  )

  @Test
  def testForAfterEndKeyword(): Unit = checkLookupElement(
    fileText =
      s"""for x <- 0 to 5 do
         |  println(x)
         |  println(x * 2)
         |end $CARET
         |""".stripMargin,
    resultText =
      s"""for x <- 0 to 5 do
         |  println(x)
         |  println(x * 2)
         |end for
         |$CARET
         |""".stripMargin,
    lookupStr = "for",
    presentationText = "for"
  )

  @Test
  def testNoCompletionForForWithOneLinerBody(): Unit = checkNoCompletion(
    fileText =
      s"""for x <- 0 to 5 do
         |  println(x)
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForForWithBracesAroundBody1(): Unit = checkNoCompletion(
    fileText =
      s"""for {
         |  x <- 0 to 5
         |} do {
         |  println(x)
         |  println(x * 2)
         |}
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForForWithBracesAroundBody2(): Unit = checkNoCompletion(
    fileText =
      s"""for {
         |  x <- 0 to 5
         |} {
         |  println(x)
         |  println(x * 2)
         |}
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForForWithBracesAroundBody3(): Unit = checkNoCompletion(
    fileText =
      s"""for
         |  x <- 0 to 5
         |  y <- 0 to 5
         |do {
         |  println(x)
         |  println(x * 2)
         |}
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForForWithEndMarker(): Unit = checkNoCompletion(
    fileText =
      s"""for x <- 0 to 5 do
         |  println(x)
         |  println(x * 2)
         |end for
         |e$CARET
         |""".stripMargin
  )

  /// try

  @Test
  def testTry(): Unit = checkLookupElement(
    fileText =
      s"""var x = 0
         |try
         |  x -= 2
         |  x += 1
         |finally
         |  println(x)
         |e$CARET
         |""".stripMargin,
    resultText =
      s"""var x = 0
         |try
         |  x -= 2
         |  x += 1
         |finally
         |  println(x)
         |end try
         |$CARET
         |""".stripMargin,
    lookupStr = "end try"
  )

  @Test
  def testTry2(): Unit = checkLookupElement(
    fileText =
      s"""var x = 0
         |try
         |  x += 1
         |finally
         |  println(x)
         |  println(x * 2)
         |e$CARET
         |""".stripMargin,
    resultText =
      s"""var x = 0
         |try
         |  x += 1
         |finally
         |  println(x)
         |  println(x * 2)
         |end try
         |$CARET
         |""".stripMargin,
    lookupStr = "end try"
  )

  @Test
  def testTry3(): Unit = checkLookupElement(
    fileText =
      s"""var x = 0
         |try
         |  x += 1
         |catch
         |  case e: NumberFormatException => ()
         |  case e: Exception => ()
         |finally
         |  println(x)
         |e$CARET
         |""".stripMargin,
    resultText =
      s"""var x = 0
         |try
         |  x += 1
         |catch
         |  case e: NumberFormatException => ()
         |  case e: Exception => ()
         |finally
         |  println(x)
         |end try
         |$CARET
         |""".stripMargin,
    lookupStr = "end try"
  )

  @Test
  def testTry4(): Unit = checkLookupElement(
    fileText =
      s"""var x = 0
         |try
         |  x += 1
         |catch
         |  case e: NumberFormatException => ()
         |  case e: Exception => ()
         |e$CARET
         |""".stripMargin,
    resultText =
      s"""var x = 0
         |try
         |  x += 1
         |catch
         |  case e: NumberFormatException => ()
         |  case e: Exception => ()
         |end try
         |$CARET
         |""".stripMargin,
    lookupStr = "end try"
  )

  @Test
  def testTryWithBracesAroundExpression(): Unit = checkLookupElement(
    fileText =
      s"""var x = 0
         |try {
         |  x -= 2
         |  x += 1
         |} finally
         |  println(x)
         |  println(x + 1)
         |e$CARET
         |""".stripMargin,
    resultText =
      s"""var x = 0
         |try {
         |  x -= 2
         |  x += 1
         |} finally
         |  println(x)
         |  println(x + 1)
         |end try
         |$CARET
         |""".stripMargin,
    lookupStr = "end try"
  )

  @Test
  def testTryWithBracesAroundCatchCaseClauses(): Unit = checkLookupElement(
    fileText =
      s"""var x = 0
         |try
         |  x -= 2
         |  x += 1
         |catch {
         |  case e: NumberFormatException => ()
         |  case e: Exception => ()
         |}
         |finally
         |  println(x)
         |e$CARET
         |""".stripMargin,
    resultText =
      s"""var x = 0
         |try
         |  x -= 2
         |  x += 1
         |catch {
         |  case e: NumberFormatException => ()
         |  case e: Exception => ()
         |}
         |finally
         |  println(x)
         |end try
         |$CARET
         |""".stripMargin,
    lookupStr = "end try"
  )

  @Test
  def testTryWithBracesAroundTryExprAndCatchCaseClauses(): Unit = checkLookupElement(
    fileText =
      s"""var x = 0
         |try {
         |  x -= 2
         |  x += 1
         |} catch {
         |  case e: NumberFormatException => ()
         |  case e: Exception => ()
         |}
         |finally
         |  println(x)
         |  println(x + 1)
         |e$CARET
         |""".stripMargin,
    resultText =
      s"""var x = 0
         |try {
         |  x -= 2
         |  x += 1
         |} catch {
         |  case e: NumberFormatException => ()
         |  case e: Exception => ()
         |}
         |finally
         |  println(x)
         |  println(x + 1)
         |end try
         |$CARET
         |""".stripMargin,
    lookupStr = "end try"
  )

  @Test
  def testTryWithoutInput(): Unit = checkLookupElement(
    fileText =
      s"""var x = 0
         |try
         |  x -= 2
         |  x += 1
         |finally
         |  println(x)
         |$CARET
         |""".stripMargin,
    resultText =
      s"""var x = 0
         |try
         |  x -= 2
         |  x += 1
         |finally
         |  println(x)
         |end try
         |$CARET
         |""".stripMargin,
    lookupStr = "end try"
  )

  @Test
  def testTryAfterEndKeyword(): Unit = checkLookupElement(
    fileText =
      s"""var x = 0
         |try
         |  x -= 2
         |  x += 1
         |finally
         |  println(x)
         |end $CARET
         |""".stripMargin,
    resultText =
      s"""var x = 0
         |try
         |  x -= 2
         |  x += 1
         |finally
         |  println(x)
         |end try
         |$CARET
         |""".stripMargin,
    lookupStr = "try",
    presentationText = "try"
  )

  @Test
  def testNoCompletionForTryWithOneLinerBlocks(): Unit = checkNoCompletion(
    fileText =
      s"""var x = 0
         |try
         |  x += 1
         |catch
         |  case e: Exception => ()
         |finally
         |  println(x)
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForTryWithBraces(): Unit = checkNoCompletion(
    fileText =
      s"""var x = 0
         |try {
         |  x -= 2
         |  x += 1
         |} catch {
         |  case e: NumberFormatException => ()
         |  case e: Exception => ()
         |}
         |finally
         |  println(x)
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForTryWithBraces2(): Unit = checkNoCompletion(
    fileText =
      s"""var x = 0
         |try
         |  x -= 2
         |  x += 1
         |catch
         |  case e: NumberFormatException => ()
         |  case e: Exception => ()
         |finally {
         |  println(x)
         |}
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForTryWithBraces3(): Unit = checkNoCompletion(
    fileText =
      s"""var x = 0
         |try
         |  x -= 2
         |  x += 1
         |catch {
         |  case e: NumberFormatException => ()
         |  case e: Exception => ()
         |}
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForTryWithEndMarker(): Unit = checkNoCompletion(
    fileText =
      s"""var x = 0
         |try
         |  x -= 2
         |  x += 1
         |finally
         |  println(x)
         |end try
         |e$CARET
         |""".stripMargin
  )

  /// match

  @Test
  def testMatch(): Unit = checkLookupElement(
    fileText =
      s"""val x = ???
         |x match
         |  case 0 => println("0")
         |  case _ =>
         |e$CARET
         |""".stripMargin,
    resultText =
      s"""val x = ???
         |x match
         |  case 0 => println("0")
         |  case _ =>
         |end match
         |$CARET
         |""".stripMargin,
    lookupStr = "end match"
  )

  @Test
  def testMatchWithoutInput(): Unit = checkLookupElement(
    fileText =
      s"""val x = ???
         |x match
         |  case 0 => println("0")
         |  case _ =>
         |$CARET
         |""".stripMargin,
    resultText =
      s"""val x = ???
         |x match
         |  case 0 => println("0")
         |  case _ =>
         |end match
         |$CARET
         |""".stripMargin,
    lookupStr = "end match"
  )

  @Test
  def testMatchAfterEndKeyword(): Unit = checkLookupElement(
    fileText =
      s"""val x = ???
         |x match
         |  case 0 => println("0")
         |  case _ =>
         |end $CARET
         |""".stripMargin,
    resultText =
      s"""val x = ???
         |x match
         |  case 0 => println("0")
         |  case _ =>
         |end match
         |$CARET
         |""".stripMargin,
    lookupStr = "match",
    presentationText = "match"
  )

  @Test
  def testNoCompletionForMatchWithOneLinerCaseClauses(): Unit = checkNoCompletion(
    fileText =
      s"""val x = ???
         |x match
         |  case 0 => println("0")
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForMatchWithBraces(): Unit = checkNoCompletion(
    fileText =
      s"""val x = ???
         |x match {
         |  case 0 => println("0")
         |  case _ =>
         |}
         |e$CARET
         |""".stripMargin
  )

  @Test
  def testNoCompletionForMatchWithEndMarker(): Unit = checkNoCompletion(
    fileText =
      s"""val x = ???
         |x match
         |  case 0 => println("0")
         |  case _ =>
         |end match
         |e$CARET
         |""".stripMargin
  )

  /// misaligned markers

  @Test
  def testMisalignedEndMarker1(): Unit = checkLookupElement(
    fileText =
      s"""package p1.p2:
         |  abstract class C():
         |    def this(x: Int) =
         |      this()
         |      if x > 0 then
         |        try
         |          x match
         |            case 0 => println("0")
         |            case _ =>
         |            e$CARET
         |""".stripMargin,
    s"""package p1.p2:
       |  abstract class C():
       |    def this(x: Int) =
       |      this()
       |      if x > 0 then
       |        try
       |          x match
       |            case 0 => println("0")
       |            case _ =>
       |          end match
       |          $CARET
       |""".stripMargin,
    lookupStr = "end match"
  )

  @Test
  def testMisalignedEndMarker2(): Unit = checkLookupElement(
    fileText =
      s"""package p1.p2:
         |  abstract class C():
         |    def this(x: Int) =
         |      this()
         |      if x > 0 then
         |        try
         |          x match
         |            case 0 => println("0")
         |            case _ =>
         |            e$CARET
         |""".stripMargin,
    s"""package p1.p2:
       |  abstract class C():
       |    def this(x: Int) =
       |      this()
       |      if x > 0 then
       |        try
       |          x match
       |            case 0 => println("0")
       |            case _ =>
       |        end try
       |        $CARET
       |""".stripMargin,
    lookupStr = "end try"
  )

  @Test
  def testMisalignedEndMarker3(): Unit = checkLookupElement(
    fileText =
      s"""package p1.p2:
         |  abstract class C():
         |    def this(x: Int) =
         |      this()
         |      if x > 0 then
         |        try
         |          x match
         |            case 0 => println("0")
         |            case _ =>
         |            e$CARET
         |""".stripMargin,
    s"""package p1.p2:
       |  abstract class C():
       |    def this(x: Int) =
       |      this()
       |      if x > 0 then
       |        try
       |          x match
       |            case 0 => println("0")
       |            case _ =>
       |      end if
       |      $CARET
       |""".stripMargin,
    lookupStr = "end if"
  )

  @Test
  def testMisalignedEndMarker4(): Unit = checkLookupElement(
    fileText =
      s"""package p1.p2:
         |  abstract class C():
         |    def this(x: Int) =
         |      this()
         |      if x > 0 then
         |        try
         |          x match
         |            case 0 => println("0")
         |            case _ =>
         |            e$CARET
         |""".stripMargin,
    s"""package p1.p2:
       |  abstract class C():
       |    def this(x: Int) =
       |      this()
       |      if x > 0 then
       |        try
       |          x match
       |            case 0 => println("0")
       |            case _ =>
       |    end this
       |    $CARET
       |""".stripMargin,
    lookupStr = "end this"
  )

  @Test
  def testMisalignedEndMarker5(): Unit = checkLookupElement(
    fileText =
      s"""package p1.p2:
         |  abstract class C():
         |    def this(x: Int) =
         |      this()
         |      if x > 0 then
         |        try
         |          x match
         |            case 0 => println("0")
         |            case _ =>
         |            e$CARET
         |""".stripMargin,
    s"""package p1.p2:
       |  abstract class C():
       |    def this(x: Int) =
       |      this()
       |      if x > 0 then
       |        try
       |          x match
       |            case 0 => println("0")
       |            case _ =>
       |  end C
       |  $CARET
       |""".stripMargin,
    lookupStr = "end C"
  )

  @Test
  def testMisalignedEndMarker6(): Unit = checkLookupElement(
    fileText =
      s"""package p1.p2:
         |  abstract class C():
         |    def this(x: Int) =
         |      this()
         |      if x > 0 then
         |        try
         |          x match
         |            case 0 => println("0")
         |            case _ =>
         |            e$CARET
         |""".stripMargin,
    s"""package p1.p2:
       |  abstract class C():
       |    def this(x: Int) =
       |      this()
       |      if x > 0 then
       |        try
       |          x match
       |            case 0 => println("0")
       |            case _ =>
       |end p2
       |$CARET
       |""".stripMargin,
    lookupStr = "end p2"
  )

  /// sorting

  @Test
  def testLookupElementsSorting1(): Unit = checkLookupElementsOrder(
    fileText =
      s"""package p1.p2:
         |  abstract class C():
         |    def this(x: Int) =
         |      this()
         |      if x > 0 then
         |        try
         |          x match
         |            case 0 => println("0")
         |            case _ =>
         |            e$CARET
         |""".stripMargin,
    expectedItems = List("end match", "end try", "end if", "end this", "end C", "end p2")
  )

  @Test
  def testLookupElementsSorting2(): Unit = checkLookupElementsOrder(
    fileText =
      s"""package p1.p2:
         |  abstract class C():
         |    def this(x: Int) =
         |      this()
         |      if x > 0 then
         |        try
         |          x match
         |            case 0 => println("0")
         |            case _ =>
         |            e$CARET
         |    def this(x: String) = this()
         |""".stripMargin,
    expectedItems = List("end match", "end try", "end if", "end this")
  )

  @Test
  def testLookupElementsSorting3(): Unit = checkLookupElementsOrder(
    fileText =
      s"""package p1.p2:
         |  abstract class C():
         |    def this(x: Int) =
         |      this()
         |      if x > 0 then
         |        try
         |          x match
         |            case 0 => println("0")
         |            case _ =>
         |            e$CARET
         |        finally
         |          println("done")
         |""".stripMargin,
    expectedItems = List("end match")
  )

}
