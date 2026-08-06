package org.jetbrains.plugins.scala.lang.psi.uast.expressions

import com.intellij.psi.{PsiElement, PsiLocalVariable, PsiType, PsiVariable}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockStatement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.uast.baseAdapters.ScUElement
import org.jetbrains.plugins.scala.lang.psi.uast.converter.Scala2UastConverter._
import org.jetbrains.plugins.scala.lang.psi.uast.declarations.{ScUVariable, ScUVariableCommon}
import org.jetbrains.plugins.scala.lang.psi.uast.internals.LazyUElement

import java.{util => ju}
import org.jetbrains.uast._

import scala.jdk.CollectionConverters._

/**
  * Local [[ScFunctionDefinition]] adapter for the [[UDeclarationsExpression]]
  *
  * Local functions are represented like lambdas, e.g.
  * {{{
  *   def outer: Unit = {
  *     def inner: Unit = ???
  *     // ...
  *   }
  * }}}
  * will be represented like
  * {{{
  *   def outer: Unit = {
  *     val inner = () => ???
  *     // ...
  *   }
  * }}}
  * or in the UAST Log format:
  * `
  *   UMethod (outer)
  *    |- UBlockExpression
  *        |- UDeclarationsExpression
  *            |- UVariable (inner)
  *                |- ULambdaExpression
  *                    |- UMethodCall (???)
  * `
  *
  * @param scElement Scala PSI element representing local function definition
  */
final class ScULocalFunctionDeclarationExpression(
  override protected val scElement: ScFunctionDefinition,
  override protected val parent: LazyUElement
) extends UDeclarationsExpressionAdapter
    with ScUElement {

  override type PsiFacade = PsiElement

  @Nullable
  override def getSourcePsi: PsiElement = scElement

  override def getDeclarations: ju.List[UDeclaration] =
    ju.Collections.singletonList(new ScULocalFunction(scElement, LazyUElement.just(this))())

  // escapes looping caused by the default implementation
  override def getUAnnotations: ju.List[UAnnotation] =
    ju.Collections.emptyList()
}

/**
  * Local [[ScFunctionDefinition]] adapter for the [[ULocalVariable]]
  *
  * @param funDef Scala PSI element representing local function definition
  */
final class ScULocalFunction(funDef: ScFunctionDefinition,
                             override protected val parent: LazyUElement)
                            // hacky early initializer
                            (_lightVariable: PsiLocalVariable =
                             ScUVariable.createLightLocalVariable(
                               funDef.name,
                               funDef.getContainingFile,
                               funDef,
                               modifierList = None,
                               isField = false,
                               isFinal = true
                             )
                            ) extends ULocalVariableAdapter(_lightVariable) with ScUVariableCommon {

  override type PsiFacade = PsiLocalVariable

  override protected val scElement: PsiFacade = _lightVariable
  @Nullable
  override def getSourcePsi: PsiElement = funDef

  override protected def lightVariable: PsiVariable = _lightVariable
  override protected def nameId: PsiElement = funDef.nameId
  override protected def typeElem: Option[ScTypeElement] = None
  override protected def initializer: Option[ScBlockStatement] = Some(funDef)

  @Nullable
  override def getUastInitializer: UExpression =
    new ScULocalFunctionLambdaExpression(funDef, LazyUElement.just(this))
}

/**
  * [[ScFunctionDefinition]] adapter for the [[ULambdaExpression]]
  *
  * @param scElement Scala PSI expression representing local function definition
  */
final class ScULocalFunctionLambdaExpression(
  override protected val scElement: ScFunctionDefinition,
  override protected val parent: LazyUElement
) extends ScUGenLambda {

  override type PsiFacade = PsiElement

  override protected def body: Option[PsiElement] = scElement.body
  override protected def isExplicitLambda: Boolean = true

  @Nullable
  override def getFunctionalInterfaceType: PsiType = null

  override def getValueParameters: ju.List[UParameter] =
    scElement.parameters.flatMap(_.convertTo[UParameter](parent = this)).asJava
}
