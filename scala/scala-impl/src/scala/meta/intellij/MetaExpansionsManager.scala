package scala.meta.intellij

import java.io._
import java.lang.reflect.InvocationTargetException
import java.net.URL

import com.intellij.openapi.compiler.{CompilationStatusListener, CompileContext, CompilerManager, CompilerPaths}
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.{ProcessCanceledException, ProgressManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.{ModuleRootManager, OrderEnumerator}
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.extensions
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScParameterizedTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTypeDefinition}
import org.jetbrains.plugins.scala.macroAnnotations.{CachedInsidePsiElement, ModCount}
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel.Scala_2_12

import scala.collection.JavaConverters._
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

  import MetaExpansionsManager.META_MAJOR_VERSION

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

  private def installCompilationListener(): Unit = {
    CompilerManager.getInstance(project).addCompilationStatusListener(compilationStatusListener)
  }

  private def uninstallCompilationListener(): Unit = {
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
    def projectOutputDirs(project: Project) = project.scalaModules.flatMap(sm => outputDirs(sm)).distinct.toList

    def classLoaderForModule(module: Module)(contextCP: Seq[URL]): URLClassLoader = {
      annotationClassLoaders.getOrElseUpdate(module.getName, {
        val dependencyCP: List[URL] = OrderEnumerator.orderEntries(module).getClassesRoots.toList.map(toUrl)
        val outDirs: List[URL] = projectOutputDirs(module.getProject).map(str => new File(str).toURI.toURL)
        val fullCP: immutable.Seq[URL] = outDirs ++ dependencyCP :+ pluginCP
        // a quick way to test for compatible meta version - check jar name in classpath
        if (annot.scalaLanguageLevelOrDefault == Scala_2_12 && dependencyCP.exists(_.toString.contains(s"trees_2.12-$META_MAJOR_VERSION")))
          new URLClassLoader(fullCP, getClass.getClassLoader)
        else if (annot.scalaLanguageLevelOrDefault == Scala_2_12)
          new MetaClassLoader(fullCP)
        else
          new MetaClassLoader(fullCP, incompScala = true)
      })
    }

    val annotClass: Option[ScClass] = for {
      ref <- annot.constructor.reference
      resolved <- ref.bind()
      parent <- resolved.parentElement.map(_.asInstanceOf[ScClass])
    } yield parent
    val metaModule = annotClass.flatMap(_.module)
    val contextCP = annot.module.map(m=>outputDirs(m).map(new File(_).toURI.toURL)).getOrElse(Nil)
    val classLoader = metaModule
      .map(classLoaderForModule(_)(contextCP))  // try annotation's own module first - if it exists as a part of rhe codebase
      .orElse(annot.module.map(classLoaderForModule(_)(contextCP))) // otherwise it's somewhere among current module dependencies
    try {
      annotClass.flatMap(clazz =>
        classLoader.map(  loader =>
          loader.loadClass(clazz.asInstanceOf[ScTypeDefinition].getQualifiedNameForDebugger + "$inline$")
        )
      )
    } catch {
      case p: ProcessCanceledException => throw p
      case _:  ClassNotFoundException => None
    }
  }
}

object MetaExpansionsManager {

  val META_MAJOR_VERSION = "1.8"

  class MetaWrappedException(val target: Throwable) extends Exception

  def getInstance(project: Project): MetaExpansionsManager = project.getComponent(classOf[MetaExpansionsManager])

  def getCompiledMetaAnnotClass(annot: ScAnnotation): Option[Class[_]] = getInstance(annot.getProject).getCompiledMetaAnnotClass(annot)

  def isUpToDate(annot: ScAnnotation): Boolean = getCompiledMetaAnnotClass(annot).exists(c => isUpToDate(annot, c))

  def isUpToDate(annot: ScAnnotation, clazz: Class[_]): Boolean = {
    try {
      val classFile = new File(clazz.getProtectionDomain.getCodeSource.getLocation.getPath, s"${clazz.getName.replaceAll("\\.", "/")}.class")
      val sourceFile = new File(annot.constructor.reference.get.resolve().getContainingFile.getVirtualFile.getPath)
      val isInJar = classFile.getPath.contains(".jar")
      isInJar || (classFile.exists() && classFile.lastModified() >= sourceFile.lastModified())
    } catch {
      case pc: ProcessCanceledException => throw pc
      case _:Exception => false
    }
  }


  def runMetaAnnotation(annot: ScAnnotation): Either[String, Tree] = {
    val holder = annot.parentOfType(classOf[ScAnnotationsHolder], strict = false).orNull

    @CachedInsidePsiElement(annot, ModCount.getBlockModificationCount)
    def runMetaAnnotationsImpl(annot: ScAnnotation): Either[String, Tree] = {

      val converter = new TreeConverter {
        override def getCurrentProject: Project = annot.getProject
        override def dumbMode: Boolean = true
        override protected val annotationToSkip: ScAnnotation = annot
      }

      try {
        val converted = converter.ideaToMeta(holder)
        val convertedAnnot = converter.toAnnotCtor(annot)
        val typeArgs = annot.typeElement match {
          case pe: ScParameterizedTypeElement => pe.typeArgList.typeArgs.map(converter.toType)
          case _ => Nil
        }
        val compiledArgs = convertedAnnot.asInstanceOf[AnyRef] +: typeArgs :+ converted.asInstanceOf[AnyRef]
        val maybeClass = getCompiledMetaAnnotClass(annot)
        ProgressManager.checkCanceled()
        val errorOrTree = (maybeClass, maybeClass.map(_.getClassLoader)) match {
          case (Some(clazz), Some(cl: MetaClassLoader)) => Right(runAdapterString(clazz, compiledArgs))
          case (Some(clazz), _) => Right(runDirect(clazz, compiledArgs))
          case (None, _)        => Left("Meta annotation class could not be found")
        }
        errorOrTree.right.map(fixTree)
      } catch {
        case pc: ProcessCanceledException => throw pc
        case me: AbortException           => Left(s"Tree conversion error: ${me.getMessage}")
        case sm: ScalaMetaException       => Left(s"Semantic error: ${sm.getMessage}")
        case so: StackOverflowError       => Left(s"Stack overflow during expansion ${holder.getText}")
        case mw: MetaWrappedException     => Left(mw.target.toString)
        case e: Exception                 => Left(e.getMessage)
      }
    }

    extensions.inReadAction(runMetaAnnotationsImpl(annot))
  }

  // use if meta versions are different within the same Scala major version
  // annotations runs inside a separate classloader to avoid conflicts of different meta versions on classpath
  // same Scala version allows use of java serialization which is faster than parsing trees from strings
  @deprecated("Seems to cause issues in 1.7.0", "1.7.0")
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
    val result = try {
      method.invoke(null, clazz, convertedArgs).toString
    } catch {
      case e: InvocationTargetException => throw e.getTargetException
    }
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
      method.invoke(inst, args: _*).asInstanceOf[Tree]
    } catch {
      case e: InvocationTargetException => throw new MetaWrappedException(e.getTargetException)
    }
  }

  // TODO: undo other paradise compatibility hacks
  def fixTree(tree: Tree): Tree = {
    import scala.meta._
    def fixParents(parents: immutable.Seq[Ctor.Call]) = parents.map({case Term.Apply(ctor: Ctor.Call, Nil) => ctor; case x=>x})
    tree transform {
      case Defn.Trait(mods, name, tparams, ctor, Template(early, parents, self, stats)) =>
        Defn.Trait(mods, name, tparams, ctor, Template(early, fixParents(parents), self, stats))
    }
  }
}
