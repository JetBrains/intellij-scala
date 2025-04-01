package org.jetbrains.sbt.shell

import play.api.libs.json.{JsArray, JsObject, JsValue, Json}

import scala.util.{Failure, Success, Try}

class ResponseProcesser extends Thread {
  override def run(): Unit = {
    while (!SbtServerClient.done) {
      try {
        val response: String = SbtServerClient.queue.take()
        processResponse(response)
      } catch {
        case _: InterruptedException => return
      }
    }
  }

  private def processResponse(response: String): Unit = {
    def parseJson(jsonStr: String): Either[Throwable, JsValue] = {
      Try(Json.parse(jsonStr)) match {
        case Success(json) => Right(json)
        case Failure(error) => Left(error)
      }
    }

    def handleCommandResponse(json: JsValue, id: String): Unit = {
      (json \ "result", json \ "error") match {
        case (result, _) if result.isDefined =>
          (result \ "capabilities") match {
            case cap if cap.isDefined =>
              println("Initialize request completed")
            case _ =>
          }
          (result \ "status") match {
            case status if status.isDefined =>
              status.asOpt[String].foreach { statusValue =>
                println(s"Status: $statusValue")
              }
            case _ =>
          }
        case (_, error) if error.isDefined =>
          val errorMessage = (error.get \ "message").asOpt[String].getOrElse("unknown")
          println(s"Error for request $id: $errorMessage")
        case _ =>
          println(s"Unknown response type: $response")
      }
    }

    def handleNotification(json: JsValue, method: String): Unit = {
      def extractParams(json: JsValue): JsObject = (json \ "params").as[JsObject]

      method match {
        case "build/logMessage" =>
          val message = (extractParams(json) \ "message").asOpt[String].getOrElse("")
          println(s"$message")

        case "build/taskStart" =>
          val params = extractParams(json)
          val taskId = (params \ "taskId" \ "id").asOpt[String].getOrElse("unknown")
          val message = (params \ "message").asOpt[String].getOrElse("unknown")
          println(s"Started task $message (ID: $taskId)")

        case "build/taskFinish" =>
          val params = extractParams(json)
          val taskId = (params \ "taskId" \ "id").asOpt[String].getOrElse("unknown")
          println(s"Finished task (ID: $taskId)")

        case "build/publishDiagnostics" =>
          processDiagnostics(extractParams(json))

        case "build/taskProgress" =>
          val params = extractParams(json)
          val taskId = (params \ "taskId" \ "id").asOpt[String].getOrElse("unknown")
          val message = (params \ "message").asOpt[String].getOrElse("unknown")
          val dataKind = (params \ "dataKind").asOpt[String].getOrElse("unknown")
          println(s"$dataKind: $message (ID: $taskId)")

        case _ =>
          println(s"-----Notification: $method------")
          println(s"  ${Json.prettyPrint(json)}")
      }
    }

    def processDiagnostics(params: JsObject): Unit = {
      val uri = (params \ "textDocument" \ "uri").asOpt[String].getOrElse("unknown")
      val diagnostics = (params \ "diagnostics").asOpt[JsArray].getOrElse(JsArray())

      diagnostics.value.foreach { diagnostic =>
        val severityMap = Map(
          1 -> "ERROR",
          2 -> "WARNING",
          3 -> "INFO",
          4 -> "HINT"
        )

        val severity = (diagnostic \ "severity").asOpt[Int]
          .flatMap(severityMap.get)
          .getOrElse("INFO")

        val message = (diagnostic \ "message").asOpt[String].getOrElse("")
        val line = (diagnostic \ "range" \ "start" \ "line").asOpt[Int].map(_ + 1).getOrElse(0)
        val character = (diagnostic \ "range" \ "start" \ "character").asOpt[Int].map(_ + 1).getOrElse(0)

        println(s"$severity in $uri at $line:$character - $message")
      }
    }

    // Main processing logic
    parseJson(response) match {
      case Right(json) =>
        (json \ "id", json \ "method") match {
          case (id, _) if id.isDefined =>
            handleCommandResponse(json, id.as[String])
          case (_, method) if method.isDefined =>
            handleNotification(json, method.as[String])
          case _ =>
            println(s"Unknown response format: $response")
        }
      case Left(error) =>
        println(s"Error parsing JSON response: ${error.getMessage}")
        println(s"Raw response: $response")
    }
  }
}
