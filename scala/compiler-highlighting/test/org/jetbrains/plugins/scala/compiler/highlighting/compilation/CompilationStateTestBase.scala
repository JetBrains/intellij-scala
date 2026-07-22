package org.jetbrains.plugins.scala.compiler.highlighting.compilation

import com.intellij.mock.MockModule
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.jps.incremental.scala.remote.SourceScope
import org.jetbrains.plugins.scala.compiler.highlighting.compilation.CompilationDecision.{Document, Incremental, Recorded, UpToDate}
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.{assertEquals, fail}

import scala.collection.mutable

/**
 * Modules, files and the transitions of [[CompilationState]], without a project.
 *
 * A `ModuleKey` is only ever a map key, and a `VirtualFile` only ever an identity, so a `MockModule` and a
 * `LightVirtualFile` are enough and no fixture is needed. [[targetOf]] takes the place of `ModuleKey.of`.
 */
abstract class CompilationStateTestBase {

  private val disposable: Disposable = Disposer.newDisposable("compilation state test modules")
  private val project = mutable.Map.empty[VirtualFile, ModuleKey]

  @AfterEach
  final def disposeModules(): Unit = Disposer.dispose(disposable)

  protected def module(name: String): ModuleKey =
    ModuleKey(new MockModule(disposable).setName(name), SourceScope.Production)

  protected def file(name: String, in: ModuleKey): VirtualFile = {
    val created = new LightVirtualFile(name)
    project(created) = in
    created
  }

  protected def fileOutsideModules(name: String): VirtualFile = new LightVirtualFile(name)

  protected def delete(file: VirtualFile): Unit =
    file.asInstanceOf[LightVirtualFile].setValid(false)

  protected def targetOf(file: VirtualFile): Option[ModuleKey] = project.get(file)

  protected def targetsOf(files: VirtualFile*): Set[ModuleKey] = files.toSet.flatMap(targetOf)

  protected def names(files: VirtualFile*): Set[String] = files.toSet.map(_.getName)

  protected def names(files: Set[VirtualFile]): Set[String] = names(files.toSeq *)

  /** The state an [[andThen]] block runs against. */
  inline def state(using s: CompilationState): CompilationState = s

  inline def file(using f: VirtualFile): VirtualFile = f


  extension (state: CompilationState) {

    def edit(files: VirtualFile*): CompilationState = state.modified(files.toSet)

    def editAll(files: Set[VirtualFile]): CompilationState = state.modified(files)

    /** The scope must hold every source of the modules the compilation built. */
    def compilationSucceeded(scope: VirtualFile*): CompilationState = state.compilationSucceeded(scope.toSet)

    def compilationSucceeded(scope: Set[VirtualFile]): CompilationState =
      state.compilationSucceeded(scope, covering = scope)

    def compilationSucceeded(scope: Set[VirtualFile], covering: Set[VirtualFile]): CompilationState =
      state.succeeded(state.tokenFor(scope), targetsOf(scope.toSeq *), covering)

    def compilationFailed(scope: VirtualFile*): CompilationState = state.compilationFailed(scope.toSet)

    def compilationFailed(scope: Set[VirtualFile]): CompilationState =
      state.compilationFailed(scope, covering = scope)

    def compilationFailed(scope: Set[VirtualFile], covering: Set[VirtualFile]): CompilationState =
      state.failed(state.tokenFor(scope), covering)

    /** Snapshots the state a compilation over `files` runs against, as the request path does. */
    def startCompilation(files: VirtualFile*): CompilationToken = state.tokenFor(files.toSet)

    def on(file: VirtualFile): AssertionType.Same = AssertionType.Same(Set(file), state)

    def onAll(files: Iterable[VirtualFile]): AssertionType.Same = AssertionType.Same(files.toSet, state)
    def onEach(files: Iterable[VirtualFile]): AssertionType.Different = AssertionType.Different(files.toSet, state)

    /** Continues the chain in a block that has this state as its context, named by [[state]]. */
    def andThen(body: CompilationState ?=> Unit): CompilationState = {
      body(using state)
      state
    }

    def decisionFor(file: VirtualFile): CompilationDecision = state.decide(targetOf(file), file)

    private def assertDecision(expected: CompilationDecision, file: VirtualFile): Unit =
      assertEquals(expected, state.decisionFor(file), s"decision for ${file.getName}")
  }

  enum AssertionType(val files: Set[VirtualFile], val state: CompilationState):
    case Same(f: Set[VirtualFile], s: CompilationState) extends AssertionType(f, s)
    case Different(f: Set[VirtualFile], s: CompilationState) extends AssertionType(f, s)

  extension (v: AssertionType) {

    def assertUpToDate: CompilationState = {
      v.files.foreach(v.state.assertDecision(UpToDate, _))
      v.state
    }

    def assertDocument: CompilationState = {
      v.files.foreach(v.state.assertDecision(Document, _))
      v.state
    }

    def assertRecorded: CompilationState = {
      v.files.foreach(v.state.assertDecision(Recorded, _))
      v.state
    }
    def forEachFile: AssertionType.Different = AssertionType.Different(v.files, v.state)
    def forAllFiles: AssertionType.Different = AssertionType.Different(v.files, v.state)
  }
  extension (v: AssertionType.Same)
    def assertIncremental(scope: VirtualFile*): CompilationState = AssertionType.Different(v.files, v.state)
      .assertIncremental(scope.toSet)

  extension (v: AssertionType.Different)
    def assertIncremental(scope: VirtualFile ?=> (Iterable[VirtualFile])): CompilationState = {
      v._1.foreach(file =>
        v._2.decisionFor(file) match {
          case Incremental(actual) => assertEquals(names(scope(using file).toSet), names(actual.toSeq *), s"scope for ${file.getName}")
          case other => fail(s"expected an incremental compilation for ${file.getName}, got $other")
        })
      v._2
    }
}

object CompilationStateTestBase {
  
  final val CompilerHighlightingTests = "org.jetbrains.plugins.scala.CompilerHighlightingTests"
}
