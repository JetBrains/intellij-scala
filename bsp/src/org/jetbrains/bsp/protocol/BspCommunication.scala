package org.jetbrains.bsp.protocol

import java.io.File
import java.net.URI
import java.nio.file._

import ch.epfl.scala.bsp4j.BspConnectionDetails
import com.google.gson.Gson
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.{Project, ProjectUtil}
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil.defaultIfEmpty
import com.intellij.util.SystemProperties
import com.intellij.util.net.NetUtils
import org.jetbrains.bsp.protocol.BspCommunication._
import org.jetbrains.bsp.protocol.BspNotifications.BspNotification
import org.jetbrains.bsp.protocol.session.BspServerConnector._
import org.jetbrains.bsp.protocol.session._
import org.jetbrains.bsp.protocol.session.BspSession._
import org.jetbrains.bsp.protocol.session.jobs.BspSessionJob
import org.jetbrains.bsp.settings.{BspExecutionSettings, BspProjectSettings, BspSettings}
import org.jetbrains.bsp.{BSP, BspError, BspErrorMessage}

import scala.io.Source
import scala.util.{Failure, Random, Success, Try}

// TODO connections should be independent from project: https://youtrack.jetbrains.com/issue/SCL-14876
class BspCommunicationComponent(project: Project) extends ProjectComponent {

  val communication = new BspCommunication(base, Some(project), executionSettings)

  private def base = new File(project.getBasePath)
  private def executionSettings = {
    val workingDirPath =
      Option(ProjectUtil.guessProjectDir(project))
        .getOrElse(throw new IllegalStateException(s"no project directory found for project ${project.getName}"))
        .getCanonicalPath
    BspExecutionSettings.executionSettingsFor(project, workingDirPath)
  }
}

class BspCommunication(base: File, project: Option[Project], executionSettings: BspExecutionSettings) {

  private val log = Logger.getInstance(classOf[BspCommunication])

  @volatile private var session: Option[BspSession] = None

  private def acquireSessionAndRun(job: BspSessionJob[_,_]): Either[BspError, BspSession] = session.synchronized {
    session match {
      case Some(currentSession) if currentSession.isAlive =>
        Right(currentSession)

      case Some(_) => // dead session
        openSession(job)

      case None =>
        openSession(job)
    }
  }

  private def openSession(job: BspSessionJob[_,_]): Either[BspError, BspSession] = {
    val sessionBuilder = prepareSession(base, executionSettings)

    sessionBuilder match {
      case Left(error) =>
        val procLogMsg = s"bsp connection failed: ${error.getMessage}"
        job.log(procLogMsg)
        log.warn("bsp connection failed", error)
        Left(error)
      case Right(newSessionBuilder) =>
        newSessionBuilder.withInitialJob(job)
          .addNotificationCallback(projectCallback)
          .withTraceLogPredicate(project
            .map(p => () => BspSettings.getInstance(p).getLinkedProjectSettings(p.getBasePath).traceBsp)
            .getOrElse(() => false))
        val newSession = newSessionBuilder.create
        session = Some(newSession)
        Right(newSession)
    }
  }

  private val bspSettings: Option[BspProjectSettings] =
    for {
      p <- project
      basePath <- Option(ProjectUtil.guessProjectDir(p))
      settings <- Option(BspSettings.getInstance(p).getLinkedProjectSettings(basePath.getPath))
    } yield settings

  val projectCallback: NotificationCallback = {
    case BspNotifications.DidChangeBuildTarget(didChange) =>
      for {
        p <- project
        s <- bspSettings
        if s.isUseAutoImport
      } {
        FileDocumentManager.getInstance.saveAllDocuments()
        ExternalSystemUtil.refreshProjects(new ImportSpecBuilder(p, BSP.ProjectSystemId))
      }
    case _ => // ignore
  }

  def closeSession(): Try[Unit] = session match {
    case None => Success(())
    case Some(s) =>
      session = None
      s.shutdown()
  }

  def run[T, A](task: BspSessionTask[T],
                default: A,
                aggregator: NotificationAggregator[A],
                processLogger: ProcessLogger
               ): BspJob[(T, A)] = {
    val job = jobs.create(task, default, aggregator, processLogger)

    acquireSessionAndRun(job) match {
      case Left(error) => new FailedBspJob(error)
      case Right(currentSession) =>
        currentSession.run(job)
    }
  }

  def run[T](bspSessionTask: BspSessionTask[T],
             notifications: NotificationCallback,
             processLogger: ProcessLogger): BspJob[T] = {
    val callback = (a: Unit, n: BspNotification) => notifications(n)
    val job = run(bspSessionTask, (), callback, processLogger)
    new NonAggregatingBspJob(job)
  }

}


object BspCommunication {

  // TODO since IntelliJ projects can correspond to multiple bsp modules, figure out how to have independent
  //      BspCommunication instances per base path: https://youtrack.jetbrains.com/issue/SCL-14876
  def forProject(project: Project): BspCommunication = {
    val pm = project.getComponent(classOf[BspCommunicationComponent])
    if (pm == null) throw new IllegalStateException(s"unable to get component BspCommunication for project $project")
    else pm.communication
  }

  def forBaseDir(baseDir: String, executionSettings: BspExecutionSettings): BspCommunication = {
    val baseFile = new File(baseDir)
    if (!baseFile.isDirectory) throw new IllegalArgumentException(s"Base path for BspCommunication is not a directory: $baseDir")
    new BspCommunication(baseFile, None, executionSettings)
  }


  private[protocol] def prepareSession(base: File, bspExecutionSettings: BspExecutionSettings): Either[BspError, Builder] = {

    val supportedLanguages = List("scala","java") // TODO somehow figure this out more generically?
    val capabilities = BspCapabilities(supportedLanguages)

    val id = java.lang.Long.toString(Random.nextLong(), Character.MAX_RADIX)

    val tcpMethod = TcpBsp(new URI("localhost"), findFreePort(5001))

    val platformMethod =
      if (SystemInfo.isWindows) WindowsLocalBsp(id)
      else if (SystemInfo.isUnix) {
        val tempDir = Files.createTempDirectory("bsp-")
        val socketFilePath = tempDir.resolve(s"$id.socket")
        val socketFile = socketFilePath.toFile
        socketFile.deleteOnExit()
        UnixLocalBsp(socketFile)
      }
      else tcpMethod

    val connectionDetails = findBspConfigs(base)

    val configuredMethods = connectionDetails.map(ProcessBsp)

    val bloopConfigDir = new File(base, ".bloop").getCanonicalFile

    val connector =
      if (connectionDetails.nonEmpty) new GenericConnector(base, capabilities)
      else if (bloopConfigDir.exists()) new BloopConnector(bspExecutionSettings.bloopExecutable, base, capabilities)
      else new DummyConnector(base.toURI)

    // TODO user dialog when multiple valid connectors exist: https://youtrack.jetbrains.com/issue/SCL-14880
    val methodsInPreferenceOrder = platformMethod :: tcpMethod :: configuredMethods
    connector.connect(methodsInPreferenceOrder : _*)
  }

  private def findBspConfigs(projectBase: File): List[BspConnectionDetails] = {

    val workspaceConfigDir = new File(projectBase, ".bsp")
    val workspaceConfigs = listFiles(List(workspaceConfigDir))
    val systemConfigs = systemDependentConnectionFiles

    val potentialConfigs = tryReadingConnectionFiles(workspaceConfigs ++ systemConfigs)

    potentialConfigs.flatMap(_.toOption).toList
  }

  private def systemDependentConnectionFiles: List[File] = {
    val basePaths =
      if (SystemInfo.isWindows) windowsBspFiles()
      else if (SystemInfo.isMac) macBspFiles()
      else if (SystemInfo.isUnix) unixBspFiles()
      else Nil

    listFiles(bspDirs(basePaths))
  }

  private def tryReadingConnectionFiles(files: Seq[File]): Seq[Try[BspConnectionDetails]] = {
    val gson = new Gson()
    files.map { file =>
      if (file.canRead) {
        val reader = Source.fromFile(file).bufferedReader()
        Try(gson.fromJson(reader, classOf[BspConnectionDetails]))
      } else Failure(BspErrorMessage(s"file not readable: $file"))
    }
  }

  private val BspDirName = "bsp"

  private def windowsBspFiles() = {
    val localAppData = System.getenv("LOCALAPPDATA")
    val programData = System.getenv("PROGRAMDATA")
    List(localAppData, programData)
  }

  private def unixBspFiles() = {
    val xdgDataHome = System.getenv("XDG_DATA_HOME")
    val xdgDataDirs = System.getenv("XDG_DATA_DIRS")
    val dataHome = defaultIfEmpty(xdgDataHome, SystemProperties.getUserHome + "/.local/share")
    val dataDirs = defaultIfEmpty(xdgDataDirs, "/usr/local/share:/usr/share").split(":").toList
    dataHome :: dataDirs
  }

  private def macBspFiles() = {
    val userHome = SystemProperties.getUserHome
    val userData = userHome + "/Library/Application Support"
    val systemData = "/Library/Application Support"
    List(userData, systemData)
  }

  private def bspDirs(basePaths: List[String]): List[File] = basePaths.map(new File(_, BspDirName))

  private def listFiles(dirs: List[File]): List[File] = dirs.flatMap { dir =>
    if (dir.isDirectory) dir.listFiles()
    else Array.empty[File]
  }


  private def findFreePort(port: Int): Int = {
    val port = 5001
    if (NetUtils.canConnectToSocket("localhost", port)) port
    else NetUtils.findAvailableSocketPort()
  }

}
