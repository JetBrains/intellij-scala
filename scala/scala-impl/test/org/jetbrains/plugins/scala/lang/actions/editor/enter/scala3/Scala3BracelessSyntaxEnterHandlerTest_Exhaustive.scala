package org.jetbrains.plugins.scala
package lang.actions.editor.enter.scala3

import com.intellij.openapi.project.Project
import com.intellij.testFramework.EditorTestUtil
import com.intellij.util.ThrowableRunnable
import junit.framework.{Test, TestCase, TestSuite}
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.lang.actions.editor.enter.scala3.Scala3TestDataBracelessCode._
import org.jetbrains.plugins.scala.settings.ScalaCompileServerSettings
import org.junit.experimental.categories.Category

// TODO: add tests for parameter default value after it's fixed in parser:
//  https://youtrack.jetbrains.com/issue/SCL-16603#focus=Comments-27-4772356.0-0

// test Enter press in different contexts
//  + different nesting level
//  + different scopes inside template definition/function body/etc...
//    + in the beginning/middle of scope
//    + in the last position inside scope
//    + in the end of the file, with & without trailing white space
//  + with & without end markers
//  + with & without space before CARET in the starting position
//  + with & without space after CARET in the starting position
//  + with some content after caret (on each step)
//  + with trimmed data & with extra spaces after it
class Scala3BracelessSyntaxEnterHandlerTest_Exhaustive extends TestCase

object Scala3BracelessSyntaxEnterHandlerTest_Exhaustive {

  import EditorTestUtil.{CARET_TAG => CARET}

  private val reservedTestNames = scala.collection.mutable.HashMap.empty[String, Int]
  private def uniqueName(name: String): String = reservedTestNames.synchronized {
    val idx = reservedTestNames.getOrElseUpdate(name, 0)
    reservedTestNames.update(name, idx + 1)
    if (idx == 0) name else name + " | " + idx
  }

  def suite: TestSuite = {
    val rootSuite = new TestSuite()

    def makeUniqueTestName(parts: String*) = uniqueName(parts.mkString(" | "))

    def buildTestName(prefix: String, indented: String, wrapper: CodeWithDebugName, typed: CodeWithDebugName): String = {
      val lineWithCaret = indented.linesIterator.find(_.contains(CARET)).get
      val nameParts = List(prefix, lineWithCaret, wrapper.debugName, typed.debugName)
      makeUniqueTestName(nameParts: _*)
    }

    def createTest(prefix: String, indented: String, wrapper: CodeWithDebugName, typed: CodeWithDebugName): ActualTest = {
      val testName = buildTestName(prefix, indented, wrapper, typed)
      val test = new ActualTest(TestData.Generated(indented, wrapper.code, typed.code))
      test.setName(testName)
      test
    }

    def createTestWithName(context: String, typed: CodeWithDebugName, testNameBase: String): ActualTest = {
      val testName = makeUniqueTestName(testNameBase, typed.debugName)
      val test = new ActualTest(TestData.Generated(context, typed.code))
      test.setName(testName)
      test
    }

    def createTests(prefix: String, indented: Seq[String], wrapper: Seq[CodeWithDebugName], typed: Seq[CodeWithDebugName]): Iterable[ActualTest] =
      for {
        indentedCode <- indented
        wrapperCode  <- wrapper
        typedCode    <- typed
      } yield createTest(prefix, indentedCode, wrapperCode, typedCode)

    def createTestsInAllWrapperContexts(prefix: String, indentedBlockContexts: Seq[String], codeToType: Seq[CodeWithDebugName]): Iterable[ActualTest] =
      createTests(prefix, indentedBlockContexts, WrapperCodeContexts.AllContexts, codeToType)

    def createEditorStatesTestsInAllWrapperContexts(editorStates: EditorStates): Iterable[ActualTest] =
      createEditorStatesTestsInContexts(editorStates, WrapperCodeContexts.AllContexts)

    def createEditorStatesTestsInContexts(editorStates: EditorStates, contexts: Seq[CodeWithDebugName]): Iterable[ActualTest] =
      for {
        wrapperCode  <- contexts
      } yield {
        val testData = TestData.ExplicitEditorStates(editorStates, wrapperCode)
        val test = new ActualTest(testData)
        val testName = makeUniqueTestName(testData.editorStates.debugName.getOrElse("unnamed"))
        test.setName(testName)
        test
      }

    val WCC = WrapperCodeContexts
    val IBC = IndentedBlockContexts
    val CTT = CodeToType

    //
    // Testing pressing Enter after different constructs which support indentation-based syntax
    //
    locally {
      rootSuite ++= createTestsInAllWrapperContexts("AfterAssignOrArrowSign",IBC.AfterAssignOrArrowSign, CTT.BlockStatements :: CTT.BlockExpressions :: Nil)
      rootSuite ++= createTestsInAllWrapperContexts("ForEnumeratorsAll", IBC.ForEnumeratorsAll, CTT.BlockStatements :: CTT.BlockExpressions :: Nil)
      rootSuite ++= createTestsInAllWrapperContexts("ControlFlow", IBC.ControlFlow, CTT.BlockStatements :: CTT.BlockExpressions :: Nil)
      rootSuite ++= createTestsInAllWrapperContexts("Extensions", IBC.Extensions, CTT.DefDef :: Nil)
      rootSuite ++= createTestsInAllWrapperContexts("TemplateDefinitions", IBC.TemplateDefinitions, CTT.TemplateStat :: Nil)
      rootSuite ++= createTestsInAllWrapperContexts("GivenWith", IBC.GivenWith, CTT.TemplateStat :: Nil)
    }

    //
    // Testing pressing Enter after =/=>/etc.. when there is some code on the same line already
    // for example: def foo = <caret>println(42)
    //
    locally {
      val wrapperContexts = WCC.TopLevel_LastStatement :: WCC.ClassWithBraces :: WCC.NestedClassWithColonWithoutEndMarker :: Nil

      val codeToType = CTT.BlankLines :: CTT.BlockStatements :: CTT.BlockExpressions :: Nil

      def addTestsWithCodeAfterCaret(
        groupName: String,
        baseIndentedBlockContexts: Seq[String],
        codeAfterCaret: String = "identifier",
        onlySpacesBeforeCaret: Boolean = false
      ): Unit = {
        val CaretWithPotentialSpacesAround = s"[ ]+$CARET[ ]+".r
        // Example: `def foo=  <caret>identifier
        val SpaceBeforeCaret = baseIndentedBlockContexts.map(CaretWithPotentialSpacesAround.replaceAllIn(_, s"   $CARET$codeAfterCaret"))
        rootSuite ++= createTests(groupName, SpaceBeforeCaret, wrapperContexts, codeToType)

        if (!onlySpacesBeforeCaret) {
          // Example: `def foo=  <caret>  identifier
          val SpaceAroundCaret = baseIndentedBlockContexts.map(CaretWithPotentialSpacesAround.replaceAllIn(_, s"   $CARET   $codeAfterCaret"))
          // Example: `def foo=<caret>  identifier
          val SpaceAfterCaret = baseIndentedBlockContexts.map(CaretWithPotentialSpacesAround.replaceAllIn(_, s"$CARET   $codeAfterCaret"))

          rootSuite ++= createTests(groupName, SpaceAroundCaret, wrapperContexts, codeToType)
          rootSuite ++= createTests(groupName, SpaceAfterCaret, wrapperContexts, codeToType)
        }
      }

      addTestsWithCodeAfterCaret("AfterAssignOrArrowSign 1", IBC.AfterAssignOrArrowSign)
      addTestsWithCodeAfterCaret("AfterAssignOrArrowSign 2", IBC.AfterAssignOrArrowSign,
        codeAfterCaret = "1 + 2 + 3")
      // TODO (minor): make onlySpacesBeforeCaret=false and fix failed tests.
      //  Details: currently enter doesn't indent caret in this code:
      //  `def foo = <caret><trailing_spaces>`
      //  this is not a frequent case, and the fix seems to be not so easy
      addTestsWithCodeAfterCaret("AfterAssignOrArrowSign 3", IBC.AfterAssignOrArrowSign,
        codeAfterCaret = "\n  identifier1\n  identifier2", onlySpacesBeforeCaret = true)
      addTestsWithCodeAfterCaret("ForEnumeratorsAll", IBC.ForEnumeratorsAll)
      addTestsWithCodeAfterCaret("ControlFlow", IBC.ControlFlow)

      // NOTE: these test are filing
      // (these are quite unlikely cases. It's unlikely that user will have e.g. such code:
      // extension (x: String) <CARET>def foo = 42)
      //addBlankLinesTestsWithCodeAfterCaret("Extensions", IBC.Extensions)
      //addBlankLinesTestsWithCodeAfterCaret("TemplateDefinitions", IBC.TemplateDefinitions)
      //addBlankLinesTestsWithCodeAfterCaret("GivenWith", IBC.GivenWith)
    }

    //
    // Testing typing of case clauses
    // (includes Enter press AND typing of entire case clause)
    //
    locally {
      import Scala3TestDataCaseClausesEditorStates._
      rootSuite ++=
        MatchCaseClausesAll.flatMap(createEditorStatesTestsInAllWrapperContexts).filterNot(test => {
          // TODO: unmute when this issue is fixed (including the comment)
          //  https://github.com/lampepfl/dotty/issues/11905
          //  https://github.com/lampepfl/dotty/issues/11905#issuecomment-808168436
          // (will require parser changes)
          test.getName.contains("MatchCaseClausesWithEmptyBodyStates | InsideCaseClausesNonLast") ||
            test.getName.contains("MatchCaseClausesWithNonEmptyBodyStates | InsideCaseClausesNonLast")
        })
      rootSuite ++=
        MatchCaseClausesAll_WithBraces.flatMap(createEditorStatesTestsInContexts(
          _, WCC.TopLevel :: WCC.NestedClassWithColonAndEndMarker_LastStatement :: Nil
        ))
      rootSuite ++=
        TryCatchCaseClausesAll.flatMap(createEditorStatesTestsInAllWrapperContexts)
    }

    locally {
      import CodeToType._
      rootSuite ++= (BlockStatements :: DefDef :: TemplateStat :: BlankLines :: Nil).map { codeToType =>
        createTestWithName(
          s"""{
             |  {$CARET
             |  }
             |}""".stripMargin,
          codeToType,
          testNameBase = codeToType.debugName
        )
      }
    }

    rootSuite
  }

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

  implicit class TestSuiteOps(private val suite: TestSuite) extends AnyVal {
    def ++=(tests: Iterable[Test]): Unit =
      tests.foreach(suite.addTest)
  }

  @Category(Array(classOf[FileSetTests]))
  private final class ActualTest(testData: TestData) extends DoEditorStateTestOps {

    // Unused, but needed to suppress inspection that JUnit test class cannot be constructed.
    private[Scala3BracelessSyntaxEnterHandlerTest_Exhaustive] def this() = this(null)

    private implicit def p: Project = getProject

    override def setUp(): Unit = {
      super.setUp()
      ScalaCompileServerSettings.getInstance.COMPILE_SERVER_ENABLED = false
      getScalaCodeStyleSettings.USE_SCALA3_INDENTATION_BASED_SYNTAX = true
    }

    override def runTestRunnable(testRunnable: ThrowableRunnable[Throwable]): Unit = {
      testData match {
        case TestData.ExplicitEditorStates(editorStates) =>
          doEditorStateTest(myFixture, editorStates)

        case TestData.Generated(contextCode, codeToType) =>
          checkIndentAfterTypingCode(contextCode, codeToType, myFixture)
      }
    }
  }
}
