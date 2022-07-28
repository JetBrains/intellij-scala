package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import com.intellij.openapi.project.Project
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScMethodLike
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockStatement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction.CommonNames.{Apply, Unapply, UnapplySeq}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiTypeParameterList
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.ObjectWithCaseClassCompanion
import org.jetbrains.plugins.scala.lang.psi.light.ScFunctionWrapper
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._

import javax.swing.Icon

/**
 * Represents Scala's internal function definitions and declarations
 */
trait ScFunction
    extends ScalaPsiElement
    with ScMember.WithBaseIconProvider
    with ScParameterOwner
    with ScDocCommentOwner
    with ScTypedDefinition
    with ScCommentOwner
    with ScDeclaredElementsHolder
    with ScMethodLike
    with ScBlockStatement {

  private[this] val probablyRecursive = ThreadLocal.withInitial[Boolean](() => false)

  final def isProbablyRecursive: Boolean = probablyRecursive.get

  //noinspection AccessorLikeMethodIsUnit
  final def isProbablyRecursive_=(value: Boolean): Unit = {
    probablyRecursive.set(value)
  }

  final def syntheticCaseClass: ScClass = name match {
    case Apply | Unapply | UnapplySeq =>
      syntheticContainingClass match {
        case ObjectWithCaseClassCompanion(_, cl) => cl
        case _                                   => null
      }
    case _ => null
  }

  def hasUnitResultType: Boolean

  def isParameterless: Boolean = paramClauses.clauses.isEmpty

  def isEmptyParen: Boolean = paramClauses.clauses.size == 1 && paramClauses.params.isEmpty

  def isNative: Boolean = hasAnnotation("scala.native")

  override def getTextOffset: Int = nameId.getTextRange.getStartOffset

  def hasParameterClause: Boolean

  def definedReturnType: TypeResult

  /**
   * Optional Type Element, denotion function's return type
   * May be omitted for non-recursive functions
   */
  def returnTypeElement: Option[ScTypeElement]

  def returnType: TypeResult

  def hasExplicitType: Boolean = returnTypeElement.isDefined

  def paramClauses: ScParameters

  override def parameterList: ScParameters = paramClauses // TODO merge

  def parameterListCount: Int

  override def clauses: Option[ScParameters] = Some(paramClauses)

  override def declaredElements: Seq[ScFunction] = Seq(this)

  def extensionMethodOwner: Option[ScExtension]

  def isExtensionMethod: Boolean

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitFunction(this)
  }

  override def psiTypeParameters: Array[PsiTypeParameter] = typeParameters.makeArray(PsiTypeParameter.ARRAY_FACTORY)

  override def getTypeParameterList = new FakePsiTypeParameterList(getManager, getLanguage, typeParameters.toArray, this)

  override def hasTypeParameters: Boolean = typeParameters.nonEmpty

  override def getParameterList: ScParameters = paramClauses

  /** PsiMethod wrappers for java compatibility
    * @return Empty array, if containing class is null.
    */
  def getFunctionWrappers(isStatic: Boolean, isAbstract: Boolean, cClass: Option[PsiClass] = None): Seq[ScFunctionWrapper]

  override def parameters: Seq[ScParameter] = paramClauses.params

  def superMethods: Seq[PsiMethod]

  def superMethod: Option[PsiMethod]

  def superMethodAndSubstitutor: Option[(PsiMethod, ScSubstitutor)]

  def superSignatures: Seq[TermSignature]

  def superSignaturesIncludingSelfType: Seq[TermSignature]

  def hasAssign: Boolean

  override def getName: String = {
    if (isConstructor) Option(getContainingClass).map(_.getName).getOrElse(super.getName)
    else super.getName
  }

  override def setName(name: String): PsiElement = {
    if (isConstructor) this
    else super.setName(name)
  }

  override def getIcon(flags: Int): Icon = super[WithBaseIconProvider].getIcon(flags)
}

object ScFunction {

  implicit class Ext(private val function: ScFunction) extends AnyVal {

    import CommonNames._

    private implicit def project: Project = function.getProject

    private implicit def elementScope: ElementScope = function.elementScope

    def isCopyMethod: Boolean = function.name == Copy

    def isApplyMethod: Boolean = function.name == Apply

    def isUnapplyMethod: Boolean = Unapplies(function.name)

    def isForComprehensionMethod: Boolean = ForComprehensions(function.name)

    /** Is this function sometimes invoked without it's name appearing at the call site? */
    def isSpecial: Boolean = Special(function.name)

    def isImplicitConversion: Boolean = {
      val isImplicit = function.hasModifierProperty("implicit")
      val hasSingleNonImplicitParam = {
        val clauses = function.paramClauses.clauses
        clauses.nonEmpty &&
          clauses.head.parameters.size == 1 && !clauses.head.isImplicit &&
          clauses.drop(1).forall(_.isImplicit)
      }
      isImplicit && hasSingleNonImplicitParam
    }
  }

  object inSynthetic {
    def unapply(func: ScFunction): Option[ScClass] = Option(func.syntheticCaseClass)
  }

  object CommonNames {
    val Copy = "copy"

    val Apply = "apply"
    val Update = "update"
    val GetSet: Set[String] = Set(Apply, Update)

    val Unapply = "unapply"
    val UnapplySeq = "unapplySeq"
    val Unapplies: Set[String] = Set(Unapply, UnapplySeq)

    val Foreach = "foreach"
    val Map = "map"
    val FlatMap = "flatMap"
    val Filter = "filter"
    val WithFilter = "withFilter"
    val ForComprehensions: Set[String] = Set(Foreach, Map, FlatMap, Filter, WithFilter)

    val Special: Set[String] = GetSet ++ Unapplies ++ ForComprehensions
  }
}
