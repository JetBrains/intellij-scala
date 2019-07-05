package org.jetbrains.plugins.scala.compiler.rt

import java.io.{BufferedReader, PrintWriter}

/**
 * Wrapper around scala interactive shell. This wrapper is required because in scala 2.13 ILoop was moved to another
 * package and the interface was changed. Due to we do not hav cross-compilation in Scala Pplugin we have do do some
 * reflection magic in order to create the correct ILoop instantiate.
 * <br><br>
 * NOTE: There is also another wrapper defined in: org.jetbrains.jps.incremental.scala.local.worksheet.ILoopWrapper
 * which has a different interface. It is not reused not to create extra dependencies. In future we could create some
 * common interface and move these wrappers to a separate module.
 */
trait ILoopWrapper {
  def process(args: List[String]): Unit
}

object ILoopWrapper {
  private val ClassName_212 = "scala.tools.nsc.interpreter.ILoop"
  private val ClassName_213 = "scala.tools.nsc.interpreter.shell.ILoop"

  def instance(in: BufferedReader, out: PrintWriter): ILoopWrapper = {
    val classLoader = this.getClass.getClassLoader

    try {
      classLoader.loadClass(ClassName_212)
      // NOTE: if we are here then scala version is < 2.13
      // we could use a more accurate way to determine current scala-library version, as it is done in
      // org.jetbrains.jps.incremental.scala.local.worksheet.ILoopWrapperFactoryHandler but this is enough for now
      new ILoopWrapper212Impl(in, out)
    } catch {
      case ex1: ClassNotFoundException =>
        try {
          val clazz = classLoader.loadClass(ClassName_213)
          new ILoopWrapper213Impl(in, out, clazz, classLoader)
        } catch {
          case ex2: ClassNotFoundException =>
            // not using interpolators to avoid implementation conflicts in different scala versions
            val message = "ILoop class could not be found:\n" + ex1.getMessage + "\n" + ex2.getMessage
            throw new ClassNotFoundException(message, ex2)
        }
    }
  }
}