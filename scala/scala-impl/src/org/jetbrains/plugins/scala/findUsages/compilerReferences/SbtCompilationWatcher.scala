package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.io.{BufferedReader, File, FileInputStream, InputStreamReader}
import java.nio.charset.StandardCharsets
import java.util.UUID

import com.intellij.openapi.compiler.CompilerPaths
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.findUsages.compilerReferences.SbtCompilationListener.ProjectIdentifier
import org.jetbrains.plugins.scala.findUsages.compilerReferences.SbtCompilationListener.ProjectIdentifier._
import org.jetbrains.plugins.scala.indices.protocol.IdeaIndicesJsonProtocol._
import org.jetbrains.plugins.scala.indices.protocol.sbt._
import org.jetbrains.plugins.scala.indices.protocol.sbt.Locking.FileLockingExt
import org.jetbrains.plugins.scala.project._
import spray.json._

import scala.util.Try

private class SbtCompilationWatcher(
  override val project:                       Project,
  protected override val currentCompilerMode: () => CompilerMode,
) extends CompilationWatcher[CompilerMode.SBT.type] {
  import SbtCompilationWatcher._

  private[this] val projectBase: String = project.getBasePath
  private[this] val moduleManager       = ModuleManager.getInstance(project)

  private[this] def parseCompilationInfo(infoFile: File): Try[SbtCompilationInfo] = {
    val result = Try {
      val br      = new BufferedReader(new InputStreamReader(new FileInputStream(infoFile), StandardCharsets.UTF_8))
      val builder = StringBuilder.newBuilder

      using(br) { in =>
        var line = in.readLine()
        while (line != null) {
          builder ++= line
          line = in.readLine()
        }
      }

      val contents = builder.result
      contents.parseJson.convertTo[SbtCompilationInfo]
    }

    infoFile.delete()
    result
  }

  private[this] def processCompilationInfo(sbtInfo: SbtCompilationInfo, isOffline: Boolean = false): Unit = {
    val module               = moduleManager.findModuleByName(sbtInfo.project)
    val isAppropriateVersion = module.scalaLanguageLevel.forall(_.version == sbtInfo.scalaVersion)

    // ignore cross compilation infos
    if (isAppropriateVersion) {
      eventPublisher.startIndexing(isCleanBuild = !sbtInfo.isIncremental)
      eventPublisher.processCompilationInfo(sbtInfo, isOffline)
      eventPublisher.finishIndexing(sbtInfo.startTimestamp)
    }
  }

  private[this] def processOfflineInfos(infoFiles: Seq[File]): Unit = {
    val parsedInfos = infoFiles.flatMap(parseCompilationInfo(_).toOption)

    if (parsedInfos.size != infoFiles.size) {
      logger.error(new RuntimeException("Failed to parse some compilation analysis files."))
      eventPublisher.onError()
    } else parsedInfos.sortBy(_.startTimestamp).foreach(processCompilationInfo(_, isOffline = true))
  }

  override def compilerMode: CompilerMode.SBT.type = CompilerMode.SBT

  private[this] def subscribeToSbtNotifications(): Unit = {
    val messageBus = project.getMessageBus
    val connection = messageBus.connect(project)

    // can be called from multiple threads in case of a parallel compilation of
    // independent sbt subprojects
    connection.subscribe(SbtCompilationListener.topic, new SbtCompilationListener {
      private def shouldProcess(identifier: ProjectIdentifier): Boolean =
        isEnabled && (identifier match {
          case ProjectBase(`projectBase`) | Unidentified => true
          case _                                         => false
        })

      override def beforeCompilationStart(base: ProjectBase): Unit =
        if (shouldProcess(base)) eventPublisher.onCompilationStart()

      override def connectionFailure(identifier: ProjectIdentifier): Unit =
        if (shouldProcess(identifier)) eventPublisher.onError()

      override def onCompilationFailure(
        identifier:    ProjectBase,
        compilationId: UUID
      ): Unit = if (shouldProcess(identifier)) eventPublisher.finishIndexing(-1L) // todo

      override def onCompilationSuccess(
        base:                ProjectBase,
        compilationId:       UUID,
        compilationInfoFile: String
      ): Unit = if (shouldProcess(base)) {
        val infoFile = new File(compilationInfoFile)
        parseCompilationInfo(infoFile).fold(
          error => {
            logger.error(s"Failed to parse compilation info file ${compilationId.toString}", error)
            eventPublisher.onError()
          },
          sbtInfo => processCompilationInfo(sbtInfo)
        )
      }
    })
  }

  override def start(): Unit = if (isEnabled) {
    executeOnPooledThread {
      val modules = project.sourceModules
      val baseDir = new File(projectBase)

      val offlineInfos = modules.flatMap { module =>
        val outputDir          = CompilerPaths.getModuleOutputDirectory(module, false)
        val target             = outputDir.getParent.getParent
        val compilationInfoDir = new File(s"$target/$compilationInfoDirName")
        compilationInfoDir.listFiles(_.getName.startsWith(compilationInfoFilePrefix))
      }

      if (offlineInfos.nonEmpty) {
        logger.info(
          s"Processing ${offlineInfos.length} compilation analysis files, " +
            s"from unsupervised sbt compilations: ${offlineInfos.map(_.getPath).mkString("[\n\t", ",\n\t", "\n]")}"
        )
      }

      baseDir.withLockInDir {
        processOfflineInfos(offlineInfos)
        subscribeToSbtNotifications()
      }
    }
  }
}

object SbtCompilationWatcher {
  private val logger = Logger.getInstance(classOf[SbtCompilationWatcher])
}
