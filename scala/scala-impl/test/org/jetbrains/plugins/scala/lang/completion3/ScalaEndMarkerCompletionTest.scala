package org.jetbrains.plugins.scala
package lang
package completion3

import com.intellij.codeInsight.completion.CompletionType
import org.junit.Assert

class ScalaEndMarkerCompletionTest extends ScalaCodeInsightTestBase {

  import ScalaCodeInsightTestBase._

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

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
    val (_, items) = activeLookupWithItems(fileText, CompletionType.BASIC, DEFAULT_TIME)
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

  def testNoCompletionAfterDot(): Unit = checkNoCompletion(
    fileText =
      s"""def foo =
         |  1
         |  2
         |  3.e$CARET
         |""".stripMargin
  )

  /// anonymous class

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

  def testNoCompletionForAnonClassWithoutTemplateBody(): Unit = checkNoCompletion(
    fileText =
      s"""class C
         |
         |new C
         |e$CARET
         |""".stripMargin
  )

  def testNoCompletionForAnonClassWithoutTemplateBodyAfterEndKeyword(): Unit = checkNoCompletion(
    fileText =
      s"""class C
         |
         |new C
         |end $CARET
         |""".stripMargin
  )

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

  def testNoCompletionForClassWithoutTemplateBody(): Unit = checkNoCompletion(
    fileText =
      s"""class C
         |e$CARET
         |""".stripMargin
  )

  def testNoCompletionForClassWithoutTemplateBodyAfterEndKeyword(): Unit = checkNoCompletion(
    fileText =
      s"""class C
         |end $CARET
         |""".stripMargin
  )

  def testNoCompletionForClassWithBraces(): Unit = checkNoCompletion(
    fileText =
      s"""class C {
         |  def foo = true
         |}
         |e$CARET
         |""".stripMargin
  )

  def testNoCompletionForClassWithEndMarker(): Unit = checkNoCompletion(
    fileText =
      s"""class C:
         |  def foo = true
         |end C
         |e$CARET
         |""".stripMargin
  )

  /// trait

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

  def testNoCompletionForTraitWithoutTemplateBody(): Unit = checkNoCompletion(
    fileText =
      s"""trait T
         |e$CARET
         |""".stripMargin
  )

  def testNoCompletionForTraitWithoutTemplateBodyAfterEndKeyword(): Unit = checkNoCompletion(
    fileText =
      s"""trait T
         |end $CARET
         |""".stripMargin
  )

  def testNoCompletionForTraitWithBraces(): Unit = checkNoCompletion(
    fileText =
      s"""trait T {
         |  def foo
         |}
         |e$CARET
         |""".stripMargin
  )

  def testNoCompletionForTraitWithEndMarker(): Unit = checkNoCompletion(
    fileText =
      s"""trait T:
         |  def foo
         |end T
         |e$CARET
         |""".stripMargin
  )

  /// object

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

  def testNoCompletionForObjectWithoutTemplateBody(): Unit = checkNoCompletion(
    fileText =
      s"""object O
         |e$CARET
         |""".stripMargin
  )

  def testNoCompletionForObjectWithoutTemplateBodyAfterEndKeyword(): Unit = checkNoCompletion(
    fileText =
      s"""object O
         |end $CARET
         |""".stripMargin
  )

  def testNoCompletionForObjectWithBraces(): Unit = checkNoCompletion(
    fileText =
      s"""object O {
         |  def foo = true
         |}
         |e$CARET
         |""".stripMargin
  )

  def testNoCompletionForObjectWithEndMarker(): Unit = checkNoCompletion(
    fileText =
      s"""object O:
         |  def foo = true
         |end O
         |e$CARET
         |""".stripMargin
  )

  /// enum

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

  def testNoCompletionForEnumWithoutTemplateBody(): Unit = checkNoCompletion(
    fileText =
      s"""enum E
         |e$CARET
         |""".stripMargin
  )

  def testNoCompletionForEnumWithoutTemplateBodyAfterEndKeyword(): Unit = checkNoCompletion(
    fileText =
      s"""enum E
         |end $CARET
         |""".stripMargin
  )

  def testNoCompletionForEnumWithBraces(): Unit = checkNoCompletion(
    fileText =
      s"""enum E {
         |  case C
         |}
         |e$CARET
         |""".stripMargin
  )

  def testNoCompletionForEnumWithEndMarker(): Unit = checkNoCompletion(
    fileText =
      s"""enum E:
         |  case C
         |end E
         |e$CARET
         |""".stripMargin
  )

  /// constructor

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

  def testNoCompletionForConstructorWithoutBody(): Unit = checkNoCompletionFor(
    fileText =
      s"""class C(i: Int):
         |  def this(i: Int, s: String)
         |  e$CARET
         |""".stripMargin,
    item = "end this"
  )

  def testNoCompletionForConstructorIfIndentIsLessThanConstructorIndent(): Unit = checkNoCompletionFor(
    fileText =
      s"""class C(i: Int):
         |  def this(i: Int, s: String) =
         |    this(i)
         | e$CARET
         |""".stripMargin,
    item = "end this"
  )

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

  def testNoCompletionForValueWithOneLinerBody(): Unit = checkNoCompletion(
    fileText =
      s"""val v =
         |  42
         |e$CARET
         |""".stripMargin
  )

  def testNoCompletionForValueWithBraces(): Unit = checkNoCompletion(
    fileText =
      s"""val v = {
         |  0
         |  42
         |}
         |e$CARET
         |""".stripMargin
  )

  def testNoCompletionForValueWithEndMarker(): Unit = checkNoCompletion(
    fileText =
      s"""val v =
         |  1 +
         |    41
         |end v
         |e$CARET
         |""".stripMargin
  )

  def testNoCompletionForAbstractValue(): Unit = checkNoCompletion(
    fileText =
      s"""val v
         |e$CARET
         |""".stripMargin
  )

  def testNoCompletionForAbstractValueAfterEndKeyword(): Unit = checkNoCompletion(
    fileText =
      s"""val v
         |end $CARET
         |""".stripMargin
  )

  def testNoCompletionForValueIfEndIsNotOnTheNewLine(): Unit = checkNoCompletion(
    fileText =
      s"""val v =
         |  0
         |  42 e$CARET
         |""".stripMargin
  )

  /// variable

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

  def testNoCompletionForVariableWithOneLinerBody(): Unit = checkNoCompletion(
    fileText =
      s"""var v =
         |  42
         |e$CARET
         |""".stripMargin
  )

  def testNoCompletionForVariableWithBraces(): Unit = checkNoCompletion(
    fileText =
      s"""var v = {
         |  0
         |  42
         |}
         |e$CARET
         |""".stripMargin
  )

  def testNoCompletionForVariableWithEndMarker(): Unit = checkNoCompletion(
    fileText =
      s"""var v =
         |  0
         |  42
         |end v
         |e$CARET
         |""".stripMargin
  )

  def testNoCompletionForAbstractVariable(): Unit = checkNoCompletion(
    fileText =
      s"""var v
         |e$CARET
         |""".stripMargin
  )

  def testNoCompletionForAbstractVariableAfterEndKeyword(): Unit = checkNoCompletion(
    fileText =
      s"""var v
         |end $CARET
         |""".stripMargin
  )

  def testNoCompletionForVariableIfEndIsNotOnTheNewLine(): Unit = checkNoCompletion(
    fileText =
      s"""var v =
         |  0
         |  42 e$CARET
         |""".stripMargin
  )

  /// value binding pattern

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

  def testNoCompletionForValueBindingWithOneLinerBody(): Unit = checkNoCompletion(
    fileText =
      s"""val h :: t =
         |  List(1, 2, 3)
         |e$CARET
         |""".stripMargin
  )

  def testNoCompletionForValueBindingWithoutAssign(): Unit = checkNoCompletion(
    fileText =
      s"""val h :: t
         |e$CARET
         |""".stripMargin
  )

  def testNoCompletionForValueBindingWithoutAssignAfterEndKeyword(): Unit = checkNoCompletion(
    fileText =
      s"""val h :: t
         |end $CARET
         |""".stripMargin
  )

  def testNoCompletionForValueBindingWithBraces(): Unit = checkNoCompletion(
    fileText =
      s"""val h :: t = {
         |  List(1,
         |    2, 3)
         |}
         |e$CARET
         |""".stripMargin
  )

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

  def testNoCompletionForVariableBinding(): Unit = checkNoCompletion(
    fileText =
      s"""var h :: t =
         |  List(1,
         |    2, 3)
         |e$CARET
         |""".stripMargin
  )

  def testNoCompletionForVariableBindingAfterEndKeyword(): Unit = checkNoCompletion(
    fileText =
      s"""var h :: t =
         |  List(1,
         |    2, 3)
         |end $CARET
         |""".stripMargin
  )

  def testNoCompletionForVariableBindingWithOneLinerBody(): Unit = checkNoCompletion(
    fileText =
      s"""var h :: t =
         |  List(1, 2, 3)
         |e$CARET
         |""".stripMargin
  )

  def testNoCompletionForVariableBindingWithoutAssign(): Unit = checkNoCompletion(
    fileText =
      s"""var h :: t
         |e$CARET
         |""".stripMargin
  )

  def testNoCompletionForVariableBindingWithoutAssignAfterEndKeyword(): Unit = checkNoCompletion(
    fileText =
      s"""var h :: t
         |end $CARET
         |""".stripMargin
  )

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

  def testNoCompletionForAnonymousGivenAliasWithOneLinerBody(): Unit = checkNoCompletion(
    fileText =
      s"""given Int =
         |  42
         |e$CARET
         |""".stripMargin
  )

  def testNoCompletionForAnonymousGivenAliasWithoutAssign(): Unit = checkNoCompletion(
    fileText =
      s"""given Int
         |e$CARET
         |""".stripMargin
  )

  def testNoCompletionForAnonymousGivenAliasWithoutAssignAfterEndKeyword(): Unit = checkNoCompletion(
    fileText =
      s"""given Int
         |end $CARET
         |""".stripMargin
  )

  def testNoCompletionForAnonymousGivenAliasWithBraces(): Unit = checkNoCompletion(
    fileText =
      s"""given Int = {
         |  0
         |  42
         |}
         |e$CARET
         |""".stripMargin
  )

  def testNoCompletionForAnonymousGivenAliasWithEndMarker(): Unit = checkNoCompletion(
    fileText =
      s"""given Int =
         |  0
         |  42
         |end given
         |e$CARET
         |""".stripMargin
  )

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

  def testNoCompletionForGivenAliasWithOneLinerBody(): Unit = checkNoCompletion(
    fileText =
      s"""given someGiven: Int =
         |  42
         |e$CARET
         |""".stripMargin
  )

  def testNoCompletionForGivenAliasWithoutAssign(): Unit = checkNoCompletion(
    fileText =
      s"""given someGiven: Int
         |e$CARET
         |""".stripMargin
  )

  def testNoCompletionForGivenAliasWithoutAssignAfterEndKeyword(): Unit = checkNoCompletion(
    fileText =
      s"""given someGiven: Int
         |end $CARET
         |""".stripMargin
  )

  def testNoCompletionForGivenAliasWithBraces(): Unit = checkNoCompletion(
    fileText =
      s"""given someGiven: Int = {
         |  0
         |  42
         |}
         |e$CARET
         |""".stripMargin
  )

  def testNoCompletionForGivenAliasWithEndMarker(): Unit = checkNoCompletion(
    fileText =
      s"""given someGiven: Int =
         |  0
         |  42
         |end someGiven
         |e$CARET
         |""".stripMargin
  )

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

  def testNoCompletionForAnonymousGivenDefinitionWithOneLinerBody(): Unit = checkNoCompletion(
    fileText =
      s"""trait Ord[T]:
         |  def compare(x: T, y: T): Int
         |given Ord[Int] with
         |  def compare(x: Int, y: Int): Int = x.compareTo(y)
         |e$CARET
         |""".stripMargin,
  )

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

  def testNoCompletionForGivenDefinitionWithOneLinerBody(): Unit = checkNoCompletion(
    fileText =
      s"""trait Ord[T]:
         |  def compare(x: T, y: T): Int
         |given intOrd: Ord[Int] with
         |  def compare(x: Int, y: Int): Int = if x < y then -1 else if x > y then +1 else 0
         |e$CARET
         |""".stripMargin
  )

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

  def testNoCompletionForExtensionWithOneLinerBody(): Unit = checkNoCompletion(
    fileText =
      s"""extension (x: String)
         |  def < (y: String): Boolean = ???
         |e$CARET
         |""".stripMargin
  )

  def testNoCompletionForExtensionWithBraces(): Unit = checkNoCompletion(
    fileText =
      s"""extension (x: String) {
         |  def < (y: String): Boolean =
         |    ???
         |}
         |e$CARET
         |""".stripMargin
  )

  def testNoCompletionForExtensionWithEndMarker(): Unit = checkNoCompletion(
    fileText =
      s"""extension (x: String)
         |  def < (y: String): Boolean =
         |    ???
         |end extension
         |e$CARET
         |""".stripMargin
  )

  def testNoCompletionForExtensionWithOneLinerFunctionOnTheSameLine(): Unit = checkNoCompletion(
    fileText =
      s"""extension (i: Int) def isZero: Boolean = i == 0
         |e$CARET
         |""".stripMargin
  )

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

  def testNoCompletionForMultilineExtensionFunctionOnTheSameLine(): Unit = checkNoCompletionFor(
    fileText =
      s"""extension (i: Int) def isZero: Boolean =
         |  i == 0
         |e$CARET
         |""".stripMargin,
    item = "end isZero"
  )

  // todo: uncomment when SCL-19689 is resolved
//  def testNoCompletionForExtensionWithMultilineFunctionOnTheSameLineWithEndMarker(): Unit = checkNoCompletion(
//    fileText =
//      s"""extension (i: Int) def isZero: Boolean =
//         |  i == 0
//         |end extension
//         |e$CARET
//         |""".stripMargin
//  )

  def testNoCompletionForExtensionWithoutFunctions(): Unit = checkNoCompletion(
    fileText =
      s"""extension (ss: Seq[String])
         |e$CARET
         |""".stripMargin
  )

  def testNoCompletionForExtensionWithoutFunctionsAfterEndKeyword(): Unit = checkNoCompletion(
    fileText =
      s"""extension (ss: Seq[String])
         |end $CARET
         |""".stripMargin
  )

  /// function

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

  def testNoCompletionForFunctionWithOneLinerBody(): Unit = checkNoCompletion(
    fileText =
      s"""def foo(str: String) =
         |  str.length
         |e$CARET
         |""".stripMargin
  )

  def testNoCompletionForFunctionWithoutBody(): Unit = checkNoCompletionFor(
    fileText =
      s"""def foo: Int
         |e$CARET
         |""".stripMargin,
    item = "end foo"
  )

  def testNoCompletionForFunctionWithoutBodyAfterEndKeyword(): Unit = checkNoCompletionFor(
    fileText =
      s"""def foo: Int
         |end $CARET
         |""".stripMargin,
    item = "foo"
  )

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

  def testNoCompletionForNonExplicitPackage(): Unit = checkNoCompletion(
    s"""package p1.p2.p3
       |e$CARET""".stripMargin
  )

  def testNoCompletionForNonExplicitPackageAfterEndKeyword(): Unit = checkNoCompletion(
    s"""package p1.p2.p3
       |end $CARET""".stripMargin
  )

  def testNoCompletionForPackageWithEndMarker(): Unit = checkNoCompletion(
    fileText =
      s"""package p1.p2.p3:
         |end p3
         |e$CARET
         |""".stripMargin
  )

  /// if

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

  def testNoCompletionForIfWithBraces1(): Unit = checkNoCompletion(
    fileText =
      s"""if (1 > 2) {
         |  println("wow")
         |  println("impossible")
         |}
         |e$CARET
         |""".stripMargin
  )

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

  def testNoCompletionForIfWithEndMarker(): Unit = checkNoCompletion(
    fileText =
      s"""if 1 > 2 then
         |  println("wow")
         |  println("impossible")
         |end if
         |e$CARET
         |""".stripMargin
  )

  def testNoCompletionForIfWithOneLinerThenWithoutElse(): Unit = checkNoCompletion(
    fileText =
      s"""if 1 > 2 then
         |  println("wow")
         |e$CARET
         |""".stripMargin
  )

  def testNoCompletionForIfWithOneLinerThenAndElse(): Unit = checkNoCompletion(
    fileText =
      s"""if 1 > 2 then
         |  println("wow")
         |else
         |  println("ok")
         |e$CARET
         |""".stripMargin
  )

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

  def testNoCompletionForWhileWithOneLinerBody(): Unit = checkNoCompletion(
    fileText =
      s"""var x = 5
         |while x > 0 do
         |  x -= 1
         |e$CARET
         |""".stripMargin
  )

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

  def testForOldStyle(): Unit = checkLookupElement(
    fileText =
      s"""for x <- 0 to 5
         |  println(x)
         |  println(x * 2)
         |e$CARET
         |""".stripMargin,
    resultText =
      s"""for x <- 0 to 5
         |  println(x)
         |  println(x * 2)
         |end for
         |$CARET
         |""".stripMargin,
    lookupStr = "end for"
  )

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

  def testNoCompletionForForWithOneLinerBody(): Unit = checkNoCompletion(
    fileText =
      s"""for x <- 0 to 5 do
         |  println(x)
         |e$CARET
         |""".stripMargin
  )

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

  def testNoCompletionForMatchWithOneLinerCaseClauses(): Unit = checkNoCompletion(
    fileText =
      s"""val x = ???
         |x match
         |  case 0 => println("0")
         |e$CARET
         |""".stripMargin
  )

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
