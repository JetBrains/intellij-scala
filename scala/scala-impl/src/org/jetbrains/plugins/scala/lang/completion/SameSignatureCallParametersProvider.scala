package org.jetbrains.plugins.scala
package lang
package completion

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementBuilder}
import com.intellij.psi.util.PsiTreeUtil.getContextOfType
import com.intellij.psi.{PsiElement, PsiMethod, PsiParameter}
import com.intellij.ui.LayeredIcon
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScParameterizedTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructorInvocation, ScMethodLike, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScMethodCall, ScReferenceExpression, ScSuperReference}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/**
 * @author Alefas
 * @since 03.09.13
 */
final class SameSignatureCallParametersProvider extends ScalaCompletionContributor {

  import SameSignatureCallParametersProvider._

  extendBasicAndSmart(classOf[ScConstructorInvocation])(new ConstructorParametersCompletionProvider)

  extendBasicAndSmart(classOf[ScMethodCall])(new SuperCallParametersCompletionProvider)

  private def extendBasicAndSmart(invocationClass: Class[_ <: ScalaPsiElement])
                                 (provider: CompletionProvider[CompletionParameters]): Unit = {
    val place = identifierWithParentsPattern(
      classOf[ScReferenceExpression],
      classOf[ScArgumentExprList],
      invocationClass
    )
    extend(CompletionType.BASIC, place, provider)
    extend(CompletionType.SMART, place, provider)
  }

}

object SameSignatureCallParametersProvider {

  private class SuperCallParametersCompletionProvider extends ScalaCompletionProvider {

    override protected def completionsFor(position: PsiElement)
                                         (implicit parameters: CompletionParameters,
                                          context: ProcessingContext): Iterable[LookupElementBuilder] = {
      val args = getContextOfType(position, classOf[ScArgumentExprList])
      val call = args.getContext.asInstanceOf[ScMethodCall]

      call.deepestInvokedExpr match {
        case reference@ScReferenceExpression.withQualifier(_: ScSuperReference) =>
          getContextOfType(reference, classOf[ScFunction]) match {
            case null => Iterable.empty
            case function if function.name == reference.refName =>
              val clauseIndex = args.invocationCount - 1

              for {
                ScalaResolveResult(method: ScMethodLike, substitutor) <- reference.getSimpleVariants()

                lookupElement <- createLookupElementBySignature(
                  parametersSignature(method, substitutor, clauseIndex),
                  function
                )
              } yield lookupElement
            case _ => Iterable.empty
          }
        case _ => Iterable.empty
      }
    }
  }

  private class ConstructorParametersCompletionProvider extends ScalaCompletionProvider {

    override protected def completionsFor(position: PsiElement)
                                         (implicit parameters: CompletionParameters,
                                          context: ProcessingContext): Iterable[LookupElementBuilder] =
      getContextOfType(position, classOf[ScTemplateDefinition]) match {
        case ClassConstructor(constructor) =>
          val args = getContextOfType(position, classOf[ScArgumentExprList])
          val constructorInvocation = args.getContext.asInstanceOf[ScConstructorInvocation]

          constructorInvocation.typeElement match {
            case typeElement@Typeable(tp) =>
              tp.extractClassType match {
                case Some((clazz: ScClass, substitutor)) if (if (clazz.hasTypeParameters) typeElement.isInstanceOf[ScParameterizedTypeElement] else true) =>
                  val index = constructorInvocation.arguments.indexOf(args)

                  for {
                    extractedClassConstructor <- clazz.constructors

                    lookupElement <- createLookupElementBySignature(
                      paramsInClause(extractedClassConstructor, substitutor, index),
                      constructor
                    )
                  } yield lookupElement
                case _ => Iterable.empty
              }
            case _ => Iterable.empty
          }
        case _ => Iterable.empty
      }
  }

  private[this] final case class ParameterDescriptor(name: String, `type`: ScType) {

    private def this(name: String)
                    (`type`: ScType,
                     substitutor: ScSubstitutor) =
      this(name, substitutor(`type`))
  }

  private[this] object ParameterDescriptor {

    def apply(parameter: ScParameter,
              substitutor: ScSubstitutor) =
      new ParameterDescriptor(parameter.name)(
        parameter.`type`().getOrAny,
        substitutor
      )

    def apply(parameter: PsiParameter,
              substitutor: ScSubstitutor) =
      new ParameterDescriptor(parameter.getName)(
        parameter.getType.toScType()(parameter),
        substitutor
      )
  }

  private[this] def parametersSignature(method: ScMethodLike,
                                        substitutor: ScSubstitutor,
                                        clauseIndex: Int): Seq[ParameterDescriptor] =
    method.effectiveParameterClauses match {
      case clauses if clauseIndex < clauses.length =>
        clauses(clauseIndex)
          .effectiveParameters
          .map(ParameterDescriptor(_, substitutor))
      case _ => Seq.empty
    }

  private[this] def paramsInClause(method: PsiMethod,
                                   substitutor: ScSubstitutor,
                                   clauseIndex: Int): Seq[ParameterDescriptor] =
    method match {
      case method: ScMethodLike => parametersSignature(method, substitutor, clauseIndex)
      case _ if clauseIndex == 0 => method.parameters.map(ParameterDescriptor(_, substitutor))
      case _ => Seq.empty
    }

  private[this] def createLookupElementBySignature(signature: Seq[ParameterDescriptor],
                                                   methodLike: ScMethodLike): Option[LookupElementBuilder] =
    if (signature.length <= 1 || signature.exists(_.name == null))
      None
    else {
      val parameters = methodLike.parameterList.params
      val names = for {
        ParameterDescriptor(name, scType) <- signature

        parameter <- parameters.find(_.name == name)
        if parameter.`type`().getOrAny.conforms(scType)
      } yield name

      if (names.length == signature.length)
        Some(createLookupElement(names.commaSeparated()))
      else
        None
    }

  private[this] def createLookupElement(lookupString: String) = {
    val result = LookupElementBuilder
      .create(lookupString)
      .withIcon(parametersIcon)
      .withInsertHandler(new MoveCaretInsertHandler)
    result.putUserData(JavaCompletionUtil.SUPER_METHOD_PARAMETERS, java.lang.Boolean.TRUE)
    result
  }

  private[this] def parametersIcon = {
    import icons.Icons.{PARAMETER => ParameterIcon}
    val result = new LayeredIcon(2)
    result.setIcon(ParameterIcon, 0, 2 * ParameterIcon.getIconWidth / 5, 0)
    result.setIcon(ParameterIcon, 1)
    result
  }

  private[this] class MoveCaretInsertHandler extends InsertHandler[LookupElement] {

    override def handleInsert(context: InsertionContext,
                              item: LookupElement): Unit = context.getCompletionChar match {
      case ')' =>
      case _ =>
        val element = context
          .getFile
          .findElementAt(context.getStartOffset)

        getContextOfType(element, classOf[ScArgumentExprList]) match {
          case null =>
          case list => context
            .getEditor
            .getCaretModel
            .moveToOffset(list.getTextRange.getEndOffset) // put caret after )
        }
    }
  }

  private[this] object ClassConstructor {

    def unapply(`class`: ScClass): Option[ScPrimaryConstructor] = `class`.constructor
  }
}
