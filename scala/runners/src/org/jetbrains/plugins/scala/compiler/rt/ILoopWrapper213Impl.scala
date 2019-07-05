package org.jetbrains.plugins.scala.compiler.rt

import java.io.{BufferedReader, PrintWriter}

import scala.collection.immutable.List
import scala.tools.nsc.GenericRunnerSettings

class ILoopWrapper213Impl(in: BufferedReader, out: PrintWriter, iLoopClass: Class[_], classLoader: ClassLoader) extends ILoopWrapper {

  //noinspection ConvertExpressionToSAM
  // Attention! Can't use lambda syntax in order to support scala  scala <= 2.12 and 2.13.
  // scala/Serializable was removed from 2.13 [[https://github.com/scala/scala/releases/tag/v2.13.0]]
  override def process(args: List[String]): Unit = {
    val settings = new GenericRunnerSettings(new Function[String, Unit] {
      override def apply(v1: String): Unit = ()
    })
    settings.processArguments(args, processAll = true)

    // Below we try to emulate following code via reflection:
    //   import scala.tools.nsc.interpreter.shell.{ILoop, ShellConfig}
    //   val interpreterLoop2 = new ILoop(ShellConfig(settings), in, out)
    //   interpreterLoop2.run(settings)
    val (interpreterLoop, runMethod) = try {
      val settingsClass = classLoader.loadClass("scala.tools.nsc.Settings")
      val shellConfigClass = classLoader.loadClass("scala.tools.nsc.interpreter.shell.ShellConfig")
      val applyMethod = shellConfigClass.getDeclaredMethod("apply", settingsClass)
      val shellConfig = applyMethod.invoke(null, settings)

      val constructor = iLoopClass.getConstructor(shellConfigClass, classOf[BufferedReader], classOf[PrintWriter])
      val runMethod = iLoopClass.getMethod("run", settingsClass)
      (constructor.newInstance(shellConfig, in, out), runMethod)
    } catch {
      case ex: ReflectiveOperationException =>
        throw new RuntimeException("ILoop could not be instantiated due to exception", ex)
    }

    runMethod.invoke(interpreterLoop, settings)
  }

}
