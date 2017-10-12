package org.jetbrains.jps.incremental.scala.remote.play

import java.io.PrintStream

import com.intellij.util.Base64Converter
import com.martiansoftware.nailgun.NGContext
import org.jetbrains.jps.incremental.messages.BuildMessage
import org.jetbrains.jps.incremental.scala.remote.MessageEvent
import org.jetbrains.jps.incremental.scala.remote.play.WatcherCommands._

/**
 * User: Dmitry.Naydanov
 * Date: 12.02.15.
 */
object SbtWatcherMain {
  private var currentExec: Option[(SbtWatcherExec, Seq[String])] = None

  def nailMain(context: NGContext) {
    handle(context.getArgs.toSeq, context.out)
  }

  def main(args: Array[String]) {
    handle(args, System.out)
  }

  private def handle(arguments: Seq[String], out: PrintStream) {
    val messageConsumer = new MessageConsumer {
      override def consume(message: String) {
        out.write(Base64Converter.encode(MessageEvent(BuildMessage.Kind.INFO, message, None, None, None).toBytes).getBytes)
      }
    }

    arguments.head match {
      case START =>
        val argsTail = arguments.tail

        def run() {
          val watcher = new LocalSbtWatcherExec
          watcher.startSbtExec(argsTail.toArray, messageConsumer)

          if (watcher.isRunning) {
            currentExec = Some((watcher, argsTail))
          }
        }

        currentExec foreach {
          case ((watcher, args)) if args == argsTail =>
          case ((watcher, _)) =>
            watcher.endSbtExec()
            run()
          case _ => run()
        }
      case STOP => currentExec.foreach(a => a._1.endSbtExec())
      case IS_RUNNING => messageConsumer.consume(currentExec.map {a => toMessage(a._1.isRunning)} getOrElse FALSE)
      case _ =>
    }
  }
}
