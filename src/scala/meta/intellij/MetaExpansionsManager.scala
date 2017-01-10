package scala.meta.intellij

import java.io.File
import java.lang.reflect.InvocationTargetException
import java.net.URL

import com.intellij.openapi.compiler.{CompilationStatusListener, CompileContext, CompilerManager, CompilerPaths}
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{ModuleRootManager, OrderEnumerator}
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.macroAnnotations.{CachedInsidePsiElement, ModCount}

import scala.meta.Tree
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

  def getCompiledMetaAnnotClass(annot: ScAnnotation): Option[Class[_]] = {

    def toUrl(f: VirtualFile) = new File(f.getPath.replaceAll("!", "")).toURI.toURL
    def outputDirs(module: Module) = (ModuleRootManager.getInstance(module).getDependencies :+ module)
      .map(m => CompilerPaths.getModuleOutputPath(m, false)).toList

    val annotClass = annot.constructor.reference.get.bind().map(_.parentElement.get)
    val metaModule = annotClass.flatMap(_.module)
    val classLoader = metaModule.map { m =>
      annotationClassLoaders.getOrElseUpdate(m.getName, {
        val cp: List[URL] = OrderEnumerator.orderEntries(m).getClassesRoots.toList.map(toUrl)
        val outDirs: List[URL] = outputDirs(m).map(str => new File(str).toURI.toURL)
        new URLClassLoader(outDirs ++ cp, this.getClass.getClassLoader)
      })
    }
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
      classFile.exists() && classFile.lastModified() >= sourceFile.lastModified()
    } catch {
      case pc: ProcessCanceledException => throw pc
      case _:Exception => false
    }
  }


  def runMetaAnnotation(annot: ScAnnotation): Either[String, Tree] = {

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
        val clazz = getCompiledMetaAnnotClass(annot)
        clazz match {
          case Some(outer) =>
            val ctor = outer.getDeclaredConstructors.head
            ctor.setAccessible(true)
            val inst = ctor.newInstance()
            val meth = outer.getDeclaredMethods.find(_.getName == "apply").get
            meth.setAccessible(true)
            try {
              val result = meth.invoke(inst, convertedAnnot.asInstanceOf[AnyRef], converted.asInstanceOf[AnyRef])
              Right(result.asInstanceOf[Tree])
            } catch {
              case pc: ProcessCanceledException => throw pc
              case e: InvocationTargetException => Left(e.getTargetException.toString)
              case e: Exception => Left(e.getMessage)
            }
          case None => Left("Meta annotation class could not be found")
        }
      } catch {
        case pc: ProcessCanceledException => throw pc
        case me: AbortException     => Left(s"Tree conversion error: ${me.getMessage}")
        case sm: ScalaMetaException => Left(s"Semantic error: ${sm.getMessage}")
        case so: StackOverflowError => Left(s"Stack overflow during expansion ${annotee.getText}")
        case e: Exception           => Left(s"Unexpected error during expansion: ${e.getMessage}")
      }
    }

    runMetaAnnotationsImpl
  }
}
