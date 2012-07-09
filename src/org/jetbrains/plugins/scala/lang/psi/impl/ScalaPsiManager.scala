package org.jetbrains.plugins.scala
package lang
package psi
package impl

import api.statements.params.ScTypeParam
import com.intellij.openapi.components.ProjectComponent
import toplevel.synthetic.{SyntheticPackageCreator, ScSyntheticPackage}
import light.PsiClassWrapper
import toplevel.typedef.MixinNodes
import toplevel.typedef.TypeDefinitionMembers._
import types._
import com.intellij.openapi.util.Key
import com.intellij.ProjectTopics
import com.intellij.reference.SoftReference
import collection.Seq
import com.intellij.psi._
import com.intellij.util.containers.WeakValueHashMap
import impl.{JavaPsiFacadeImpl, PsiManagerEx}
import java.util.concurrent.{ConcurrentHashMap, ConcurrentMap}
import caches.ScalaShortNamesCacheManager
import api.toplevel.typedef.{ScTemplateDefinition, ScObject}
import extensions.toPsiNamedElementExt
import com.intellij.openapi.project.{DumbServiceImpl, Project}
import stubs.StubIndex
import psi.stubs.index.ScalaIndexKeys
import finder.ScalaSourceFilterScope
import com.intellij.openapi.vfs.VirtualFile
import util.PsiUtilCore
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.openapi.diagnostic.Logger
import collection.mutable.HashSet
import com.intellij.psi.search.{PsiShortNamesCache, GlobalSearchScope}
import java.util.{Collections, Map}
import com.intellij.openapi.roots.{ModuleRootEvent, ModuleRootListener}
import ParameterlessNodes.{Map => PMap}, TypeNodes.{Map => TMap}, SignatureNodes.{Map => SMap}

class ScalaPsiManager(project: Project) extends ProjectComponent {
  private val implicitObjectMap: ConcurrentMap[String, SoftReference[java.util.Map[GlobalSearchScope, Seq[ScObject]]]] =
    new ConcurrentHashMap()

  private val classMap: ConcurrentMap[String, SoftReference[Map[GlobalSearchScope, Option[PsiClass]]]] =
    new ConcurrentHashMap()

  private val classesMap: ConcurrentMap[String, SoftReference[Map[GlobalSearchScope, Array[PsiClass]]]] =
    new ConcurrentHashMap()

  private val classFacadeMap: ConcurrentMap[String, SoftReference[Map[GlobalSearchScope, Option[PsiClass]]]] =
    new ConcurrentHashMap()

  private val classesFacadeMap: ConcurrentMap[String, SoftReference[Map[GlobalSearchScope, Array[PsiClass]]]] =
    new ConcurrentHashMap()

  private val inheritorsMap: ConcurrentMap[PsiClass, SoftReference[ConcurrentMap[PsiClass, java.lang.Boolean]]] =
    new ConcurrentHashMap()

  private val scalaPackageClassesMap: ConcurrentMap[GlobalSearchScope, Array[PsiClass]] =
    new ConcurrentHashMap[GlobalSearchScope, Array[PsiClass]]

  private val javaPackageClassNamesMap: ConcurrentMap[(GlobalSearchScope, String), java.util.Set[String]] =
    new ConcurrentHashMap[(GlobalSearchScope, String), java.util.Set[String]]

  private val scalaPackageClassNamesMap: ConcurrentMap[(GlobalSearchScope, String), HashSet[String]] =
    new ConcurrentHashMap[(GlobalSearchScope, String), HashSet[String]]

  private val compoundTypesParameterslessNodes: ConcurrentMap[(ScCompoundType, Option[ScType]), SoftReference[PMap]] =
    new ConcurrentHashMap

  private val compoundTypesTypeNodes: ConcurrentMap[(ScCompoundType, Option[ScType]), SoftReference[TMap]] =
    new ConcurrentHashMap

  private val compoundTypesSignatureNodes: ConcurrentMap[(ScCompoundType, Option[ScType]), SoftReference[SMap]] =
    new ConcurrentHashMap

  def getParameterlessSignatures(tp: ScCompoundType, compoundTypeThisType: Option[ScType]): PMap = {
    val ref = compoundTypesParameterslessNodes.get(tp, compoundTypeThisType)
    var result: PMap = if (ref == null) null else ref.get()
    if (result == null) {
      result = ParameterlessNodes.build(tp, compoundTypeThisType)
      compoundTypesParameterslessNodes.put((tp, compoundTypeThisType), new SoftReference(result))
    }
    result
  }

  def getTypes(tp: ScCompoundType, compoundTypeThisType: Option[ScType]): TMap = {
    val ref = compoundTypesTypeNodes.get(tp, compoundTypeThisType)
    var result: TMap = if (ref == null) null else ref.get()
    if (result == null) {
      result = TypeNodes.build(tp, compoundTypeThisType)
      compoundTypesTypeNodes.put((tp, compoundTypeThisType), new SoftReference(result))
    }
    result
  }

  def getSignatures(tp: ScCompoundType, compoundTypeThisType: Option[ScType]): SMap = {
    val ref = compoundTypesSignatureNodes.get(tp, compoundTypeThisType)
    var result: SMap = if (ref == null) null else ref.get()
    if (result == null) {
      result = SignatureNodes.build(tp, compoundTypeThisType)
      compoundTypesSignatureNodes.put((tp, compoundTypeThisType), new SoftReference(result))
    }
    result
  }
  
  def cachedDeepIsInheritor(clazz: PsiClass, base: PsiClass): Boolean = {
    val ref = inheritorsMap.get(clazz)
    var map: ConcurrentMap[PsiClass, java.lang.Boolean] = null
    map = if (ref == null) null else ref.get()
    if (map == null) {
      map = new ConcurrentHashMap()
      inheritorsMap.put(clazz, new SoftReference(map))
    }

    val b = map.get(base)
    if (b != null) return b.booleanValue()

    val result = clazz.isInheritor(base, true)
    map.put(base, result)
    result
  }

  def getPackageImplicitObjects(fqn: String, scope: GlobalSearchScope): Seq[ScObject] = {
    def calc(): Seq[ScObject] = {
      ScalaShortNamesCacheManager.getInstance(project).getImplicitObjectsByPackage(fqn, scope)
    }

    val reference = implicitObjectMap.get(fqn)
    val map = if (reference == null || reference.get() == null) {
      val map = new ConcurrentHashMap[GlobalSearchScope, Seq[ScObject]]()
      map.put(scope, calc())
      implicitObjectMap.put(fqn, new SoftReference(map))
      map
    } else reference.get()
    var result = map.get(scope)
    if (result == null) {
      result = calc()
      map.put(scope, result)
    }
    result
  }
  


  def getCachedClass(scope: GlobalSearchScope, fqn: String): PsiClass = {
    def calc(): Option[PsiClass] = {
      val res = ScalaShortNamesCacheManager.getInstance(project).getClassByFQName(fqn, scope)
      if (res != null) return Some(res)
      Option(getCachedFacadeClass(scope, fqn))
    }

    val reference = classMap.get(fqn)
    val map = if (reference == null || reference.get() == null) {
      val map = new ConcurrentHashMap[GlobalSearchScope, Option[PsiClass]]()
      map.put(scope, calc())
      classMap.put(fqn, new SoftReference(map))
      map
    } else reference.get()
    var result = map.get(scope)
    if (result == null) {
      result = calc()
      map.put(scope, result)
    }
    result.getOrElse(null)
  }

  def getCachedFacadeClass(scope: GlobalSearchScope, fqn: String): PsiClass = {
    def calc(): Option[PsiClass] = {
      val clazz = JavaPsiFacade.getInstance(project).findClass(fqn, scope)
      if (clazz == null || clazz.isInstanceOf[ScTemplateDefinition] || clazz.isInstanceOf[PsiClassWrapper]) return None
      Option(clazz)
    }

    val reference = classFacadeMap.get(fqn)
    val map = if (reference == null || reference.get() == null) {
      val map = new ConcurrentHashMap[GlobalSearchScope, Option[PsiClass]]()
      map.put(scope, calc())
      classFacadeMap.put(fqn, new SoftReference(map))
      map
    } else reference.get()
    var result = map.get(scope)
    if (result == null) {
      result = calc()
      map.put(scope, result)
    }
    result.getOrElse(null)
  }

  def getClassesByName(name: String, scope: GlobalSearchScope): Seq[PsiClass] = {
    val scalaClasses = ScalaShortNamesCacheManager.getInstance(project).getClassesByName(name, scope)
    scalaClasses ++ PsiShortNamesCache.getInstance(project).getClassesByName(name, scope).filterNot(p =>
      p.isInstanceOf[ScTemplateDefinition] || p.isInstanceOf[PsiClassWrapper]
    )
  }

  import ScalaPsiManager.ClassCategory._
  def getCachedClass(fqn: String, scope: GlobalSearchScope, classCategory: ClassCategory): PsiClass = {
    val allClasses = getCachedClasses(scope, fqn)
    val classes = 
      classCategory match {
        case ALL => allClasses
        case OBJECT => allClasses.filter(_.isInstanceOf[ScObject])
        case TYPE => allClasses.filter(!_.isInstanceOf[ScObject])
      }
    if (classes.length == 0) null
    else classes(0)
  }

  def getClasses(pack: PsiPackage, scope: GlobalSearchScope): Array[PsiClass] = {
    def calc: Array[PsiClass] = {
      val classes =
        JavaPsiFacade.getInstance(project).asInstanceOf[JavaPsiFacadeImpl].getClasses(pack, scope).filterNot(p =>
          p.isInstanceOf[ScTemplateDefinition] || p.isInstanceOf[PsiClassWrapper]
        )
      val scalaClasses = ScalaShortNamesCacheManager.getInstance(project).getClasses(pack, scope)
      classes ++ scalaClasses
    }
    if (pack.getQualifiedName == "scala") {
      val packageClasses = scalaPackageClassesMap.get(scope)
      if (packageClasses == null) {
        val res = calc
        scalaPackageClassesMap.put(scope, res)
        return res
      } else return packageClasses
    }
    calc
  }

  def getCachedClasses(scope: GlobalSearchScope, fqn: String): Array[PsiClass] = {
    def calc(): Array[PsiClass] = {
      val classes = getCachedFacadeClasses(scope, fqn)
      val fromScala = ScalaShortNamesCacheManager.getInstance(project).getClassesByFQName(fqn, scope)
      if (classes.length == 0) {
        fromScala.toArray
      } else if (fromScala.length == 0) {
        classes
      } else {
        val res = new Array[PsiClass](classes.length + fromScala.length)
        System.arraycopy(classes, 0, res, 0, classes.length)
        System.arraycopy(fromScala.toArray, 0, res, classes.length, fromScala.length)
        res
      }
    }

    val reference = classesMap.get(fqn)
    val map = if (reference == null || reference.get() == null) {
      val map = new ConcurrentHashMap[GlobalSearchScope, Array[PsiClass]]()
      map.put(scope, calc())
      classesMap.put(fqn, new SoftReference(map))
      map
    } else reference.get()
    var result = map.get(scope)
    if (result == null) {
      result = calc()
      map.put(scope, result)
    }
    result
  }

  private def getCachedFacadeClasses(scope: GlobalSearchScope, fqn: String): Array[PsiClass] = {
    def calc(): Array[PsiClass] = {
      val classes = JavaPsiFacade.getInstance(project).findClasses(fqn, scope).filterNot(p =>
        p.isInstanceOf[ScTemplateDefinition] || p.isInstanceOf[PsiClassWrapper]
      )
      classes
    }

    val reference = classesFacadeMap.get(fqn)
    val map = if (reference == null || reference.get() == null) {
      val map = new ConcurrentHashMap[GlobalSearchScope, Array[PsiClass]]()
      map.put(scope, calc())
      classesFacadeMap.put(fqn, new SoftReference(map))
      map
    } else reference.get()
    var result = map.get(scope)
    if (result == null) {
      result = calc()
      map.put(scope, result)
    }
    result
  }

  import java.util.{Set => JSet}
  def getJavaPackageClassNames(psiPackage: PsiPackage, scope: GlobalSearchScope): JSet[String] = {
    val qualifier: String = psiPackage.getQualifiedName
    def calc: JSet[String] = {
      if (DumbServiceImpl.getInstance(project).isDumb) return Collections.emptySet()
      val classes = StubIndex.getInstance.get(ScalaIndexKeys.JAVA_CLASS_NAME_IN_PACKAGE_KEY, qualifier, project,
        new ScalaSourceFilterScope(scope, project))
      import java.util.HashSet
      val strings: HashSet[String] = new HashSet[String]
      val classesIterator = classes.iterator()
      while (classesIterator.hasNext) {
        val element = classesIterator.next()
        if (!(element.isInstanceOf[PsiClass])) {
          val faultyContainer: VirtualFile = PsiUtilCore.getVirtualFile(element)
          ScalaPsiManager.LOG.error("Wrong Psi in Psi list: " + faultyContainer)
          if (faultyContainer != null && faultyContainer.isValid) {
            FileBasedIndex.getInstance.requestReindex(faultyContainer)
          }
          return null
        }
        val clazz: PsiClass = element
        strings add clazz.getName
        clazz match {
          case t: ScTemplateDefinition =>
            for (name <- t.additionalJavaNames) strings add name
          case _ =>
        }
      }
      strings
    }
    var res = javaPackageClassNamesMap.get(scope, qualifier)
    if (res == null) {
      res = calc
      javaPackageClassNamesMap.put((scope, qualifier), res)
    }
    res
  }

  def getScalaClassNames(psiPackage: PsiPackage, scope: GlobalSearchScope): HashSet[String] = {
    val qualifier: String = psiPackage.getQualifiedName
    def calc: HashSet[String] = {
      if (DumbServiceImpl.getInstance(project).isDumb) return HashSet.empty
      val classes = StubIndex.getInstance.get(ScalaIndexKeys.CLASS_NAME_IN_PACKAGE_KEY, qualifier, project,
        new ScalaSourceFilterScope(scope, project))
      var strings: HashSet[String] = new HashSet[String]
      val classesIterator = classes.iterator()
      while (classesIterator.hasNext) {
        val element = classesIterator.next()
        if (!(element.isInstanceOf[PsiClass])) {
          val faultyContainer: VirtualFile = PsiUtilCore.getVirtualFile(element)
          ScalaPsiManager.LOG.error("Wrong Psi in Psi list: " + faultyContainer)
          if (faultyContainer != null && faultyContainer.isValid) {
            FileBasedIndex.getInstance.requestReindex(faultyContainer)
          }
          return null
        }
        val clazz: PsiClass = element
        strings += clazz.name
      }
      strings
    }
    var res = scalaPackageClassNamesMap.get(scope, qualifier)
    if (res == null) {
      res = calc
      scalaPackageClassNamesMap.put((scope, qualifier), res)
    }
    res
  }

  def projectOpened() {}
  def projectClosed() {
  }
  def getComponentName = "ScalaPsiManager"
  def disposeComponent() {}
  def initComponent() {
    PsiManager.getInstance(project).asInstanceOf[PsiManagerEx].registerRunnableToRunOnAnyChange(new Runnable {
      override def run() {
        syntheticPackages.clear()
      }
    })

    PsiManager.getInstance(project).asInstanceOf[PsiManagerEx].registerRunnableToRunOnChange(new Runnable {
      def run() {
        implicitObjectMap.clear()
        classMap.clear()
        classesMap.clear()
        classFacadeMap.clear()
        classesFacadeMap.clear()
        inheritorsMap.clear()
        scalaPackageClassesMap.clear()
        javaPackageClassNamesMap.clear()
        scalaPackageClassNamesMap.clear()
        compoundTypesParameterslessNodes.clear()
        compoundTypesSignatureNodes.clear()
        compoundTypesTypeNodes.clear()
        Conformance.cache.clear()
      }
    })

    project.getMessageBus.connect.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener {
      def beforeRootsChange(event: ModuleRootEvent) {
      }

      def rootsChanged(event: ModuleRootEvent) {
        implicitObjectMap.clear()
        classMap.clear()
        classesMap.clear()
        classFacadeMap.clear()
        classesFacadeMap.clear()
        inheritorsMap.clear()
        scalaPackageClassesMap.clear()
        javaPackageClassNamesMap.clear()
        scalaPackageClassNamesMap.clear()
        compoundTypesParameterslessNodes.clear()
        compoundTypesSignatureNodes.clear()
        compoundTypesTypeNodes.clear()
        Conformance.cache.clear()
      }
    })
  }

  private val syntheticPackagesCreator = new SyntheticPackageCreator(project)
  private val syntheticPackages = new WeakValueHashMap[String, Any]

  def syntheticPackage(fqn : String) : ScSyntheticPackage = {
    var p = syntheticPackages.get(fqn)
    if (p == null) {
      p = syntheticPackagesCreator.getPackage(fqn)
      if (p == null) p = Null
      synchronized {
        val pp = syntheticPackages.get(fqn)
        if (pp == null) {
          syntheticPackages.put(fqn, p)
        } else {
          p = pp
        }
      }
    }

    p match {case synth : ScSyntheticPackage => synth case _ => null}
  }

  def typeVariable(tp: PsiTypeParameter) : ScTypeParameterType = {
    import Misc.fun2suspension
    tp match {
      case stp: ScTypeParam => {
        val inner = stp.typeParameters.map{typeVariable(_)}.toList
        val lower = () => stp.lowerBound.getOrNothing
        val upper = () => stp.upperBound.getOrAny
        // todo rework for error handling!
        val res = new ScTypeParameterType(stp.name, inner, lower, upper, stp)
        res
      }
      case _ => {
        val lower = () => Nothing
        val upper = () => tp.getSuperTypes match {
          case array: Array[PsiClassType] if array.length == 1 => ScType.create(array(0), project)
          case many => new ScCompoundType(collection.immutable.Seq(many.map {
            ScType.create(_, project)
          }.toSeq: _*),
            Seq.empty, Seq.empty, ScSubstitutor.empty)
        }
        val res = new ScTypeParameterType(tp.name, Nil, lower, upper, tp)
        res
      }
    }
  }
}

object ScalaPsiManager {
  private val LOG: Logger = Logger.getInstance("#org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager")

  val TYPE_VARIABLE_KEY: Key[ScTypeParameterType] = Key.create("type.variable.key")

  def instance(project : Project) = project.getComponent(classOf[ScalaPsiManager])

  def typeVariable(tp : PsiTypeParameter): ScTypeParameterType = instance(tp.getProject).typeVariable(tp)

  object ClassCategory extends Enumeration {
    type ClassCategory = Value
    val ALL, OBJECT, TYPE = Value
  }
}