package org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.ui.UiInterceptors
import org.jetbrains.plugins.scala.compiler.ScalaCompilerTestBase
import org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.ShowScalaCompilerTreeActionTestBase.WaitForCompileServerTimeout
import org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.ui.{CompilerTreesDialog, TreeDisplayOptions}
import org.jetbrains.plugins.scala.extensions.invokeAndWait
import org.jetbrains.plugins.scala.ui.AwaitTestUtils
import org.jetbrains.plugins.scala.{ScalaVersion, SlowTests}
import org.junit.ComparisonFailure
import org.junit.experimental.categories.Category

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Await, Future, Promise}

@Category(Array(classOf[SlowTests]))
abstract class ShowScalaCompilerTreeActionTestBase extends ScalaCompilerTestBase {
  override protected def useCompileServer: Boolean = true

  override protected def compileServerShutdownTimeout: FiniteDuration = WaitForCompileServerTimeout

  protected def testCompilerPhasesAndTreesAreParsedAndDisplayed(
    fileName: String,
    fileText: String,
    expectedPhasesWithTreePlaceholders: Seq[PhaseWithTreeText]
  ): Unit = {
    val treeDisplayOptions = TreeDisplayOptions(
      showEmptyPhases = true,
      showTasty = true,
      showUncapturedMessages = true
    )
    testCompilerPhasesAndTreesAreParsedAndDisplayed(fileName, fileText, treeDisplayOptions, expectedPhasesWithTreePlaceholders)
  }

  protected def testCompilerPhasesAndTreesAreParsedAndDisplayed(
    fileName: String,
    fileText: String,
    treeDisplayOptions: TreeDisplayOptions,
    expectedPhasesWithTreePlaceholders: Seq[PhaseWithTreeText]
  ): Unit = {
    val vFile = addFileToProjectSources(fileName, fileText)

    val psiFile = PsiManager.getInstance(getProject).findFile(vFile)
    val dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PSI_FILE, psiFile)
      .build()

    // Register a hook to collect the phases from the dialog once it's created
    val collectedPhasesFuture = registerDialogUiInterceptorAndGetPhasesFuture(treeDisplayOptions)

    // Execute the action - it will start the phases generation in the background, populating the dialog with data
    val action = new ShowScalaCompilerTreeAction
    action.actionPerformed(TestActionEvent.createTestEvent(dataContext))

    val collectedPhases = AwaitTestUtils.waitFutureDispatchingAllEdtEvents(collectedPhasesFuture, WaitForCompileServerTimeout)
    val actualPhasesWithTreePlaceholders = replaceActualTreesWithPlaceholders(collectedPhases)
    if (expectedPhasesWithTreePlaceholders != actualPhasesWithTreePlaceholders) {
      throw new ComparisonFailure(
        "Compiler trees",
        buildTextForTests(expectedPhasesWithTreePlaceholders),
        buildTextForTests(actualPhasesWithTreePlaceholders),
      )
    }
  }

  private var UniqueTrees = 0
  private val TreePlaceholderGenerator = Map[String, String]().withDefault { _ =>
    UniqueTrees += 1
    s"Tree placeholder $UniqueTrees"
  }

  private def registerDialogUiInterceptorAndGetPhasesFuture(
    treeDisplayOptions: TreeDisplayOptions
  ): Future[Seq[PhaseWithTreeText]] = {
    val promise = Promise[Seq[PhaseWithTreeText]]()

    UiInterceptors.register(new UiInterceptors.UiInterceptor[CompilerTreesDialog](classOf[CompilerTreesDialog]) {
      override protected def doIntercept(dialog: CompilerTreesDialog): Unit = {
        // Ensure the dialog and editor are disposed to avoid leaking UI between tests
        Disposer.register(getTestRootDisposable, dialog.getDisposable)

        dialog.setDisplayOptionsForTests(treeDisplayOptions)

        ApplicationManager.getApplication.executeOnPooledThread(() => {
          // Wait until the dialog is filled with data
          AwaitTestUtils.waitForConditionOrFail(
            WaitForCompileServerTimeout,
            s"Compiler trees collection did not finish"
          ) { () =>
            dialog.isCollectionFinishedForTests
          }

          val phases: Seq[PhaseWithTreeText] = invokeAndWait {
            dialog.phasesFromModelForTests
          }
          promise.trySuccess(phases)
        })
      }
    })

    promise.future
  }

  /**
   * Replaces the actual content of the trees with placeholders.
   *
   * Testing of exact trees might be too much because:
   *   - it can contain file-system dependent annotations with source file
   *   - it can contain a lot of code
   *
   * So, instead, we are testing just the fact that the trees are unique and replace the actual content with the placeholders
   */
  private def replaceActualTreesWithPlaceholders(phasesWithTrees: Seq[PhaseWithTreeText]): Seq[PhaseWithTreeText] = {
    phasesWithTrees.map(replaceActualTreeWithPlaceholder)
  }

  private def replaceActualTreeWithPlaceholder(phaseWithTree: PhaseWithTreeText): PhaseWithTreeText = {
    val treePlaceholder = if (phaseWithTree.phaseText.nonEmpty) TreePlaceholderGenerator(phaseWithTree.phaseText) else phaseWithTree.phaseText
    PhaseWithTreeText(phaseWithTree.phaseName, treePlaceholder)
  }

  private def buildTextForTests(trees: Seq[PhaseWithTreeText]): String =
    trees
      .map(pt => {
        s"""PhaseWithTreeText("${pt.phaseName}", "${pt.phaseText}")"""
      })
      .mkString("Seq(\n  ", ",\n  ", "\n)")

  protected val CommonScala2AndScala3FileText =
    """package org.example
      |
      |class MyClass {
      |  implicit val s: String = ???
      |  def foo() = {
      |    implicitly[String]
      |
      |    //Intentionally producing some warning and to complete parsing
      |    useDeprecatedMethod()
      |
      |    sealed trait T
      |    final case class C1() extends T
      |    final case class C2() extends T
      |    (??? : T) match {
      |      case C1() => ???
      |    }
      |  }
      |
      |  @deprecated("This method is deprecated", "1.0")
      |  def useDeprecatedMethod(): Unit = {
      |    println("Deprecated method called!")
      |  }
      |}
      |""".stripMargin

  protected val CommonScala2AndScala3FileText_EmptyPackage =
    """class MyClass2""".stripMargin.trim
}

object ShowScalaCompilerTreeActionTestBase {
  val WaitForCompileServerTimeout: FiniteDuration = 30.seconds
}

class ShowScalaCompilerTreeActionTest_210 extends ShowScalaCompilerTreeActionTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_10

  override protected def reuseCompileServerProcessBetweenTests: Boolean = false

  def testParsePhasesAndTreesFromCompilerOutput(): Unit = {
    testCompilerPhasesAndTreesAreParsedAndDisplayed(
      s"MyClass.scala",
      CommonScala2AndScala3FileText,
      Seq(
        PhaseWithTreeText("parser", "Tree placeholder 1"),
        PhaseWithTreeText("namer", ""),
        PhaseWithTreeText("packageobjects", ""),
        PhaseWithTreeText("typer", "Tree placeholder 2"),
        PhaseWithTreeText("patmat", "Tree placeholder 3"),
        PhaseWithTreeText("superaccessors", ""),
        PhaseWithTreeText("extmethods", ""),
        PhaseWithTreeText("pickler", ""),
        PhaseWithTreeText("refchecks", "Tree placeholder 4"),
        PhaseWithTreeText("uncurry", "Tree placeholder 5"),
        PhaseWithTreeText("tailcalls", ""),
        PhaseWithTreeText("specialize", ""),
        PhaseWithTreeText("explicitouter", "Tree placeholder 6"),
        PhaseWithTreeText("erasure", "Tree placeholder 7"),
        PhaseWithTreeText("posterasure", ""),
        PhaseWithTreeText("lazyvals", ""),
        PhaseWithTreeText("lambdalift", "Tree placeholder 8"),
        PhaseWithTreeText("constructors", "Tree placeholder 9"),
        PhaseWithTreeText("flatten", "Tree placeholder 10"),
        PhaseWithTreeText("mixin", "Tree placeholder 11"),
        PhaseWithTreeText("cleanup", ""),
        PhaseWithTreeText("icode", ""),
        PhaseWithTreeText("jvm", ""),
        PhaseWithTreeText("xsbt-analyzer", "")
      )
    )
  }

  def testParsePhasesAndTreesFromCompilerOutput_RecogniseClassWithEmptyPackage(): Unit = {
    testCompilerPhasesAndTreesAreParsedAndDisplayed(
      s"MyClass2.scala",
      CommonScala2AndScala3FileText_EmptyPackage,
      Seq(
        PhaseWithTreeText("parser", "Tree placeholder 1"),
        PhaseWithTreeText("namer", ""),
        PhaseWithTreeText("packageobjects", ""),
        PhaseWithTreeText("typer", "Tree placeholder 2"),
        PhaseWithTreeText("patmat", ""),
        PhaseWithTreeText("superaccessors", ""),
        PhaseWithTreeText("extmethods", ""),
        PhaseWithTreeText("pickler", ""),
        PhaseWithTreeText("refchecks", ""),
        PhaseWithTreeText("uncurry", "Tree placeholder 3"),
        PhaseWithTreeText("tailcalls", ""),
        PhaseWithTreeText("specialize", ""),
        PhaseWithTreeText("explicitouter", ""),
        PhaseWithTreeText("erasure", ""),
        PhaseWithTreeText("posterasure", ""),
        PhaseWithTreeText("lazyvals", ""),
        PhaseWithTreeText("lambdalift", ""),
        PhaseWithTreeText("constructors", ""),
        PhaseWithTreeText("flatten", ""),
        PhaseWithTreeText("mixin", ""),
        PhaseWithTreeText("cleanup", ""),
        PhaseWithTreeText("icode", ""),
        PhaseWithTreeText("jvm", ""),
        PhaseWithTreeText("xsbt-analyzer", "")
      )
    )
  }
}

class ShowScalaCompilerTreeActionTest_211 extends ShowScalaCompilerTreeActionTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_11

  def testParsePhasesAndTreesFromCompilerOutput(): Unit = {
    testCompilerPhasesAndTreesAreParsedAndDisplayed(
      s"MyClass.scala",
      CommonScala2AndScala3FileText,
      Seq(
        PhaseWithTreeText("parser", "Tree placeholder 1"),
        PhaseWithTreeText("namer", ""),
        PhaseWithTreeText("packageobjects", ""),
        PhaseWithTreeText("typer", "Tree placeholder 2"),
        PhaseWithTreeText("patmat", "Tree placeholder 3"),
        PhaseWithTreeText("superaccessors", ""),
        PhaseWithTreeText("extmethods", ""),
        PhaseWithTreeText("pickler", ""),
        PhaseWithTreeText("refchecks", "Tree placeholder 4"),
        PhaseWithTreeText("uncurry", "Tree placeholder 5"),
        PhaseWithTreeText("tailcalls", ""),
        PhaseWithTreeText("specialize", ""),
        PhaseWithTreeText("explicitouter", "Tree placeholder 6"),
        PhaseWithTreeText("erasure", "Tree placeholder 7"),
        PhaseWithTreeText("posterasure", ""),
        PhaseWithTreeText("lazyvals", ""),
        PhaseWithTreeText("lambdalift", "Tree placeholder 8"),
        PhaseWithTreeText("constructors", "Tree placeholder 9"),
        PhaseWithTreeText("flatten", "Tree placeholder 10"),
        PhaseWithTreeText("mixin", "Tree placeholder 11"),
        PhaseWithTreeText("cleanup", ""),
        PhaseWithTreeText("delambdafy", ""),
        PhaseWithTreeText("icode", ""),
        PhaseWithTreeText("jvm", ""),
        PhaseWithTreeText("xsbt-analyzer", ""),
      )
    )
  }

  def testParsePhasesAndTreesFromCompilerOutput_RecogniseClassWithEmptyPackage(): Unit = {
    testCompilerPhasesAndTreesAreParsedAndDisplayed(
      s"MyClass2.scala",
      CommonScala2AndScala3FileText_EmptyPackage,
      Seq(
        PhaseWithTreeText("parser", "Tree placeholder 1"),
        PhaseWithTreeText("namer", ""),
        PhaseWithTreeText("packageobjects", ""),
        PhaseWithTreeText("typer", "Tree placeholder 2"),
        PhaseWithTreeText("patmat", ""),
        PhaseWithTreeText("superaccessors", ""),
        PhaseWithTreeText("extmethods", ""),
        PhaseWithTreeText("pickler", ""),
        PhaseWithTreeText("refchecks", ""),
        PhaseWithTreeText("uncurry", "Tree placeholder 3"),
        PhaseWithTreeText("tailcalls", ""),
        PhaseWithTreeText("specialize", ""),
        PhaseWithTreeText("explicitouter", ""),
        PhaseWithTreeText("erasure", ""),
        PhaseWithTreeText("posterasure", ""),
        PhaseWithTreeText("lazyvals", ""),
        PhaseWithTreeText("lambdalift", ""),
        PhaseWithTreeText("constructors", ""),
        PhaseWithTreeText("flatten", ""),
        PhaseWithTreeText("mixin", ""),
        PhaseWithTreeText("cleanup", ""),
        PhaseWithTreeText("delambdafy", ""),
        PhaseWithTreeText("icode", ""),
        PhaseWithTreeText("jvm", ""),
        PhaseWithTreeText("xsbt-analyzer", ""),
      )
    )
  }
}

class ShowScalaCompilerTreeActionTest_212 extends ShowScalaCompilerTreeActionTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_12

  def testParsePhasesAndTreesFromCompilerOutput(): Unit = {
    testCompilerPhasesAndTreesAreParsedAndDisplayed(
      s"MyClass.scala",
      CommonScala2AndScala3FileText,
      Seq(
        PhaseWithTreeText("parser", "Tree placeholder 1"),
        PhaseWithTreeText("namer", ""),
        PhaseWithTreeText("packageobjects", ""),
        PhaseWithTreeText("typer", "Tree placeholder 2"),
        PhaseWithTreeText("patmat", "Tree placeholder 3"),
        PhaseWithTreeText("superaccessors", ""),
        PhaseWithTreeText("extmethods", ""),
        PhaseWithTreeText("pickler", ""),
        PhaseWithTreeText("refchecks", ""),
        PhaseWithTreeText("uncurry", "Tree placeholder 4"),
        PhaseWithTreeText("fields", "Tree placeholder 5"),
        PhaseWithTreeText("tailcalls", ""),
        PhaseWithTreeText("specialize", ""),
        PhaseWithTreeText("explicitouter", "Tree placeholder 6"),
        PhaseWithTreeText("erasure", "Tree placeholder 7"),
        PhaseWithTreeText("posterasure", ""),
        PhaseWithTreeText("lambdalift", "Tree placeholder 8"),
        PhaseWithTreeText("constructors", "Tree placeholder 9"),
        PhaseWithTreeText("flatten", "Tree placeholder 10"),
        PhaseWithTreeText("mixin", ""),
        PhaseWithTreeText("cleanup", "Tree placeholder 11"),
        PhaseWithTreeText("delambdafy", ""),
        PhaseWithTreeText("jvm", ""),
        PhaseWithTreeText("xsbt-analyzer", ""),
      )
    )
  }

  def testParsePhasesAndTreesFromCompilerOutput_RecogniseClassWithEmptyPackage(): Unit = {
    testCompilerPhasesAndTreesAreParsedAndDisplayed(
      s"MyClass2.scala",
      CommonScala2AndScala3FileText_EmptyPackage,
      Seq(
        PhaseWithTreeText("parser", "Tree placeholder 1"),
        PhaseWithTreeText("namer", ""),
        PhaseWithTreeText("packageobjects", ""),
        PhaseWithTreeText("typer", "Tree placeholder 2"),
        PhaseWithTreeText("patmat", ""),
        PhaseWithTreeText("superaccessors", ""),
        PhaseWithTreeText("extmethods", ""),
        PhaseWithTreeText("pickler", ""),
        PhaseWithTreeText("refchecks", ""),
        PhaseWithTreeText("uncurry", "Tree placeholder 3"),
        PhaseWithTreeText("fields", ""),
        PhaseWithTreeText("tailcalls", ""),
        PhaseWithTreeText("specialize", ""),
        PhaseWithTreeText("explicitouter", ""),
        PhaseWithTreeText("erasure", ""),
        PhaseWithTreeText("posterasure", ""),
        PhaseWithTreeText("lambdalift", ""),
        PhaseWithTreeText("constructors", ""),
        PhaseWithTreeText("flatten", ""),
        PhaseWithTreeText("mixin", ""),
        PhaseWithTreeText("cleanup", ""),
        PhaseWithTreeText("delambdafy", ""),
        PhaseWithTreeText("jvm", ""),
        PhaseWithTreeText("xsbt-analyzer", ""),
      )
    )
  }
}

class ShowScalaCompilerTreeActionTest_213 extends ShowScalaCompilerTreeActionTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13

  def testParsePhasesAndTreesFromCompilerOutput(): Unit = {
    testCompilerPhasesAndTreesAreParsedAndDisplayed(
      s"MyClass.scala",
      CommonScala2AndScala3FileText,
      Seq(
        PhaseWithTreeText("parser", "Tree placeholder 1"),
        PhaseWithTreeText("namer", ""),
        PhaseWithTreeText("packageobjects", ""),
        PhaseWithTreeText("typer", "Tree placeholder 2"),
        PhaseWithTreeText("superaccessors", ""),
        PhaseWithTreeText("extmethods", ""),
        PhaseWithTreeText("pickler", ""),
        PhaseWithTreeText("refchecks", ""),
        PhaseWithTreeText("patmat", "Tree placeholder 3"),
        PhaseWithTreeText("uncurry", "Tree placeholder 4"),
        PhaseWithTreeText("fields", "Tree placeholder 5"),
        PhaseWithTreeText("tailcalls", ""),
        PhaseWithTreeText("specialize", ""),
        PhaseWithTreeText("explicitouter", "Tree placeholder 6"),
        PhaseWithTreeText("erasure", "Tree placeholder 7"),
        PhaseWithTreeText("posterasure", ""),
        PhaseWithTreeText("lambdalift", "Tree placeholder 8"),
        PhaseWithTreeText("constructors", "Tree placeholder 9"),
        PhaseWithTreeText("flatten", "Tree placeholder 10"),
        PhaseWithTreeText("mixin", "Tree placeholder 11"),
        PhaseWithTreeText("cleanup", "Tree placeholder 12"),
        PhaseWithTreeText("delambdafy", ""),
        PhaseWithTreeText("jvm", ""),
        PhaseWithTreeText("xsbt-analyzer", ""),
      )
    )
  }

  def testParsePhasesAndTreesFromCompilerOutput_RecogniseClassWithEmptyPackage(): Unit = {
    testCompilerPhasesAndTreesAreParsedAndDisplayed(
      s"MyClass2.scala",
      CommonScala2AndScala3FileText_EmptyPackage,
      Seq(
        PhaseWithTreeText("parser", "Tree placeholder 1"),
        PhaseWithTreeText("namer", ""),
        PhaseWithTreeText("packageobjects", ""),
        PhaseWithTreeText("typer", "Tree placeholder 2"),
        PhaseWithTreeText("superaccessors", ""),
        PhaseWithTreeText("extmethods", ""),
        PhaseWithTreeText("pickler", ""),
        PhaseWithTreeText("refchecks", ""),
        PhaseWithTreeText("patmat", ""),
        PhaseWithTreeText("uncurry", "Tree placeholder 3"),
        PhaseWithTreeText("fields", ""),
        PhaseWithTreeText("tailcalls", ""),
        PhaseWithTreeText("specialize", ""),
        PhaseWithTreeText("explicitouter", ""),
        PhaseWithTreeText("erasure", ""),
        PhaseWithTreeText("posterasure", ""),
        PhaseWithTreeText("lambdalift", ""),
        PhaseWithTreeText("constructors", ""),
        PhaseWithTreeText("flatten", ""),
        PhaseWithTreeText("mixin", ""),
        PhaseWithTreeText("cleanup", ""),
        PhaseWithTreeText("delambdafy", ""),
        PhaseWithTreeText("jvm", ""),
        PhaseWithTreeText("xsbt-analyzer", ""),
      )
    )
  }
}

class ShowScalaCompilerTreeActionTest_Scala3 extends ShowScalaCompilerTreeActionTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  def testParsePhasesAndTreesFromCompilerOutput(): Unit = {
    testCompilerPhasesAndTreesAreParsedAndDisplayed(
      s"MyClass.scala",
      CommonScala2AndScala3FileText,
      Seq(
        PhaseWithTreeText("parser", "Tree placeholder 1"),
        PhaseWithTreeText("typer", "Tree placeholder 2"),
        PhaseWithTreeText("inlinedPositions", ""),
        PhaseWithTreeText("posttyper", "Tree placeholder 3"),
        PhaseWithTreeText("pickler", ""),
        PhaseWithTreeText("inlining", ""),
        PhaseWithTreeText("postInlining", ""),
        PhaseWithTreeText("staging", ""),
        PhaseWithTreeText("splicing", ""),
        PhaseWithTreeText("pickleQuotes", ""),
        PhaseWithTreeText("MegaPhase{crossVersionChecks, firstTransform, checkReentrant, elimPackagePrefixes, cookComments, checkLoopingImplicits, betaReduce, inlineVals, expandSAMs, elimRepeated, refchecks}", "Tree placeholder 4"),
        PhaseWithTreeText("MegaPhase{protectedAccessors, extmethods, uncacheGivenAliases, checkStatic, elimByName, hoistSuperArgs, forwardDepChecks, specializeApplyMethods, tryCatchPatterns, patternMatcher}", "Tree placeholder 5"),
        PhaseWithTreeText("preRecheck", ""),
        PhaseWithTreeText("cc", ""),
        PhaseWithTreeText("MegaPhase{elimOpaque, explicitOuter, explicitSelf, interpolators, dropBreaks}", "Tree placeholder 6"),
        PhaseWithTreeText("MegaPhase{pruneErasedDefs, uninitialized, inlinePatterns, vcInlineMethods, seqLiterals, intercepted, getters, specializeFunctions, specializeTuples, collectNullableFields, elimOuterSelect, resolveSuper, functionXXLForwarders, paramForwarding, genericTuples, letOverApply, arrayConstructors}", "Tree placeholder 7"),
        PhaseWithTreeText("erasure", "Tree placeholder 8"),
        PhaseWithTreeText("MegaPhase{elimErasedValueType, pureStats, vcElideAllocations, etaReduce, arrayApply, elimPolyFunction, tailrec, completeJavaEnums, mixin, lazyVals, memoize, nonLocalReturns, capturedVars}", "Tree placeholder 9"),
        PhaseWithTreeText("constructors", "Tree placeholder 10"),
        PhaseWithTreeText("MegaPhase{lambdaLift, elimStaticThis, countOuterAccesses}", "Tree placeholder 11"),
        PhaseWithTreeText("MegaPhase{dropOuterAccessors, checkNoSuperThis, flatten, transformWildcards, moveStatic, expandPrivate, restoreScopes, selectStatic, Collect entry points, repeatableAnnotations}", "Tree placeholder 12"),
        PhaseWithTreeText("genBCode", ""),
        PhaseWithTreeText("Tasty (class MyClass)", "Tree placeholder 13"),
        PhaseWithTreeText("== WARNING Output ==", "Tree placeholder 14")
      )
    )
  }

  def testParsePhasesAndTreesFromCompilerOutput_RecogniseClassWithEmptyPackage(): Unit = {
    testCompilerPhasesAndTreesAreParsedAndDisplayed(
      s"MyClass2.scala",
      CommonScala2AndScala3FileText_EmptyPackage,
      Seq(
        PhaseWithTreeText("parser", "Tree placeholder 1"),
        PhaseWithTreeText("typer", "Tree placeholder 2"),
        PhaseWithTreeText("inlinedPositions", ""),
        PhaseWithTreeText("posttyper", "Tree placeholder 3"),
        PhaseWithTreeText("pickler", ""),
        PhaseWithTreeText("inlining", ""),
        PhaseWithTreeText("postInlining", ""),
        PhaseWithTreeText("staging", ""),
        PhaseWithTreeText("splicing", ""),
        PhaseWithTreeText("pickleQuotes", ""),
        PhaseWithTreeText("MegaPhase{crossVersionChecks, firstTransform, checkReentrant, elimPackagePrefixes, cookComments, checkLoopingImplicits, betaReduce, inlineVals, expandSAMs, elimRepeated, refchecks}", ""),
        PhaseWithTreeText("MegaPhase{protectedAccessors, extmethods, uncacheGivenAliases, checkStatic, elimByName, hoistSuperArgs, forwardDepChecks, specializeApplyMethods, tryCatchPatterns, patternMatcher}", ""),
        PhaseWithTreeText("preRecheck", ""),
        PhaseWithTreeText("cc", ""),
        PhaseWithTreeText("MegaPhase{elimOpaque, explicitOuter, explicitSelf, interpolators, dropBreaks}", ""),
        PhaseWithTreeText("MegaPhase{pruneErasedDefs, uninitialized, inlinePatterns, vcInlineMethods, seqLiterals, intercepted, getters, specializeFunctions, specializeTuples, collectNullableFields, elimOuterSelect, resolveSuper, functionXXLForwarders, paramForwarding, genericTuples, letOverApply, arrayConstructors}", ""),
        PhaseWithTreeText("erasure", ""),
        PhaseWithTreeText("MegaPhase{elimErasedValueType, pureStats, vcElideAllocations, etaReduce, arrayApply, elimPolyFunction, tailrec, completeJavaEnums, mixin, lazyVals, memoize, nonLocalReturns, capturedVars}", "Tree placeholder 4"),
        PhaseWithTreeText("constructors", "Tree placeholder 5"),
        PhaseWithTreeText("MegaPhase{lambdaLift, elimStaticThis, countOuterAccesses}", ""),
        PhaseWithTreeText("MegaPhase{dropOuterAccessors, checkNoSuperThis, flatten, transformWildcards, moveStatic, expandPrivate, restoreScopes, selectStatic, Collect entry points, repeatableAnnotations}", ""),
        PhaseWithTreeText("genBCode", ""),
        PhaseWithTreeText("Tasty (class MyClass2)", "Tree placeholder 6"),
      )
    )
  }

  def testParsePhasesAndTreesFromCompilerOutput_RecogniseClassWithEmptyPackage_WithEmptyDisplayOptions(): Unit = {
    testCompilerPhasesAndTreesAreParsedAndDisplayed(
      s"MyClass2.scala",
      CommonScala2AndScala3FileText_EmptyPackage,
      treeDisplayOptions = TreeDisplayOptions(
        showEmptyPhases = false,
        showTasty = false,
        showUncapturedMessages = false
      ),
      Seq(
        PhaseWithTreeText("parser", "Tree placeholder 1"),
        PhaseWithTreeText("typer", "Tree placeholder 2"),
        PhaseWithTreeText("posttyper", "Tree placeholder 3"),
        PhaseWithTreeText("MegaPhase{elimErasedValueType, pureStats, vcElideAllocations, etaReduce, arrayApply, elimPolyFunction, tailrec, completeJavaEnums, mixin, lazyVals, memoize, nonLocalReturns, capturedVars}", "Tree placeholder 4"),
        PhaseWithTreeText("constructors", "Tree placeholder 5"),
      )
    )
  }
}
