package org.jetbrains.plugins.scala.compiler.highlighting.services.core

import com.intellij.mock.MockModule
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.jps.incremental.scala.remote.SourceScope
import org.jetbrains.plugins.scala.compiler.highlighting.services.core
import org.jetbrains.plugins.scala.compiler.highlighting.services.core.CompilationDecision.{Document, Incremental, Recorded, UpToDate}
import org.jetbrains.plugins.scala.compiler.highlighting.services.core.{CompilationDecision, CompilationState, CompilationToken, ModuleKey}
import org.junit.jupiter.api.{AfterEach, Test}
import org.junit.jupiter.api.Assertions.{assertEquals, fail}

import scala.collection.mutable

/**
 * Modules, files and the transitions of [[CompilationState]], without a project.
 *
 * A `ModuleKey` is only ever a map key, and a `VirtualFile` only ever an identity, so a `MockModule` and a
 * `LightVirtualFile` are enough and no fixture is needed. [[targetOf]] takes the place of `ModuleKey.of`.
 */
trait CompilationStateTestBase:

  private val disposable: Disposable = Disposer.newDisposable("compilation state test modules")
  private val project = mutable.Map.empty[VirtualFile, ModuleKey]
  protected val empty: CompilationState = CompilationState.empty

  @AfterEach
  final def disposeModules(): Unit = Disposer.dispose(disposable)

  protected def module(name: String): ModuleKey =
    ModuleKey(new MockModule(disposable).setName(name), SourceScope.Production)

  protected def file(name: String, in: ModuleKey): VirtualFile = 
    val created = new LightVirtualFile(name)
    project(created) = in
    created
  
  extension(module: ModuleKey)
    def / (name: String): VirtualFile = file(name, module)
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
  
  extension (state: CompilationState)

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
      state.failed(state.startCompilation(scope.toSeq*), covering)

    /** Snapshots the state a compilation over `files` runs against, as the request path does. */
    def startCompilation(files: VirtualFile*): CompilationToken = state.tokenFor(files.toSet)

    def on(file: VirtualFile): AssertionType.Same = AssertionType.Same(Set(file), state)

    def onAll(files: Iterable[VirtualFile]): AssertionType.Same = AssertionType.Same(files.toSet, state)
    def onEach(files: Iterable[VirtualFile]): AssertionType.Different = AssertionType.Different(files.toSet, state)

    /** Continues the chain in a block that has this state as its context, named by [[state]]. */
    def andThen(body: CompilationState ?=> Unit): CompilationState = 
      body(using state)
      state
    
    def decisionFor(file: VirtualFile): CompilationDecision = state.decide(targetOf(file), file)

    private def assertDecision(expected: CompilationDecision, file: VirtualFile): Unit =
      assertEquals(expected, state.decisionFor(file), s"decision for ${file.getName}")
    
  enum AssertionType(val files: Set[VirtualFile], val state: CompilationState):
    case Same(f: Set[VirtualFile], s: CompilationState) extends AssertionType(f, s)
    case Different(f: Set[VirtualFile], s: CompilationState) extends AssertionType(f, s)

  extension (v: AssertionType)

    def assertUpToDate: CompilationState =
      v.files.foreach(v.state.assertDecision(UpToDate, _))
      v.state
    
    def assertDocument: CompilationState = 
      v.files.foreach(v.state.assertDecision(Document, _))
      v.state
    
    def assertRecorded: CompilationState = 
      v.files.foreach(v.state.assertDecision(Recorded, _))
      v.state
    
    def forEachFile: AssertionType.Different = AssertionType.Different(v.files, v.state)
    def forAllFiles: AssertionType.Different = AssertionType.Different(v.files, v.state)
  
  extension (v: AssertionType.Same)
    def assertIncremental(scope: VirtualFile*): CompilationState = AssertionType.Different(v.files, v.state)
      .assertIncremental(scope.toSet)

  extension (v: AssertionType.Different)
    def assertIncremental(scope: VirtualFile ?=> Iterable[VirtualFile]): CompilationState = {
      v._1.foreach(file =>
        v._2.decisionFor(file) match {
          case Incremental(actual) => assertEquals(names(scope(using file).toSet), names(actual.toSeq *), s"scope for ${file.getName}")
          case other => fail(s"expected an incremental compilation for ${file.getName}, got $other")
        })
      v._2
    }

  @Test
  def testTooling(): Unit =
    val m1 = module("test")
    val m2 = module("test2")
    val f1 = m1 / "f1"
    val f2 = m2 / "f2"
    // edit
    assertEquals(empty.modified(Set(f1, f2)), empty.edit(f1,f2))
    // start compilation
    assertEquals(empty.tokenFor(Set(f1, f2)), empty.startCompilation(f1, f2))
    // compilation succeeded
    assertEquals(empty.succeeded(empty.tokenFor(Set(f1, f2)), Set(m1,m2), Set(f1,f2)),
      empty.compilationSucceeded(f1,f2)
    )
    assertEquals(empty.succeeded(empty.tokenFor(Set(f1)), Set(m1), Set(f1,f2)),
      empty.compilationSucceeded(Set(f1), Set(f1,f2))
    )
    // compilation failed
    assertEquals(empty.failed(empty.tokenFor(Set(f1, f2)), Set(f1, f2)),
      empty.compilationFailed(f1, f2)
    )
    assertEquals(empty.failed(empty.tokenFor(Set(f1)), Set(f1, f2)),
      empty.compilationFailed(Set(f1), Set(f1, f2))
    )
    // decision
    assertEquals(empty.modified(Set(f1)).decide(Some(m1), f1), empty.edit(f1).decisionFor(f1))

object CompilationStateTestBase:
  final val CompilerHighlightingCoreTest = "org.jetbrains.plugins.scala.CompilerHighlightingTests"

