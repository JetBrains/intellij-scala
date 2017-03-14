package org.jetbrains.jps.incremental.scala.local.worksheet

import java.io.{File, OutputStream}
import java.net.{URLClassLoader, URLDecoder}
import java.util

import com.intellij.openapi.util.io.FileUtil
import com.martiansoftware.nailgun.ThreadLocalPrintStream
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.jps.incremental.scala.data.{CompilerJars, SbtData}
import org.jetbrains.jps.incremental.scala.local.{CompilerFactoryImpl, NullLogger}
import org.jetbrains.jps.incremental.scala.remote.{Arguments, WorksheetOutputEvent}
import sbt.Path
import sbt.compiler.{AnalyzingCompiler, RawCompiler}

/**
  * User: Dmitry.Naydanov
  * Date: 12.03.17.
  */
class ILoopWrapperFactoryHandler {
  import ILoopWrapperFactoryHandler._
  
  private var replFactory: Option[(Class[_], Any)] = None

  def loadReplWrapperAndRun(commonArguments: Arguments, out: OutputStream, client: Option[Client]) {
    val compilerJars = commonArguments.compilerData.compilerJars.orNull
    
    replFactory match {
      case Some(_) =>
      case None =>
        val loader = createIsolatingClassLoader(getBaseJars(compilerJars))
        val clazz = loader.loadClass(REPL_FQN)
        replFactory = Option((clazz, clazz.newInstance()))
    }

    val iLoopFile = getOrCompileReplLoopFile(commonArguments.sbtData, compilerJars, client)

    client.foreach(_ progress "Running REPL...")
    
    replFactory foreach {
      case (clazz, instance) =>
        val m =
          clazz.getDeclaredMethod(
            "loadReplWrapperAndRun", 
            classOf[java.util.List[String]], classOf[String], classOf[File], classOf[File], 
            classOf[java.util.List[File]], classOf[java.util.List[File]], classOf[java.io.OutputStream], classOf[java.io.File])
        m.invoke(
          instance, scalaToJava(commonArguments.worksheetFiles),
          commonArguments.compilationData.sources.headOption.map(_.getName).getOrElse(""), compilerJars.library, compilerJars.compiler,
          scalaToJava(compilerJars.extra), scalaToJava(commonArguments.compilationData.classpath), out, iLoopFile
        )
    }
  }
  
  protected def getOrCompileReplLoopFile(sbtData: SbtData, compilerJars: CompilerJars, client: Option[Client]): File = {
    val scalaInstance = CompilerFactoryImpl.createScalaInstance(compilerJars)
    val home = sbtData.interfacesHome
    val interfaceJar = sbtData.interfaceJar

    val sourceJar = {
      val f = sbtData.sourceJar
      new File(f.getParent, "repl-interface-sources.jar")
    }

    val replLabel = s"repl-wrapper-${CompilerFactoryImpl.readScalaVersionIn(scalaInstance.loader).getOrElse("Undefined")}-${sbtData.javaClassVersion}.jar"
    val targetFile = new File(home, replLabel)

    if (!targetFile.exists()) {
      val log = NullLogger
      home.mkdirs()

      findContainingJar(this.getClass) foreach {
        thisJar =>
          client.foreach(_.progress("Compiling REPL runner..."))

          AnalyzingCompiler.compileSources(
            Seq(sourceJar), targetFile, Seq(interfaceJar, thisJar), replLabel,
            new RawCompiler(scalaInstance, sbt.ClasspathOptions.auto, log), log
          )
      }
    }


    targetFile
  }
}

object ILoopWrapperFactoryHandler {
  private val REPL_FQN = "org.jetbrains.jps.incremental.scala.local.worksheet.ILoopWrapperFactory"

  protected def findContainingJar(clazz: Class[_]): Option[File] = {
    val resource = clazz.getResource('/' + clazz.getName.replace('.', '/') + ".class")

    if (resource == null) return None

    val url = URLDecoder.decode(resource.toString.stripPrefix("jar:file:"), "UTF-8")
    val idx = url.indexOf(".jar!")
    if (idx == -1) return None

    Option(new File(url.substring(0, idx + 4))).filter(_.exists())
  }

  protected def getBaseJars(compilerJars: CompilerJars): Seq[File] = {
    val jars = compilerJars.library +: compilerJars.compiler +: compilerJars.extra

    findContainingJar(this.getClass) flatMap {
      jar1 =>
        findContainingJar(classOf[FileUtil]) flatMap (
          jar2 =>
            findContainingJar(classOf[ThreadLocalPrintStream]) flatMap (
              jar3 =>
                findContainingJar(classOf[WorksheetOutputEvent]) map (jar4 => jars :+ jar1 :+ jar2 :+ jar3 :+ jar4)
              )
          )
    } getOrElse jars
  }

  protected def createIsolatingClassLoader(fromJars: Seq[File]): URLClassLoader = {
    new URLClassLoader(Path.toURLs(fromJars), sbt.classpath.ClasspathUtilities.rootLoader)
  }

  //We need this method as scala std lib converts scala collections to its own wrappers with asJava method
  protected def scalaToJava[T](seq: Seq[T]): util.List[T] = {
    val al = new util.ArrayList[T]()
    seq.foreach(al.add)
    al
  }
}