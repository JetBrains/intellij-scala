package org.jetbrains.bsp.project.test.environment

import ch.epfl.scala.bsp4j._
import com.intellij.execution.configurations.{ModuleBasedConfiguration, RunConfiguration}
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{JavaPsiFacade, PsiFile}
import org.jetbrains.annotations.Nls
import org.jetbrains.bsp.BspBundle
import org.jetbrains.bsp.BspUtil._
import org.jetbrains.bsp.data.BspMetadata
import org.jetbrains.bsp.protocol.session.BspSession.{BspServer, BspSessionTask, BuildServerInfo}
import org.jetbrains.bsp.protocol.{BspCommunication, BspJob}
import org.jetbrains.plugins.scala.build.BuildToolWindowReporter.CancelBuildAction
import org.jetbrains.plugins.scala.build.{BuildMessages, BuildToolWindowReporter}
import org.jetbrains.plugins.scala.extensions.invokeAndWait

import java.net.URI
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import scala.concurrent.Promise
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

object BspJvmEnvironment {

  trait BspTargetIdHolder {
    def currentValue: Option[BuildTargetIdentifier]
    def update(value: BuildTargetIdentifier): Unit
  }

  case class Error(msg: String) extends RuntimeException(msg)

  type Result[A] = Either[Error, A]

  def getBspTargets(module: Module): Result[Seq[BuildTargetIdentifier]] = {
    BspMetadata.get(module.getProject, module)
      .left.map(err => Error(err.msg))
      .map(data => data.targetIds.asScala.iterator.map(id => new BuildTargetIdentifier(id.toString)).toSeq)
  }

  def promptUserToSelectBspTarget(
    project: Project,
    targetIds: Seq[BuildTargetIdentifier],
    holder: BspTargetIdHolder
  ): Option[BuildTargetIdentifier] = {
    val selected = invokeAndWait(BspSelectTargetDialog.promptForBspTarget(project, targetIds, holder.currentValue))
    selected.foreach(holder.update)
    selected
  }

  def promptUserToSelectBspTargetForWorksheet(module: Module): Unit = {
    val potentialTargets = getBspTargets(module)
    potentialTargets.foreach { targetIds =>
      val holder = persistentHolderForWorksheet(module)
      promptUserToSelectBspTarget(module.getProject, targetIds, holder)
    }
  }

  def resolveForWorksheet(module: Module): Result[JvmEnvironment] = {
    val holder = persistentHolderForWorksheet(module)
    for {
      workspace <- workspaceUri(module)
      potentialTargets <- getBspTargets(module)
      selectedTarget <- holder.currentValue
        .orElse(potentialTargets.singleElement)
        .orElse(promptUserToSelectBspTarget(module.getProject, potentialTargets, holder))
        .toRight(Error(BspBundle.message("bsp.task.error.could.not.choose.any.target.id")))
      environment <- fetchJvmEnvironment(selectedTarget, workspace, module.getProject, ExecutionEnvironmentType.RUN)
    } yield environment
  }

  def resolveForRun(
    config: ModuleBasedConfiguration[_, _],
    module: Module,
    holder: BspTargetIdHolder
  ): Result[JvmEnvironment] = {
    for {
      extractor <- classExtractor(config)
      workspace <- workspaceUri(module)
      potentialTargets <- getBspTargets(module)
      targetsMatchingSources <- findTargetsMatchingSources(config, module.getProject, extractor, potentialTargets, workspace)
      selectedTarget <- holder.currentValue
        .orElse(targetsMatchingSources.singleElement)
        .orElse(potentialTargets.singleElement)
        .orElse(promptUserToSelectBspTarget(module.getProject, potentialTargets, holder))
        .toRight(Error(BspBundle.message("bsp.task.error.could.not.choose.any.target.id")))
      environment <- fetchJvmEnvironment(selectedTarget, workspace, module.getProject, extractor.environmentType)
    } yield environment
  }

  private def classExtractor(configuration: RunConfiguration) = {
    BspEnvironmentRunnerExtension.getClassExtractor(configuration)
      .toRight(Error(BspBundle.message("bsp.task.error.no.class.extractor", configuration.getClass.getName)))
  }

  private def findTargetsMatchingSources(
    configuration: RunConfiguration,
    project: Project,
    extractor: BspEnvironmentRunnerExtension,
    potentialTargets: Seq[BuildTargetIdentifier],
    workspace: URI
  ): Result[Seq[BuildTargetIdentifier]] = {
    def sourceFileForClass(className: String): Option[PsiFile] = {
      val psiFacade = JavaPsiFacade.getInstance(project)
      val scope = GlobalSearchScope.allScope(project)
      val matchedClasses = invokeAndWait(readInSmartMode(project)(psiFacade.findClasses(className, scope)))
      matchedClasses match {
        case Array(matchedClass) => Option(matchedClass.getContainingFile)
        case _ => None
      }
    }

    def filterTargetsContainingSources(sourceItems: Seq[SourcesItem], files: Seq[PsiFile]): Seq[BuildTargetIdentifier] = {
      val filePaths = files.map(file => Paths.get(file.getVirtualFile.getPath))

      def sourceItemContainsAnyOfFiles(sourceItem: SourceItem): Boolean = {
        val sourcePath = Paths.get(sourceItem.getUri.toURI)
        filePaths.exists(_.startsWith(sourcePath))
      }

      sourceItems
        .filter(_.getSources.asScala.exists(sourceItemContainsAnyOfFiles))
        .map(_.getTarget)
    }

    val testClasses = extractor.classes(configuration).getOrElse(Nil)
    val testSources = testClasses.flatMap(sourceFileForClass)
    fetchSourcesForTargets(potentialTargets, workspace, project).toEither
      .map(filterTargetsContainingSources(_, testSources))
      .left.map(_ => Error(BspBundle.message("bsp.task.error.could.not.fetch.sources")))
  }

  private def persistentHolderForWorksheet(module: Module): BspTargetIdHolder = {
    PersistentBspTargetIdHolder.getInstance(module)
  }

  private def workspaceUri(module: Module): Result[URI] = {
    Option(ExternalSystemApiUtil.getExternalProjectPath(module)).map(Paths.get(_).toUri)
      .toRight(Error(BspBundle.message("bsp.task.error.could.not.extract.path", module.getName)))
  }

  private def fetchJvmEnvironment(
    target: BuildTargetIdentifier,
    workspace: URI,
    project: Project,
    environmentType: ExecutionEnvironmentType
  ): Result[JvmEnvironment] = {
    bspRequest(
      workspace, project, BspBundle.message("bsp.task.fetching.jvm.test.environment"),
      createJvmEnvironmentRequest(List(target), environmentType)
    ).flatMap {
      case Left(value) => Failure(value)
      case Right(Seq(environment)) => Success(JvmEnvironment.fromBsp(environment))
      case _ => Failure(Error(BspBundle.message("bsp.task.invalid.environment.response")))
    }.toEither.left.map {
      case e: Error => e
      case _ => Error(BspBundle.message("bsp.task.error.could.not.fetch.test.jvm.environment"))
    }
  }

  private def createJvmEnvironmentRequest(targets: Seq[BuildTargetIdentifier], environmentType: ExecutionEnvironmentType)(
    server: BspServer,
    serverInfo: BuildServerInfo
  ): CompletableFuture[Result[Seq[JvmEnvironmentItem]]] = {
    def environment[R](
      capability: BuildServerCapabilities => java.lang.Boolean,
      endpoint: java.util.List[BuildTargetIdentifier] => CompletableFuture[R],
      items: R => java.util.List[JvmEnvironmentItem],
      endpointName: String
    ): CompletableFuture[Result[Seq[JvmEnvironmentItem]]] = {
      if (Option(capability(serverInfo.capabilities)).exists(_.booleanValue)) {
        endpoint(targets.asJava).thenApply(response => Right(items(response).asScala.toSeq))
      } else {
        CompletableFuture.completedFuture(Left(Error(
          BspBundle.message("bsp.task.error.env.not.supported", endpointName)))
        )
      }
    }

    environmentType match {
      case ExecutionEnvironmentType.RUN =>
        environment[JvmRunEnvironmentResult](
          _.getJvmRunEnvironmentProvider,
          targets => server.jvmRunEnvironment(new JvmRunEnvironmentParams(targets)),
          _.getItems,
          "buildTarget/jvmRunEnvironment")
      case ExecutionEnvironmentType.TEST =>
        environment[JvmTestEnvironmentResult](
          _.getJvmTestEnvironmentProvider,
          targets => server.jvmTestEnvironment(new JvmTestEnvironmentParams(targets)),
          _.getItems,
          "buildTarget/jvmTestEnvironment")
    }
  }

  private def fetchSourcesForTargets(
    potentialTargets: Seq[BuildTargetIdentifier],
    workspace: URI,
    project: Project
  ): Try[Seq[SourcesItem]] = {
    bspRequest(
      workspace, project, BspBundle.message("bsp.task.fetching.sources"),
      { case (s, _) => createSourcesRequest(potentialTargets)(s) }
    ).map(sources => sources.getItems.asScala.toList)
  }

  private def createSourcesRequest(targets: Seq[BuildTargetIdentifier])(server: BspServer): CompletableFuture[SourcesResult] = {
    server.buildTargetSources(new SourcesParams(targets.asJava))
  }

  private def bspRequest[A](
    workspace: URI,
    project: Project,
    @Nls reporterTitle: String,
    task: BspSessionTask[A]
  ): Try[A] = {
    val communication = BspCommunication.forWorkspace(workspace.toFile, project)
    val bspTaskId = BuildMessages.randomEventId
    val cancelAction = new CancelBuildAction(Promise[Unit]())
    implicit val reporter: BuildToolWindowReporter =
      new BuildToolWindowReporter(project, bspTaskId, reporterTitle, cancelAction)
    val job = communication.run(
      bspSessionTask = task,
      notifications = _ => (),
      processLogger = reporter.log,
    )
    BspJob.waitForJob(job, retries = 10)
  }

  private implicit class SeqOps[A](s: Seq[A]) {
    def singleElement: Option[A] = s match {
      case Seq(single) => Some(single)
      case _ => None
    }
  }

  private def readInSmartMode[A](project: Project)(block: => A): A = {
    val dumbService = DumbService.getInstance(project)
    dumbService.runReadActionInSmartMode(() => block)
  }

}
