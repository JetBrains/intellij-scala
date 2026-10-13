package org.jetbrains.plugins.scala.compiler.highlighting.services.core

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents
import org.jetbrains.plugins.scala.compiler.highlighting.services.core.CompilationStateTestBase.CompilerHighlightingCoreTest
import org.jetbrains.plugins.scala.compiler.highlighting.services.core.Planner.*
import org.jetbrains.plugins.scala.compiler.highlighting.services.core.{CompilationState, Planner}
import org.jetbrains.plugins.scala.compiler.highlighting.services.requests.HighlightingRequest
import org.junit.Assert.assertNotEquals
import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue, fail}
import org.junit.jupiter.api.{Tag, Test}

/**
 * The combination rules, over a state built by hand.
 *
 * Needs no project: the planner is told whether a file is a worksheet and what its target is
 */
@Tag(CompilerHighlightingCoreTest)
class CompilationPlannerTest extends CompilationStateTestBase:

  private val app = module("app")
  private val domain = module("domain")

  private val controller = app / "Controller.scala"
  private val view = app / "View.scala"
  private val sheet = app / "Sheet.sc"

  private val service = domain / "Service.scala"


  private def built: CompilationState =
    CompilationState.empty.compilationSucceeded(Set(controller, view, service))
    
  private def names(requests: Iterable[HighlightingRequest]):Set[String] = names(requests.map(_.file).toSet)


  private def ask(file: VirtualFile, isWorksheet: Boolean = false): PlanInput =
    PlanInput(HighlightingRequest(TriggerPhaseEvents.newRequestId(), file, "test"), targetOf(file), isWorksheet)

  extension(state: CompilationState)
    private def planOf(inputs: PlanInput*): Plan = Planner().plan(state, inputs)

  private def scopeOf(work: PlannedWork): Set[VirtualFile] = work.kind match
    case WorkKind.Incremental(scope) => scope
    case other => fail(s"expected a build, got $other")

  @Test
  def answersFromTheStoreWhenNothingHasChanged(): Unit =
    val plan = built
      .planOf(ask(controller), ask(view))

    assertEquals(names(controller, view), names(plan.serveRecorded))
    assertTrue(plan.work.isEmpty, "nothing needs compiling")

  @Test
  def foldsEveryFileThatNeedsABuildIntoOne(): Unit =
    val plan = built.edit(controller)
      .planOf(ask(view), ask(service))

    assertEquals(1, plan.work.size)
    assertEquals(names(controller, view, service), names(scopeOf(plan.work.head)))

  /** The scope carries what is unbuilt; only the files somebody asked about are answered by it. */
  @Test
  def coversOnlyTheFilesThatWereAskedAbout(): Unit =
    val plan = built.edit(controller)
      .planOf(ask(view), ask(service))

    assertEquals(names(view, service), names(plan.work.head.covers.map(_.request)))

  @Test
  def foldsADocumentAnswerIntoTheBuild(): Unit =
    val state = built.edit(controller)
    // On its own `controller` would be document compiled, since it is the only unbuilt file. Asking about
    // another module at the same time makes that answer stale, and the build already covers it.
    val plan = state.planOf(ask(controller), ask(service))

    assertEquals(1, plan.work.size)
    assertEquals(names(controller, service), names(plan.work.head.covers.map(_.request)))
    assertTrue(scopeOf(plan.work.head).equals(Set(controller, service)))

  @Test
  def keepsADocumentAnswerWhenItIsTheOnlyOne(): Unit =
    val plan = built.edit(controller).planOf(ask(controller))

    assertEquals(List(WorkKind.Document), plan.work.map(_.kind))
    assertEquals(names(controller), names(plan.work.head.covers.map(_.request)))

  @Test
  def keepsAWorksheetSeparateFromTheBuild(): Unit =
    val plan = built.edit(controller)
      .planOf(ask(service), ask(sheet, isWorksheet = true))

    assertEquals(2, plan.work.size)
    val (worksheet, incremental) = plan.work.partition {
      _.kind match
        case WorkKind.Worksheet(_) => true
        case _ => false
    }
    assertNotEquals(worksheet.head, incremental.head)


  @Test
  def doNotAnswerAWorksheetFromTheStoreWhenItsTargetIsCurrent(): Unit =
    val plan = built.planOf(ask(sheet, isWorksheet = true))

    assertNotEquals(names(sheet), names(plan.serveRecorded))
    plan.work.head.kind match
      case WorkKind.Worksheet(_) => ()
      case other => fail(s"expected a worksheet, got $other")
    assertFalse(plan.work.isEmpty)
  /** Evaluating a worksheet is the only thing that produces its highlightings. */
  @Test
  def neverAnswersAWorksheetFromTheStore(): Unit =
    val edited = built.edit(controller)
    val state = edited.compilationFailed(Set(controller, sheet), covering = Set(controller, sheet))
    val plan = state.planOf(ask(sheet, isWorksheet = true))

    assertTrue(plan.serveRecorded.isEmpty, "a recorded worksheet is still evaluated")
    assertEquals(List(WorkKind.Worksheet(needsModuleBuild = true)), plan.work.map(_.kind))

  @Test
  def twoWorksheetsAreTwoPiecesOfWork(): Unit =
    val other = domain / "Other.sc"
    val plan = built.edit(controller)
      .planOf(ask(sheet, isWorksheet = true), ask(other, isWorksheet = true))

    assertEquals(2, plan.work.size)
    assertNotEquals(plan.work.head, plan.work.toSeq(1))
    assertEquals(2, plan.work.map(_.kind).collect({case WorkKind.Worksheet(_) => true}).size)
    assertEquals(names(sheet, other), names(plan.work.flatMap(_.covers).map(_.request)))

