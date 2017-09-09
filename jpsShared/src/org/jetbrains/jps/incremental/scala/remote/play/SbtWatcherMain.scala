package org.jetbrains.jps.incremental.scala.remote.play

import java.io.{BufferedWriter, OutputStreamWriter, PrintStream}
import java.net.{InetAddress, Socket}

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
  private val IT_COMPLETED_MESSAGE = "Waiting for source changes... (press enter to interrupt)"
  private val MESSAGE_LIMIT = 12
  private val WAIT_TIME = 150
  private var currentExec: Option[(SbtWatcherExec, CachingMessageConsumer, Seq[String])] = None


  def nailMain(context: NGContext) {
    handle(context.getArgs.toSeq, context.out)
  }

  def main(args: Array[String]) {
    handle(args, System.out)
  }

  private def handle(arguments: Seq[String], out: PrintStream) {
    def write2source(message: String) {
      out.write(Base64Converter.encode(MessageEvent(BuildMessage.Kind.INFO, message, None, None, None).toBytes).getBytes)
    }

    def createConsumer(delegate: MessageConsumer) = new CachingMessageConsumer(delegate) {
      override protected def needFlush(msg: String, msgCount: Int): Boolean =
        msg.trim.endsWith(IT_COMPLETED_MESSAGE) || msgCount > MESSAGE_LIMIT
    }

    val decoded = arguments map Base64Converter.decode

    decoded.head match {
      case cm@(START | LOOP) =>
        val port = Integer.parseInt(decoded.tail.head)
        val argsTail = decoded.tail.tail

        def delegate = if (cm == START) new MessageConsumer {
          override def consume(message: String) {
            try {
              val sc = new Socket(InetAddress.getByName(null), port)
              val writer = new BufferedWriter(new OutputStreamWriter(sc.getOutputStream))
              writer.write(message)
              writer.flush()
              writer.close()
            }
            catch {
              case ex: Exception =>
            }
          }
        } else new MessageConsumer {
          override def consume(message: String) {
            val encoded = Base64Converter.encode(MessageEvent(BuildMessage.Kind.INFO, message, None, None, None).toBytes)
            out write encoded.getBytes
          }
        }

        def run() {
          val watcher = new LocalSbtWatcherExec

          val cons = createConsumer(delegate)

          watcher.startSbtExec(argsTail.toArray, cons)

          if (watcher.isRunning) {
            currentExec = Some((watcher, cons, argsTail))
          }
        }

        currentExec match {
          case Some((watcher, cons, args)) if watcher.isRunning && args.head == argsTail.head && cm == LOOP =>
            val oldDelegate = cons.delegate
            val newDelegate = delegate
            cons.delegate = newDelegate
            Thread.sleep(550)
            do {
              Thread.sleep(WAIT_TIME)
            } while (!cons.messages.isEmpty)

            cons.delegate = oldDelegate
          case Some((watcher, _, args)) if watcher.isRunning && args.head == argsTail.head =>
          case Some((watcher, _, _)) if watcher.isRunning =>
            watcher.endSbtExec()
            run()
          case _ => run()
        }
      case STOP => currentExec.foreach(a => a._1.endSbtExec())
      case IS_RUNNING => write2source(currentExec.map {a => toMessage(a._1.isRunning)} getOrElse FALSE)
      case _ =>
    }
  }
}
