package org.jetbrains.plugins.scala.semantic

import dotty.tools.dotc
import dotty.tools.dotc.core.*
import dotty.tools.dotc.core.Contexts.*
import dotty.tools.dotc.decompiler.PartialTASTYDecompiler
import dotty.tools.dotc.quoted.MacroExpansion
import dotty.tools.dotc.reporting.*
import dotty.tools.io.{AbstractFile, VirtualFile}

import java.io.File
import scala.quoted.runtime.impl.QuotesImpl
import scala.quoted.runtime.impl.printers.SyntaxHighlight

/**
 * @see scala.tasty.inspector.TastyInspector
 * @see dotty.tools.dotc.decompiler.Main
 * @see dotty.tools.dotc.decompiler.IDEDecompilerDriver
 */
class Decompiler(classpath: Seq[String]) extends dotc.Driver {
  private val myInitCtx: Context = {
    val rootCtx = initCtx.fresh.addMode(Mode.Interactive | Mode.ReadPositions)
    rootCtx.setSetting(rootCtx.settings.YretainTrees, true)
    rootCtx.setSetting(rootCtx.settings.fromTasty, true)
    rootCtx.setSetting(rootCtx.settings.classpath, classpath.mkString(File.pathSeparator))
    val ctx = setup(Array("dummy.scala"), rootCtx).get._2
    ctx.initialize()(using ctx)
    ctx
  }

  private val decompiler = new PartialTASTYDecompiler()

  def decompile(fileName: String, contents: Array[Byte]): String =
    run(new VirtualFile(fileName, contents))

  def run(tastyFile: AbstractFile): String = {
    val reporter = new StoreReporter(null) with HideNonSensicalMessages
    val run = decompiler.newRun(using myInitCtx.fresh.setReporter(reporter))
    inContext(run.runContext) {
      run.compile(List(tastyFile))
      run.printSummary()
      val unit = ctx.run.nn.units.head
      val quotes = QuotesImpl()(using MacroExpansion.context(unit.tpdTree))
      val text = SourceCode.showTree(using quotes)(unit.tpdTree.asInstanceOf[quotes.reflect.Tree])(SyntaxHighlight.plain, fullNames = true)
      reporter.removeBufferedMessages.foreach(message => System.err.println(message))
      text
    }
  }
}
