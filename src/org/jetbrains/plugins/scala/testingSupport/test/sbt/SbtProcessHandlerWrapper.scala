package org.jetbrains.plugins.scala.testingSupport.test.sbt

import java.io.OutputStream

import com.intellij.execution.process.{OSProcessHandler, ProcessHandler, ProcessListener}

/** Process wrapper used to imitate termination of process so that 'stop' button in UI stops tests, but foes not kill
  * SBT shell.
  * @author Roman.Shein
  *         Date: 16.02.2017
  */
case class SbtProcessHandlerWrapper(inner: OSProcessHandler) extends ProcessHandler() {

  private var isTerminated: Boolean = false

  override def detachProcess(): Unit = detachProcessImpl()

  override def detachProcessImpl(): Unit = isTerminated = true

  override def destroyProcess(): Unit = destroyProcessImpl()

  override def isProcessTerminated: Boolean = isTerminated

  override def isProcessTerminating: Boolean = false

  override def notifyTextAvailable(text: String, outputType: com.intellij.openapi.util.Key[_]): Unit =
    inner.notifyTextAvailable(text, outputType)

  override def destroyProcessImpl(): Unit = isTerminated = true

  override def getProcessInput: OutputStream = inner.getProcessInput

  override def detachIsDefault(): Boolean = false

  override def addProcessListener(listener: ProcessListener) {
    inner.addProcessListener(listener)
  }

  override def removeProcessListener(listener: ProcessListener) {
    inner.removeProcessListener(listener)
  }
}
