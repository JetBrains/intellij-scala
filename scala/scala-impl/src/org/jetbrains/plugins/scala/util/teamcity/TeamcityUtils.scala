package org.jetbrains.plugins.scala.util.teamcity

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
object TeamcityUtils {

  sealed trait Status {
    import Status._
    def value: String = this match {
      case Normal  => "NORMAL"
      case Warning => "WARNING"
      case Failure => "FAILURE"
      case Error   => "ERROR"
    }
  }
  object Status {
    case object Normal extends Status
    case object Warning extends Status
    case object Failure extends Status
    case object Error extends Status
  }

  // https://www.jetbrains.com/help/teamcity/service-messages.html#Reporting+Messages+for+Build+Log
  def logUnderTeamcity(message: String, status: Status = Status.Normal): Unit = {
    val isUnderTeamcity = com.intellij.internal.statistic.utils.StatisticsUploadAssistant.isTeamcityDetected
    if (isUnderTeamcity) {
      val messageText = escapeTeamcityValue(message)
      val result = s"##teamcity[message text='$messageText' status='${status.value}']"
      println(result)
    }
  }

  // https://www.jetbrains.com/help/teamcity/service-messages.html#Reporting+Messages+for+Build+Log
  def escapeTeamcityValue(value: String): String =
    value
      .replace("|", "||")
      .replace("'", "|'")
      .replace("\n",  "|n")
      .replace("\r", " |r")
      .replace("[",  "|[")
      .replace("]",  "|]")
  //replace("\\uNNNN",  "|0xNNNN") // todo
}