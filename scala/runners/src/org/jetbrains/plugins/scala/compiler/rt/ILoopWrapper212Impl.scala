package org.jetbrains.plugins.scala.compiler.rt

import java.io.{BufferedReader, PrintWriter}

import scala.tools.nsc.GenericRunnerSettings
import scala.tools.nsc.interpreter.ILoop

// NOTE: this class will work while it is compiled with Scala 2.12, if we migrate to a newer version
// we need to rewrite it in a way similar to how ILoopWrapper212Impl is written
class ILoopWrapper212Impl private[rt](in: BufferedReader, out: PrintWriter) extends ILoopWrapper {

  override def process(args: List[String]): Unit = {
    val interpreterLoop = new ILoop(in, out)
    if (processWithNameMethod(interpreterLoop, args)) return

    val settings = new GenericRunnerSettings(_ => ())
    settings.processArguments(args, processAll = true)
    interpreterLoop.process(settings)
  }

  private def processWithNameMethod(interpreterLoop: AnyRef, args: Seq[String]): Boolean = {
    val methods = interpreterLoop.getClass.getMethods
    for (method <- methods) {
      if (method.getName == "name" && method.getParameterTypes.length == 1 && method.getParameterTypes.head.isArray) {
        method.invoke(interpreterLoop, args.toArray)
        return true
      }
    }
    false
  }
}