package org.jetbrains.plugins.scala
package lang
package psi
package impl

import java.util
import java.util.Collections
import java.util.concurrent.{ConcurrentHashMap, ConcurrentMap}

import com.intellij.ProjectTopics
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.roots.{ModuleRootEvent, ModuleRootListener}
import com.intellij.openapi.util.{Key, LowMemoryWatcher}
import com.intellij.psi._
import com.intellij.psi.impl.{JavaPsiFacadeImpl, PsiManagerEx}
import com.intellij.psi.search.{GlobalSearchScope, PsiShortNamesCache}
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.containers.WeakValueHashMap
import com.intellij.util.{ArrayUtil, SofterReference}
import org.jetbrains.plugins.scala.caches.ScalaShortNamesCacheManager
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.finder.ScalaSourceFilterScope
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.{ScSyntheticPackage, SyntheticPackageCreator}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers.ParameterlessNodes.{Map => PMap}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers.SignatureNodes.{Map => SMap}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers.TypeNodes.{Map => TMap}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers._
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector
import org.jetbrains.plugins.scala.lang.psi.light.PsiClassWrapper
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.resolve.SyntheticClassProducer
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import scala.collection.{Seq, mutable}

class ScalaPsiManager(project: Project) extends ProjectComponent {
  private val implicitObjectMap: ConcurrentMap[String, SofterReference[java.util.Map[GlobalSearchScope, Seq[ScObject]]]] =
    new ConcurrentHashMap()

  private val packageMap: ConcurrentMap[String, SofterReference[Option[PsiPackage]]] =
    new ConcurrentHashMap()

  private val classMap: ConcurrentMap[String, SofterReference[util.Map[GlobalSearchScope, Option[PsiClass]]]] =
    new ConcurrentHashMap()

  private val classesMap: ConcurrentMap[String, SofterReference[util.Map[GlobalSearchScope, Array[PsiClass]]]] =
    new ConcurrentHashMap()

  private val inheritorsMap: ConcurrentMap[PsiClass, SofterReference[ConcurrentMap[PsiClass, java.lang.Boolean]]] =
    new ConcurrentHashMap()

  private val scalaPackageClassesMap: ConcurrentMap[GlobalSearchScope, Array[PsiClass]] =
    new ConcurrentHashMap[GlobalSearchScope, Array[PsiClass]]

  private val javaPackageClassNamesMap: ConcurrentMap[(GlobalSearchScope, String), java.util.Set[String]] =
    new ConcurrentHashMap[(GlobalSearchScope, String), java.util.Set[String]]

  private val scalaPackageClassNamesMap: ConcurrentMap[(GlobalSearchScope, String), mutable.HashSet[String]] =
    new ConcurrentHashMap[(GlobalSearchScope, String), mutable.HashSet[String]]

  private val compoundTypesParameterslessNodes: ConcurrentMap[(ScCompoundType, Option[ScType]), SofterReference[PMap]] =
    new ConcurrentHashMap

  private val compoundTypesTypeNodes: ConcurrentMap[(ScCompoundType, Option[ScType]), SofterReference[TMap]] =
    new ConcurrentHashMap

  private val compoundTypesSignatureNodes: ConcurrentMap[(ScCompoundType, Option[ScType]), SofterReference[SMap]] =
    new ConcurrentHashMap

  private val psiTypeParameterUpperTypeMap: ConcurrentMap[PsiTypeParameter, SofterReference[ScType]] =
    new ConcurrentHashMap

  def getParameterlessSignatures(tp: ScCompoundType, compoundTypeThisType: Option[ScType]): PMap = {
    if (ScalaProjectSettings.getInstance(project).isDontCacheCompoundTypes) return ParameterlessNodes.build(tp, compoundTypeThisType)
    val ref = compoundTypesParameterslessNodes.get(tp, compoundTypeThisType)
    var result: PMap = if (ref == null) null else ref.get()
    if (result == null) {
      result = ParameterlessNodes.build(tp, compoundTypeThisType)
      compoundTypesParameterslessNodes.put((tp, compoundTypeThisType), new SofterReference(result))
    }
    result
  }

  def getTypes(tp: ScCompoundType, compoundTypeThisType: Option[ScType]): TMap = {
    if (ScalaProjectSettings.getInstance(project).isDontCacheCompoundTypes) return TypeNodes.build(tp, compoundTypeThisType)
    val ref = compoundTypesTypeNodes.get(tp, compoundTypeThisType)
    var result: TMap = if (ref == null) null else ref.get()
    if (result == null) {
      result = TypeNodes.build(tp, compoundTypeThisType)
      compoundTypesTypeNodes.put((tp, compoundTypeThisType), new SofterReference(result))
    }
    result
  }

  def getSignatures(tp: ScCompoundType, compoundTypeThisType: Option[ScType]): SMap = {
    if (ScalaProjectSettings.getInstance(project).isDontCacheCompoundTypes) return SignatureNodes.build(tp, compoundTypeThisType)
    val ref = compoundTypesSignatureNodes.get(tp, compoundTypeThisType)
    var result: SMap = if (ref == null) null else ref.get()
    if (result == null) {
      result = SignatureNodes.build(tp, compoundTypeThisType)
      compoundTypesSignatureNodes.put((tp, compoundTypeThisType), new SofterReference(result))
    }
    result
  }

  def cachedDeepIsInheritor(clazz: PsiClass, base: PsiClass): Boolean = {
    val ref = inheritorsMap.get(clazz)
    var map: ConcurrentMap[PsiClass, java.lang.Boolean] = null
    map = if (ref == null) null else ref.get()
    if (map == null) {
      map = new ConcurrentHashMap()
      inheritorsMap.put(clazz, new SofterReference(map))
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

    if (DumbService.getInstance(project).isDumb) return Seq.empty

    val reference = implicitObjectMap.get(fqn)
    val map = if (reference == null || reference.get() == null) {
      val map = new ConcurrentHashMap[GlobalSearchScope, Seq[ScObject]]()
      map.put(scope, calc())
      implicitObjectMap.put(fqn, new SofterReference(map))
      map
    } else reference.get()
    var result = map.get(scope)
    if (result == null) {
      result = calc()
      map.put(scope, result)
    }
    result
  }

  def getCachedPackage(fqn: String): PsiPackage = {
    def calc(): Option[PsiPackage] = {
      Option(JavaPsiFacade.getInstance(project).findPackage(fqn))
    }

    val reference = packageMap.get(fqn)
    if (reference == null || reference.get() == null) {
      val res: Option[PsiPackage] = calc()
      packageMap.put(fqn, new SofterReference(res))
      res.orNull
    } else reference.get().orNull
  }


  def getCachedClass(scope: GlobalSearchScope, fqn: String): PsiClass = {
    def getCachedFacadeClass(scope: GlobalSearchScope, fqn: String): Option[PsiClass] = {
      val clazz = JavaPsiFacade.getInstance(project).findClass(fqn, scope)
      if (clazz == null || clazz.isInstanceOf[ScTemplateDefinition] || clazz.isInstanceOf[PsiClassWrapper]) return None
      Option(clazz)
    }

    def calc(): Option[PsiClass] = {
      val res = ScalaShortNamesCacheManager.getInstance(project).getClassByFQName(fqn, scope)
      if (res != null) return Some(res)
      getCachedFacadeClass(scope, fqn)
    }

    val reference = classMap.get(fqn)
    val map = if (reference == null || reference.get() == null) {
      val map = new ConcurrentHashMap[GlobalSearchScope, Option[PsiClass]]()
      map.put(scope, calc())
      classMap.put(fqn, new SofterReference(map))
      map
    } else reference.get()
    var result = map.get(scope)
    if (result == null) {
      result = calc()
      map.put(scope, result)
    }
    result.orNull
  }

  def getStableAliasesByName(name: String, scope: GlobalSearchScope): Seq[ScTypeAlias] = {
    val types: util.Collection[ScTypeAlias] =
      StubIndex.getElements(ScalaIndexKeys.TYPE_ALIAS_NAME_KEY, name, project,
        new ScalaSourceFilterScope(scope, project), classOf[ScTypeAlias])
    import scala.collection.JavaConversions._
    types.toSeq
  }

  def getClassesByName(name: String, scope: GlobalSearchScope): Seq[PsiClass] = {
    val scalaClasses = ScalaShortNamesCacheManager.getInstance(project).getClassesByName(name, scope)
    val buffer: mutable.Buffer[PsiClass] = PsiShortNamesCache.getInstance(project).getClassesByName(name, scope).filterNot(p =>
      p.isInstanceOf[ScTemplateDefinition] || p.isInstanceOf[PsiClassWrapper]
    ).toBuffer
    val classesIterator = scalaClasses.iterator
    while (classesIterator.hasNext) {
      val clazz = classesIterator.next()
      buffer += clazz
    }
    buffer.toSeq
  }

  import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager.ClassCategory._
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
    def getCachedFacadeClasses(scope: GlobalSearchScope, fqn: String): Array[PsiClass] = {
      val classes = JavaPsiFacade.getInstance(project).findClasses(fqn, scope).filterNot(p =>
        p.isInstanceOf[ScTemplateDefinition] || p.isInstanceOf[PsiClassWrapper]
      )
      ArrayUtil.mergeArrays(classes, SyntheticClassProducer.getAllClasses(fqn, scope))
    }

    def calc(): Array[PsiClass] = {
      val classes = getCachedFacadeClasses(scope, fqn)
      val fromScala = ScalaShortNamesCacheManager.getInstance(project).getClassesByFQName(fqn, scope)

      ArrayUtil.mergeArrays(classes, ArrayUtil.mergeArrays(fromScala.toArray, SyntheticClassProducer.getAllClasses(fqn, scope)))
    }

    val reference = classesMap.get(fqn)
    val map = if (reference == null || reference.get() == null) {
      val map = new ConcurrentHashMap[GlobalSearchScope, Array[PsiClass]]()
      map.put(scope, calc())
      classesMap.put(fqn, new SofterReference(map))
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
      if (DumbService.getInstance(project).isDumb) return Collections.emptySet()
      val classes: util.Collection[PsiClass] =
        StubIndex.getElements(ScalaIndexKeys.JAVA_CLASS_NAME_IN_PACKAGE_KEY, qualifier, project,
          new ScalaSourceFilterScope(scope, project), classOf[PsiClass])
      val strings: util.HashSet[String] = new util.HashSet[String]
      val classesIterator = classes.iterator()
      while (classesIterator.hasNext) {
        val clazz: PsiClass = classesIterator.next()
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
      if (res == null) return Collections.emptySet()
      javaPackageClassNamesMap.put((scope, qualifier), res)
    }
    res
  }

  def getScalaClassNames(psiPackage: PsiPackage, scope: GlobalSearchScope): mutable.HashSet[String] = {
    val qualifier: String = psiPackage.getQualifiedName
    def calc: mutable.HashSet[String] = {
      if (DumbService.getInstance(project).isDumb) return mutable.HashSet.empty
      val classes: util.Collection[PsiClass] =
        StubIndex.getElements(ScalaIndexKeys.CLASS_NAME_IN_PACKAGE_KEY, qualifier, project,
          new ScalaSourceFilterScope(scope, project), classOf[PsiClass])
      var strings: mutable.HashSet[String] = new mutable.HashSet[String]
      val classesIterator = classes.iterator()
      while (classesIterator.hasNext) {
        val clazz: PsiClass = classesIterator.next()
        strings += clazz.name
      }
      strings
    }
    var res = scalaPackageClassNamesMap.get(scope, qualifier)
    if (res == null) {
      res = calc
      if (res == null) return mutable.HashSet.empty
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
    def clearOnChange(): Unit = {
      compoundTypesParameterslessNodes.clear()
      compoundTypesSignatureNodes.clear()
      compoundTypesTypeNodes.clear()
      Conformance.cache.clear()
      Equivalence.cache.clear()
      ScParameterizedType.substitutorCache.clear()
      ScalaPsiUtil.collectImplicitObjectsCache.clear()
      ImplicitCollector.cache.clear()
      psiTypeParameterUpperTypeMap.clear()
    }

    def clearOnOutOfCodeBlockChange(): Unit = {
      implicitObjectMap.clear()
      packageMap.clear()
      classMap.clear()
      classesMap.clear()
      inheritorsMap.clear()
      scalaPackageClassesMap.clear()
      javaPackageClassNamesMap.clear()
      scalaPackageClassNamesMap.clear()
      syntheticPackages.clear()
    }

    project.getMessageBus.connect.subscribe(PsiModificationTracker.TOPIC, new PsiModificationTracker.Listener {
      def modificationCountChanged() {
        clearOnChange()
        val count = PsiModificationTracker.SERVICE.getInstance(project).getOutOfCodeBlockModificationCount
        if (outOfCodeBlockModCount != count) {
          outOfCodeBlockModCount = count
          clearOnOutOfCodeBlockChange()
        }
      }
      
      @volatile
      private var outOfCodeBlockModCount: Long = 0L
    })

    project.getMessageBus.connect.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener {
      def beforeRootsChange(event: ModuleRootEvent) {
      }

      def rootsChanged(event: ModuleRootEvent) {
        clearOnChange()
        clearOnOutOfCodeBlockChange()
      }
    })

    LowMemoryWatcher.register(new Runnable {
      def run(): Unit = {
        scalaPackageClassesMap.clear()
        javaPackageClassNamesMap.clear()
        scalaPackageClassNamesMap.clear()
        compoundTypesParameterslessNodes.clear()
        compoundTypesSignatureNodes.clear()
        compoundTypesTypeNodes.clear()
        Conformance.cache.clear()
        Equivalence.cache.clear()
        ScParameterizedType.substitutorCache.clear()
        ScalaPsiUtil.collectImplicitObjectsCache.clear()
        ImplicitCollector.cache.clear()
      }
    })
  }

  private val syntheticPackagesCreator = new SyntheticPackageCreator(project)
  private val syntheticPackages = new WeakValueHashMap[String, Any]

  def syntheticPackage(fqn : String) : ScSyntheticPackage = {
    var p = syntheticPackages.get(fqn)
    if (p == null) {
      p = syntheticPackagesCreator.getPackage(fqn)
      if (p == null) p = types.Null
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

  def psiTypeParameterUpperType(tp: PsiTypeParameter): ScType = {
    def calc: ScType = {
      tp.getSuperTypes match {
        case array: Array[PsiClassType] if array.length == 1 => ScType.create(array(0), project)
        case many => new ScCompoundType(many.map { ScType.create(_, project) }, Map.empty, Map.empty)
      }
    }
    val parameterType = psiTypeParameterUpperTypeMap.get(tp)
    var res: ScType = if (parameterType == null) null else parameterType.get()
    if (res == null) {
      res = calc
      psiTypeParameterUpperTypeMap.put(tp, new SofterReference[ScType](res))
      res
    } else res
  }

  def typeVariable(tp: PsiTypeParameter) : ScTypeParameterType = {
    import org.jetbrains.plugins.scala.Misc.fun2suspension
    tp match {
      case stp: ScTypeParam =>
        val inner = stp.typeParameters.map{typeVariable(_)}.toList
        val lower = () => stp.lowerBound.getOrNothing
        val upper = () => stp.upperBound.getOrAny
        // todo rework for error handling!
        val res = new ScTypeParameterType(stp.name, inner, lower, upper, stp)
        res
      case _ =>
        val lower = () => types.Nothing
        val upper = () => psiTypeParameterUpperType(tp)
        val res = new ScTypeParameterType(tp.name, Nil, lower, upper, tp)
        res
    }
  }

  def getStableTypeAliasesNames: Seq[String] = {
    val keys = StubIndex.getInstance.getAllKeys(ScalaIndexKeys.STABLE_ALIAS_NAME_KEY, project)
    import scala.collection.JavaConversions._
    keys.toSeq
  }
}

object ScalaPsiManager {
  val TYPE_VARIABLE_KEY: Key[ScTypeParameterType] = Key.create("type.variable.key")

  def instance(project : Project) = project.getComponent(classOf[ScalaPsiManager])

  def typeVariable(tp : PsiTypeParameter): ScTypeParameterType = instance(tp.getProject).typeVariable(tp)

  object ClassCategory extends Enumeration {
    type ClassCategory = Value
    val ALL, OBJECT, TYPE = Value
  }
}