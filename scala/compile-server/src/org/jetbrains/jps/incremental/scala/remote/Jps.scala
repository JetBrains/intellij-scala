package org.jetbrains.jps.incremental.scala.remote

import org.jetbrains.jps.incremental.scala.Client

import java.nio.file.Path
import java.util.ServiceLoader

private object Jps {
  // Cached so that all requests share one JpsFacadeImpl, whose projectLock serializes builds of the same project.
  // If the initializer throws, nothing is cached and the lookup is retried on the next request.
  private lazy val facade: JpsFacade = {
    val jpsFacadeClass = classOf[JpsFacade]
    val loader = ServiceLoader.load(jpsFacadeClass, jpsFacadeClass.getClassLoader)
    val iterator = loader.iterator()
    if (iterator.hasNext) iterator.next()
    else throw new IllegalStateException(
      s"No implementation of ${jpsFacadeClass.getName} found on the compile server classpath (is compiler-jps.jar missing?)"
    )
  }

  def compileJpsLogic(command: CompileServerCommand.CompileJps, client: Client, jpsBuildSystemDir: Path): Unit = {
    facade.compileJpsLogic(command, client, jpsBuildSystemDir)
  }
}
