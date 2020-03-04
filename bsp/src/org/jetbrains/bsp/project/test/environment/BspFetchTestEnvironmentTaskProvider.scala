package org.jetbrains.bsp.project.test.environment

import java.lang
import java.net.URI
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture

import ch.epfl.scala.bsp4j._
import com.intellij.execution.BeforeRunTaskProvider
import com.intellij.execution.configurations.{ModuleBasedConfiguration, RunConfiguration}
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.util.{ExternalSystemApiUtil => ES}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Key
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{JavaPsiFacade, PsiClass, PsiFile}
import javax.swing.Icon
import org.jetbrains.bsp.BspUtil._
import org.jetbrains.bsp.data.BspMetadata
import org.jetbrains.bsp.protocol.session.BspSession.{BspServer, ProcessLogger}
import org.jetbrains.bsp.protocol.{BspCommunication, BspJob}
import org.jetbrains.bsp.{BspBundle, BspUtil, Icons}
import org.jetbrains.concurrency.{Promise, Promises}
import org.jetbrains.plugins.scala.build.BuildMessages.EventId
import org.jetbrains.plugins.scala.build.BuildToolWindowReporter.CancelBuildAction
import org.jetbrains.plugins.scala.build.{BuildMessages, BuildToolWindowReporter}
import org.jetbrains.plugins.scala.testingSupport.test.scalatest.ScalaTestRunConfiguration
import org.jetbrains.plugins.scala.testingSupport.test.testdata.{AllInPackageTestData, ClassTestData}

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

case class JvmTestEnvironment(
                               classpath: Seq[String],
                               workdir: String,
                               environmentVariables: Map[String, String],
                               jvmOptions: Seq[String]
                             )

case object JvmTestEnvironmentNotSupported
  extends Throwable(BspBundle.message("bsp.task.error.test.env.not.supported"))

class BspFetchTestEnvironmentTaskProvider extends BeforeRunTaskProvider[BspFetchTestEnvironmentTask] {
  private val logger = Logger.getInstance(classOf[BspCommunication])

  override def getId: Key[BspFetchTestEnvironmentTask] = BspFetchTestEnvironmentTask.runTaskKey

  override def getName: String = BspBundle.message("bsp.task.name")

  override def getIcon: Icon = Icons.BSP

  override def createTask(runConfiguration: RunConfiguration): BspFetchTestEnvironmentTask = new BspFetchTestEnvironmentTask

  override def isConfigurable: Boolean = true

  override def configureTask(context: DataContext, configuration: RunConfiguration, task: BspFetchTestEnvironmentTask): Promise[lang.Boolean] = {
    val module = configuration.asInstanceOf[ScalaTestRunConfiguration].getModule
    val res = for {
      potentialTargets <- getBspTargets(module)
      _ <- askUserForTargetId(potentialTargets.map(_.getUri), task)
    } yield ()
    Promises.resolvedPromise(res.isDefined)
  }

  case class BspGetEnvironmentError(msg: String)

  override def executeTask(context: DataContext,
                           configuration: RunConfiguration,
                           env: ExecutionEnvironment,
                           task: BspFetchTestEnvironmentTask): Boolean = {
    configuration match {
      case config: ModuleBasedConfiguration[_, _]
        if BspUtil.isBspModule(config.getConfigurationModule.getModule) && BspTesting.isBspRunnerSupportedConfiguration(config) =>
        val module = config.getConfigurationModule.getModule
        val taskResult: Either[BspGetEnvironmentError, Unit] = for {
          potentialTargets <- getBspTargets(module)
            .toRight(BspGetEnvironmentError(BspBundle.message("bsp.task.error.could.not.find.potential.targets",module.getName)))
          projectPath <- Option(ES.getExternalProjectPath(module))
            .toRight(BspGetEnvironmentError(BspBundle.message("bsp.task.error.could.not.extract.path", module.getName)))
          workspaceUri = Paths.get(projectPath).toUri
          testClasses <- getApplicableClasses(configuration)
            .toRight(BspGetEnvironmentError(BspBundle.message("bsp.task.error.could.not.detect.run.classes", configuration.getName)))
          testSources = testClasses.flatMap(class2File(_, module.getProject))
          targetsMatchingSources <- fetchTargetIdsFromFiles(testSources, workspaceUri, module.getProject, potentialTargets)
            .map(Right(_))
            .getOrElse(Left(BspGetEnvironmentError(BspBundle.message("bsp.task.error.could.not.fetch.sources"))))
          chosenTargetId <- task.state
            .orElse(if (targetsMatchingSources.length == 1) targetsMatchingSources.headOption.map(_.getUri) else None)
            .orElse(askUserForTargetId(potentialTargets.map(_.getUri), task))
            .toRight(BspGetEnvironmentError(BspBundle.message("bsp.task.error.could.not.choose.any.target.id")))
          testEnvironment <-
            fetchJvmTestEnvironment(new BuildTargetIdentifier(chosenTargetId), workspaceUri, module.getProject)
              .map(Right(_))
              .recover{
                case err: JvmTestEnvironmentNotSupported.type => Left(BspGetEnvironmentError(err.getMessage))
              }
              .getOrElse(Left(BspGetEnvironmentError(BspBundle.message("bsp.task.error.could.not.fetch.test.jvm.environment"))))
          _ = config.putUserData(BspFetchTestEnvironmentTask.jvmTestEnvironmentKey, testEnvironment)
        } yield ()
        taskResult match {
          case Left(value) =>
            logger.error(BspBundle.message("bsp.task.error", value))
            false
          case Right(_) => true
        }
      case _ => true
    }

  }

  private def askUserForTargetId(targetIds: Seq[String], task: BspFetchTestEnvironmentTask): Option[String] = {
    var chosenTarget: Option[String] = None
    ApplicationManager.getApplication.invokeAndWait { () => {
      chosenTarget = Option(Messages.showEditableChooseDialog(
        BspBundle.message("bsp.task.choose.target.message"),
        BspBundle.message("bsp.task.choose.target.title"),
        Icons.BSP_TOOLWINDOW,
        targetIds.toArray,
        targetIds.headOption.getOrElse(""),
        null
      ))
    }
    }
    if (chosenTarget.isDefined) {
      task.state = chosenTarget
    }
    chosenTarget
  }

  private def fetchTargetIdsFromFiles(files: Seq[PsiFile],
                                      workspace: URI,
                                      project: Project,
                                      potentialTargets: Seq[BuildTargetIdentifier]): Try[Seq[BuildTargetIdentifier]] = {
    val communication: BspCommunication = BspCommunication.forWorkspace(workspace.toFile)
    val bspTaskId: EventId = BuildMessages.randomEventId
    val cancelToken = scala.concurrent.Promise[Unit]()
    val cancelAction = new CancelBuildAction(cancelToken)


    implicit val reporter: BuildToolWindowReporter =
      new BuildToolWindowReporter(
        project = project,
        buildId = bspTaskId,
        title = BspBundle.message("bsp.task.fetching.sources"),
        cancelAction
      )
    val job = communication.run(
      bspSessionTask = getFiles(potentialTargets)(_, _),
      notifications = _ => (),
      processLogger = processLog(reporter),
    )
    BspJob.waitForJob(job, 10).map {
      sources =>
        filterTargetsContainingSources(sources.getItems.asScala, files)
    }
  }

  /**
   * @param files       List of sources you are looking for
   * @param sourceLists List of targets with their sources, that will be searched
   * @return All targets from `sourceLists` that contain any of the sources from `sources`
   */
  private def filterTargetsContainingSources(sourceLists: Seq[SourcesItem],
                                             files: Seq[PsiFile]): Seq[BuildTargetIdentifier] = {
    val fileUris = files.map(_.getVirtualFile.getPath).map(path => Paths.get(path).toUri.toString)
    sourceLists.filter(_.getSources.asScala.map(_.getUri).exists(fileUris.contains(_))).map(_.getTarget)
  }

  private def class2File(clazz: String, project: Project): Option[PsiFile] = {
    val psiFacade = JavaPsiFacade.getInstance(project)
    val scope = GlobalSearchScope.allScope(project)
    var matchedClasses: Array[PsiClass] = Array()
    ApplicationManager.getApplication.invokeAndWait{
      () => ApplicationManager.getApplication.runReadAction{
        new Runnable {
          override def run(): Unit = {
            matchedClasses = psiFacade.findClasses(clazz, scope)
          }
        }
      }
    }
    if (matchedClasses.length <= 1) matchedClasses.headOption.map(_.getContainingFile) else None
  }

  private def getApplicableClasses(configuration: RunConfiguration) = {
    configuration match {
      case scalaConfig: ModuleBasedConfiguration[_, _]
        if BspUtil.isBspModule(scalaConfig.getConfigurationModule.getModule) =>
        val classes = scalaConfig match {
          case p: ScalaTestRunConfiguration =>
            p.testConfigurationData match {
              case data: AllInPackageTestData => Some(data.classBuf.asScala)
              case data: ClassTestData => Some(List(data.testClassPath))
              case _ => None
            }
        }
        classes
    }
  }

  private def processLog(report: BuildToolWindowReporter): ProcessLogger = { message =>
    report.log(message)
  }

  private def fetchJvmTestEnvironment(target: BuildTargetIdentifier, workspace: URI, project: Project): Try[JvmTestEnvironment] = {
    val communication: BspCommunication = BspCommunication.forWorkspace(workspace.toFile)
    val bspTaskId: EventId = BuildMessages.randomEventId
    val cancelToken = scala.concurrent.Promise[Unit]()
    val cancelAction = new CancelBuildAction(cancelToken)
    implicit val reporter: BuildToolWindowReporter = new BuildToolWindowReporter(project, bspTaskId, BspBundle.message("bsp.task.fetching.jvm.test.environment"), cancelAction)
    val job = communication.run(
      bspSessionTask = jvmTestEnvironmentBspRequest(List(target))(_, _),
      notifications = _ => (),
      processLogger = processLog(reporter),
    )

    BspJob.waitForJob(job, retries = 10).flatMap {
          case Left(value) => Failure(value)
          case Right(value) =>
            val environment = value.getItems.asScala.head
            Success(JvmTestEnvironment(
              classpath = environment.getClasspath.asScala.map(x => new URI(x).getPath),
              workdir = environment.getWorkingDirectory,
              environmentVariables = environment.getEnvironmentVariables.asScala.toMap,
              jvmOptions = environment.getJvmOptions.asScala.toList
            ))
    }
  }

  private def getBspTargets(module: Module): Option[Seq[BuildTargetIdentifier]] =
    for {
      data <- BspMetadata.get(module.getProject, module)
      moduleId <- Option(ES.getExternalProjectId(module))
      res = data.targetIds.asScala.map(id => new BuildTargetIdentifier(id.toString)).filterNot(_.getUri == moduleId)
    } yield res

  private def jvmTestEnvironmentBspRequest(targets: Seq[BuildTargetIdentifier])
                                          (implicit server: BspServer,
                                           capabilities: BuildServerCapabilities): CompletableFuture[Either[JvmTestEnvironmentNotSupported.type ,JvmTestEnvironmentResult]] =
    if (Option(capabilities.getJvmTestEnvironmentProvider).exists(_.booleanValue())) {
      server.jvmTestEnvironment(new JvmTestEnvironmentParams(targets.asJava)).thenApply(Right(_))
    } else {
      CompletableFuture.completedFuture(Left(JvmTestEnvironmentNotSupported))
    }

  private def getFiles(target: Seq[BuildTargetIdentifier])
                      (implicit server: BspServer, capabilities: BuildServerCapabilities): CompletableFuture[SourcesResult] =
    server.buildTargetSources(new SourcesParams(target.asJava))
}