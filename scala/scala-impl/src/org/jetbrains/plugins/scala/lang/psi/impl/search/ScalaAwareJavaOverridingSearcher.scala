package org.jetbrains.plugins.scala.lang.psi.impl.search

import com.intellij.openapi.util.Pair
import com.intellij.psi._
import com.intellij.psi.impl.light.{LightMethod, LightParameter, LightParameterListBuilder}
import com.intellij.psi.impl.search.JavaOverridingMethodsSearcher
import com.intellij.psi.impl.source.PsiMethodImpl
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.{AllOverridingMethodsSearch, ClassInheritorsSearch, OverridingMethodsSearch}
import com.intellij.psi.util.{MethodSignature, MethodSignatureBackedByPsiMethod, PsiUtil}
import com.intellij.util.{Processor, QueryExecutor}
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt, PsiMemberExt, PsiNamedElementExt, PsiTypeExt, inReadAction}
import org.jetbrains.plugins.scala.finder.ScalaFilterScope
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.search.ScalaAwareJavaOverridingSearcherUtils._
import org.jetbrains.plugins.scala.lang.psi.light.ScFunctionWrapper
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil

/**
 * Supplements platform Java overriding-method search with Scala-aware handling of Java parameter types.
 *
 * Covers platform Java search gaps for valid Scala overrides:
 *  - raw Java parameters;
 *  - containing-class type parameters specialized to Scala value types.
 *
 * ==Raw Java parameters==
 * Scala views a Java raw type as an existential type, whereas the platform search compares the raw Java signature.
 * For example:
 * {{{
 * // Java
 * public class JavaBase {
 *   public void foo(java.util.List values) {}
 * }
 *
 * // Scala
 * class ScalaChild extends JavaBase {
 *   override def foo(values: java.util.List[_]): Unit = ()
 * }
 * }}}
 * The platform does not match `foo(List)` with `foo(List[_])`. This searcher presents raw parameters as their
 * Scala existential types to the platform search, then verifies that every candidate has the original Java method
 * in its Scala supermethod hierarchy.
 *
 * ==Containing-class type parameters specialized to Scala value types==
 * Java's overriding search substitutes a containing-class type parameter with `Object` for Scala inheritors. This
 * loses a value-type specialization such as `T` to `Int`, because `Int` is represented by the primitive `int` and
 * the platform cannot match `foo(Object)` with `foo(int)`. For example:
 * {{{
 * // Java
 * public class JavaBase<T> {
 *   public void foo(T value) {}
 * }
 *
 * // Scala: found by this searcher
 * class ScalaIntChild extends JavaBase[Int] {
 *   override def foo(value: Int): Unit = ()
 * }
 * }}}
 * This searcher visits Scala inheritors directly and accepts functions whose Scala-resolved supermethod is the
 * queried Java method. A reference-type specialization such as `String` works out of the box: Java's normal
 * reference-type overriding rules already find `foo(String)`.
 *
 * [[ScalaAwareJavaAllOverridingSearcher]] applies the same compatibility rules to the all-overriding-methods query
 * used by gutter markers. Unlike this executor, which returns overriding methods for one queried method, it emits
 * supermethod-overrider pairs for every relevant method in a class.
 */
class ScalaAwareJavaOverridingSearcher extends QueryExecutor[PsiMethod, OverridingMethodsSearch.SearchParameters] {
  override def execute(searchParams: OverridingMethodsSearch.SearchParameters, consumer: Processor[_ >: PsiMethod]): Boolean = {
    val method = searchParams.getMethod
    method match {
      // Handles Java raw parameters such as `List`, whose Scala overrides use existential types such as `List[_]`.
      case m: PsiMethodImpl if hasRawTypeParam(m) =>
        val cClass = inReadAction(m.containingClass)
        if (cClass == null) return true

        val wrapper = rawMethodWrapper(m, cClass)
        val scalaScope = ScalaFilterScope(searchParams.getScope)(wrapper.getProject)

        val newParams = new OverridingMethodsSearch.SearchParameters(wrapper, scalaScope, searchParams.isCheckDeep)
        val newProcessor = new Processor[PsiMethod] {
          override def process(t: PsiMethod): Boolean = {
            if (isSuperMethodForScala(m, t)) consumer.process(t)
            else true
          }
        }
        new JavaOverridingMethodsSearcher().execute(newParams, newProcessor)
      case m: PsiMethodImpl if hasParameterWithTypeOfContainingClassTypeParam(m) =>
        // Java's overrider search substitutes a Scala specialization of a Java class type parameter with `Object`.
        // It therefore misses a value-type override such as `foo(Int)` of `foo(T)` from `JavaBaseClass[Int]`.
        // Scala supermethod resolution preserves that substitution and identifies the exact overriding function.
        val scalaScope = ScalaFilterScope(searchParams.getScope)(m.getProject)
        processScalaPrimitiveSpecializationOverriders(m, scalaScope, searchParams.isCheckDeep, consumer)
      case _ =>
        true
    }
  }
}

/**
 * Supplements the platform all-overriding-methods search for Java classes with parameter types Scala interprets differently.
 *
 * The query powers gutter markers and needs a pair containing the Java supermethod and each overriding method.
 * It applies the raw-parameter and value-type-specialization handling from [[ScalaAwareJavaOverridingSearcher]]
 * to every overridable method in the queried class.
 *
 * Unlike [[ScalaAwareJavaOverridingSearcher]], which processes one method for implementation navigation and returns
 * only its overriding methods, this executor emits supermethod-overrider pairs for the complete class.
 */
class ScalaAwareJavaAllOverridingSearcher extends QueryExecutor[Pair[PsiMethod, PsiMethod], AllOverridingMethodsSearch.SearchParameters] {
  import ScalaAwareJavaAllOverridingSearcher._

  override def execute(
    seachParameters: AllOverridingMethodsSearch.SearchParameters,
    consumer: Processor[_ >: Pair[PsiMethod, PsiMethod]]
  ): Boolean = {
    val clazz = seachParameters.getPsiClass
    val candidates = inReadAction {
      collectCandidates(clazz)
    }

    for ((superMethod, kind) <- candidates) {
      val continue = processCandidate(superMethod, kind, clazz, seachParameters, consumer)
      if (!continue)
        return false
    }

    true
  }

  /**
   * This may look like an over-abstraction for a small collection, but it keeps the mutable construction needed to
   * avoid the intermediate collections and `Option`s from `toSeq.flatMap` local to this method.
   *
   * Its allocation footprint has not been measured, so this may still be an over-optimization.
   */
  private def collectCandidates(clazz: PsiClass): scala.collection.Iterable[(PsiMethodImpl, CandidateKind)] = {
    val result = scala.collection.mutable.ArrayBuffer.empty[(PsiMethodImpl, CandidateKind)]
    clazz.getMethods.foreach {
      case method: PsiMethodImpl if PsiUtil.canBeOverridden(method) =>
        if (hasRawTypeParam(method))
          result += method -> CandidateKind.RawParameter
        else if (hasParameterWithTypeOfContainingClassTypeParam(method))
          result += method -> CandidateKind.ClassTypeParameter
      case _ =>
    }
    result
  }

  private def processCandidate(
    superMethod: PsiMethodImpl,
    kind: CandidateKind,
    clazz: PsiClass,
    searchParameters: AllOverridingMethodsSearch.SearchParameters,
    consumer: Processor[_ >: Pair[PsiMethod, PsiMethod]]
  ): Boolean = kind match {
    case CandidateKind.RawParameter => inReadAction {
      val wrapper = rawMethodWrapper(superMethod, clazz)
      val scalaScope = ScalaFilterScope(searchParameters.getScope)(wrapper.getProject)

      val params = new OverridingMethodsSearch.SearchParameters(wrapper, scalaScope, /*checkDeep*/ true)
      val processor = new Processor[PsiMethod] {
        override def process(t: PsiMethod): Boolean = {
          if (isSuperMethodForScala(superMethod, t))
            consumer.process(new Pair(superMethod, t))
          else
            true
        }
      }
      new JavaOverridingMethodsSearcher().execute(params, processor)
    }

    case CandidateKind.ClassTypeParameter =>
      val scalaScope = ScalaFilterScope(searchParameters.getScope)(superMethod.getProject)
      val processor = new Processor[PsiMethod] {
        override def process(t: PsiMethod): Boolean =
          consumer.process(new Pair(superMethod, t))
      }
      processScalaPrimitiveSpecializationOverriders(superMethod, scalaScope, processAllExactOverriders = true, processor)
  }
}

private object ScalaAwareJavaAllOverridingSearcher {
  private sealed trait CandidateKind

  private object CandidateKind {
    case object RawParameter extends CandidateKind
    case object ClassTypeParameter extends CandidateKind
  }
}

private object ScalaAwareJavaOverridingSearcherUtils {
  /**
   * Checks whether a Java method has a raw parameter type.
   *
   * Scala views raw Java types as existential types, so their overriding methods require the Scala-aware search path.
   * For example:
   * {{{
   * void raw(java.util.List values) {}                   // true
   * void parameterized(java.util.List<String> values) {} // false
   * }}}
   */
  private[search] def hasRawTypeParam(method: PsiMethodImpl): Boolean = inReadAction {
    val parameters = method.getParameterList.getParameters
    parameters.map(_.getType).exists(isRaw)
  }

  private def isRaw(t: PsiType): Boolean = t match {
    case ct: PsiClassType => ct.isRaw
    case _ => false
  }

  /**
   * Checks whether a Java method has a parameter whose type resolves to a type parameter of its containing class.
   *
   * Scala inheritors can specialize such a parameter to a value type, but Java overriding search represents that
   * substitution as `Object`.
   * For example:
   * {{{
   * class JavaBase<T> {
   *   void acceptsTypeParameter(T value) {}              // true
   *   void acceptsParameterizedType(java.util.List<T> values) {} // false
   * }
   * }}}
   */
  private[search] def hasParameterWithTypeOfContainingClassTypeParam(method: PsiMethodImpl): Boolean = inReadAction {
    val result = containingClassTypeParameterPositions(method)
    result.nonEmpty
  }

  private def containingClassTypeParameterPositions(method: PsiMethod): Array[Int] = {
    val containingClass = method.containingClass
    val classTypeParameters = Option(containingClass).fold(Array.empty[PsiTypeParameter])(_.getTypeParameters)
    if (classTypeParameters.isEmpty)
      return Array.empty[Int]

    val parameters = method.getParameterList.getParameters
    parameters.iterator.zipWithIndex.collect {
      case (parameter, index) if isClassTypeParam(parameter.getType, classTypeParameters) => index
    }.toArray
  }

  private def isClassTypeParam(
    parameterType: PsiType,
    classTypeParameters: Array[PsiTypeParameter]
  ): Boolean = parameterType match {
    case classType: PsiClassType =>
      lazy val classTypeResolved = classType.resolve
      classTypeParameters.exists(_ == classTypeResolved)
    case _ =>
      false
  }

  private[search] def rawMethodWrapper(m: PsiMethod, cClass: PsiClass): PsiMethod = {
    //TODO: extract a named class for this for better debuggability?
    new LightMethod(m.getManager, m, cClass) {
      override def getParameterList: PsiParameterList = {
        val lightList = new LightParameterListBuilder(m.getManager, m.getLanguage)
        val originalParams = m.getParameterList.getParameters
        originalParams.foreach(p => lightList.addParameter(asViewedFromScala(p)))
        lightList
      }

      override def getSignature(substitutor: PsiSubstitutor): MethodSignature =
        MethodSignatureBackedByPsiMethod.create(this, substitutor)
    }
  }

  private def asViewedFromScala(p: PsiParameter): PsiParameter = {
    val paramType: PsiType = p.getType
    if (!isRaw(paramType)) return p

    implicit val pc: ProjectContext = p.projectContext
    val typeFromScala = paramType.toScType().toPsiType
    val parameterName = Option(p.name).getOrElse("")

    new LightParameter(parameterName, typeFromScala, p.getDeclarationScope, ScalaLanguage.INSTANCE, p.isVarArgs)
  }

  private[search] def isSuperMethodForScala(superMethod: PsiMethod, subMethod: PsiMethod): Boolean = {
    val scalaDef = unwrapScalaFunction(subMethod)
    scalaDef.exists { scFun =>
      inReadAction {
        val superMethodClasses = scFun.superMethods.map(_.containingClass)
        superMethodClasses.exists(ScEquivalenceUtil.areClassesEquivalent(_, superMethod.containingClass))
      }
    }
  }

  /**
   * Processes Scala methods that exactly override a Java method with a containing-class type parameter.
   *
   * This is intentionally a narrow fallback for the known `T`-to-primitive mismatch (SCL-7463). Before invoking Scala
   * supermethod resolution, a candidate must have the queried name and arity, and expose a primitive JVM parameter
   * at a position where the Java method directly uses a type parameter of its containing class.
   * For example, `foo(String, T)` is checked semantically against `foo(String, Int)`, but not against `foo(String, String)`.
   *
   * The filtering relies on these current assumptions:
   *  - platform Java search already handles reference-type substitutions such as `T` to `String`;
   *  - the gap addressed here concerns a type parameter used directly as a method parameter, not nested occurrences
   *    such as `List<T>`, return types, or method type parameters;
   *  - a Scala value-type candidate that Java search misses exposes a [[PsiPrimitiveType]] at the corresponding
   *    light-method parameter position;
   *  - the normal Java overriding-method executor participates in the enclosing extensible query and contributes
   *    candidates rejected here because all relevant parameter positions use reference types.
   *
   * This positional check is deliberately an optimization rather than a general definition of cross-language overriding.
   * It may be an over-optimization: it adds its own inspection work and has not been proven cheaper for every hierarchy shape.
   * Future potential Scala/JVM representations that Java search cannot match but that do not expose a
   * primitive at the relevant position will need to broaden this predicate and add focused coverage.
   *
   * The ideal general solution would avoid encoding Java-search failures here. A platform extension invoked by
   * `JavaOverridingMethodsSearcher.findOverridingMethod` after its normal signature lookup fails could delegate the
   * already-discovered candidate to the inheritor language. It would reuse the existing Java hierarchy pass and its
   * result cache. The relevant platform implementation is
   * `community/java/java-indexing-impl/src/com/intellij/psi/impl/search/JavaOverridingMethodsSearcher.java`.
   * A more general implementation could also investigate hierarchical supermethod APIs such as
   * `PsiSuperMethodUtil.isSuperMethod`, defined in
   * `community/java/java-psi-api/src/com/intellij/psi/util/PsiSuperMethodUtil.java`.
   *
   * @param processAllExactOverriders Controls result processing.<br>
   *                                  `false` supports an existence check, such as deciding whether to show a gutter icon,
   *                                  by stopping after the first exact override.<br>
   *                                  `true` processes every exact override for a complete result list.
   */
  private[search] def processScalaPrimitiveSpecializationOverriders(
    superMethod: PsiMethod,
    scope: SearchScope,
    processAllExactOverriders: Boolean,
    consumer: Processor[_ >: PsiMethod]
  ): Boolean = {
    val superClass = inReadAction(superMethod.containingClass)
    if (superClass == null)
      return true

    val classTypeParameterPositions = inReadAction {
      containingClassTypeParameterPositions(superMethod)
    }
    if (classTypeParameterPositions.isEmpty)
      return true

    ClassInheritorsSearch.search(superClass, scope, /*checkDeep*/ true).forEach((inheritor: PsiClass) => {
      val primitiveSpecializationCandidates = inReadAction {
        val methodsWithSameName = inheritor.findMethodsByName(superMethod.name, /*checkBases*/ false)
        methodsWithSameName.filter { candidate =>
          couldNeedScalaPrimitiveSpecializationFallback(superMethod, classTypeParameterPositions, candidate)
        }
      }

      val shouldContinueProcessing = primitiveSpecializationCandidates.iterator
        .filter(isExactSuperMethodForScala(superMethod, _))
        .forall { method =>
          val continueFromConsumer = consumer.process(method)
          // Existence-only callers stop after the first exact override.
          continueFromConsumer && processAllExactOverriders
        }

      shouldContinueProcessing
    })
  }

  /**
   * Applies cheap structural checks before the more expensive Scala supermethod lookup.
   *
   * The order is intentional: reject a different name first, then a different arity, and only then inspect the
   * parameter positions known to contain direct class type parameters in the Java base method. The name check makes
   * this helper self-contained even though the current caller also narrows candidates with `findMethodsByName`.
   */
  private def couldNeedScalaPrimitiveSpecializationFallback(
    superMethod: PsiMethod,
    classTypeParameterPositions: Array[Int],
    candidate: PsiMethod
  ): Boolean = {
    if (candidate.getName != superMethod.getName)
      return false

    val candidateParameterList = candidate.getParameterList
    val superParameterCount = superMethod.getParameterList.getParametersCount
    if (candidateParameterList.getParametersCount != superParameterCount)
      return false

    val candidateParameters = candidateParameterList.getParameters
    classTypeParameterPositions.exists { position =>
      candidateParameters(position).getType.is[PsiPrimitiveType]
    }
  }

  private def isExactSuperMethodForScala(superMethod: PsiMethod, subMethod: PsiMethod): Boolean = {
    val scalaDef = unwrapScalaFunction(subMethod)
    scalaDef.exists { scFun =>
      inReadAction {
        scFun.superMethods.contains(superMethod)
      }
    }
  }

  private def unwrapScalaFunction(method: PsiMethod): Option[ScFunction] = method match {
    case ScFunctionWrapper(delegate) => Some(delegate)
    case fun: ScFunction             => Some(fun)
    case _                           => None
  }
}
