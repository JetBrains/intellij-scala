package org.jetbrains.bsp.project.test

import java.io.{File, OutputStream}
import java.net.URI
import java.util.UUID
import java.util.concurrent.CompletableFuture

import ch.epfl.scala.bsp4j.TaskDataKind._
import ch.epfl.scala.bsp4j.TestStatus._
import ch.epfl.scala.bsp4j._
import com.google.gson.{Gson, JsonObject}
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.process.{ProcessHandler, ProcessOutputType}
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties
import com.intellij.execution.{DefaultExecutionResult, ExecutionResult, Executor}
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import jetbrains.buildServer.messages.serviceMessages._
import org.jetbrains.bsp.{BspBundle, BspErrorMessage}
import org.jetbrains.bsp.data.BspMetadata
import org.jetbrains.bsp.protocol.BspCommunication
import org.jetbrains.bsp.protocol.BspNotifications.{BspNotification, LogMessage, TaskFinish, TaskStart}
import org.jetbrains.bsp.protocol.session.BspSession.BspServer
import org.jetbrains.plugins.scala.build.BuildToolWindowReporter.CancelBuildAction
import org.jetbrains.plugins.scala.build.{BuildMessages, BuildReporter, BuildToolWindowReporter}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Promise
import scala.util.{Failure, Success}

class BspTestRunner(
                     val project: Project,
                     val rc: BspTestRunConfiguration,
                     val ex: Executor,
                     val testClasses: Option[Map[URI, List[String]]]) extends RunProfileState {

  val gson = new Gson()

  class MProcHandler extends ProcessHandler {
    override def destroyProcessImpl(): Unit = ()

    override def detachProcessImpl(): Unit = {}

    override def detachIsDefault(): Boolean = false

    override def getProcessInput: OutputStream = null

    def shutdown(): Unit = {
      super.notifyProcessTerminated(0)
    }
  }

  private def targets(): List[URI] = {
    ModuleManager.getInstance(project).getModules.toList
      .flatMap(BspMetadata.get(project, _).toOption)
      .flatMap(x => x.targetIds.asScala.toList)
  }

  private def testRequest(server: BspServer, capabilities: BuildServerCapabilities): CompletableFuture[TestResult] = {
    // TODO should we check for intersection of language ids in individual targets, and whether targets are testable?
    val testsSupported = ! capabilities.getTestProvider.getLanguageIds.isEmpty
    if (testsSupported) {
      val targetIds = targets().map(uri => new BuildTargetIdentifier(uri.toString))
      val params = new TestParams(targetIds.asJava)
      params.setOriginId(UUID.randomUUID().toString)
      testClasses match {
        case Some(m) =>
          val scalaTestClasses = m
            .map { case (uri, classes) =>
              new ScalaTestClassesItem(new BuildTargetIdentifier(uri.toString), classes.asJava) }
            .toList
          params.setDataKind("scala-test")
          params.setData({
            val p = new ScalaTestParams
            p.setTestClasses(scalaTestClasses.asJava)
            p
          })
        case None =>
      }
      server.buildTargetTest(params)
    } else {
      val result = new CompletableFuture[TestResult]()
      result.completeExceptionally(BspErrorMessage(BspBundle.message("bsp.test.build.server.does.not.support.testing")))
      result
    }
  }


  def printProc(str: MessageWithAttributes)(implicit proc: ProcessHandler): Unit = {
    proc.notifyTextAvailable(str + System.lineSeparator(), ProcessOutputType.STDOUT)
  }

  class ServiceMsg(val name: String, val map: Map[String, String]) extends MessageWithAttributes(name, map.asJava) {
    def this(n: String) = this(n, Map())
  }

  case class NestedBspMsg(nested: AnyRef, msg: AnyRef)

  type HasNested = {
    def getDataKind: String
    def getData: Object
  }

  private def extractNestedMessage(bspMessage: AnyRef): AnyRef = bspMessage match {
    case x: HasNested@unchecked =>
      val kind = x.getDataKind
      val rawNested = x.getData
      rawNested match {
        case d: JsonObject => kind match {
          case TEST_FINISH => gson.fromJson(d, classOf[TestFinish])
          case TEST_TASK => gson.fromJson(d, classOf[TestTask])
          case TEST_REPORT => gson.fromJson(d, classOf[TestReport])
          case TEST_START => gson.fromJson(d, classOf[TestStart])
          case _ => rawNested
        }
        case _ => rawNested
      }
    case x => x
  }

  class BspTestSession {
    val startTime: mutable.Map[String, Long] = mutable.Map()
  }

  private def onBspNotification(proc: ProcessHandler, session: BspTestSession)(n: BspNotification): Unit = {
    implicit val pr: ProcessHandler = proc
    n match {
      case LogMessage(params) => // TODO associate log messages with the running test correctly
        printProc(new Message(params.getMessage + System.lineSeparator(), "NORMAL", null))
      case TaskStart(params) => extractNestedMessage(params) match {
        case t: TestTask =>
          printProc(new ServiceMsg("enteredTheMatrix"))
          printProc(new TestSuiteStarted("BSP"))
        case t: TestStart =>
          printProc(new TestStarted(t.getDisplayName, false, null))
          session.startTime += (t.getDisplayName -> params.getEventTime)
        case _ =>
      }
      case TaskFinish(params) => extractNestedMessage(params) match {
        case t: TestReport => printProc(new TestSuiteFinished("BSP"))
        case t: TestFinish =>
          t.getStatus match {
            case PASSED =>
            case FAILED => printProc(new ServiceMsg("testFailed", Map("name" -> t.getDisplayName, "message" -> t.getMessage)))
            case _ => printProc(new TestIgnored(t.getDisplayName, "Ignored"))
          }
          printProc(new TestFinished(t.getDisplayName, session.startTime.get(t.getDisplayName)
            .map { st =>
              session.startTime -= t.getDisplayName
              (params.getEventTime - st).intValue()
            }.getOrElse(0)))
        case _ =>
      }
      case _ =>
    }

  }


  override def execute(executor: Executor, runner: ProgramRunner[_]): ExecutionResult = {
    val procHandler = new MProcHandler
    val console = SMTestRunnerConnectionUtil.createAndAttachConsole("BSP", procHandler, new SMTRunnerConsoleProperties(
      project, rc, "BSP", ex))
    val bspCommunication = BspCommunication.forWorkspace(new File(project.getBasePath))

    val cancelToken = Promise[Unit]()
    val cancelAction = new CancelBuildAction(cancelToken)
    implicit val reporter: BuildReporter = new BuildToolWindowReporter(project, BuildMessages.randomEventId, BspBundle.message("bsp.tests.reporter.title"), cancelAction)
    reporter.start()

    bspCommunication
      .run(testRequest, onBspNotification(procHandler, new BspTestSession()), reporter.log)
      .future
      .onComplete { res =>
        res match {
          case Success(testResult) =>
            reporter.finish(BuildMessages.empty.status(BuildMessages.OK))
          case Failure(x) =>
            reporter.finishWithFailure(x)
        }
        procHandler.shutdown()
      }
    new DefaultExecutionResult(console, procHandler)
  }

}
