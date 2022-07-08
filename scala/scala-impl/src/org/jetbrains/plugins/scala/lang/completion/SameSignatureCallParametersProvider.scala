package org.jetbrains.plugins.scala
package lang
package completion

import com.intellij.codeInsight.CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement
import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementBuilder}
import com.intellij.codeInsight.template.TemplateBuilderFactory
import com.intellij.psi.util.PsiTreeUtil.getContextOfType
import com.intellij.psi.{PsiElement, PsiMember, PsiMethod}
import com.intellij.ui.LayeredIcon
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.handlers.ScalaInsertHandler.AssignmentText
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScParameterizedTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction.CommonNames
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScConstructorOwner, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createExpressionFromText, createExpressionWithContextFromText}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import javax.swing.Icon

final class SameSignatureCallParametersProvider extends CompletionContributor {

  import SameSignatureCallParametersProvider._

  extendBasicAndSmart(classOf[ScMethodCall])(new MethodParametersCompletionProvider)

  extendBasicAndSmart(classOf[ScConstructorInvocation])(new ConstructorParametersCompletionProvider)

  private def extendBasicAndSmart(invocationClass: Class[_ <: ScalaPsiElement])
                                 (provider: CompletionProvider[CompletionParameters]): Unit = {
    val place = identifierWithParentsPattern(
      classOf[ScReferenceExpression],
      classOf[ScArgumentExprList],
      invocationClass
    ).afterLeaf("(", ",")
      .beforeLeaf(")")

    extend(CompletionType.BASIC, place, provider)
    extend(CompletionType.SMART, place, provider)
  }

}

object SameSignatureCallParametersProvider {

  private final class MethodParametersCompletionProvider extends ScalaCompletionProvider {

    override protected def completionsFor(position: PsiElement)
                                         (implicit parameters: CompletionParameters,
                                          context: ProcessingContext): Iterable[LookupElementBuilder] = {
      val argumentsList = findArgumentsList(position)

      argumentsList.getContext match {
        case ScMethodCall.withDeepestInvoked(reference: ScReferenceExpression) =>
          val argumentToStart = new ArgumentToStart(argumentsList)()

          createFunctionArgumentsElements(
            reference,
            argumentToStart,
            reference.qualifier.exists(_.is[ScSuperReference]),
            parameters.getInvocationCount
          ) ++ createAssignmentElements(reference, argumentToStart)
        case _ => Iterable.empty
      }
    }

    /** Complete function's arguments (including syntactic sugar for apply): `(foo, bar, baz)` starting from the given
     * argument. Only applicable when there are variables with the same name and matching type in scope for every
     * argument starting from argumentToStart */
    private def createFunctionArgumentsElements(reference: ScReferenceExpression,
                                                argumentToStart: ArgumentToStart,
                                                hasSuperQualifier: Boolean,
                                                invocationCount: Int) = for {
      ScalaResolveResult(method: ScMethodLike, substitutor) <- reference.completionVariants() ++ reference.multiResolveScala(incomplete = true)
      if method.name == CommonNames.Apply || method.name == reference.refName

      lookupElement <- createFunctionLookupElement(reference, method, argumentToStart, substitutor, invocationCount, hasSuperQualifier)
    } yield lookupElement

    /** Complete apply method's arguments: `(foo = ???, bar = ???, baz = ???)` starting from the given argument
     * and run an interactive [[com.intellij.codeInsight.template.Template]] */
    private def createAssignmentElements(reference: ScReferenceExpression,
                                         argumentToStart: ArgumentToStart) = for {
      ScalaResolveResult(function: ScFunction, substitutor) <- reference.multiResolveScala(incomplete = true).toSeq
      if function.isApplyMethod

      lookupElement <- createAssignmentLookupElement(function, argumentToStart, substitutor)
    } yield lookupElement

  }

  private final class ConstructorParametersCompletionProvider extends ScalaCompletionProvider {

    override protected def completionsFor(position: PsiElement)
                                         (implicit parameters: CompletionParameters,
                                          context: ProcessingContext): Iterable[LookupElementBuilder] = {
      val constructorInvocation = getContextOfType(position, classOf[ScConstructorInvocation])
      if (constructorInvocation == null) Iterable.empty
      else {
        val argumentsList = findArgumentsList(position)
        if (argumentsList == null) Iterable.empty
        else {
          constructorInvocation.typeElement match {
            case typeElement@Typeable(tp) =>
              tp.extractClassType match {
                //noinspection ScalaUnnecessaryParentheses
                case Some((constructorOwner: ScConstructorOwner, substitutor))
                  if (if (constructorOwner.hasTypeParameters) typeElement.is[ScParameterizedTypeElement] else true) =>
                  val argumentToStart = new ArgumentToStart(argumentsList)(constructorInvocation.arguments.indexOf(argumentsList))

                  val constructorArgsElements = createConstructorArgumentsElements(
                    position,
                    constructorOwner,
                    argumentToStart,
                    substitutor,
                    parameters.getInvocationCount
                  )

                  if (constructorOwner.is[ScTrait]) constructorArgsElements
                  else constructorArgsElements ++ createAssignmentElements(constructorOwner, argumentToStart, substitutor)
                case _ => Iterable.empty
              }
            case _ => Iterable.empty
          }
        }
      }
    }

    private def createConstructorArgumentsElements(context: PsiElement, constructorOwner: ScConstructorOwner, argumentToStart: ArgumentToStart,
                                                   substitutor: ScSubstitutor, invocationCount: Int) =
      constructorOwner.constructors.flatMap { extractedClassConstructor =>
        createFunctionLookupElement(context, extractedClassConstructor, argumentToStart, substitutor,
          invocationCount, hasSuperQualifier = true)
      }

    private def createAssignmentElements(constructorOwner: ScConstructorOwner, argumentToStart: ArgumentToStart, substitutor: ScSubstitutor) =
      constructorOwner.constructors.flatMap { extractedClassConstructor =>
        createAssignmentLookupElement(extractedClassConstructor, argumentToStart, substitutor)
      }
  }

  private[this] final class ArgumentToStart private(clauseIndex: Int,
                                                    argumentsToDrop: Int) {

    def this(list: ScArgumentExprList)
            (clauseIndex: Int = list.invocationCount - 1) = this(
      clauseIndex,
      list.children.count(_.textMatches(","))

      /** empty expressions cannot be handled via [[ScArgumentExprList.exprs]] */
    )

    def parametersNames(method: ScMethodLike): Seq[ScParameter] = method
      .parametersInClause(clauseIndex)
      .drop(argumentsToDrop)
  }

  private[this] sealed abstract class Argument(protected val typeable: Typeable,
                                               protected val iconable: PsiElement) {

    final def conformsTo(parameter: ScParameter,
                         substitutor: ScSubstitutor): Boolean = {
      val parameterType = substitutor(parameter.`type`().getOrAny)
      typeable.`type`().getOrAny.conforms(parameterType)
    }

    final def icon: Icon = iconable.getIcon(0)
  }

  private[this] final case class ExpressionArgument(override protected val typeable: ScReferenceExpression,
                                                    override protected val iconable: PsiElement)
    extends Argument(typeable, iconable)

  private[this] final case class ParameterArgument(override protected val typeable: ScParameter)
    extends Argument(typeable, typeable)

  private def createFunctionLookupElement(context: PsiElement,
                                          method: ScMethodLike,
                                          argumentToStart: ArgumentToStart,
                                          substitutor: ScSubstitutor,
                                          invocationCount: Int,
                                          hasSuperQualifier: Boolean): Option[LookupElementBuilder] =
    createLookupElement(method, argumentToStart, substitutor) {
      findResolvableParameters(context, invocationCount)
    }.map { builder =>
      builder.withMoveCaretInsertionHandler.withSuperMethodParameters(hasSuperQualifier)
    }

  private[this] def createAssignmentLookupElement(method: ScMethodLike,
                                                  argumentToStart: ArgumentToStart,
                                                  substitutor: ScSubstitutor): Option[LookupElementBuilder] =
    createLookupElement(method, argumentToStart, substitutor)(findMethodParameters(method)).map { builder =>
      builder.withTailText(AssignmentText).withInsertHandler(new AssignmentsInsertHandler)
    }

  private[this] def findResolvableParameters(reference: PsiElement,
                                             invocationCount: Int)
                                            (parameters: Seq[ScParameter]): Seq[(String, ExpressionArgument)] = for {
    parameter <- parameters
    name = parameter.name

    expression = createExpressionWithContextFromText(
      name,
      reference.getContext,
      reference
    ).asInstanceOf[ScReferenceExpression]

    iconable <- expression.resolve match {
      case method: PsiMethod if method.isConstructor || !method.isParameterless => None
      case member: PsiMember if !isAccessible(member, invocationCount)(reference) => None
      case iconable => Option(iconable)
    }
  } yield name -> ExpressionArgument(expression, iconable)

  private[this] def findMethodParameters(method: ScMethodLike): Seq[ScParameter] => Seq[(String, ParameterArgument)] = { _ =>
    method
      .parameterList
      .params
      .map { parameter =>
        parameter.name -> ParameterArgument(parameter)
      }
  }

  private[this] def createLookupElement(method: ScMethodLike,
                                        argumentToStart: ArgumentToStart,
                                        substitutor: ScSubstitutor)
                                       (argumentsWithNames: Seq[ScParameter] => Seq[(String, Argument)]) = {
    val parameters = argumentToStart.parametersNames(method)

    parameters.length match {
      case 0 | 1 => None
      case clauseLength =>
        val nameToArgument = argumentsWithNames(parameters).toMap

        applicableNames(parameters, substitutor, nameToArgument) match {
          case names if names.length == clauseLength =>
            val Seq(leftIcon, rightIcon) = names
              .take(2)
              .map(nameToArgument)
              .map(_.icon)

            Some {
              LookupElementBuilder
                .create(names.commaSeparated())
                .withIcon(compositeIcon(leftIcon, rightIcon))
            }
          case _ => None
        }
    }
  }

  private[this] def applicableNames(parameters: Seq[ScParameter],
                                    substitutor: ScSubstitutor,
                                    nameToArgument: Map[String, Argument]) =
    for {
      parameter <- parameters

      name = parameter.name
      if name != null

      argument <- nameToArgument.get(name)
      if argument.conformsTo(parameter, substitutor)
    } yield name

  private[this] abstract class ExpressionListInsertHandler extends InsertHandler[LookupElement] {

    override final def handleInsert(context: InsertionContext,
                                    element: LookupElement): Unit = context.getCompletionChar match {
      case ')' =>
      case _ =>
        val element = context
          .getFile
          .findElementAt(context.getStartOffset)

        findArgumentsList(element) match {
          case null =>
          case list => onExpressionList(list)(context)
        }
    }

    protected def onExpressionList(list: ScArgumentExprList)
                                  (implicit context: InsertionContext): Unit
  }

  private[this] final class AssignmentsInsertHandler extends ExpressionListInsertHandler {

    override protected def onExpressionList(list: ScArgumentExprList)
                                           (implicit context: InsertionContext): Unit = {
      foreachArgument(list) { argument =>
        val replacementText = argument.getText + AssignmentText + NotImplementedError
        argument.replaceExpression(
          createExpressionFromText(replacementText)(argument),
          removeParenthesis = false
        )
      }

      val newList = forcePsiPostprocessAndRestoreElement(list)
      createTemplateBuilder(newList)
        .run(context.getEditor, false)
    }

    private def foreachArgument(list: ScArgumentExprList)
                               (action: ScExpression => Unit)
                               (implicit context: InsertionContext): Unit =
      list
        .exprs
        .filterNot(_.getTextRange.getEndOffset < context.getStartOffset)
        .foreach(action)

    private def createTemplateBuilder(list: ScArgumentExprList)
                                     (implicit context: InsertionContext) = {
      val result = TemplateBuilderFactory
        .getInstance
        .createTemplateBuilder(list)

      foreachArgument(list) {
        case ScAssignment(_, Some(placeholder)) =>
          result.replaceElement(
            placeholder,
            NotImplementedError
          )
      }

      result
    }
  }

  private[this] def findArgumentsList(position: PsiElement) =
    getContextOfType(position, classOf[ScArgumentExprList])

  private[this] implicit class LookupElementBuilderExt(private val builder: LookupElementBuilder) extends AnyVal {

    import LookupElementBuilderExt._

    def withMoveCaretInsertionHandler: LookupElementBuilder =
      builder.withInsertHandler {
        new MoveCaretInsertHandler
      }

    def withSuperMethodParameters(hasSuperQualifier: Boolean): LookupElementBuilder =
      if (hasSuperQualifier)
        builder.withBooleanUserData(JavaCompletionUtil.SUPER_METHOD_PARAMETERS)
      else
        builder
  }

  private[this] object LookupElementBuilderExt {

    private final class MoveCaretInsertHandler extends ExpressionListInsertHandler {

      override protected def onExpressionList(list: ScArgumentExprList)
                                             (implicit context: InsertionContext): Unit =
        context
          .getEditor
          .getCaretModel
          .moveToOffset(list.getTextRange.getEndOffset) // put caret after )
    }
  }

  private[this] def compositeIcon(leftIcon: Icon,
                                  rightIcon: Icon) = {
    val result = new LayeredIcon(2)
    result.setIcon(
      rightIcon,
      0,
      2 * leftIcon.getIconWidth / 5,
      0
    )
    result.setIcon(
      leftIcon,
      1
    )
    result
  }

}
