package org.jetbrains.plugins.scala.injection

import com.intellij.lang.Language
import com.intellij.lang.injection.{InjectedLanguageManager, MultiHostInjector, MultiHostRegistrar}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Key
import com.intellij.psi._
import org.apache.commons.lang3.StringUtils
import org.intellij.plugins.intelliLang.Configuration

import java.{util => ju}
import org.intellij.plugins.intelliLang.inject._
import org.intellij.plugins.intelliLang.inject.config.BaseInjection
import org.intellij.plugins.intelliLang.inject.java.JavaLanguageInjectionSupport
import org.jetbrains.plugins.scala.caches.BlockModificationTracker
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.injection.ScalaInjectionInfosCollector.InjectionSplitResult
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.readAttribute
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScInterpolatedPatternPrefix
import org.jetbrains.plugins.scala.patterns.ScalaElementPatternImpl
import org.jetbrains.plugins.scala.settings._

import scala.annotation.tailrec
import scala.jdk.CollectionConverters._

final class ScalaLanguageInjector extends MultiHostInjector {

  import ScalaLanguageInjector._

  override def elementsToInjectIn: ju.List[_ <: Class[_ <: PsiElement]] = ElementsToInjectIn

  // TODO: rethink, add caching
  //  this adds significant typing performance overhead if file contains many injected literals
  override def getLanguagesToInject(registrar: MultiHostRegistrar, host: PsiElement): Unit = {
    implicit val support: ScalaLanguageInjectionSupport = LanguageInjectionSupport.EP_NAME.findExtension(classOf[ScalaLanguageInjectionSupport])
    if (support == null)
      return

    val literals: Seq[ScStringLiteral] = literalsOf(host)
    if (literals.isEmpty)
      return

    implicit val r: MultiHostRegistrar = registrar

    if (injectUsingIntention(host, literals))
      return
    if (injectUsingComment(host, literals))
      return

    val project = host.getProject
    implicit val projectSettings: ScalaProjectSettings = ScalaProjectSettings.getInstance(project)
    if (injectUsingInterpolatedStringPrefix(host, literals, projectSettings.getIntInjectionMapping))
      return

    //TODO: make this check earlier? when exactly? should we support explicit injection via intention or comment?
    if (projectSettings.isDisableLangInjection)
      return

    if (injectUsingAnnotation(host, literals))
      return

    val injectionConfiguration = Configuration.getProjectInstance(project)
    if (injectUsingScalaPatterns(host, literals, injectionConfiguration))
      return

    if (injectUsingJavaPatterns(host, literals, injectionConfiguration))
      return

    // final expression uses return for easy debugging
  }

  private def isDfaEnabled: Boolean =
    Configuration.getInstance.getAdvancedConfiguration.getDfaOption != Configuration.DfaOption.OFF

  /**
   * @return 1) `host` itself - if host is string literal that is not inside string concatenation <br>
   *         2) string concatenation operands if `host` is a top level concatenation expression <br>
   *         3) empty collection otherwise
   */
  private def literalsOf(host: PsiElement): Seq[ScStringLiteral] = host.getParent match {
    case ScInfixExpr(_, ElementText("+"), _) =>
      // if string literal is inside concatenations skip it
      // we would like to process top-level expressions only
      Seq.empty
    case _ =>
      val expressions = host.depthFirst {
        case expression: ScExpression => !expression.getParent.is[ScInterpolatedStringLiteral]
        case _ => true
      }.toList.filter(_.is[ScExpression])

      val suitable = expressions.forall {
        case s: ScStringLiteral => s.isString
        case _: ScInterpolatedPatternPrefix => true
        case _: ScInfixExpr => true
        case r: ScReferenceExpression if r.textMatches("+") => true //string concatenation
        case expression => expression.getParent.is[ScInterpolatedStringLiteral]
      }

      if (suitable) {
        expressions.filter {
          case literal: ScStringLiteral => literal.isString
          case _ => false
        }.map {
          _.asInstanceOf[ScStringLiteral]
        }
      } else {
        Seq.empty
      }
  }

  /**
   * @example {{{
   *   sql"""SELECT * FROM $tableName ORDER BY $field ASC;
   *
   *   implicit class StringContextOps(val sc: StringContext) {
   *     def sql(args: Any*): String = ???
   *   }
   * }}}
   */
  private def injectUsingInterpolatedStringPrefix(
    host: PsiElement,
    literals: Seq[ScStringLiteral],
    mapping: ju.Map[String, String]
  )(implicit support: ScalaLanguageInjectionSupport, registrar: MultiHostRegistrar): Boolean = {
    val interpolatedLiterals = literals.filterByType[ScInterpolatedStringLiteral]
    val allStringsAreInterpolated = interpolatedLiterals.size == literals.size
    if (allStringsAreInterpolated) {
      val languages = for {
        interpolated <- interpolatedLiterals
        reference <- interpolated.reference

        langId = mapping.get(reference.getText)
        if StringUtils.isNotBlank(langId)
      } yield langId

      if (languages.nonEmpty) {
        languages.toSet.toList match {
          case langId :: Nil =>
            val language = Language.findLanguageByID(langId)
            if (language != null) {
              inject(host, interpolatedLiterals, language, prefix = "", suffix = "")
            }
          case _ => // only inject if all interpolations in string concatenation have same language ids
        }
        true
      }
      else false
    }
    else false
  }

  /**
   * @example {{{
   *   //language=SQL
   *   "SELECT * FROM employees ORDER BY name ASC;"
   * }}}
   */
  private def injectUsingComment(
    host: PsiElement,
    literals: Seq[ScStringLiteral]
  )(implicit support: ScalaLanguageInjectionSupport, registrar: MultiHostRegistrar): Boolean = {
    val injection = InjectorUtils.findCommentInjection(host, "comment", null)
    if (injection == null)
      return false
    val langId: String = injection.getInjectedLanguageId
    if (StringUtils.isBlank(langId))
      return false
    val language = injection.getInjectedLanguage
    if (language == null)
      return false

    inject(host, literals, language, injection.getPrefix, injection.getSuffix)

    true
  }

  /**
   * @note language annotation class can be changed via<br>
   *       `File | Settings | Editor | Language Injections | Advanced`
   * @example {{{
   *    package org.intellij.lang.annotations
   *
   *    class Language(val value: String) extends scala.annotation.StaticAnnotation
   *
   *    def format(@Language("Scala") code: String): Unit = ???
   *
   *    format("""val value = 2 + 2""")
   * }}}
   */
  private def injectUsingAnnotation(
    host: PsiElement,
    literals: Seq[ScStringLiteral]
  )(implicit support: ScalaLanguageInjectionSupport, registrar: MultiHostRegistrar): Boolean =
    host match {
      case expression: ScExpression if isDfaEnabled =>
        val annotationName = Configuration.getProjectInstance(expression.getProject).getAdvancedConfiguration.getLanguageAnnotationClass
        injectUsingAnnotation(expression, literals, annotationName)
      case _ =>
        false
    }

  private def injectUsingAnnotation(
    host: ScExpression,
    literals: Seq[ScStringLiteral],
    annotationQualifiedName: String
  )(implicit support: ScalaLanguageInjectionSupport, registrar: MultiHostRegistrar): Boolean = {
    val maybeAnnotationOwner: Option[AnnotationOwner] = host match {
      case literal: ScStringLiteral =>
        if (literal.isString) annotationOwnerForScStringLiteral(literal)
        else None
      case _ =>
        annotationOwnerFor(host) //.orElse(implicitAnnotationOwnerFor(host)) //NOTE: implicit conversion checking (SCL-2599), disabled (performance reasons)
    }

    val maybePair = for {
      annotationOwner <- maybeAnnotationOwner
      annotation <- annotationOwner.getAnnotations.find(_.getQualifiedName == annotationQualifiedName)
      languageId <- readAttribute(annotation, "value")
      language = InjectedLanguage.findLanguageById(languageId)
      if language != null
    } yield (annotation, language)

    for {
      (annotation, language) <- maybePair
    } inject(
      host,
      literals,
      language,
      readAttribute(annotation, "prefix").mkString,
      readAttribute(annotation, "suffix").mkString
    )

    maybePair.isDefined
  }

  // FIXME: looks like this does not work for now, see SCL-15463
  private def injectUsingIntention(
    host: PsiElement,
    literals: Seq[ScStringLiteral]
  )(implicit support: ScalaLanguageInjectionSupport, registrar: MultiHostRegistrar): Boolean = {
    val stringHost = host match {
      case s: ScStringLiteral => s
      case _ =>
        return false
    }

    val registry = TemporaryPlacesRegistry.getInstance(host.getProject)
    val injectedLanguage = registry.getLanguageFor(stringHost, stringHost.getContainingFile)
    if (injectedLanguage == null)
      return false

    val language = injectedLanguage.getLanguage
    if (language != null) {
      inject(
        stringHost,
        literals,
        language,
        injectedLanguage.getPrefix,
        injectedLanguage.getSuffix
      )
    }
    true
  }

  /**
   * Scala patterns are defined in<br>
   * `resources/org/jetbrains/plugins/scala/injection/scalaInjections.xml`
   *
   * @example {{{
   *    "Hello [\\d\\w]+!".r
   * }}}
   */
  private def injectUsingScalaPatterns(
    host: PsiElement,
    literals: Seq[ScStringLiteral],
    injectionConfiguration: Configuration
  )(implicit support: ScalaLanguageInjectionSupport, registrar: MultiHostRegistrar): Boolean = {
    val injectionsForScala = injectionConfiguration.getInjections(ScalaLanguageInjectionSupport.Id)
    injectUsingPatterns(host, host, literals, injectionsForScala)
  }

  private def injectUsingJavaPatterns(
    host: PsiElement,
    literals: Seq[ScStringLiteral],
    injectionConfiguration: Configuration
  )(implicit support: ScalaLanguageInjectionSupport, registrar: MultiHostRegistrar): Boolean = {
    if (shouldAvoidResolve)
      return false

    val (ref, argumentIndex) = ScalaElementPatternImpl.methodRefWithArgumentIndex(host) match {
      case Some(value) => value
      case _ =>
        return false
    }

    val javaMethod = ref.resolve() match {
      case _: ScalaPsiElement =>
        return false //skip scala code, we are only interested in java methods
      case javaMethod: PsiMethod =>
        javaMethod
      case _ =>
        return false
    }

    val javaParameter = javaMethod.getParameterList.getParameters.applyOrElse(argumentIndex, null)
    if (javaParameter == null)
      return false

    val injectionsForJava = injectionConfiguration.getInjections(JavaLanguageInjectionSupport.JAVA_SUPPORT_ID)
    injectUsingPatterns(host, javaParameter, literals, injectionsForJava)
  }

  private def injectUsingPatterns(
    host: PsiElement,
    elementToAccept: PsiElement,
    literals: Seq[ScStringLiteral],
    injections: ju.List[BaseInjection]
  )(implicit support: ScalaLanguageInjectionSupport, registrar: MultiHostRegistrar): Boolean = {
    /**
     * Optimization against freeze (see SCL-16749):<br>
     * acceptsPsiElement under the hood does many resolving
     *
     * @see [[org.jetbrains.plugins.scala.patterns.ScalaElementPattern]]
     *      [[org.intellij.plugins.intelliLang.inject.config.BaseInjection#acceptsPsiElement(com.intellij.psi.PsiElement)]]
     *      [[./scalaInjections.xml]]
     */
    if (shouldAvoidResolve)
      return false

    val baseInjection = injections.iterator.asScala.find(_.acceptsPsiElement(elementToAccept)).orNull
    if (baseInjection == null)
      return false

    val language = baseInjection.getInjectedLanguage
    if (language == null)
      return false

    inject(host, literals, language, baseInjection.getPrefix, baseInjection.getSuffix)

    true
  }
}

private object ScalaLanguageInjector {

  private val ElementsToInjectIn = ju.Arrays.asList(
    classOf[ScInterpolatedStringLiteral],
    classOf[ScStringLiteral],
    classOf[ScInfixExpr] //string concatenation
  )

  private type AnnotationOwner = PsiAnnotationOwner with PsiElement

  private[this] object CachedAnnotationOwner {

    private[this] val OwnerKey = Key.create[(Option[AnnotationOwner], Long)]("scala.annotation.owner")

    def apply(literal: ScStringLiteral, modCount: Long): Option[Option[AnnotationOwner]] = {
      val owner = literal.getCopyableUserData(OwnerKey)
      owner match {
        case null => None
        case (result, cachedModCount) =>
          if ((modCount == cachedModCount || isEdt) && result.forall(_.isValid))
            Some(result)
          else
            None
      }
    }

    def update(literal: ScStringLiteral, maybeOwner: (Option[AnnotationOwner], Long)): Unit =
      literal.putCopyableUserData(OwnerKey, maybeOwner)
  }

  @inline
  private def isEdt: Boolean = ApplicationManager.getApplication.isDispatchThread

  private def inject(
    host: PsiElement,
    literals: Seq[ScStringLiteral],
    language: Language,
    prefix: String,
    suffix: String
  )(implicit support: ScalaLanguageInjectionSupport, registrar: MultiHostRegistrar): Unit = {
    val InjectionSplitResult(isUnparseable, injectionInfos) = ScalaInjectionInfosCollector.collectInjectionInfos(literals, language, prefix, suffix)

    InjectorUtils.registerInjection(language, host.getContainingFile, injectionInfos.asJava, registrar)
    InjectorUtils.registerSupport(support, true, host, language)
    InjectorUtils.putInjectedFileUserData(
      host,
      language,
      InjectedLanguageManager.FRANKENSTEIN_INJECTION,
      if (isUnparseable) _root_.java.lang.Boolean.TRUE else null
    )
  }

  private def annotationOwnerForScStringLiteral(stringLiteral: ScStringLiteral): Option[AnnotationOwner] = {
    val modCount: Long = BlockModificationTracker(stringLiteral).getModificationCount
    CachedAnnotationOwner(stringLiteral, modCount) match {
      case Some(result) => result
      case _ =>
        val result = annotationOwnerFor(stringLiteral)
        CachedAnnotationOwner(stringLiteral) = (result, modCount)
        result
    }
  }

  @tailrec
  private def annotationOwnerFor(expression: ScExpression): Option[AnnotationOwner] = expression.getParent match {
    case pattern: ScPatternDefinition                   => Some(pattern)
    case variable: ScVariableDefinition                 => Some(variable)
    case param: ScParameter                             => Some(param)
    case _: ScArgumentExprList                          => parameterOf(expression)
    case tuple: ScTuple if tuple.isCall                 => parameterOf(expression)
    case ScAssignment(leftExpression, _)                => assignmentTarget(leftExpression)
    case parExpr: ScParenthesisedExpr                   => annotationOwnerFor(parExpr)
    case infix: ScInfixExpr                             =>
      if (expression == infix.getFirstChild)
        if (isSafeCall(infix))
          annotationOwnerFor(infix)
        else
          None
      else
        parameterOf(expression)
    case safeCall: ScExpression if isSafeCall(safeCall) => annotationOwnerFor(safeCall)
    case _                                              => None
  }

  private def isSafeCall(testExpr: ScExpression): Boolean = {
    val name = testExpr match {
      case MethodInvocation(ElementText(text), _) => text
      case ref: ScReferenceExpression => ref.refName
      case _ => null
    }

    name match {
      case "stripMargin" | "+" => true
      case _ => false
    }
  }

  private[this] def parameterOf(argument: ScExpression): Option[AnnotationOwner] = {
    if (shouldAvoidResolve)
      return None

    def getParameter(methodInv: MethodInvocation, index: Int): Option[PsiElement with PsiAnnotationOwner] = {
      if (index == -1) None else {
        val refOpt = methodInv.getEffectiveInvokedExpr.asOptionOf[ScReferenceExpression]
        refOpt.flatMap { ref =>
          ref.resolve().toOption match {
            case Some(f: ScFunction) =>
              val parameters = f.parameters
              parameters.lift(index)
            case Some(m: PsiMethod) =>
              val parameters = m.parameters
              parameters.lift(index).safeMap(_.getModifierList)
            case _ => None
          }
        }
      }
    }

    argument.getParent match {
      case args: ScArgumentExprList =>
        args.getParent match {
          case call: ScMethodCall => getParameter(call, args.exprs.indexOf(argument))
          case _ => None
        }
      case tuple: ScTuple if tuple.isCall =>
        getParameter(tuple.getContext.asInstanceOf[ScInfixExpr], tuple.exprs.indexOf(argument))
      case infix: ScInfixExpr => getParameter(infix, 0)
      case _ => None
    }
  }

  //avoid reference resolving on EDT thread
  private def shouldAvoidResolve: Boolean =
    isEdt && !ApplicationManager.getApplication.isUnitTestMode

  // map(x) = y check
  private[this] def assignmentTarget(leftExpression: ScExpression): Option[AnnotationOwner] = leftExpression match {
    case _: ScMethodCall => None
    case ScReference(target) =>
      val context = target match {
        case p: ScReferencePattern => p.getParent.getParent
        case field: PsiField => field.getModifierList
        case _ => target
      }

      context match {
        case owner: AnnotationOwner => Some(owner)
        case _ => None
      }
    case _ => None
  }
}
