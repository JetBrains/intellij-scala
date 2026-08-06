package org.jetbrains.plugins.scala.lang.actions.editor.enter_long_tests.scala3

import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.lang.actions.editor.enter.scala3.EditorStates
import org.jetbrains.plugins.scala.lang.actions.editor.enter_long_tests.scala3.Scala3TestDataBracelessCode.CodeToType._
import org.jetbrains.plugins.scala.lang.actions.editor.enter_long_tests.scala3.Scala3TestDataBracelessCode._

import scala.collection.mutable

private[scala3] object Scala3BracelessSyntaxEnterHandlerTest_ExhaustiveGenerator {

  import EditorTestUtil.{CARET_TAG => CARET}

  sealed trait TestData
  object TestData {
    final case class ExplicitEditorStates(editorStates: EditorStates) extends TestData
    object ExplicitEditorStates {
      def apply(editorStates: EditorStates, wrapperContextCode: CodeWithDebugName): ExplicitEditorStates = {
        val statesNew = editorStates.states.map(_.withTransformedText(injectCodeWithIndentAdjust(_, wrapperContextCode.code)))
        val nameNew = editorStates.debugName.getOrElse("unnamed") + " | " + wrapperContextCode.debugName
        val editorStatesNew = EditorStates(nameNew, statesNew)
        ExplicitEditorStates(editorStatesNew)
      }
    }

    final case class Generated(contextCode: String, codeToType: String) extends TestData
    object Generated {
      def apply(contextCode: String, codeToType: String): Generated =
        new Generated(contextCode.withNormalizedSeparator, codeToType.withNormalizedSeparator)

      def apply(indentationBlockCode: String, wrapperContextCode: String, codeToType: String): Generated = {
        val contextCode = injectCodeWithIndentAdjust(indentationBlockCode, wrapperContextCode)
        apply(contextCode, codeToType)
      }
    }
  }

  final case class NamedTestData(testName: String, testData: TestData)

  private final class GroupBuilder {
    private val reservedTestNames = mutable.HashMap.empty[String, Int]
    private val buffer = mutable.ArrayBuffer.empty[NamedTestData]

    private def uniqueName(name: String): String = {
      val idx = reservedTestNames.getOrElseUpdate(name, 0)
      reservedTestNames.update(name, idx + 1)
      if (idx == 0) name else name + " | " + idx
    }

    private def makeUniqueTestName(parts: String*): String = uniqueName(parts.mkString(" | "))

    private def buildTestName(prefix: String, indented: String, wrapper: CodeWithDebugName, typed: CodeWithDebugName): String = {
      val lineWithCaret = indented.linesIterator.find(_.contains(CARET)).get
      val nameParts = List(prefix, lineWithCaret, wrapper.debugName, typed.debugName)
      makeUniqueTestName(nameParts: _*)
    }

    def addGeneratedTests(
      prefix: String,
      indented: Seq[String],
      wrapper: Seq[CodeWithDebugName],
      typed: Seq[CodeWithDebugName]
    ): Unit = {
      for {
        indentedCode <- indented
        wrapperCode <- wrapper
        typedCode <- typed
      } {
        val testName = buildTestName(prefix, indentedCode, wrapperCode, typedCode)
        val testData = TestData.Generated(indentedCode, wrapperCode.code, typedCode.code)
        buffer += NamedTestData(testName, testData)
      }
    }

    def addGeneratedTestsInAllWrapperContexts(
      prefix: String,
      indentedBlockContexts: Seq[String],
      codeToType: Seq[CodeWithDebugName]
    ): Unit = {
      addGeneratedTests(prefix, indentedBlockContexts, WrapperCodeContexts.AllContexts, codeToType)
    }

    def addEditorStatesTests(editorStates: EditorStates, contexts: Seq[CodeWithDebugName]): Unit = {
      for {
        wrapperCode <- contexts
      } {
        val testData = TestData.ExplicitEditorStates(editorStates, wrapperCode)
        val testName = makeUniqueTestName(testData.editorStates.debugName.getOrElse("unnamed"))
        buffer += NamedTestData(testName, testData)
      }
    }

    def addEditorStatesTestsInAllWrapperContexts(editorStates: EditorStates): Unit = {
      addEditorStatesTests(editorStates, WrapperCodeContexts.AllContexts)
    }

    def addNestedBlockInsertionTests(): Unit = {
      val testCases = (BlockStatements :: DefDef :: TemplateStat :: BlankLines :: Nil).map { codeToType =>
        val testName = makeUniqueTestName(codeToType.debugName, codeToType.debugName)
        val testData = TestData.Generated(
          s"""{
             |  {$CARET
             |  }
             |}""".stripMargin,
          codeToType.code
        )
        NamedTestData(testName, testData)
      }
      buffer ++= testCases
    }

    def result(): Seq[NamedTestData] = buffer.toSeq
  }

  private def toParams(tests: Seq[NamedTestData]): Array[AnyRef] = tests.toArray.map {
    case NamedTestData(testName, testData) => Array(testName, testData)
  }

  private val WCC = WrapperCodeContexts
  private val IBC = IndentedBlockContexts
  private val CTT = CodeToType

  private def indentationContextTests(
    prefix: String,
    indentedContexts: Seq[String],
    codeToType: CodeWithDebugName
  ): Array[AnyRef] = {
    val builder = new GroupBuilder
    builder.addGeneratedTestsInAllWrapperContexts(prefix, indentedContexts, codeToType :: Nil)
    toParams(builder.result())
  }

  def afterAssignOrArrowSignTests(): Array[AnyRef] = {
    afterAssignOrArrowSignStatementsTests() ++ afterAssignOrArrowSignExpressionsTests()
  }

  def forEnumeratorsAllTests(): Array[AnyRef] = {
    forEnumeratorsAllStatementsTests() ++ forEnumeratorsAllExpressionsTests()
  }

  def controlFlowTests(): Array[AnyRef] = {
    controlFlowStatementsTests() ++ controlFlowExpressionsTests()
  }

  def afterAssignOrArrowSignStatementsTests(): Array[AnyRef] =
    indentationContextTests("AfterAssignOrArrowSign", IBC.AfterAssignOrArrowSign, CTT.BlockStatements)

  def afterAssignOrArrowSignExpressionsTests(): Array[AnyRef] =
    indentationContextTests("AfterAssignOrArrowSign", IBC.AfterAssignOrArrowSign, CTT.BlockExpressions)

  def forEnumeratorsAllStatementsTests(): Array[AnyRef] =
    indentationContextTests("ForEnumeratorsAll", IBC.ForEnumeratorsAll, CTT.BlockStatements)

  def forEnumeratorsAllExpressionsTests(): Array[AnyRef] =
    indentationContextTests("ForEnumeratorsAll", IBC.ForEnumeratorsAll, CTT.BlockExpressions)

  def controlFlowStatementsTests(): Array[AnyRef] =
    indentationContextTests("ControlFlow", IBC.ControlFlow, CTT.BlockStatements)

  def controlFlowExpressionsTests(): Array[AnyRef] =
    indentationContextTests("ControlFlow", IBC.ControlFlow, CTT.BlockExpressions)

  def extensionsTests(): Array[AnyRef] = {
    val builder = new GroupBuilder
    builder.addGeneratedTestsInAllWrapperContexts("Extensions", IBC.Extensions, CTT.DefDef :: Nil)
    toParams(builder.result())
  }

  def templateDefinitionsTests(): Array[AnyRef] = {
    val builder = new GroupBuilder
    builder.addGeneratedTestsInAllWrapperContexts("TemplateDefinitions", IBC.TemplateDefinitions, CTT.TemplateStat :: Nil)
    toParams(builder.result())
  }

  def givenWithTests(): Array[AnyRef] = {
    val builder = new GroupBuilder
    builder.addGeneratedTestsInAllWrapperContexts("GivenWith", IBC.GivenWith, CTT.TemplateStat :: Nil)
    toParams(builder.result())
  }

  private def codeAfterCaretTests(
    groupName: String,
    baseIndentedBlockContexts: Seq[String],
    codeAfterCaret: String = "identifier",
    onlySpacesBeforeCaret: Boolean = false
  ): Array[AnyRef] = {
    val builder = new GroupBuilder
    val wrapperContexts = WCC.TopLevel_LastStatement :: WCC.ClassWithBraces :: WCC.NestedClassWithColonWithoutEndMarker :: Nil
    val codeToType = CTT.BlankLines :: CTT.BlockStatements :: CTT.BlockExpressions :: Nil

    val caretWithPotentialSpacesAround = s"[ ]+$CARET[ ]+".r
    val spaceBeforeCaret = baseIndentedBlockContexts.map(caretWithPotentialSpacesAround.replaceAllIn(_, s"   $CARET$codeAfterCaret"))
    builder.addGeneratedTests(groupName, spaceBeforeCaret, wrapperContexts, codeToType)

    if (!onlySpacesBeforeCaret) {
      val spaceAroundCaret = baseIndentedBlockContexts.map(caretWithPotentialSpacesAround.replaceAllIn(_, s"   $CARET   $codeAfterCaret"))
      val spaceAfterCaret = baseIndentedBlockContexts.map(caretWithPotentialSpacesAround.replaceAllIn(_, s"$CARET   $codeAfterCaret"))

      builder.addGeneratedTests(groupName, spaceAroundCaret, wrapperContexts, codeToType)
      builder.addGeneratedTests(groupName, spaceAfterCaret, wrapperContexts, codeToType)
    }

    toParams(builder.result())
  }

  def codeAfterCaretAfterAssignOrArrowSign1Tests(): Array[AnyRef] =
    codeAfterCaretTests("AfterAssignOrArrowSign 1", IBC.AfterAssignOrArrowSign)

  def codeAfterCaretAfterAssignOrArrowSign2Tests(): Array[AnyRef] =
    codeAfterCaretTests("AfterAssignOrArrowSign 2", IBC.AfterAssignOrArrowSign, codeAfterCaret = "1 + 2 + 3")

  def codeAfterCaretAfterAssignOrArrowSign3Tests(): Array[AnyRef] =
    codeAfterCaretTests(
      "AfterAssignOrArrowSign 3",
      IBC.AfterAssignOrArrowSign,
      codeAfterCaret = "\n  identifier1\n  identifier2",
      onlySpacesBeforeCaret = true
    )

  def codeAfterCaretForEnumeratorsAllTests(): Array[AnyRef] =
    codeAfterCaretTests("ForEnumeratorsAll", IBC.ForEnumeratorsAll)

  def codeAfterCaretControlFlowTests(): Array[AnyRef] =
    codeAfterCaretTests("ControlFlow", IBC.ControlFlow)

  def caseClausesBracelessTests(): Array[AnyRef] = {
    import Scala3TestDataCaseClausesEditorStates._

    val builder = new GroupBuilder
    MatchCaseClausesAll.foreach(builder.addEditorStatesTestsInAllWrapperContexts)

    val filteredTests = builder.result().filterNot { namedTestData =>
      namedTestData.testName.contains("MatchCaseClausesWithEmptyBodyStates | InsideCaseClausesNonLast") ||
        namedTestData.testName.contains("MatchCaseClausesWithNonEmptyBodyStates | InsideCaseClausesNonLast")
    }
    toParams(filteredTests)
  }

  def caseClausesWithBracesTests(): Array[AnyRef] = {
    import Scala3TestDataCaseClausesEditorStates._

    val builder = new GroupBuilder
    MatchCaseClausesAll_WithBraces.foreach { editorStates =>
      builder.addEditorStatesTests(editorStates, WCC.TopLevel :: WCC.NestedClassWithColonAndEndMarker_LastStatement :: Nil)
    }
    toParams(builder.result())
  }

  def caseClausesTryCatchTests(): Array[AnyRef] = {
    import Scala3TestDataCaseClausesEditorStates._

    val builder = new GroupBuilder
    TryCatchCaseClausesAll.foreach(builder.addEditorStatesTestsInAllWrapperContexts)
    toParams(builder.result())
  }

  def nestedBlockInsertionTests(): Array[AnyRef] = {
    val builder = new GroupBuilder
    builder.addNestedBlockInsertionTests()
    toParams(builder.result())
  }

}
