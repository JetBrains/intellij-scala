package org.jetbrains.plugins.scala.compiler.highlighting.compilation

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.compiler.highlighting.compilation.CompilationStateTestBase.CompilerHighlightingTests
import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue}
import org.junit.jupiter.api.{Nested, Tag, Test}
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@Tag(CompilerHighlightingTests)
class CompilationScenarioTest extends CompilationStateTestBase {

  private val app = module("app")
  private val domain = module("domain")
  private val util = module("util")

  private val controller = file("Controller.scala", in = app)
  private val view = file("View.scala", in = app)
  private val service = file("Service.scala", in = domain)
  private val strings = file("Strings.scala", in = util)

  private val everything = Set(controller, view, service, strings)

  private def freshProject = CompilationState.empty

  private def builtProject = freshProject.compilationSucceeded(everything)

  @Nested
  class OpeningFiles {

    @Test
    def inAProjectThatWasNeverBuiltCompilesTheFile(): Unit =
      freshProject.on(controller).assertIncremental(controller)

    @Test
    def afterTheProjectWasBuiltShowsStoredHighlightings(): Unit =
      builtProject.on(controller).assertUpToDate

    @Test
    def costsNothingForAnyFileInTheProject(): Unit =
      everything.foreach(builtProject.on(_).assertUpToDate)

    @Test
    def outsideEveryModuleCompilesTheFile(): Unit = {
      val scratch = fileOutsideModules("Scratch.scala")

      builtProject.on(scratch).assertIncremental(scratch)
    }
  }

  @Nested
  class Editing {

    @Test
    def theOpenFileUsesTheDocumentCompiler(): Unit =
      builtProject.edit(controller).on(controller).assertDocument

    @Test
    def continuouslyKeepsUsingTheDocumentCompiler(): Unit =
      (1 to 20).foldLeft(builtProject)((state, _) => state.edit(controller))
        .on(controller).assertDocument

    @Test
    def thenSwitchingToAnotherFileCompilesBoth(): Unit =
      builtProject.edit(controller).on(view).assertIncremental(controller, view)

    @Test
    def aSecondFileEndsTheDocumentCompilerForBoth(): Unit =
      builtProject.edit(controller)
        .edit(view)
        .on(controller).assertIncremental(controller, view)
        .on(view).assertIncremental(controller, view)

    @Test
    def thenCompilingRestoresTheDocumentCompiler(): Unit =
      builtProject.edit(controller).compilationSucceeded(controller, view)
        .on(view).assertUpToDate
        .edit(view).on(view).assertDocument

    @Test
    def thenClosingTheEditorKeepsTheFileOwedACompilation(): Unit =
      builtProject.edit(controller)
        .on(view).assertIncremental(controller, view)
        .on(controller).assertDocument

    @Test
    def thenDeletingTheFileKeepsItOutOfLaterScopes(): Unit = {
      builtProject.edit(view)
      .andThen { delete(view) } 
      .on(controller).assertIncremental(controller)
    }
  }

  @Nested
  class EditingWhileACompilationRuns {

    @Test
    def keepsTheEditTheCompilationCouldNotSee(): Unit = {
      compileWhileEditing(controller)
      .andThen{ assertEquals(names(controller), names(state.modifiedFiles))}
      .on(controller).assertDocument
    }

    @Test
    def doesNotCheckASiblingAgainstAnOutputThatMissedTheEdit(): Unit =
      compileWhileEditing(controller).edit(view).on(view).assertIncremental(controller, view)

    @Test
    def inAnotherModuleDoesNotHoldBackWhatWasBuilt(): Unit = {
      val dispatched = builtProject.edit(controller)
      val token = dispatched.startCompilation(controller)
      dispatched.edit(service)
        .succeeded(token, targetsOf(controller), Set(controller))
        .andThen { assertEquals(names(service), names(state.modifiedFiles))}
    }
  }

  /**
   *  With the current system this scenario cannot happen.
   *  We test it to verify that the state logic is correct
   */
  @Nested
  class WhenAnIdeBuildIsRunning {

    @Test
    def itsReportDoesNotSpeakForACompilationThatFinishedDuringIt(): Unit = {
      val buildStarted = builtProject.edit(service)
      val buildToken = buildStarted.startCompilation()

      val duringTheBuild = buildStarted.edit(controller)
      val ours = duringTheBuild
        .succeeded(duringTheBuild.startCompilation(controller), targetsOf(controller), Set(controller))

      val buildFinished = ours.succeeded(buildToken, targetsOf(service), Set(service))

      assertEquals(ours.lastBuildEpoch, buildFinished.lastBuildEpoch)
      buildFinished.on(controller).assertUpToDate
      buildFinished.on(service).assertIncremental(service)
    }
  }

  @Nested
  class WhenOneModuleFails {

    @Test
    def itsFilesAreLeftUnbuildable(): Unit =
      builtProject.edit(controller, view)
        .compilationFailed(controller, view)
        .on(service).assertIncremental(service)

    @Test
    def theFilesItReachedAreAnsweredFromTheStore(): Unit =
      builtProject
        .edit(service)
        .compilationFailed(Set(service, controller), covering = Set(service, controller, view))
        .on(controller).assertRecorded
        .on(view).assertRecorded
        .on(service).assertRecorded
        .edit(service)
        .on(service).assertDocument

    @Test
    def theFilesItNeverReachedAreCompiledAgainButOnlyOnce(): Unit =
      builtProject.edit(service)
        .on(service).assertDocument
        .compilationFailed(Set(service, controller), covering = Set(service))
        .on(controller).assertIncremental(controller)
        .on(service).assertRecorded
        // lets assume we open controller which module depends on service's module
        .compilationFailed(scope = Set(controller), covering = Set(service))
        .on(controller).assertUpToDate
        .edit(service)
        .on(controller).assertIncremental(controller, service)

    @Test
    def serveRecordedForAFailedFileWithNoChanges(): Unit =
      builtProject.edit(service)
        .on(service).assertDocument
        .compilationFailed(Set(controller, service))
        .on(service).assertRecorded
        .edit(service)
        .on(service).assertDocument

    @Test
    def anyEditWithdrawsTheStoredAnswer(): Unit =
      builtProject.edit(service)
        .compilationFailed(Set(service, controller), covering = Set(service, view))
        .on(view).assertRecorded
        .edit(strings)
        .on(view).assertIncremental(view, strings)

    @Test
    def editingABrokenFileBringsItBackIntoLaterScopes(): Unit =
      builtProject.edit(service, controller)
        .compilationFailed(service, controller)
        .on(strings).assertIncremental(strings)
        .edit(service)
        .on(strings).assertIncremental(service, strings)

    @Test
    def editingTheOnlyBrokenFileUsesTheDocumentCompiler(): Unit =
      builtProject.edit(service)
        .compilationFailed(service)
        .on(controller).assertIncremental(controller)
        .edit(service)
        .on(service).assertDocument
        .on(controller).assertIncremental(service, controller)

    @Test
    def fixingABrokenFileBringsItsModuleBack(): Unit =
      builtProject.edit(service)
        .compilationFailed(service)
        .edit(service).compilationSucceeded(service)
        .on(service).assertUpToDate

    @Test
    def aSuccessElsewhereDoesNotSpeakForIt(): Unit =
      brokenWithASuccessElsewhere
        .on(strings).assertUpToDate
        .on(service).assertIncremental(service)
        .on(controller).assertIncremental(controller)

    @Test
    def theModuleThatBuildsRegainsTheDocumentCompiler(): Unit =
      brokenWithASuccessElsewhere
        .edit(strings)
        .on(strings).assertDocument

    private def brokenWithASuccessElsewhere: CompilationState =
      builtProject
        .edit(service, controller)
        .compilationFailed(service, controller, view)
        .compilationSucceeded(strings)
  }

  @Nested
  class WhenSeveralModulesFail {

    @Test
    def theyDoNotBlockEachOther(): Unit =
      builtProject.edit(service, controller, strings)
        .on(service).assertIncremental(service, controller, strings)
        .compilationFailed(Set(service, controller, strings), covering = Set(service))
        .on(service).assertRecorded
        .on(controller).assertIncremental(controller)
        .compilationFailed(controller)
        .on(controller).assertRecorded
        .on(strings).assertIncremental(strings)


    @Test
    def oneRecoversWhileTheOthersStayBroken(): Unit =
      builtProject.edit(service, controller, strings)
        .compilationFailed(Set(service, controller, strings), covering = Set(service))
        .compilationFailed(controller)
        .compilationSucceeded(strings)
        .on(strings).assertUpToDate
        .on(service).assertIncremental(service)
        .on(controller).assertIncremental(controller)
  }

  @Nested
  class Invalidation {

    @Test
    def withdrawsEveryTarget(): Unit =
      builtProject
        .edit(controller)
        .on(controller).assertDocument
        .invalidated
        .on(controller).assertIncremental(controller)
    
    @Test
    def thenEditingAFileDoesNotReachTheDocumentCompiler(): Unit =
      builtProject
        .invalidated
        .edit(controller)
        .on(controller).assertIncremental(controller)
  }

  @Nested
  class RelyingOnTheBuildOutput {

    @Test
    def onlyABuiltTargetsOutputCanBeReliedUpon(): Unit = {
      val failed = builtProject.edit(service)
        .compilationFailed(Set(service, controller), covering = Set(service, view))

      assertTrue(builtProject.decisionFor(controller).trustsBuildOutput)
      assertTrue(builtProject.edit(controller).decisionFor(controller).trustsBuildOutput)
      assertFalse(failed.decisionFor(view).trustsBuildOutput)
      assertFalse(builtProject.invalidated.decisionFor(controller).trustsBuildOutput)
    }
  }

  @Nested
  class ALargeProject {

    @ParameterizedTest(name = "{0} modules")
    @ValueSource(ints = Array(10, 200, 1000, 5000))
    def isUpToDateEverywhereAfterOneBuild(moduleCount: Int): Unit = {
      val (_, files) = largeProject(moduleCount)
      freshProject
        .compilationSucceeded(files.toSet)
        .onAll(files).assertUpToDate
    }

    @ParameterizedTest(name = "{0} modules")
    @ValueSource(ints = Array(1, 5, 20, 40))
    def isUnaffectedElsewhereWhenOneFileIsEdited(moduleCount: Int): Unit = {
      val (_, files) = largeProject(moduleCount)
      val edited = files.head
      freshProject.compilationSucceeded(files.toSet)
        .edit(edited)
        .on(edited).assertDocument
        .onEach(files.tail).assertIncremental(Set(file, edited))
    }

    @ParameterizedTest(name = "{0} modules")
    @ValueSource(ints = Array(2, 5, 20))
    def leavesEveryOtherModuleBehindWhenOneIsBuilt(moduleCount: Int): Unit = {
      val (modules, files) = largeProject(moduleCount)
      val (target, targetFiles) = modules.head
      val state = freshProject
        .compilationSucceeded(files.toSet)
        .editAll(targetFiles.toSet)
        .compilationSucceeded(targetFiles.toSet)

      assertEquals(Set(target), state.documentEnabledFor)
      state.onAll(targetFiles).assertUpToDate
      state.onEach(modules.tail.flatMap(_._2)).assertIncremental(Set(file))
    }

    @ParameterizedTest(name = "{0} modules")
    @ValueSource(ints = Array(2, 5, 20))
    def keepsCompilingTheHealthyModuleWhenTheRestAreBroken(moduleCount: Int): Unit = {
      val (modules, files) = largeProject(moduleCount)
      val broken = modules.dropRight(1).flatMap(_._2).toSet
      val healthy = modules.last._2.head

      freshProject.compilationSucceeded(files.toSet)
        .editAll(broken)
        .compilationFailed(broken)
        .on(healthy).assertIncremental(healthy)
        .on(broken.last).assertRecorded
        .compilationSucceeded(healthy)
        .on(healthy).assertUpToDate

    }

    @ParameterizedTest(name = "{0} modules")
    @ValueSource(ints = Array(1, 5, 20))
    def isUpToDateAgainAfterARebuildModuleByModule(moduleCount: Int): Unit = {
      val (modules, files) = largeProject(moduleCount)
      val edited = freshProject.compilationSucceeded(files.toSet).editAll(files.toSet)
      val rebuilt = modules.foldLeft(edited)((state, m) => state.compilationSucceeded(m._2.toSet))

      rebuilt.onAll(files).assertUpToDate
    }
  }

  private def compileWhileEditing(target: VirtualFile): CompilationState = {
    val dispatched = builtProject.edit(target)
    val token = dispatched.startCompilation(target)
    dispatched.edit(target).succeeded(token, targetsOf(target), Set(target))
  }

  private def largeProject(moduleCount: Int): (Seq[(ModuleKey, Seq[VirtualFile])], Seq[VirtualFile]) = {
    val modules = (1 to moduleCount).map { m =>
      val target = module(s"module-$m")
      target -> (1 to 3).map(f => file(s"Source$m-$f.scala", in = target))
    }
    (modules, modules.flatMap(_._2))
  }
}
