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
import sbt.internal.inc.classpath.ClasspathUtilities
import sbt.internal.inc.{AnalyzingCompiler, RawCompiler}
import sbt.io.Path
import xsbti.compile.{ClasspathOptionsUtil, ScalaInstance}

/**
  * User: Dmitry.Naydanov
  * Date: 12.03.17.
  */
class ILoopWrapperFactoryHandler {
  import ILoopWrapperFactoryHandler._
  
  private var replFactory: Option[(Class[_], Any, String)] = None

  def loadReplWrapperAndRun(commonArguments: Arguments, out: OutputStream, client: Option[Client]) {
    val compilerJars = commonArguments.compilerData.compilerJars.orNull
    val scalaInstance = CompilerFactoryImpl.createScalaInstance(compilerJars)
    val iLoopFile = getOrCompileReplLoopFile(commonArguments.sbtData, scalaInstance, client)
    val scalaVersion = findScalaVersionIn(scalaInstance)
    
    replFactory match {
      case Some((_, _, oldVersion)) if oldVersion == scalaVersion =>
      case _ =>
        val loader = createIsolatingClassLoader(getBaseJars(compilerJars))
        val clazz = loader.loadClass(REPL_FQN)
        replFactory = Option((clazz, clazz.newInstance(), scalaVersion))
    }

    client.foreach(_ progress "Running REPL...")
    
    replFactory foreach {
      case (clazz, instance, _) =>
        WorksheetServer.patchSystemOut(out)

        val m =
          clazz.getDeclaredMethod(
            "loadReplWrapperAndRun", 
            classOf[java.util.List[String]], classOf[String], classOf[File], classOf[File], classOf[java.util.List[File]], 
            classOf[java.util.List[File]], classOf[java.io.OutputStream], classOf[java.io.File], classOf[Comparable[String]])
        
        withFilteredPath {
          m.invoke(
            instance, scalaToJava(commonArguments.worksheetFiles), commonArguments.compilationData.sources.headOption.map(_.getName).getOrElse(""),
            compilerJars.library, compilerJars.compiler, scalaToJava(compilerJars.extra), scalaToJava(commonArguments.compilationData.classpath),
            out, iLoopFile, if (client.isEmpty) null else new Comparable[String] {
              override def compareTo(o: String): Int = {client.get.progress(o) ; 0}
            }
          )
        }
    }
  }
  
  protected def getOrCompileReplLoopFile(sbtData: SbtData, scalaInstance: ScalaInstance, client: Option[Client]): File = {
    val home = sbtData.interfacesHome
    val interfaceJar = sbtData.compilerInterfaceJar

    val sourceJar = {
      val f = sbtData.sourceJars._2_11
      new File(f.getParent, "repl-interface-sources.jar")
    }

    val replLabel = 
      s"repl-wrapper-${findScalaVersionIn(scalaInstance)}-${sbtData.javaClassVersion}-$WRAPPER_VERSION.jar"
    val targetFile = new File(home, replLabel)

    if (!targetFile.exists()) {
      val log = NullLogger
      home.mkdirs()

      findContainingJar(this.getClass) foreach {
        thisJar =>
          client.foreach(_.progress("Compiling REPL runner..."))

          AnalyzingCompiler.compileSources(
            Seq(sourceJar), targetFile, Seq(interfaceJar, thisJar), replLabel,
            new RawCompiler(scalaInstance, ClasspathOptionsUtil.auto(), log), log
          )
      }
    }


    targetFile
  }
}

object ILoopWrapperFactoryHandler {
  private val WRAPPER_VERSION = 1
  private val REPL_FQN = "org.jetbrains.jps.incremental.scala.local.worksheet.ILoopWrapperFactory"

  private val JAVA_USER_CP_KEY = "java.class.path"
  private val STOP_WORDS = Set("scala-library.jar", "scala-nailgun-runner.jar", "nailgun.jar", "jpsShared.jar",
    "incremental-compiler.jar", "scala-jps-plugin.jar")


  private def withFilteredPath(action: => Unit) {
    val oldCp = System.getProperty(JAVA_USER_CP_KEY)

    if (oldCp == null) {
      action
      return
    }

    val newCp = oldCp.split(File.pathSeparatorChar).map(
      new File(_).getAbsoluteFile
    ).filter {
      file => file.exists() && !STOP_WORDS.contains(file.getName)
    }.map(_.getAbsolutePath).mkString(File.pathSeparator)

    System.setProperty(JAVA_USER_CP_KEY, newCp)
    
    try {
      action
    } finally {
      System.setProperty(JAVA_USER_CP_KEY, oldCp)
    }
  }
  
  private def findScalaVersionIn(scalaInstance: ScalaInstance): String = 
    CompilerFactoryImpl.readScalaVersionIn(scalaInstance.loader).getOrElse("Undefined")

  private def findContainingJar(clazz: Class[_]): Option[File] = {
    val resource = clazz.getResource('/' + clazz.getName.replace('.', '/') + ".class")

    if (resource == null) return None

    val url = URLDecoder.decode(resource.toString.stripPrefix("jar:file:"), "UTF-8")
    val idx = url.indexOf(".jar!")
    if (idx == -1) return None

    Option(new File(url.substring(0, idx + 4))).filter(_.exists())
  }

  private def findContainingJars(classes: Seq[Class[_]]): Seq[File] = {
    (Seq[File]() /: classes) {
      case (cur, cl) => findContainingJar(cl).map(cur :+ _) getOrElse cur
    }
  }

  private def getBaseJars(compilerJars: CompilerJars): Seq[File] = {
    val jars =
      compilerJars.library +: compilerJars.compiler +: compilerJars.extra
    val additionalJars =
      findContainingJars(Seq(this.getClass, classOf[FileUtil], classOf[ThreadLocalPrintStream], classOf[WorksheetOutputEvent]))
    
    jars ++ additionalJars
  }

  private def createIsolatingClassLoader(fromJars: Seq[File]): URLClassLoader = {
    new URLClassLoader(Path.toURLs(fromJars), ClasspathUtilities.rootLoader)
  }

  //We need this method as scala std lib converts scala collections to its own wrappers with asJava method
  private def scalaToJava[T](seq: Seq[T]): util.List[T] = {
    val al = new util.ArrayList[T]()
    seq.foreach(al.add)
    al
  }
}