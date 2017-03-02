package org.jetbrains.jps.incremental.scala.local.worksheet

import java.io.{File, OutputStream, PrintWriter}
import java.net.URLClassLoader
import java.util.regex.Pattern

import com.intellij.util.Base64
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.jps.incremental.scala.data.SbtData
import org.jetbrains.jps.incremental.scala.local.worksheet.ILoopWrapperFactory._
import org.jetbrains.jps.incremental.scala.local.worksheet.WorksheetServer.{MyUpdatePrintWriter, WorksheetArgs}
import org.jetbrains.jps.incremental.scala.local.{CompilerFactoryImpl, NullLogger}
import sbt.Path
import sbt.compiler.{AnalyzingCompiler, RawCompiler}
import xsbti.compile.ScalaInstance

import scala.collection.mutable

/**
  * User: Dmitry.Naydanov
  * Date: 27.01.17.
  */
class ILoopWrapperFactory {
  private val cache = new MySimpleCache(REPL_SESSION_LIMIT)

  /**
    * Main factory method 
    * 
    * @param sbtData holder for all data needed 
    * @param worksheetArgs worksheet specific extracted args; contains sessionId to be associated with wrapper. 
    *                      In current impl it is path to .sc file (for "real" files) or !SCRATCH!\filename for light worksheets 
    * @param outStream OutputStream that will be used for printing the result of input code execution
    * @param client Client for displaying progress (if we must compile wrapper class)
    * @return REPL wrapper for worksheet, cached or newly created
    */
  def loadReplWrapperAndRun(sbtData: SbtData, worksheetArgs: WorksheetArgs, outStream: OutputStream, client: Option[Client]): Unit = {
    worksheetArgs.replArgs foreach {
      replArgs =>
        client.foreach(_.progress("Retrieving REPL instance..."))
        val inst = cache.getOrCreate (
          replArgs.sessionId, 
          () => createILoopWrapper(sbtData, worksheetArgs, new MyUpdatePrintWriter(outStream), client), 
          (wrapper: ILoopWrapper) => wrapper.shutdown()
        )
        
        val out = inst.getOutputWriter match {
          case up: MyUpdatePrintWriter => 
            up.updateOut(outStream)
            up
          case other => other
        }
        
        client.foreach(_.progress("Worksheet execution started"))
        out.println(REPL_START)
        out.flush()

        val code = new String(Base64 decode replArgs.codeChunk, "UTF-8")
        val statements = code split Pattern.quote("\n$\n$\n")
        
        val stmtCount = statements.length
        var count = 1.0f
        
        def stmtProcessed() {
          client.foreach(_.progress("Executing worksheet...", Some(count / stmtCount)))
          count += 1
        }

        statements foreach {
          case CommandExtractor(Command(_, action)) => 
            stmtProcessed()
            action(inst)
          case codeChunk => 
            val shouldContinue = codeChunk.trim.length == 0 || inst.processChunk(codeChunk)

            stmtProcessed()
            
            out.println(REPL_CHUNK_END)
            out.flush()
            if (!shouldContinue) return 
        }
        
        out.println(REPL_LAST_CHUNK_PROCESSED)
        out.flush()
    }
  }
  
  private def createILoopWrapper(sbtData: SbtData, worksheetArgs: WorksheetArgs, out: PrintWriter, client: Option[Client]): ILoopWrapper = {
    val compilerJars = worksheetArgs.compilerJars
    val scalaInstance = CompilerFactoryImpl.createScalaInstance(compilerJars)
    val iLoopInheritor = getOrCompileReplLoopFile(sbtData, scalaInstance, client)
    
    val allJars = (compilerJars.library +: compilerJars.compiler +: compilerJars.extra) ++ worksheetArgs.outputDirs 
    val loader = new URLClassLoader(Path.toURLs(allJars :+ iLoopInheritor))
    
    val clazz = loader.loadClass("org.jetbrains.jps.incremental.scala.local.worksheet.ILoopWrapperImpl")
    val inst = clazz.getConstructor(classOf[PrintWriter], classOf[Iterable[String]]).newInstance(
      out, allJars ++ worksheetArgs.outputDirs ++ worksheetArgs.classpathUrls.map(u => new File(u.toURI)).filter(_.exists())
    ).asInstanceOf[ILoopWrapper]
    
    inst.init()
    inst
  }

  private def getOrCompileReplLoopFile(sbtData: SbtData, scalaInstance: ScalaInstance, client: Option[Client]): File = {
    val home = sbtData.interfacesHome
    val interfaceJar = sbtData.interfaceJar
    
    val sourceJar = {
      val f = sbtData.sourceJar
      new File(f.getParent, "repl-interface-sources.jar")
    }

    val replLabel = s"repl-wrapper-${CompilerFactoryImpl.readScalaVersionIn(scalaInstance.loader()).getOrElse("Undefined")}-${sbtData.javaClassVersion}.jar"
    val targetFile = new File(home, replLabel)

    if (!targetFile.exists()) {
      val log = NullLogger
      home.mkdirs()
      
      findContainingJar() foreach {
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
  
  private def findContainingJar(): Option[File] = {
    val resource = this.getClass.getResource('/' + this.getClass.getName.replace('.', '/') + ".class")
    
    if (resource == null) return None
    
    val url = resource.toString.stripPrefix("jar:file:")
    val idx = url.indexOf(".jar!")
    if (idx == -1) return None
    
    Option(new File(url.substring(0, idx + 4))).filter(_.exists())
  }
}

object ILoopWrapperFactory {
  private val REPL_START = "$$worksheet$$repl$$start$$"
  private val REPL_CHUNK_END = "$$worksheet$$repl$$chunk$$end$$"
  private val REPL_LAST_CHUNK_PROCESSED = "$$worksheet$$repl$$last$$chunk$$processed$$"
  
  //maximum count of repl sessions handled at any time 
  val REPL_SESSION_LIMIT = 5
  
  private class MySimpleCache(private val limit: Int) {
    private implicit val ordering = new ReplSessionOrdering
    private val sessions = mutable.PriorityQueue[ReplSession]()
    
    def getOrCreate(id: String, onCreation: () => ILoopWrapper, onDiscard: ILoopWrapper => Unit): ILoopWrapper = {
      val existingSession = sessions.find(_.id == id)
      
      existingSession.foreach {
        session => 
          ordering.inc(id)
          return session.wrapper
      }
      
      if (sessions.size >= limit) {
        val anOldSession = sessions.dequeue()
        
        onDiscard(anOldSession.wrapper)
        ordering.remove(anOldSession.id)
      }
      
      val newSession = ReplSession(id, onCreation())
      
      ordering.put(id)
      sessions.enqueue(newSession)
      
      newSession.wrapper
    }
    
    private class ReplSessionOrdering extends Ordering[ReplSession] {
      //session id -> count
      private val storage = mutable.HashMap[String, Int]()

      def inc(id: String) {
        storage.remove(id).map(_ + 1).map(v => storage.put(id, v))
      }

      def dec(id: String) {
        storage.remove(id).map(_ - 1).map(v => storage.put(id, v))
      }

      def put(id: String) {
        storage.put(id, 10)
      }

      def remove(id: String) {
        storage.remove(id)
      }

      override def compare(x: ReplSession, y: ReplSession): Int = {
        val vx = storage.getOrElse(x.id, return 1)
        val vy = storage.getOrElse(y.id, return -1)

        vy - vx
      }
    }

    private case class ReplSession(id: String, wrapper: ILoopWrapper)
  }
  
  case class Command(eqString: String, action: ILoopWrapper => Unit)
  
  object CommandExtractor {
    private val commands: Map[String, ILoopWrapper => Unit] = 
      Map(":reset" -> {(wrapper: ILoopWrapper) => wrapper.reset()})
    
    def unapply(arg: String): Option[Command] = commands.get(arg).map(action => Command(arg, action))  
  }
}
