package scala.meta.intellij

import java.io._
import java.lang.reflect.InvocationTargetException
import java.net.URL

import com.intellij.openapi.compiler.{CompilationStatusListener, CompileContext, CompilerManager, CompilerPaths}
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.{ProcessCanceledException, ProgressManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.{ModuleRootManager, OrderEnumerator}
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScParameterizedTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTemplateDefinition}
import org.jetbrains.plugins.scala.macroAnnotations.{CachedInsidePsiElement, ModCount}
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel.Scala_2_11
import org.jetbrains.plugins.scala.project._

import scala.collection.immutable
import scala.meta.parsers.Parse
import scala.meta.trees.{AbortException, ScalaMetaException, TreeConverter}
import scala.meta.{Dialect, Tree}
import scala.reflect.internal.util.ScalaClassLoader.URLClassLoader

/**
  * @author Mikhail Mutcianko
  * @since 20.09.16
  */
class MetaExpansionsManager(project: Project) extends AbstractProjectComponent(project)  {
  import org.jetbrains.plugins.scala.project._

  import scala.collection.convert.decorateAsScala._

  override def getComponentName = "MetaExpansionsManager"

  override def projectOpened(): Unit = installCompilationListener()

  override def projectClosed(): Unit = {
    uninstallCompilationListener()
    annotationClassLoaders.clear()
  }

  private val annotationClassLoaders = new java.util.concurrent.ConcurrentHashMap[String, URLClassLoader]().asScala

  private val compilationStatusListener = new CompilationStatusListener {
    override def compilationFinished(aborted: Boolean, errors: Int, warnings: Int, context: CompileContext): Unit = {
      for {
        scope <- Option(context.getCompileScope)
        module <- scope.getAffectedModules
      } {
        invalidateModuleClassloader(module)
      }
    }
  }

  private def installCompilationListener() = {
    CompilerManager.getInstance(project).addCompilationStatusListener(compilationStatusListener)
  }

  private def uninstallCompilationListener() = {
    CompilerManager.getInstance(project).removeCompilationStatusListener(compilationStatusListener)
  }

  def invalidateModuleClassloader(module: Module): Option[URLClassLoader] = annotationClassLoaders.remove(module.getName)

  def getMetaLibsForModule(module: Module): Seq[Library] = {
    module.libraries.filter(_.getName.contains("org.scalameta")).toSeq
  }

  def getCompiledMetaAnnotClass(annot: ScAnnotation): Option[Class[_]] = {

    def toUrl(f: VirtualFile) = new File(f.getPath.replaceAll("!", "")).toURI.toURL
    def pluginCP = new URL(getClass.getResource(".").toString
      .replaceAll("^jar:", "")
      .replaceAll("!/.+$", "")
      .replaceAll(getClass.getPackage.getName.replace(".", "/") + "/$", ""))
    def outputDirs(module: Module) = (ModuleRootManager.getInstance(module).getDependencies :+ module)
      .map(m => CompilerPaths.getModuleOutputPath(m, false)).filter(_ != null).toList

    def classLoaderForModule(module: Module): URLClassLoader = {
      annotationClassLoaders.getOrElseUpdate(module.getName, {
        val cp: List[URL] = OrderEnumerator.orderEntries(module).getClassesRoots.toList.map(toUrl)
        val outDirs: List[URL] = outputDirs(module).map(str => new File(str).toURI.toURL)
        val fullCP: immutable.Seq[URL] = outDirs ++ cp :+ pluginCP
        // a quick way to test for compatible meta version - check jar name in classpath
        if (annot.scalaLanguageLevelOrDefault == Scala_2_11 && cp.exists(_.toString.contains("trees_2.11-1.6")))
          new URLClassLoader(fullCP, getClass.getClassLoader)
        else if (annot.scalaLanguageLevelOrDefault == Scala_2_11)
          new MetaClassLoader(fullCP)
        else
          new MetaClassLoader(fullCP, incompScala = true)
      })
    }

    val annotClass = annot.constructor.reference.get.bind().map(_.parentElement.get.asInstanceOf[ScClass])
    val metaModule = annotClass.flatMap(_.module)
    val classLoader = metaModule
      .map(classLoaderForModule)  // try annotation's own module first - if it exists as a part of rhe codebase
      .orElse(annot.module.map(classLoaderForModule)) // otherwise it's somwere among current module dependencies
    try {
      classLoader.map(_.loadClass(annotClass.get.asInstanceOf[ScTemplateDefinition].qualifiedName + "$inline$"))
    } catch {
      case _:  ClassNotFoundException => None
    }
  }
}

object MetaExpansionsManager {

  private val LOG = Logger.getInstance(getClass)

  def getInstance(project: Project): MetaExpansionsManager = project.getComponent(classOf[MetaExpansionsManager]).asInstanceOf[MetaExpansionsManager]

  def getCompiledMetaAnnotClass(annot: ScAnnotation): Option[Class[_]] = getInstance(annot.getProject).getCompiledMetaAnnotClass(annot)

  def isUpToDate(annot: ScAnnotation): Boolean = getCompiledMetaAnnotClass(annot).exists(c => isUpToDate(annot, c))

  def isUpToDate(annot: ScAnnotation, clazz: Class[_]): Boolean = {
    try {
      val classFile = new File(clazz.getProtectionDomain.getCodeSource.getLocation.getPath, s"${clazz.getName.replaceAll("\\.", "/")}.class")
      val sourceFile = new File(annot.constructor.reference.get.resolve().getContainingFile.getVirtualFile.getPath)
      val isInJar = classFile.getPath.contains(".jar/")
      isInJar || (classFile.exists() && classFile.lastModified() >= sourceFile.lastModified())
    } catch {
      case pc: ProcessCanceledException => throw pc
      case _:Exception => false
    }
  }


  def runMetaAnnotation(annot: ScAnnotation): Either[String, Tree] = {

    def hasCompatibleScalaVersion = annot.scalaLanguageLevelOrDefault == Scala_2_11

    @CachedInsidePsiElement(annot, ModCount.getModificationCount)
    def runMetaAnnotationsImpl: Either[String, Tree] = {

      val copiedAnnot = annot.getContainingFile.copy().findElementAt(annot.getTextOffset).getParent

      val converter = new TreeConverter {
        override def getCurrentProject: Project = annot.getProject
        override def dumbMode: Boolean = true
      }

      val annotee: ScAnnotationsHolder = ScalaPsiUtil.getParentOfType(copiedAnnot, classOf[ScAnnotationsHolder])
        .asInstanceOf[ScAnnotationsHolder]

      annotee.annotations.find(_.getText == annot.getText).foreach(_.delete())
      try {
        val converted = converter.ideaToMeta(annotee)
        val convertedAnnot = converter.toAnnotCtor(annot)
        val typeArgs = annot.typeElement match {
          case pe: ScParameterizedTypeElement => pe.typeArgList.typeArgs.map(converter.toType)
          case _ => Nil
        }
        val compiledArgs = convertedAnnot.asInstanceOf[AnyRef] +: typeArgs :+ converted.asInstanceOf[AnyRef]
        val maybeClass = getCompiledMetaAnnotClass(annot)
        ProgressManager.checkCanceled()
        (maybeClass, maybeClass.map(_.getClassLoader)) match {
          case (Some(clazz), Some(cl:MetaClassLoader)) if cl.incompScala => Right(runAdapterString(clazz, compiledArgs))
          case (Some(clazz), Some(_:MetaClassLoader))  => Right(runAdapterBinary(clazz, compiledArgs))
          case (Some(clazz), _)                        => Right(runDirect(clazz, compiledArgs))
          case (None, _)                               => Left("Meta annotation class could not be found")
        }
      } catch {
        case pc: ProcessCanceledException => throw pc
        case me: AbortException           => Left(s"Tree conversion error: ${me.getMessage}")
        case sm: ScalaMetaException       => Left(s"Semantic error: ${sm.getMessage}")
        case so: StackOverflowError       => Left(s"Stack overflow during expansion ${annotee.getText}")
        case e: InvocationTargetException => Left(e.getTargetException.getMessage)
        case e: Exception                 => Left(s"Unexpected error during expansion: ${e.getMessage}")
      }
    }

    runMetaAnnotationsImpl
  }

  // use if meta versions are different within the same Scala major version
  // annotations runs inside a separate classloader to avoid conflicts of different meta versions on classpath
  // same Scala version allows use of java serialization which is faster than parsing trees from strings
  private def runAdapterBinary(clazz: Class[_], args: Seq[AnyRef]): Tree = {
    val runner = clazz.getClassLoader.loadClass(classOf[MetaAnnotationRunner].getName)
    val method = runner.getDeclaredMethod("run", classOf[Class[_]], Integer.TYPE, classOf[Array[Byte]])
    val arrayOutputStream = new ByteArrayOutputStream(2048)
    var objectOutputStream: ObjectOutputStream = null
    try {
      objectOutputStream = new ObjectOutputStream(arrayOutputStream)
      args.foreach(objectOutputStream.writeObject)
      val argc = args.size.asInstanceOf[AnyRef]
      val data = method.invoke(null, clazz, argc, arrayOutputStream.toByteArray).asInstanceOf[Array[Byte]]
      var resultInputStream: ObjectInputStream = null
      try {
        resultInputStream = new ObjectInputStream(new ByteArrayInputStream(data))
        val res = resultInputStream.readObject()
        res.asInstanceOf[Tree]
      } finally {
        resultInputStream.close()
      }
    } finally {
      objectOutputStream.close()
    }
  }

  // use if major Scala versions are different - binary serializations is unavaliable, using String parsing insead
  // parsing trees is very expensive - so this the most performance costly method and should be disableable
  private def runAdapterString(clazz: Class[_], args: Seq[AnyRef]): Tree = {
    val runner = clazz.getClassLoader.loadClass(classOf[MetaAnnotationRunner].getName)
    val method = runner.getDeclaredMethod("runString", classOf[Class[_]], classOf[Array[String]])
    val convertedArgs = args.map(_.toString).toArray
    val result = method.invoke(null, clazz, convertedArgs).toString
    val parsed = Parse.parseStat.apply(scala.meta.Input.String(result), Dialect.standards("Scala212"))
    parsed.getOrElse(throw new AbortException(s"Failed to parse result: $result"))
  }

  // if both scala.meta and Scala major versions are compatible, we can invoke the annotation directly
  private def runDirect(clazz: Class[_], args: Seq[AnyRef]): Tree = {
    val ctor = clazz.getDeclaredConstructors.head
    ctor.setAccessible(true)
    val inst = ctor.newInstance()
    val method = clazz.getDeclaredMethods.find(_.getName == "apply")
      .getOrElse(throw new RuntimeException(
        s"No 'apply' method in annotation class, declared methods:\n ${clazz.getDeclaredMethods.mkString("\n")}")
      )
    method.setAccessible(true)
    try {
      method.invoke(inst, args.map(_.asInstanceOf[scala.meta.Stat]): _*).asInstanceOf[Tree]
    } catch {
      // rewrap exception to mimic adapter behaviour
      case e: InvocationTargetException =>
        throw new InvocationTargetException(new RuntimeException(e.getTargetException.toString))
    }
  }
}
