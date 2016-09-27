package org.jetbrains.plugins.scala.project.migration.api

import com.intellij.psi.PsiFile

/**
  * User: Dmitry.Naydanov
  * Date: 25.07.16.
  */
class MigrationReport() {
  import MigrationReport.{MyMessage, MessageType}
  
  def this(messages: MigrationReport.MyMessage*) {
    this()
    this.messages ++= messages
  }
  
  private val messages = scala.collection.mutable.ListBuffer[MyMessage]()
 
  def addMessage(category: MessageType, text: String, file: Option[PsiFile] = None, 
                 line: Option[Int] = None, column: Option[Int] = None): Unit = {
    addMessage(MyMessage(category, text, file, line, column))
  }
  
  def addMessage(message: MyMessage) {
    messages += message
  }
  
  def getAllMessages = messages.toList
}

object MigrationReport {
  //todo get rid of Psi
  case class MyMessage(category: MessageType, text: String, file: Option[PsiFile], line: Option[Int], column: Option[Int]) { 
    def summary: String = 
      s"[[${category.name}]]\n $text at ${file.map(_.getName).getOrElse("NO FILE")} (${line.getOrElse("NO LINE")}:${column.getOrElse("NO COLUMN")})"
  }
  
  sealed case class MessageType(code: Int, name: String)
  
  object Simple extends MessageType(1, "SIMPLE")
  object Statistics extends MessageType(2, "STATISTICS")
  object Information extends MessageType(3, "INFORMATION")
  object Error extends MessageType(4, "ERROR")
  object Warning extends MessageType(5, "WARNING")
  object Note extends MessageType(6, "NOTE")
  
  
  def createSingleMessageReport(category: MessageType, text: String, file: Option[PsiFile] = None,
                                line: Option[Int] = None, column: Option[Int] = None): MigrationReport = {
    new MigrationReport(MyMessage(category, text, file, line, column))
  }
}
