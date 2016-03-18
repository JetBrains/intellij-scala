package org.jetbrains.plugins.scala.components.libinjection

import java.io.File

import com.intellij.openapi.module.Module
import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.plugins.scala.compiler.{RemoteServerConnectorBase, RemoteServerRunner}

import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer

/**
  * Created by mucianm on 18.03.16.
  */
class InjectorServerConnector(module: Module, filesToCompile: Seq[File], outputDir: File, val platformClasspath: Seq[File])
extends RemoteServerConnectorBase(module, filesToCompile, outputDir) {
  override val additionalCp: Seq[File] = platformClasspath

  val errors = ListBuffer[String]()

  val client = new Client {
    override def message(kind: Kind, text: String, source: Option[File], line: Option[Long], column: Option[Long]): Unit = {
      if (kind == Kind.ERROR) errors += text
    }
    override def deleted(module: File): Unit = {}
    override def progress(text: String, done: Option[Float]): Unit = {}
    override def isCanceled: Boolean = false
    override def debug(text: String): Unit = {}
    override def processed(source: File): Unit = {}
    override def trace(exception: Throwable): Unit = {}
    override def generated(source: File, module: File, name: String): Unit = {}
  }

  @tailrec
  private def classfiles(dir: File, namePrefix: String = ""): Array[(File, String)] = dir.listFiles() match {
    case Array(d) if d.isDirectory => classfiles(d, s"$namePrefix${d.getName}.")
    case files => files.map(f => (f, s"$namePrefix${f.getName}".stripSuffix(".class")))
  }

  def compile(): Either[Array[(File, String)], Seq[String]] = {
    val project = module.getProject

    val compilationProcess = new RemoteServerRunner(project).buildProcess(arguments, client)
    var result: Either[Array[(File, String)], Seq[String]] = Right(Seq("Compilation failed"))
    compilationProcess.addTerminationCallback {
      result = if (errors.nonEmpty) Right(errors) else Left(classfiles(outputDir))
    }
    compilationProcess.run()
    result
  }
}
