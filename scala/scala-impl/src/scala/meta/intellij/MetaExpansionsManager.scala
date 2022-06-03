package scala.meta.intellij

import com.intellij.openapi.compiler._
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.{ProcessCanceledException, ProgressManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{ModuleRootManager, OrderEnumerator}
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.caches.BlockModificationTracker
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScParameterizedTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, ScAnnotationsHolder}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTypeDefinition}
import org.jetbrains.plugins.scala.macroAnnotations.CachedInUserData
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel.Scala_2_12
import org.jetbrains.plugins.scala.{NlsString, extensions}

import java.io._
import java.lang.reflect.InvocationTargetException
import java.net.URL
import scala.jdk.CollectionConverters._
import scala.meta.parsers.Parse
import scala.meta.trees.{AbortException, ScalaMetaException, TreeConverter}
import scala.meta.{Dialect, ScalaMetaBundle, Tree}
import scala.reflect.internal.util.ScalaClassLoader.URLClassLoader

// TODO: remove somewhere in 2022.1 / 2022.2 SCL-19637
class MetaExpansionsManager {
  import org.jetbrains.plugins.scala.project._

  import MetaExpansionsManager.META_MAJOR_VERSION

  private val annotationClassLoaders = new java.util.concurrent.ConcurrentHashMap[String, URLClassLoader]().asScala

  def invalidateModuleClassloader(module: Module): Option[URLClassLoader] = annotationClassLoaders.remove(module.getName)

  def getCompiledMetaAnnotClass(annot: ScAnnotation): Option[Class[_]] = {

    def toUrl(f: VirtualFile) = new File(f.getPath.replaceAll("!", "")).toURI.toURL
    def pluginCP: URL = {
      val resource = Option(getClass.getResource(".")).orElse(Option(getClass.getResource(""))).orNull
      if (resource == null)
        throw new AssertionError("Can't determine plugin classpath location")
      val url = resource.toString
        .replaceAll("^jar:", "")
        .replaceAll("!/.+$", "")
        .replaceAll(getClass.getPackage.getName.replace(".", "/") + "/$", "")
      new URL(url)
    }
    def outputDirs(module: Module) = (ModuleRootManager.getInstance(module).getDependencies :+ module)
      .map(m => CompilerPaths.getModuleOutputPath(m, false)).filter(_ != null).toList

    def projectOutputDirs(project: Project) = project.modulesWithScala.flatMap(outputDirs).distinct.toList

    def classLoaderForModule(module: Module): URLClassLoader = {
      annotationClassLoaders.getOrElseUpdate(module.getName, {
        val dependencyCP: List[URL] = OrderEnumerator.orderEntries(module).getClassesRoots.toList.map(toUrl)
        val outDirs = projectOutputDirs(module.getProject).map(str => new File(str).toURI.toURL)
        val fullCP = outDirs ++ dependencyCP :+ pluginCP
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
      ref <- annot.constructorInvocation.reference
      resolved <- ref.bind()
      parent <- resolved.parentElement.map(_.asInstanceOf[ScClass])
    } yield parent
    val metaModule = annotClass.flatMap(_.module)
    val classLoader = metaModule
      .map(classLoaderForModule)  // try annotation's own module first - if it exists as a part of rhe codebase
      .orElse(annot.module.map(classLoaderForModule)) // otherwise it's somewhere among current module dependencies
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

class ClearMetaExpansionManagerListener extends CompilationStatusListener {
  override def compilationFinished(aborted: Boolean, errors: Int, warnings: Int, context: CompileContext): Unit = {
    for {
      scope <- Option(context.getCompileScope)
      module <- scope.getAffectedModules
    } {
      MetaExpansionsManager.getInstance(module.getProject).invalidateModuleClassloader(module)
    }
  }
}

object MetaExpansionsManager {

  // TODO 2.13 Need update to 4.4.9?
  val META_MAJOR_VERSION  = "1.8"
  val META_MINOR_VERSION  = "1.8.0"
  val PARADISE_VERSION    = "3.0.0-M10"

  class MetaWrappedException(val target: Throwable) extends Exception

  def getInstance(project: Project): MetaExpansionsManager = project.getService(classOf[MetaExpansionsManager])

  def getCompiledMetaAnnotClass(annot: ScAnnotation): Option[Class[_]] = getInstance(annot.getProject).getCompiledMetaAnnotClass(annot)

  def isUpToDate(annot: ScAnnotation): Boolean = getCompiledMetaAnnotClass(annot).exists(c => isUpToDate(annot, c))

  def isUpToDate(annot: ScAnnotation, clazz: Class[_]): Boolean = {
    try {
      val classFile = new File(clazz.getProtectionDomain.getCodeSource.getLocation.getPath, s"${clazz.getName.replaceAll("\\.", "/")}.class")
      val sourceFile = new File(annot.constructorInvocation.reference.get.resolve().getContainingFile.getVirtualFile.getPath)
      val isInJar = classFile.getPath.contains(".jar")
      isInJar || (classFile.exists() && classFile.lastModified() >= sourceFile.lastModified())
    } catch {
      case pc: ProcessCanceledException => throw pc
      case _:Exception => false
    }
  }

  final case class MetaAnnotationError(message: NlsString, cause: Option[Throwable] = None)

  def runMetaAnnotation(annot: ScAnnotation): Either[MetaAnnotationError, Tree] = {
    val holder = annot.parentOfType(classOf[ScAnnotationsHolder], strict = false).orNull

    def err(message: NlsString, cause: Throwable = null) = MetaAnnotationError(message, Option(cause))

    @CachedInUserData(annot, BlockModificationTracker(annot))
    def runMetaAnnotationsImpl(annot: ScAnnotation): Either[MetaAnnotationError, Tree] = {
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
          case (Some(clazz), Some(_: MetaClassLoader)) => Right(runAdapterString(clazz, compiledArgs))
          case (Some(clazz), _) => Right(runDirect(clazz, compiledArgs))
          case (None, _)        => Left(err(ScalaMetaBundle.nls("meta.annotation.class.could.not.be.found")))
        }
        errorOrTree.map(fixTree)
      } catch {
        case e: ProcessCanceledException => throw e
        case e: AbortException           => Left(err(ScalaMetaBundle.nls("tree.conversion.error", e.getMessage), e))
        case e: ScalaMetaException       => Left(err(ScalaMetaBundle.nls("semantic.error", e.getMessage), e))
        case e: StackOverflowError       => Left(err(ScalaMetaBundle.nls("stack.overflow.during.expansion", holder.getText), e))
        case e: MetaWrappedException     => Left(err(NlsString.force(e.target.toString), e))
        case e: Exception                => Left(err(NlsString.force(e.getMessage), e))
        case e: Error                    => Left(err(ScalaMetaBundle.nls("internal.error.getmessage", e.getMessage), e)) // class loading issues?
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
      case e: InvocationTargetException =>
        throw new MetaWrappedException(e.getTargetException)
    }
  }

  // TODO: undo other paradise compatibility hacks
  def fixTree(tree: Tree): Tree = {
    import scala.meta._
    def fixParents(parents: List[Init]): List[Init] = parents.map {
      case Term.Apply(ctor: Init, Nil) => ctor;
      case x => x
    }
    tree transform {
      case c@Defn.Trait(_, _, _, _, t)  => c.copy(templ = t.copy(inits = fixParents(t.inits)))
      case c@Defn.Class(_, _, _, _, t)  => c.copy(templ = t.copy(inits = fixParents(t.inits)))
      case c@Defn.Object(_, _, t)       => c.copy(templ = t.copy(inits = fixParents(t.inits)))
    }
  }
}
