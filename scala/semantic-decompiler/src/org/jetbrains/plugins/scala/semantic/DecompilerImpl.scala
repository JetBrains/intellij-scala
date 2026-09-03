package org.jetbrains.plugins.scala.semantic

import dotty.tools.dotc
import dotty.tools.dotc.ast.Positioned
import dotty.tools.dotc.core.*
import dotty.tools.dotc.core.Comments.{ContextDoc, ContextDocstrings}
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
 *
 * Implements org.jetbrains.plugins.scala.semantic.Decompiler
 */
class DecompilerImpl(classpath: Array[String]) extends dotc.Driver {
  private val myInitCtx: Context = {
    val rootCtx = initCtx.fresh.addMode(Mode.Interactive | Mode.ReadPositions)
    rootCtx.setSetting(rootCtx.settings.fromTasty, true)
    rootCtx.setSetting(rootCtx.settings.classpath, classpath.mkString(File.pathSeparator))
    val ctx = setup(rootCtx)
    ctx.initialize()(using ctx)
    ctx
  }

  /**
   * @see dotty.tools.dotc.Driver.setup
   */
  private def setup(rootCtx: Context): Context = {
    val ictx = rootCtx.fresh
    val summary = command.distill(Array.empty, ictx.settings)(ictx.settingsState)(using ictx)
    ictx.setSettings(summary.sstate)
    MacroClassLoader.init(ictx)
    Positioned.init(using ictx)

    inContext(ictx) {
      if !ctx.settings.XdropComments.value || ctx.settings.XreadComments.value then
        ictx.setProperty(ContextDoc, new ContextDocstrings)
      fromTastySetup(List.empty)
    }
  }

  private val decompiler = new PartialTASTYDecompiler()

  // Implements.jetbrains.plugins.scala.semantic.Decompiler.decompile
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
