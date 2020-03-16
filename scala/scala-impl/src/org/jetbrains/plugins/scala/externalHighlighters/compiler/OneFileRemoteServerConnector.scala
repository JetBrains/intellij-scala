package org.jetbrains.plugins.scala.externalHighlighters.compiler

import java.io.File

import com.intellij.openapi.module.Module
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.jetbrains.jps.incremental.scala.remote.CommandIds
import org.jetbrains.plugins.scala.compiler.{CompilerEvent, RemoteServerConnectorBase, RemoteServerRunner}
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.project.VirtualFileExt
import org.jetbrains.plugins.scala.tasty.{Location, TastyReader}
import org.jetbrains.plugins.scala.tasty
import org.jetbrains.plugins.scala.extensions.inReadAction

private[compiler] class OneFileRemoteServerConnector(
  module: Module,
  sourceFile: VirtualFile,
) extends RemoteServerConnectorBase(
  module = module,
  filesToCompile = Some(Seq(sourceFile.toFile)),
  outputDir = FileUtil.createTempDirectory("compilation-tmp-output", "", true)
) {

  override protected def additionalScalaParameters: Seq[String] =
    if (module.hasScala3)
      Seq.empty
    else
      Seq(s"-Ystop-after:patmat")

  def compile(): Unit = {
    val project = module.getProject
    val client = new CompilationClient(project)
    try {
      new RemoteServerRunner(project).buildProcess(CommandIds.Compile, argumentsRaw, client).runSync()
      if (module.hasScala3)
        for {
          psiFile <- Option(inReadAction(PsiManager.getInstance(project).findFile(sourceFile)))
          Location(outputDirectory, className) <- tasty.compiledLocationOf(psiFile)
          classpath = outputDir.getCanonicalPath + File.pathSeparator + outputDirectory
          tastyFile <- TastyReader.read(classpath, className)
          event = CompilerEvent.CacheTastyFile(client.compilationId, sourceFile.toFile, tastyFile)
        } client.sendEvent(event)
    } finally {
      FileUtil.delete(outputDir)
    }
  }
}
