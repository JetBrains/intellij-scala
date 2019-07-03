package org.jetbrains.plugins.scala.util

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.PsiElement
import org.apache.maven.artifact.versioning.ComparableVersion
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.parser.parsing.top.TmplDef
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScGenericCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.lang.psi.types.api.Any
import org.jetbrains.plugins.scala.lang.psi.types.{ScParameterizedType, ScType, ScalaType}
import org.jetbrains.plugins.scala.macroAnnotations.CachedInUserData
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.project._

/**
  * Partially based on and inspired by contribution from @vovapolu
  * (https://github.com/JetBrains/intellij-scala/pull/435).
  */
class KindProjectorUtil(project: Project) {
  import KindProjectorUtil._

  /**
    * Synthetic top level declarations required to make kind-projector
    * specific syntax (e.g. `Lambda`, `λ`, `?`) resolvable.
    */
  val syntheticDeclarations: Seq[PsiElement] = {
    implicit val projectContext: Project = project

    // used in type-level lambdas
    val syntheticClasses =
      (Seq(Lambda, LambdaSymbolic) ++ inlineSyntaxIds)
        .map(new ScSyntheticClass(_, Any))

    // used in value-level lambdas
    val syntheticObjects =
      Seq(Lambda, LambdaSymbolic)
        .map(createPolyLambdaSyntheticObject)

    syntheticClasses ++ syntheticObjects
  }
}

object KindProjectorUtil {
  val Lambda: String               = "Lambda"
  val LambdaSymbolic: String       = "λ"
  val inlineSyntaxIds: Seq[String] = Seq("?", "+?", "-?", "*", "+*", "-*")

  def apply(project: Project): KindProjectorUtil = ServiceManager.getService(project, classOf[KindProjectorUtil])

  private[this] val newSyntaxVersion = new ComparableVersion("0.10.0")
  private[this] val VersionPattern = "(?:.+?)(?:-(\\d.*?))?\\.jar".r

  def placeholderSymbolFor(e: PsiElement): String =
    if (isQuestionMarkSyntaxDeprecatedFor(e)) "*"
    else                                      "?"

  /**
   * Usage of `?` placeholder is going to be deprecated, since it will be used in Dotty
   * to express wildcard types (e.g. `List[? <: Foo]`) to allow the use of `_` in type lambdas.
   * Use `*` instead.
   */
  def isQuestionMarkSyntaxDeprecatedFor(e: PsiElement): Boolean =
    e.kindProjectorPlugin.exists {
      case VersionPattern(version) => isQuestionMarkSyntaxDeprecatedFor(version)
      case _                       => false
    }

  private def isQuestionMarkSyntaxDeprecatedFor(version: String): Boolean =
    new ComparableVersion(version).compareTo(newSyntaxVersion) >= 0

  /**
    * Creates synhtetic companion object used in value-level polymorphic lambdas
    * (e.g. `val a: PF[List, Option] = λ[PF[List, Option]].run(_.headOption)`).
    * Apply method return type is computed in an ad-hoc manner in [[org.jetbrains.plugins.scala.lang.psi.impl.expr.ScGenericCallImpl]]
    * See usages of [[PolymorphicLambda]] extractor.
    */
  private def createPolyLambdaSyntheticObject(objectName: String)(implicit cxt: ProjectContext) = {
    val text =
      s"""
         |object $objectName {
         |  def apply[T[_[_], _[_]]]: Any = ???
         |}
       """.stripMargin

    ScalaPsiElementFactory.createElement(text)(TmplDef.parse)
  }

  /**
    * As per kind-projector README:
    *
    *  This rewrite requires that the following are true:
    *    1) F and G are unary type constructors (i.e. of shape F[_] and G[_]).
    *    2) <expr> is an expression of type Function1[_, _].
    *    3) Op is parameterized on two unary type constructors.
    *    4) someMethod is parametric (for any type A it takes F[A] and returns G[A]).
    */
  private[this] def canBeRewritten(fn: ScFunction, tparams: Seq[ScTypeParam]): Boolean = {
    val isAbstract     = fn.isAbstractMember
    val singleArgument = fn.parameters.size == 1
    val hasTypeParam   = fn.typeParameters.size == 1

    // tparams must be two unary type constructors
    val typeParametersValid = tparams.length == 2 && tparams.forall(_.typeParameters.size == 1)

    def isAppliedTypeConstructor(
      typeElement: ScTypeElement,
      cons:        ScTypeParam,
      argument:    ScTypeParam
    ): Boolean = typeElement match {
      case ScParameterizedTypeElement(ScSimpleTypeElement(tcons), Seq(ScSimpleTypeElement(arg))) =>
        tcons.refName == cons.name && arg.refName == argument.name
      case _ => false
    }

    isAbstract && typeParametersValid && singleArgument && hasTypeParam && {
      val correctReturnType =
        fn.returnTypeElement.exists(isAppliedTypeConstructor(_, tparams.last, fn.typeParameters.head))

      val correctParameterType =
        fn.parameters.head.typeElement.exists(isAppliedTypeConstructor(_, tparams.head, fn.typeParameters.head))

      correctReturnType && correctParameterType
    }
  }

  private[this] def containingFileModTracker(tdef: ScTypeDefinition): ModificationTracker = {
    val rootManager = ProjectRootManager.getInstance(tdef.getProject)

    def isInLibrary(file: ScalaFile): Boolean =
      file.isCompiled && rootManager.getFileIndex.isInLibrary(file.getVirtualFile)

    tdef.getContainingFile match {
      case file: ScalaFile if isInLibrary(file) => rootManager
      case null                                 => ModificationTracker.NEVER_CHANGED
      case file                                 => Option(file.getVirtualFile).getOrElse(ModificationTracker.NEVER_CHANGED)
    }
  }

  implicit class `synthetic poly-lambda builder ext`(private val tdef: ScTypeDefinition) extends AnyVal {
    /**
    * Creates an intermidiate "Builder" trait which represents the type of an
    * expression of shape `Lambda[Op[F, G]]`, i.e. suppose we have the following definitions
    * {{{
    * trait Op[M[_], N[_]] {
    *   def someMethod[A](x: M[A]): N[A]
    *   def anotherMethod[B](x: M[A]): N[A]
    * }
    * }}}
    * A synthetic trait with the following definitions will be generated.
    * {{{
    * trait OpPolyLambdaBuilder[F[_], G[_]] {
    *   type A
    *   def someMethod(f: F[A] => G[A]): Op[F, G] = ???
    *   def anotherMethod(f: F[A] => G[A]): Op[F, G] = ???
    * }
    * }}}
    *
    * Returns parameterized type designated to generated trait, with `f` and `g` as it's type arguments.
    */
    @CachedInUserData(tdef, containingFileModTracker(tdef))
    def synhteticPolyLambdaBuilder(f: ScTypeElement, g: ScTypeElement): Option[ScType] = {
      val tparams = tdef.typeParameters
      val methods = tdef.functions.filter(canBeRewritten(_, tparams))

      methods.nonEmpty.option {
        val methodsText = methods.map { m =>
          s"def ${m.name}(f: F[A] => G[A]): ${tdef.name}[F, G] = ???"
        }.mkString("\n  ")

        val text =
          s"""
             |trait `${tdef.getName}PolyLambdaBuilder`[F[_], G[_]] {
             |  type A
             |  $methodsText
             |}
         """.stripMargin

        val buiderTrait = ScalaPsiElementFactory.createTypeDefinitionWithContext(text, tdef, null)

        ScParameterizedType(
          ScalaType.designator(buiderTrait),
          Seq(f.`type`().getOrAny, g.`type`().getOrAny)
        )
      }
    }
  }


  def kindProjectorPolymorphicLambdaType(
    target: ScTypeElement,
    lhs:    ScTypeElement,
    rhs:    ScTypeElement,
    place:  PsiElement
  )(implicit pc: ProjectContext): Option[ScType] =
    for {
      targetTpe   <- target.`type`().toOption
      targetClass <- targetTpe.extractClass
      tdef        <- targetClass.asOptionOf[ScTypeDefinition]
      tpe         <- tdef.synhteticPolyLambdaBuilder(lhs, rhs)
    } yield tpe

  object PolymorphicLambda {
    private[this] val polyLambdaIds = Seq(Lambda, LambdaSymbolic)

    def unapply(gc: ScGenericCall): Option[(ScTypeElement, ScTypeElement, ScTypeElement)] =
      if (gc.kindProjectorPluginEnabled) {
        gc.referencedExpr match {
          case ref: ScReferenceExpression if !ref.isQualified && polyLambdaIds.contains(ref.getText) =>
            gc.arguments match {
              case Seq(infix @ ScInfixTypeElement(lhs, _, Some(rhs)))      => Option((infix, lhs, rhs))
              case Seq(tpe @ ScParameterizedTypeElement(_, Seq(lhs, rhs))) => Option((tpe, lhs, rhs))
              case _                                                       => None
            }
          case _ => None
        }
      } else None
  }
}
