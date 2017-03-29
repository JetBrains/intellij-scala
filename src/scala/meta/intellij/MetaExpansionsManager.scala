package scala.meta.intellij

import java.io._
import java.lang.reflect.InvocationTargetException
import java.net.URL

import com.intellij.openapi.compiler.{CompilationStatusListener, CompileContext, CompilerManager, CompilerPaths}
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.{ProcessCanceledException, ProgressManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.{Library, LibraryUtil}
import com.intellij.openapi.roots.{ModuleRootManager, OrderEnumerator, OrderRootType}
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScParameterizedTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTemplateDefinition}
import org.jetbrains.plugins.scala.macroAnnotations.{CachedInsidePsiElement, ModCount}
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel.Scala_2_11
import org.jetbrains.plugins.scala.project._

import scala.meta.parsers.Parse
import scala.meta.{Dialect, Tree}
import scala.meta.trees.{AbortException, ScalaMetaException, TreeConverter}
import scala.reflect.internal.util.ScalaClassLoader.URLClassLoader

/**
  * @author Mikhail Mutcianko
  * @since 20.09.16
  */
class MetaExpansionsManager(project: Project) extends ProjectComponent {
  import org.jetbrains.plugins.scala.project._

  import scala.collection.convert.decorateAsScala._

  override def getComponentName = "MetaExpansionsManager"
  override def projectOpened(): Unit = installCompilationListener()
  override def projectClosed(): Unit = {
    uninstallCompilationListener()
    annotationClassLoaders.clear()
  }
  override def initComponent(): Unit = ()
  override def disposeComponent(): Unit = ()

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
    def outputDirs(module: Module) = (ModuleRootManager.getInstance(module).getDependencies :+ module)
      .map(m => CompilerPaths.getModuleOutputPath(m, false)).filter(_ != null).toList

    def classLoaderForModule(module: Module): URLClassLoader = {
      annotationClassLoaders.getOrElseUpdate(module.getName, {
        val cp: List[URL] = OrderEnumerator.orderEntries(module).getClassesRoots.toList.map(toUrl)
        val outDirs: List[URL] = outputDirs(module).map(str => new File(str).toURI.toURL)
        new URLClassLoader(outDirs ++ cp :+ getClass.getProtectionDomain.getCodeSource.getLocation, null)
      })
    }
    def classLoaderForEnclosingLibrary(annotClass: ScClass): URLClassLoader = {
      def classLoaderForLibrary(lib: Library): URLClassLoader = {
        annotationClassLoaders.getOrElseUpdate(lib.getName, {
          val libraryCP: Array[String] = lib.getUrls(OrderRootType.CLASSES)
          val metaCP: Seq[String] = annot.module
            .map(getMetaLibsForModule)
            .map(_.flatMap(_.getUrls(OrderRootType.CLASSES)))
            .getOrElse(Nil)
          val fullCP = libraryCP ++ metaCP :+ getClass.getProtectionDomain.getCodeSource.getLocation.toString
          new URLClassLoader(fullCP.map(u=>new URL("file:"+u.replaceAll("!/$", ""))), null)
        })
      }
      annotationClassLoaders.getOrElseUpdate(annotClass.qualifiedName, {
        val lib = LibraryUtil.findLibraryByClass(annotClass.qualifiedName, project)
        annotationClassLoaders.getOrElseUpdate(lib.getName, classLoaderForLibrary(lib))
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
        val compiledArgs = Seq(convertedAnnot.asInstanceOf[AnyRef]) ++ typeArgs :+ converted.asInstanceOf[AnyRef]
        val maybeClass = getCompiledMetaAnnotClass(annot)
        ProgressManager.checkCanceled()
        maybeClass match {
          case Some(clazz) if hasCompatibleScalaVersion => Right(runDirect(clazz, compiledArgs))
          case Some(clazz)                              => Right(runAdapterString(clazz, compiledArgs))
          case None                                     => Left("Meta annotation class could not be found")
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

  private def runAdapter(clazz: Class[_], args: Seq[AnyRef]): Tree = {
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

  private def runAdapterString(clazz: Class[_], args: Seq[AnyRef]): Tree = {
    val runner = clazz.getClassLoader.loadClass(classOf[MetaAnnotationRunner].getName)
    val method = runner.getDeclaredMethod("runString", classOf[Class[_]], classOf[Array[String]])
    val convertedArgs = args.map(_.toString).toArray
    val result = method.invoke(null, clazz, convertedArgs).toString
    val parsed = Parse.parseStat.apply(scala.meta.Input.String(result), Dialect.standards("Scala212"))
    parsed.getOrElse(throw new AbortException(s"Failed to parse result: $result"))
  }

  private def runDirect(clazz: Class[_], args: Seq[AnyRef]): Tree = {
    val ctor = clazz.getDeclaredConstructors.head
    ctor.setAccessible(true)
    val inst = ctor.newInstance()
    val meth = clazz.getDeclaredMethods.find(_.getName == "apply")
      .getOrElse(throw new RuntimeException(
        s"No 'apply' method in annotation class, declared methods:\n ${clazz.getDeclaredMethods.mkString("\n")}")
      )
    meth.setAccessible(true)
    meth.invoke(inst, args:_*).asInstanceOf[Tree]
  }
}
