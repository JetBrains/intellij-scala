package org.jetbrains.plugins.scala.compiler

import com.intellij.pom.java.LanguageLevel
import org.jetbrains.jps.incremental.scala.remote.{Chunk, ClientEventProcessor, CommandIds, Event, RemoteResourceOwner}
import org.jetbrains.jps.incremental.scala.{Client, DummyClient, MessageKind}
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.compiler.CompileServerCommandsIntegrationTest.*
import org.jetbrains.plugins.scala.compiler.CompilerMessagesUtil.assertNoErrorsOrWarnings
import org.jetbrains.plugins.scala.server.CompileServerPort
import org.jetbrains.plugins.scala.settings.ScalaCompileServerSettings
import org.junit.Assert.{assertEquals, assertNotEquals, assertTrue}
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import java.io.DataInputStream
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.util.Base64
import scala.jdk.CollectionConverters.CollectionHasAsScala

/**
 * Checks which commands the running Scala Compile Server accepts over its Nailgun socket.
 *
 * Nailgun registers a set of built-in commands by default. The compile server must only expose its own commands
 * (see `org.jetbrains.plugins.scala.nailgun.NailgunRunner.createServer`), all of which validate the token before
 * doing any work.
 */
@RunWith(classOf[JUnit4])
class CompileServerCommandsIntegrationTest extends ScalaCompilerTestBase:
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  override def testProjectJdkVersion: LanguageLevel = LanguageLevel.JDK_21

  @Test
  def onlyCompileServerCommandsAreRegistered(): Unit =
    addFileToProjectSources("org/example/Person.scala",
      """package org.example
        |
        |final case class Person(name: String, age: Int)
        |""".stripMargin)

    val messages = compiler.make().asScala.toSeq
    assertNoErrorsOrWarnings(messages)

    val port = CompileServerLauncher.compileServerPort.getOrElse(
      throw AssertionError("Cannot connect to Scala Compile Server: unknown TCP port, make sure the server is running")
    )
    val sender = RawNailgunCommandSender(port)

    // The Nailgun built-in commands and the stop command we used to register must not be known to the server.
    // The stop commands are checked last: if any of them were registered, the server would shut down.
    val legacyStopCommand = "stop_" + ScalaCompileServerSettings.getInstance().COMPILE_SERVER_ID
    val unregisteredCommands = Seq("ng-alias", "ng-cp", "ng-stats", "ng-version", "ng-stop", legacyStopCommand)
    for command <- unregisteredCommands do
      val response = sender.execute(command, Seq.empty)
      assertEquals(s"Command `$command` must not be registered in the Scala Compile Server", ExitNoSuchCommand, response.exitCode)

    // Every compile server command must be registered. Sent with a wrong token, each of them reaches the nail,
    // which rejects the request, instead of being reported as an unknown command.
    for command <- CompileServerCommands do
      val response = sender.execute(command, Seq(WrongToken))
      assertNotEquals(s"Command `$command` must be registered in the Scala Compile Server", ExitNoSuchCommand, response.exitCode)

    val wrongTokenResponse = sender.execute(CommandIds.GetMetrics, Seq(WrongToken))
    assertTrue(
      s"A wrong token must be rejected by the Scala Compile Server, but got: ${wrongTokenResponse.messages}",
      wrongTokenResponse.errors.exists(_.startsWith("Token is incorrect"))
    )

    // The server is still running and serves requests with the correct token after all of the above.
    val metrics = CompileServerClient.get(getProject).getMetrics()
    assertTrue("The Scala Compile Server did not report its metrics", metrics.maxHeapSize > 0)

private object CompileServerCommandsIntegrationTest:

  /** `com.facebook.nailgun.NGConstants.EXIT_NOSUCHCOMMAND`, reported by the Nailgun default nail for unknown commands. */
  private final val ExitNoSuchCommand = 898

  private final val WrongToken = "wrong-token"

  /** Must be kept in sync with [[CommandIds]] and `org.jetbrains.plugins.scala.nailgun.NailgunRunner.COMMANDS`. */
  private val CompileServerCommands: Seq[String] = Seq(
    CommandIds.Compile,
    CommandIds.ComputeStamps,
    CommandIds.CompileJps,
    CommandIds.CompileDocument,
    CommandIds.EvaluateExpression,
    CommandIds.GetMetrics
  )

private final case class RawNailgunCommandResponse(exitCode: Int, messages: Seq[(MessageKind, String)]):
  def errors: Seq[String] = messages.collect:
    case (MessageKind.Error, text) => text

/**
 * Sends raw Nailgun commands to the compile server, without the token handling of [[RemoteServerRunner]], and
 * records the exit code reported by the server, which [[RemoteResourceOwner.handle]] does not expose.
 */
private final class RawNailgunCommandSender(port: CompileServerPort) extends RemoteResourceOwner:
  override protected val address: InetAddress = InetAddress.getByName(null)

  override protected def compileServerPort: CompileServerPort = port

  private var exitCode: Option[Int] = None

  def execute(command: String, arguments: Seq[String]): RawNailgunCommandResponse =
    exitCode = None
    val messages = Seq.newBuilder[(MessageKind, String)]
    val client = new DummyClient:
      override def message(msg: Client.ClientMsg): Unit = messages += ((msg.kind, msg.text))
    send(command, arguments, client)
    val code = exitCode.getOrElse(throw AssertionError(s"The Scala Compile Server did not report an exit code for command `$command`"))
    RawNailgunCommandResponse(code, messages.result())

  override protected def handle(input: DataInputStream, client: Client): Unit =
    val processor = ClientEventProcessor(client)
    while true do
      // The chunk type markers are the same as in `NailgunConstants`, which is not accessible from this package.
      Chunk.readFrom(input) match
        case Chunk('X', code) =>
          exitCode = Some(String(code, StandardCharsets.UTF_8).trim.toInt)
          return
        case Chunk('1', data) =>
          processor.process(Event.fromBytes(Base64.getDecoder.decode(data)))
        case Chunk('2', data) =>
          //noinspection ReferencePassedToNls
          client.warning(String(data, StandardCharsets.UTF_8))
        case _ =>
