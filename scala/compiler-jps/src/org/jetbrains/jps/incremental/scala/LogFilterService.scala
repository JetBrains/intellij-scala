package org.jetbrains.jps.incremental.scala

import java.io.File

import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import org.jetbrains.jps.service.JpsServiceManager

import scala.jdk.CollectionConverters._

abstract class LogFilterService {
  def shouldLog(kind: Kind,
                text: String,
                source: Option[File],
                line: Option[Long],
                column: Option[Long]): Boolean
}

object LogFilter extends LogFilterService {
  private val filters = JpsServiceManager.getInstance
          .getExtensions(classOf[LogFilterService]).asScala


  override def shouldLog(kind: Kind,
                         text: String,
                         source: Option[File],
                         line: Option[Long],
                         column: Option[Long]): Boolean = {
    // We want to run shouldLog on all filters not just retrun fast
    val res = filters.map(_.shouldLog(kind, text, source, line, column)).toSeq

    !res.contains(false)
  }
}