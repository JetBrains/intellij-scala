package org.jetbrains.plugins.scala.lang.psi.uast.expressions

import java.{util => ju}

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiLocalVariable, PsiType, PsiVariable}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockStatement
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.uast.baseAdapters.ScUElement
import org.jetbrains.plugins.scala.lang.psi.uast.converter.Scala2UastConverter._
import org.jetbrains.plugins.scala.lang.psi.uast.declarations.{ScUVariable, ScUVariableCommon}
import org.jetbrains.plugins.scala.lang.psi.uast.internals.LazyUElement
import org.jetbrains.uast._

import scala.annotation.tailrec
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
  containingTypeDef: ScTemplateDefinition,
  override protected val parent: LazyUElement
) extends UDeclarationsExpressionAdapter
    with ScUElement {

  override type PsiFacade = PsiElement

  @Nullable
  override def getSourcePsi: PsiElement = scElement

  override def getDeclarations: ju.List[UDeclaration] =
    Seq(
      new ScULocalFunction(scElement, containingTypeDef, LazyUElement.just(this)): UDeclaration
    ).asJava

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
                       containingTypeDef: ScTemplateDefinition,
                       override protected val parent: LazyUElement)
    extends {
  private[this] val _lightVariable: PsiLocalVariable =
    ScUVariable.createLightVariable(
      isField = false,
      name = funDef.name,
      isVal = true,
      containingTypeDef,
      modifiersList = None,
      funDef
    )
} with ULocalVariableAdapter(_lightVariable) with ScUVariableCommon {

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

object ScULocalFunction {
  @tailrec
  def findContainingTypeDef(funDef: ScFunction): Option[ScTemplateDefinition] =
    funDef.containingClass match {
      case found: ScTemplateDefinition => Some(found)
      case _ =>
        PsiTreeUtil.getParentOfType(funDef, classOf[ScFunction]) match {
          case containingFun: ScFunction => findContainingTypeDef(containingFun)
          case _                         => None
        }
    }
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
