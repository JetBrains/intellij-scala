package org.jetbrains.plugins.scala
package lang.completion

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementBuilder}
import com.intellij.patterns.{PlatformPatterns, PsiElementPattern}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiMethod}
import com.intellij.ui.LayeredIcon
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScParameterizedTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructor, ScMethodLike}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScMethodCall, ScReferenceExpression, ScSuperReference}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.types.{ScSubstitutor, ScType, ScTypeExt}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import scala.collection.mutable.ArrayBuffer

/**
 * @author Alefas
 * @since 03.09.13
 */
class SameSignatureCallParametersProvider extends ScalaCompletionContributor {
  val constructorFilter: PsiElementPattern.Capture[PsiElement] = PlatformPatterns.psiElement().withParent(classOf[ScReferenceExpression]).
          withSuperParent(2, classOf[ScArgumentExprList]).withSuperParent(3, classOf[ScConstructor])

  val superCallFilter: PsiElementPattern.Capture[PsiElement] = PlatformPatterns.psiElement().withParent(classOf[ScReferenceExpression]).
          withSuperParent(2, classOf[ScArgumentExprList]).withSuperParent(3, classOf[ScMethodCall])

  extend(CompletionType.BASIC, constructorFilter, new CompletionProvider[CompletionParameters]() {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      addConstructorCompletions(parameters, result)
    }
  })

  extend(CompletionType.SMART, constructorFilter, new CompletionProvider[CompletionParameters]() {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      addConstructorCompletions(parameters, result)
    }
  })

  extend(CompletionType.BASIC, superCallFilter, new CompletionProvider[CompletionParameters]() {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      addSuperCallCompletions(parameters, result)
    }
  })

  extend(CompletionType.SMART, superCallFilter, new CompletionProvider[CompletionParameters]() {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      addSuperCallCompletions(parameters, result)
    }
  })

  private def addSuperCallCompletions(parameters: CompletionParameters, result: CompletionResultSet): Unit = {
    val position = positionFromParameters(parameters)

    val elementType = position.getNode.getElementType
    if (elementType != ScalaTokenTypes.tIDENTIFIER) return
    val call = PsiTreeUtil.getContextOfType(position, classOf[ScMethodCall])
    val args = PsiTreeUtil.getContextOfType(position, classOf[ScArgumentExprList])
    if (call == null || args == null) return
    val index = args.invocationCount - 1
    call.deepestInvokedExpr match {
      case ref: ScReferenceExpression =>
        ref.qualifier match {
          case Some(_: ScSuperReference) =>
            val function = PsiTreeUtil.getContextOfType(ref, classOf[ScFunction])
            if (function != null && function.name == ref.refName) {
              val variants = ref.getSimpleVariants(implicits = false, filterNotNamedVariants = false)
              val signatures = variants.toSeq.collect {
                case ScalaResolveResult(method: ScMethodLike, subst) => paramsInClause(method, subst, index)
              }.filter(_.length > 1)

              if (signatures.isEmpty) return

              checkSignatures(signatures, function, result)
            }
          case _ =>
        }
      case _ =>
    }
  }

  private def addConstructorCompletions(parameters: CompletionParameters, result: CompletionResultSet) {
    val position = positionFromParameters(parameters)
    val elementType = position.getNode.getElementType
    if (elementType != ScalaTokenTypes.tIDENTIFIER) return
    PsiTreeUtil.getContextOfType(position, classOf[ScTemplateDefinition]) match {
      case c: ScClass =>
        val args = PsiTreeUtil.getContextOfType(position, classOf[ScArgumentExprList])
        val constructor = args.getContext.asInstanceOf[ScConstructor]
        val index = constructor.arguments.indexOf(args)
        val typeElement = constructor.typeElement
        typeElement.getType(TypingContext.empty) match {
          case Success(tp, _) =>
            val project = position.getProject

            val signatures = tp.extractClassType match {
              case Some((clazz: ScClass, subst)) if !clazz.hasTypeParameters || (clazz.hasTypeParameters &&
                      typeElement.isInstanceOf[ScParameterizedTypeElement]) =>
                val constructors = clazz match {
                  case c: ScClass => c.constructors
                  case _ => clazz.getConstructors.toSeq
                }
                constructors
                  .map(paramsInClause(_, subst, index))
                  .filter(_.length > 1)
              case _ => Seq.empty
            }

            if (signatures.isEmpty) return

            c.constructor match {
              case Some(constr) => checkSignatures(signatures, constr, result)
              case _ =>
            }
          case _ =>
        }
      case _ =>
    }
  }

  private def paramsInClause(m: PsiMethod, subst: ScSubstitutor, clauseIndex: Int): Seq[(String, ScType)] = {
    implicit val projectContext = m.projectContext
    m match {
      case fun: ScMethodLike =>
        val clauses = fun.effectiveParameterClauses
        if (clauses.length > clauseIndex) clauses(clauseIndex).effectiveParameters.map(p =>
          (p.name, subst.subst(p.getType(TypingContext.empty).getOrAny)))
        else Seq.empty
      case _ =>
        if (clauseIndex != 0) Seq.empty
        else m.parameters.map { p =>
          (p.name, subst.subst(p.getType.toScType()))
        }
    }
  }

  private def checkSignatures(signatures: Seq[Seq[(String, ScType)]], methodLike: ScMethodLike, result: CompletionResultSet) {
    for (signature <- signatures if signature.forall(_._1 != null)) {
      val names = new ArrayBuffer[String]()
      val res = signature.map {
        case (name: String, tp: ScType) =>
          methodLike.parameterList.params.find(_.name == name) match {
            case Some(param) if param.getType(TypingContext.empty).getOrAny.conforms(tp) =>
              names += name
              name
            case _ => names += ""
          }
      }.mkString(", ")
      if (!names.contains("")) {
        val w: Int = Icons.PARAMETER.getIconWidth
        val icon: LayeredIcon = new LayeredIcon(2)
        icon.setIcon(Icons.PARAMETER, 0, 2 * w / 5, 0)
        icon.setIcon(Icons.PARAMETER, 1)
        val element = LookupElementBuilder.create(res).withIcon(icon).withInsertHandler(new InsertHandler[LookupElement] {
          def handleInsert(context: InsertionContext, item: LookupElement) {
            val completionChar = context.getCompletionChar
            if (completionChar == ')') return

            val file = context.getFile
            val element = file.findElementAt(context.getStartOffset)
            val exprs = PsiTreeUtil.getContextOfType(element, classOf[ScArgumentExprList])
            if (exprs == null) return
            context.getEditor.getCaretModel.moveToOffset(exprs.getTextRange.getEndOffset) // put caret after )
          }
        })
        element.putUserData(JavaCompletionUtil.SUPER_METHOD_PARAMETERS, java.lang.Boolean.TRUE)
        result.addElement(element)
      }
    }
  }
}
