package org.jetbrains.jps.incremental.scala.remote.play

import java.io._
import java.util.concurrent.{Executors, Future}

/**
 * User: Dmitry.Naydanov
 * Date: 12.02.15.
 */
class LocalSbtWatcherExec extends SbtWatcherExec {
  private val myExecutor = Executors.newSingleThreadExecutor()
  private var descriptor: Option[MyProcessDescriptor] = None
  private var state = false

  override def startSbtExec(args: Array[String], consumer: MessageConsumer) {
    if (isRunning) return

    val builder = new ProcessBuilder(args.tail: _*)
    builder.directory(new File(args.head))

    descriptor = Option(builder.start()) map {
      case p =>
        createDescriptor(p, consumer).startListening()
    }

    if (isRunning) state = true
  }

  override def endSbtExec() {
    if (!isRunning) return

    descriptor.foreach(_.stopListening())

    descriptor.foreach {
      case d =>
        val process = d.getProcess
        val writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream))

        writer.newLine()
        writer.flush()
    }

    state = false
  }

  override def isRunning: Boolean = descriptor.exists(_.isRunning)


  // core server related stuff
  private val MAGIC_SLEEP_TIME_MILLIS = 150

  private class ProcessListener(consumer: MessageConsumer, p: Process) {
    private val streamReader = new BufferedReader(new InputStreamReader(p.getInputStream))

    @volatile private var stop = false

    def startMain() = {
      try Executors.newSingleThreadExecutor().submit {
        new Runnable {
          override def run() {
            while (!stop) {
              readString()
              Thread.sleep(MAGIC_SLEEP_TIME_MILLIS) //interrupt point
            }
          }
        }
      } catch {
        case _: InterruptedException => //ignored
      }

      this
    }

    def stopMain() {
      stop = true
    }

    private def readString() {
      processMessage(streamReader.readLine())
    }

    private def processMessage(msg: String) {
      consumer consume msg
    }
  }

  private class MyProcessDescriptor(private val process: Process, private val watcher: Future[_], listener: ProcessListener) {
    def isRunning = !watcher.isDone

    def getProcess = process

    def startListening() = {listener.startMain(); this}

    def stopListening() = listener.stopMain()
  }

  private def createDescriptor(process: Process, consumer: MessageConsumer) = new MyProcessDescriptor (
    process,
    myExecutor.submit(new Runnable {
      override def run() {
        process.waitFor()
      }
    }), new ProcessListener(consumer, process)
  )
}
