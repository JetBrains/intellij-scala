package org.jetbrains.plugins.scala.findUsages.compilerReferences
package compilation

import java.io._
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.util.UUID

import com.intellij.ProjectTopics
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.{ModuleListener, Project}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.findUsages.compilerReferences.ScalaCompilerReferenceService.CompilerIndicesState
import org.jetbrains.plugins.scala.findUsages.compilerReferences.compilation.SbtCompilationListener.ProjectIdentifier
import org.jetbrains.plugins.scala.findUsages.compilerReferences.compilation.SbtCompilationListener.ProjectIdentifier._
import org.jetbrains.plugins.scala.indices.protocol.IdeaIndicesJsonProtocol._
import org.jetbrains.plugins.scala.indices.protocol.sbt.Locking.FileLockingExt
import org.jetbrains.plugins.scala.indices.protocol.sbt._
import org.jetbrains.plugins.scala.project.ProjectExt
import spray.json._

import scala.util.{Try, Using}
import scala.util.control.NonFatal

private[compilerReferences] class SbtCompilationWatcher(
  override val project:          Project,
  override val transactionGuard: TransactionGuard[CompilerIndicesState],
  val indexVersion:              Int
) extends CompilationWatcher[CompilerMode.SBT.type] {
  import SbtCompilationWatcher._

  private[this] val projectBase: Path = Paths.get(project.getBasePath)

  private[this] def parseCompilationInfo(infoFile: File): Try[SbtCompilationInfo] = {
    val result = Try {
      val br      = Files.newBufferedReader(infoFile.toPath, StandardCharsets.UTF_8)
      val builder = new StringBuilder()

      Using.resource(br) { in =>
        var line = in.readLine()
        while (line != null) {
          builder ++= line
          line = in.readLine()
        }
      }

      val contents = builder.result()
      contents.parseJson.convertTo[SbtCompilationInfo]
    }

    infoFile.delete()
    result
  }

  private[this] def processCompilationInfo(
    sbtInfo:   SbtCompilationInfo,
    publisher: CompilerIndicesEventPublisher,
    isOffline: Boolean = false
  ): Unit =
    applyTo(publisher)(
      _.startIndexing(isCleanBuild = !sbtInfo.isIncremental),
      _.processCompilationInfo(sbtInfo, isOffline),
      _.finishIndexing(),
      _.onCompilationFinish(success = true)
    )

  private[this] def processOfflineInfos(infoFiles: Seq[File]): Unit = {
    val parsedInfos = infoFiles.flatMap(parseCompilationInfo(_).toOption)

    if (parsedInfos.size != infoFiles.size) {
      processEventInTransaction(_.onError(ScalaBundle.message("failed.to.parse.offline.compilation.analysis.files")))
    } else {
      val infos = parsedInfos.sortBy(_.startTimestamp)
      processEventInTransaction { publisher => infos.foreach(processCompilationInfo(_, publisher, isOffline = true)) }
    }
  }

  override def compilerMode: CompilerMode.SBT.type = CompilerMode.SBT

  private[this] def subscribeToSbtNotifications(): Unit = {
    val messageBus = project.getMessageBus
    val connection = messageBus.connect(project.unloadAwareDisposable)

    connection.subscribe(ProjectTopics.MODULES, new ModuleListener {
      // if an sbt project is added to the IDEA model, just nuke the indices
      // since it may possess a compiler state we are unaware of
      // (this is fine since reindexing is relatively cheap with sbt (no rebuild)).
      override def moduleAdded(project: Project, module: Module): Unit =
        processEventInTransaction(_.onError(ScalaBundle.message("sbt.module.added")))
    })

    // can be called from multiple threads in case of a parallel compilation of
    // independent sbt subprojects
    connection.subscribe(SbtCompilationListener.topic, new SbtCompilationListener {
      private def thisBuild(identifier: ProjectIdentifier): Boolean = identifier match {
        case ProjectBase(`projectBase`) | Unidentified => true
        case _                                         => false
      }

      override def beforeCompilationStart(base: ProjectBase, compilationId: UUID): Unit =
        if (thisBuild(base)) processEventInTransaction(_.onCompilationStart())

      override def connectionFailure(identifier: ProjectIdentifier, compilationId: Option[UUID]): Unit =
        if (thisBuild(identifier)) processEventInTransaction(_.onError(ScalaBundle.message("sbt.connection.failure")))

      override def onCompilationFailure(
        identifier:    ProjectBase,
        compilationId: UUID
      ): Unit = if (thisBuild(identifier)) processEventInTransaction(_.onCompilationFinish(success = false))

      override def onCompilationSuccess(
        base:                ProjectBase,
        compilationId:       UUID,
        compilationInfoFile: String
      ): Unit = if (thisBuild(base)) {
        val infoFile = new File(compilationInfoFile)
        // here we parse compilation info files unconditionally (even if compilerMode == JPS)
        // to avoid doing it in transaction, this is relatively small overhead and allows
        // us to keep transactions short and avoid blocking UI thread.
        parseCompilationInfo(infoFile).fold(
          error => processEventInTransaction { publisher =>
            publisher.onError(ScalaBundle.message("failed.to.parse.compilation.info.file", compilationId), Option(error))
          },
          sbtInfo => processEventInTransaction(processCompilationInfo(sbtInfo, _))
        )
      }
    })
  }

  override def start(): Unit = executeOnPooledThread {
    try {
      val baseInfoDir            = compilationInfoBaseDir(projectBase.toFile).toFile
      val moduleInfoDirs         = baseInfoDir.listFiles(_.isDirectory).toOption.getOrElse(Array.empty)
      val fileFilter: FileFilter = _.getName.startsWith(compilationInfoFilePrefix)
      val offlineInfos           = moduleInfoDirs.flatMap(_.listFiles(fileFilter).toOption.getOrElse(Array.empty))

      moduleInfoDirs.foreach(_.lock(log = logger.info))

      try {
        if (offlineInfos.nonEmpty && upToDateCompilerIndexExists(project, indexVersion)) {
          logger.info(
            s"Processing ${offlineInfos.length} compilation analysis files, " +
              s"from unsupervised sbt compilations: ${offlineInfos.map(_.getPath).mkString("[\n\t", ",\n\t", "\n]")}"
          )
          processOfflineInfos(offlineInfos.toSeq)
        } else offlineInfos.foreach(_.delete())

      } finally moduleInfoDirs.foreach(_.unlock(log = logger.info))
    } catch {
      case NonFatal(e) => processEventInTransaction { publisher =>
        publisher.onError(ScalaBundle.message("error.while.reading.sbt.compilation.info"), Option(e))
      }
    } finally subscribeToSbtNotifications()
  }
}

object SbtCompilationWatcher {
  private val logger = Logger.getInstance(classOf[SbtCompilationWatcher])
}
