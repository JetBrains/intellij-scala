package org.jetbrains.sbt.shell

import play.api.libs.json.{JsObject, JsValue, Json}

sealed trait SbtCommand {
  def name: String

  def createCommand(id: String): JsValue
}

object SbtCommand {
  private case object Init extends SbtCommand {
    override def name: String = "init"
    override def createCommand(id: String): JsValue = createInitCommand(id)
  }

  private case object Compile extends SbtCommand {
    override def name: String = "compile"
    override def createCommand(id: String): JsValue = createExecCommand(id, "compile")
  }

  private case object Test extends SbtCommand {
    override def name: String = "test"
    override def createCommand(id: String): JsValue = createExecCommand(id, "test")
  }

  private case object Run extends SbtCommand {
    override def name: String = "run"
    override def createCommand(id: String): JsValue = createExecCommand(id, "run")
  }

  private case object Shutdown extends SbtCommand {
    override def name: String = "shutdown"
    override def createCommand(id: String): JsValue = createShutdownCommand(id)
  }

  def fromString(command: String): Option[SbtCommand] = command.toLowerCase match {
    case "init" => Some(Init)
    case "compile" => Some(Compile)
    case "test" => Some(Test)
    case "run" => Some(Run)
    case "shutdown" => Some(Shutdown)
    case _ => None
  }

  def availableCommands: List[String] = List(
    Init.name, Compile.name, Test.name, Run.name, Shutdown.name
  )

  def isValid(cmd: String): Boolean = availableCommands.contains(cmd)

  private def createInitCommand(id: String): JsObject = {
    Json.obj(
      "jsonrpc" -> "2.0",
      "id" -> id,
      "method" -> "initialize",
      "params" -> Json.obj(
        "initializationOptions" -> Json.obj()
      )
    )
  }

  private def createExecCommand(id: String, command: String): JsObject = {
    Json.obj(
      "jsonrpc" -> "2.0",
      "id" -> id,
      "method" -> "sbt/exec",
      "params" -> Json.obj(
        "commandLine" -> command
      )
    )
  }

  private def createShutdownCommand(id: String): JsObject = {
    Json.obj(
      "jsonrpc" -> "2.0",
      "id" -> id,
      "method" -> "shutdown",
      "params" -> Json.obj()
    )
  }
}
