package org.jetbrains.plugins.scala
package lang
package psi
package impl

import api.statements.params.ScTypeParam
import com.intellij.openapi.components.ProjectComponent
import toplevel.synthetic.{SyntheticPackageCreator, ScSyntheticPackage}
import org.jetbrains.plugins.scala.lang.psi.light.{ScPrimaryConstructorWrapper, PsiClassWrapper}
import toplevel.typedef.TypeDefinitionMembers._
import types._
import com.intellij.openapi.util.{Computable, Key}
import com.intellij.ProjectTopics
import com.intellij.reference.SoftReference
import scala.collection.{mutable, Seq}
import com.intellij.psi._
import com.intellij.util.containers.{ConcurrentHashMap, WeakValueHashMap}
import impl.JavaPsiFacadeImpl
import java.util.concurrent.ConcurrentMap
import org.jetbrains.plugins.scala.caches.{CachesUtil, ScalaShortNamesCacheManager}
import api.toplevel.typedef.{ScTemplateDefinition, ScObject}
import extensions.toPsiNamedElementExt
import com.intellij.openapi.project.{DumbServiceImpl, Project}
import stubs.StubIndex
import psi.stubs.index.ScalaIndexKeys
import finder.ScalaSourceFilterScope
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.search.{PsiShortNamesCache, GlobalSearchScope}
import java.util.Collections
import com.intellij.openapi.roots.{ModuleRootEvent, ModuleRootListener}
import ParameterlessNodes.{Map => PMap}, TypeNodes.{Map => TMap}, SignatureNodes.{Map => SMap}
import java.util
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias}
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap
import com.intellij.psi.util._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.types.ScCompoundType
import org.jetbrains.plugins.scala.lang.psi.types.ScTypeParameterType
import org.jetbrains.plugins.scala.caches.CachesUtil.ProbablyRecursionException
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression.ExpressionTypeResult
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.implicits.ScImplicitlyConvertible.ImplicitResolveResult
import org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, ResolvableStableCodeReferenceElement, ResolvableReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.base.types.ScSimpleTypeElementImpl

class ScalaPsiManager(project: Project) extends ProjectComponent {
  import ScalaPsiManager._

  private val implicitObjectMap: ConcurrentMap[String, SoftReference[java.util.Map[GlobalSearchScope, Seq[ScObject]]]] =
    new ConcurrentHashMap()

  private val classMap: ConcurrentMap[String, SoftReference[util.Map[GlobalSearchScope, Option[PsiClass]]]] =
    new ConcurrentHashMap()

  private val classesMap: ConcurrentMap[String, SoftReference[util.Map[GlobalSearchScope, Array[PsiClass]]]] =
    new ConcurrentHashMap()

  private val classFacadeMap: ConcurrentMap[String, SoftReference[util.Map[GlobalSearchScope, Option[PsiClass]]]] =
    new ConcurrentHashMap()

  private val classesFacadeMap: ConcurrentMap[String, SoftReference[util.Map[GlobalSearchScope, Array[PsiClass]]]] =
    new ConcurrentHashMap()

  private val inheritorsMap: ConcurrentMap[PsiClass, SoftReference[ConcurrentMap[PsiClass, java.lang.Boolean]]] =
    new ConcurrentHashMap()

  private val scalaPackageClassesMap: ConcurrentMap[GlobalSearchScope, Array[PsiClass]] =
    new ConcurrentHashMap[GlobalSearchScope, Array[PsiClass]]

  private val javaPackageClassNamesMap: ConcurrentMap[(GlobalSearchScope, String), java.util.Set[String]] =
    new ConcurrentHashMap[(GlobalSearchScope, String), java.util.Set[String]]

  private val scalaPackageClassNamesMap: ConcurrentMap[(GlobalSearchScope, String), mutable.HashSet[String]] =
    new ConcurrentHashMap[(GlobalSearchScope, String), mutable.HashSet[String]]

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

    if (DumbServiceImpl.getInstance(project).isDumb) return Seq.empty

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

  def getStableAliasesByName(name: String, scope: GlobalSearchScope): Seq[ScTypeAlias] = {
    val types: util.Collection[ScTypeAlias] =
      StubIndex.getInstance.safeGet(ScalaIndexKeys.TYPE_ALIAS_NAME_KEY, name, project,
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
      val classes: util.Collection[PsiClass] =
        StubIndex.getInstance.safeGet(ScalaIndexKeys.JAVA_CLASS_NAME_IN_PACKAGE_KEY, qualifier, project,
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
      if (DumbServiceImpl.getInstance(project).isDumb) return mutable.HashSet.empty
      val classes: util.Collection[PsiClass] =
        StubIndex.getInstance.safeGet(ScalaIndexKeys.CLASS_NAME_IN_PACKAGE_KEY, qualifier, project,
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
    def clearOnChange() {
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
      conformanceCache.clear()
      ScalaPsiManager.clearCaches()
    }

    def clearOnOutOfCodeBlockChange() {
      syntheticPackages.clear()
      classParameterslessNodes.clear()
      classSignatureNodes.clear()
      classTypeNodes.clear()
      ScalaPsiManager.clearOutOfCodeBlockCaches()
    }

    project.getMessageBus.connect.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener {
      def beforeRootsChange(event: ModuleRootEvent) {
      }

      def rootsChanged(event: ModuleRootEvent) {
        clearOnChange()
        clearOnOutOfCodeBlockChange()
      }
    })

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
        val lower = () => types.Nothing
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

  def getStableTypeAliasesNames: Seq[String] = {
    val keys = StubIndex.getInstance.getAllKeys(ScalaIndexKeys.STABLE_ALIAS_NAME_KEY, project)
    import scala.collection.JavaConversions._
    keys.toSeq
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

  private def buildMap[T, S](capacity: Long): ConcurrentLinkedHashMap[T, S] = {
    new ConcurrentLinkedHashMap.Builder[T, S].maximumWeightedCapacity(capacity).build()
  }

  private def getCache[T, S](t: T, cache: ConcurrentLinkedHashMap[T, S])(builder: T => S): S = {
    var result: S = cache.get(t)
    if (result == null) {
      result = builder(t)
      cache.put(t, result)
    }
    result
  }

  private val classStructureCacheCapacity: Long = 100
  private val smallCacheCapacity: Long = 2000
  private val resolveCapacity: Long = scala.Long.MaxValue

  //class structure cache
  private val compoundTypesParameterslessNodes: ConcurrentLinkedHashMap[(ScCompoundType, Option[ScType]), PMap] = buildMap(classStructureCacheCapacity)
  private val compoundTypesTypeNodes: ConcurrentLinkedHashMap[(ScCompoundType, Option[ScType]), TMap] = buildMap(classStructureCacheCapacity)
  private val compoundTypesSignatureNodes: ConcurrentLinkedHashMap[(ScCompoundType, Option[ScType]), SMap] = buildMap(classStructureCacheCapacity)
  private val classParameterslessNodes: ConcurrentLinkedHashMap[PsiClass, PMap] = buildMap(classStructureCacheCapacity)
  private val classTypeNodes: ConcurrentLinkedHashMap[PsiClass, TMap] = buildMap(classStructureCacheCapacity)
  private val classSignatureNodes: ConcurrentLinkedHashMap[PsiClass, SMap] = buildMap(classStructureCacheCapacity)

  type ConformanceKey = (ScType, ScType, Boolean)
  private val conformanceCache: ConcurrentLinkedHashMap[ConformanceKey, (Boolean, ScUndefinedSubstitutor)] = buildMap(smallCacheCapacity)

  def getParameterlessSignatures(tp: ScCompoundType, compoundTypeThisType: Option[ScType]): PMap =
    getCache((tp, compoundTypeThisType), compoundTypesParameterslessNodes){ case (t, c) => ParameterlessNodes.build(t, c) }

  def getTypes(tp: ScCompoundType, compoundTypeThisType: Option[ScType]): TMap =
    getCache((tp, compoundTypeThisType), compoundTypesTypeNodes){ case (t, c) => TypeNodes.build(t, c) }

  def getSignatures(tp: ScCompoundType, compoundTypeThisType: Option[ScType]): SMap =
    getCache((tp, compoundTypeThisType), compoundTypesSignatureNodes){ case (t, c) => SignatureNodes.build(t, c) }

  def getParameterlessSignatures(clazz: PsiClass): PMap = getCache(clazz, classParameterslessNodes)(ParameterlessNodes.build)
  def getTypes(clazz: PsiClass): TMap = getCache(clazz, classTypeNodes)(TypeNodes.build)
  def getSignatures(clazz: PsiClass): SMap = getCache(clazz, classSignatureNodes)(SignatureNodes.build)

  def getConformanceCache(key: ConformanceKey): (Boolean, ScUndefinedSubstitutor) = conformanceCache.get(key)
  def putConformanceCache(key: ConformanceKey, data: (Boolean, ScUndefinedSubstitutor)): Unit = conformanceCache.put(key, data)

  type Cache[From, To] = Key[(From, To)]

  private val caches: mutable.HashMap[Key[_], ConcurrentLinkedHashMap[_, _]] = new mutable.HashMap[Key[_], ConcurrentLinkedHashMap[_, _]]()
  private val outOfCodeBlockCaches: mutable.HashMap[Key[_], ConcurrentLinkedHashMap[_, _]] = new mutable.HashMap[Key[_], ConcurrentLinkedHashMap[_, _]]()

  private def addCache[From, To](key: Cache[From, To], capacity: Long = smallCacheCapacity, isOutOfCodeBlock: Boolean = false) = {
    (if (isOutOfCodeBlock) outOfCodeBlockCaches else caches) += key -> buildMap[From, To](capacity)
  }

  private def clearCaches() {
    caches.valuesIterator.foreach(_.clear())
  }

  private def clearOutOfCodeBlockCaches() {
    outOfCodeBlockCaches.valuesIterator.foreach(_.clear())
  }

  private def getCacheGeneric[From, To](key: Cache[From, To], elem: From,
                                        storage: mutable.HashMap[Key[_], ConcurrentLinkedHashMap[_, _]])(builder: From => To): To = {
    val cache = storage(key).asInstanceOf[ConcurrentLinkedHashMap[From, To]]
    var result: To = cache.get(elem)
    if (result == null) {
      result = builder(elem)
      cache.put(elem, result)
    }
    result
  }

  def getCaches[From, To](key: Cache[From, To], elem: From)(builder: From => To): To =
    getCacheGeneric(key, elem, caches)(builder)

  def getOutOfCodeBlockCaches[From, To](key: Cache[From, To], elem: From)(builder: From => To): To =
    getCacheGeneric(key, elem, outOfCodeBlockCaches)(builder)

  val primaryConstructorWrapperKey: Cache[ScPrimaryConstructor, ScPrimaryConstructorWrapper] = Key.create("primaryConstructorWrapperKey")
  addCache(primaryConstructorWrapperKey, isOutOfCodeBlock = true)
  val beanMethodsKey: Cache[ScTypedDefinition, Seq[PsiMethod]] = Key.create("beanMethodsKey")
  addCache(beanMethodsKey, isOutOfCodeBlock = true)
  val isBeanMethodKey: Cache[ScTypedDefinition, PsiMethod] = Key.create("isBeanMethodKey")
  addCache(isBeanMethodKey, isOutOfCodeBlock = true)
  val getBeanMethodsKey: Cache[ScTypedDefinition, PsiMethod] = Key.create("getBeanMethodsKey")
  addCache(getBeanMethodsKey, isOutOfCodeBlock = true)
  val setBeanMethodsKey: Cache[ScTypedDefinition, PsiMethod] = Key.create("setBeanMethodsKey")
  addCache(setBeanMethodsKey, isOutOfCodeBlock = true)
  val underEqualsMethodsKey: Cache[ScTypedDefinition, PsiMethod] = Key.create("underEqualsMethodsKey")
  addCache(underEqualsMethodsKey, isOutOfCodeBlock = true)

  //Map cache keys
  type MappedKey[Data, Result] = Key[(Data, Result)]
  val TYPE_AFTER_IMPLICIT_KEY: MappedKey[(ScExpression, (Boolean, Boolean, Option[ScType], Boolean, Boolean)), ExpressionTypeResult] =
    Key.create("type.after.implicit.key")
  addCache(TYPE_AFTER_IMPLICIT_KEY)
  val TYPE_WITHOUT_IMPLICITS: MappedKey[(ScExpression, (Boolean, Boolean)), TypeResult[ScType]] =
    Key.create("type.without.implicits.key")
  addCache(TYPE_WITHOUT_IMPLICITS)
  val NON_VALUE_TYPE_KEY: MappedKey[(ScExpression, (Boolean, Boolean)), TypeResult[ScType]] = Key.create("non.value.type.key")
  addCache(NON_VALUE_TYPE_KEY)
  val EXPECTED_TYPES_KEY: MappedKey[(ScExpression, Boolean), Array[(ScType, Option[ScTypeElement])]] = Key.create("expected.types.key")
  addCache(EXPECTED_TYPES_KEY)
  val SMART_EXPECTED_TYPE: MappedKey[(ScExpression, Boolean), Option[ScType]] = Key.create("smart.expected.type")
  addCache(SMART_EXPECTED_TYPE)
  val IMPLICIT_MAP1_KEY: MappedKey[(PsiElement, (Option[ScType], Boolean, Option[ScType])), Seq[ImplicitResolveResult]] =
    Key.create("implicit.map1.key")
  addCache(IMPLICIT_MAP1_KEY)
  val IMPLICIT_MAP2_KEY: MappedKey[(PsiElement, (Option[ScType], Boolean, Seq[ScType], Option[ScType])), Seq[ImplicitResolveResult]] =
    Key.create("implicit.map2.key")
  addCache(IMPLICIT_MAP2_KEY)
  val RESOLVE_KEY: MappedKey[(ResolvableReferenceExpression, Boolean), Array[ResolveResult]] =
    Key.create("resolve.key")
  addCache(RESOLVE_KEY, capacity = resolveCapacity)
  val STABLE_RESOLVE_KEY: MappedKey[(ResolvableStableCodeReferenceElement, Boolean), Array[ResolveResult]] =
    Key.create("resolve.key")
  addCache(STABLE_RESOLVE_KEY, capacity = resolveCapacity)
  val EXPRESSION_APPLY_SHAPE_RESOLVE_KEY: MappedKey[(ScExpression, (ScType, Seq[ScExpression], Option[MethodInvocation], TypeResult[ScType])), Array[ScalaResolveResult]] =
    Key.create("expression.apply.shape.resolve.key")
  addCache(EXPRESSION_APPLY_SHAPE_RESOLVE_KEY)
  val PROJECTION_TYPE_ACTUAL_INNER: MappedKey[(PsiNamedElement, ScType), Option[(PsiNamedElement, ScSubstitutor)]] =
    Key.create("projection.type.actual.inner.key")
  addCache(PROJECTION_TYPE_ACTUAL_INNER)

  //preventing recursion cache
  val TYPE_ELEMENT_TYPE_KEY: Cache[ScTypeElement, TypeResult[ScType]] = Key.create("type.element.type.key")
  addCache(TYPE_ELEMENT_TYPE_KEY)
  val NON_VALUE_TYPE_ELEMENT_TYPE_KEY: Cache[ScSimpleTypeElementImpl, TypeResult[ScType]] = Key.create("type.element.type.key")
  addCache(NON_VALUE_TYPE_ELEMENT_TYPE_KEY)
  val LINEARIZATION_KEY: Cache[PsiClass, Seq[ScType]] = Key.create("linearization.key")
  addCache(LINEARIZATION_KEY, isOutOfCodeBlock = true)
  val REF_EXPRESSION_SHAPE_RESOLVE_KEY: Cache[ResolvableReferenceExpression, Array[ResolveResult]] =
    Key.create("ref.expression.shape.resolve.key")
  addCache(REF_EXPRESSION_SHAPE_RESOLVE_KEY, capacity = resolveCapacity)
  val NO_CONSTRUCTOR_RESOLVE_KEY: Cache[ResolvableStableCodeReferenceElement, Array[ResolveResult]] = Key.create("no.constructor.resolve.key")
  addCache(NO_CONSTRUCTOR_RESOLVE_KEY, capacity = resolveCapacity)
  val REF_ELEMENT_RESOLVE_CONSTR_KEY: Cache[ResolvableStableCodeReferenceElement, Array[ResolveResult]] =
    Key.create("ref.element.resolve.constr.key")
  addCache(REF_ELEMENT_RESOLVE_CONSTR_KEY, capacity = resolveCapacity)
  val REF_ELEMENT_SHAPE_RESOLVE_KEY: Cache[ResolvableStableCodeReferenceElement, Array[ResolveResult]] =
    Key.create("ref.element.shape.resolve.key")
  addCache(REF_ELEMENT_SHAPE_RESOLVE_KEY, capacity = resolveCapacity)
  val REF_ELEMENT_SHAPE_RESOLVE_CONSTR_KEY: Cache[ResolvableStableCodeReferenceElement, Array[ResolveResult]] =
    Key.create("ref.element.shape.resolve.constr.key")
  addCache(REF_ELEMENT_SHAPE_RESOLVE_CONSTR_KEY, capacity = resolveCapacity)

  def getMappedWithRecursionPreventingWithRollback[Dom <: PsiElement, Data, Result](e: Dom, data: Data,
                                                                                    key: Cache[(Dom, Data), Result],
                                                                                    builder: (Dom, Data) => Result,
                                                                                    defaultValue: => Result,
                                                                                    isOutOfCodeBlock: Boolean): Result = {
    val storage = (if (isOutOfCodeBlock) outOfCodeBlockCaches else caches).apply(key).asInstanceOf[ConcurrentLinkedHashMap[(Dom, Data), Result]]
    var result = storage.get(e, data)
    if (result == null) {
      var isCache = true
      result = {
        val guard = CachesUtil.getRecursionGuard(key.toString)
        if (guard.currentStack().contains((e, data))) {
          if (ScPackageImpl.isPackageObjectProcessing) {
            throw new ScPackageImpl.DoNotProcessPackageObjectException
          }
          val fun = PsiTreeUtil.getContextOfType(e, true, classOf[ScFunction])
          if (fun == null || fun.isProbablyRecursive) {
            isCache = false
            defaultValue
          } else {
            fun.setProbablyRecursive(b = true)
            throw new ProbablyRecursionException(e, data, key, Set(fun))
          }
        } else {
          guard.doPreventingRecursion((e, data), false, new Computable[Result] {
            def compute(): Result = {
              try {
                builder(e, data)
              }
              catch {
                case ProbablyRecursionException(`e`, `data`, k, set) if k == key =>
                  try {
                    builder(e, data)
                  } finally set.foreach(_.setProbablyRecursive(b = false))
                case t@ProbablyRecursionException(ee, innerData, k, set) if k == key =>
                  val fun = PsiTreeUtil.getContextOfType(e, true, classOf[ScFunction])
                  if (fun == null || fun.isProbablyRecursive) throw t
                  else {
                    fun.setProbablyRecursive(b = true)
                    throw ProbablyRecursionException(ee, innerData, k, set + fun)
                  }
              }
            }
          }) match {
            case null => defaultValue
            case notNull => notNull
          }
        }
      }
      if (isCache) {
        storage.put((e, data), result)
      }
    }
    result
  }

  def getWithRecursionPreventingWithRollback[Dom <: PsiElement, Result](e: Dom, key: Cache[Dom, Result],
                                                                        builder: Dom => Result,
                                                                        defaultValue: => Result,
                                                                        isOutOfCodeBlock: Boolean): Result = {
    val storage = (if (isOutOfCodeBlock) outOfCodeBlockCaches else caches).apply(key).asInstanceOf[ConcurrentLinkedHashMap[Dom, Result]]
    var result = storage.get(e)
    if (result == null) {
      result = {
        val guard = CachesUtil.getRecursionGuard(key.toString)
        if (guard.currentStack().contains(e)) {
          if (ScPackageImpl.isPackageObjectProcessing) {
            throw new ScPackageImpl.DoNotProcessPackageObjectException
          }
          val fun = PsiTreeUtil.getContextOfType(e, true, classOf[ScFunction])
          if (fun == null || fun.isProbablyRecursive) {
            defaultValue
          } else {
            fun.setProbablyRecursive(b = true)
            throw new ProbablyRecursionException(e, (), key, Set(fun))
          }
        }
        guard.doPreventingRecursion(e, false /* todo: true? */, new Computable[Result] {
          def compute(): Result = {
            try {
              builder(e)
            }
            catch {
              case ProbablyRecursionException(`e`, (), k, set) if k == key =>
                try {
                  builder(e)
                }
                finally set.foreach(_.setProbablyRecursive(b = false))
              case t@ProbablyRecursionException(ee, data, k, set) if k == key =>
                val fun = PsiTreeUtil.getContextOfType(e, true, classOf[ScFunction])
                if (fun == null || fun.isProbablyRecursive) throw t
                else {
                  fun.setProbablyRecursive(b = true)
                  throw ProbablyRecursionException(ee, data, k, set + fun)
                }
            }
          }
        }) match {
          case null => defaultValue
          case notNull => notNull
        }
      }
      storage.put(e, result)
    }
    result
  }
}
