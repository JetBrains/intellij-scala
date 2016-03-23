package org.jetbrains.plugins.scala
package lang.completion

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementBuilder}
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiMethod, PsiParameter}
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
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import scala.collection.mutable.ArrayBuffer

/**
 * @author Alefas
 * @since 03.09.13
 */
class SameSignatureCallParametersProvider extends ScalaCompletionContributor {
  val constructorFilter = PlatformPatterns.psiElement().withParent(classOf[ScReferenceExpression]).
          withSuperParent(2, classOf[ScArgumentExprList]).withSuperParent(3, classOf[ScConstructor])

  val superCallFilter = PlatformPatterns.psiElement().withParent(classOf[ScReferenceExpression]).
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
          case Some(s: ScSuperReference) =>
            val function = PsiTreeUtil.getContextOfType(ref, classOf[ScFunction])
            if (function != null && function.name == ref.refName) {
              val variants = ref.getSimpleVariants(implicits = false, filterNotNamedVariants = false)
              val signatures = variants.toSeq.map {
                case ScalaResolveResult(fun: ScMethodLike, subst) =>
                  val params = fun.effectiveParameterClauses
                  if (params.length > index) params(index).effectiveParameters.map(p =>
                    (p.name, subst.subst(p.getType(TypingContext.empty).getOrAny)))
                  else Seq.empty
                case ScalaResolveResult(method: PsiMethod, subst) =>
                  if (index != 0) Seq.empty
                  else method.getParameterList.getParameters.toSeq.map {
                    case p: PsiParameter =>
                      (p.name, subst.subst(ScType.create(p.getType, position.getProject, position.getResolveScope)))
                  }
                case _ => Seq.empty
              }.filter(_.length > 1)

              if (signatures.isEmpty) return

              checkSignatures(signatures, function, result)
            }
          case _ =>
        }
      case _ =>
    }
  }

  private def addConstructorCompletions(parameters: CompletionParameters, result: CompletionResultSet): Unit = {
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
            val signatures = ScType.extractClassType(tp, Some(position.getProject)) match {
              case Some((clazz: ScClass, subst)) if !clazz.hasTypeParameters || (clazz.hasTypeParameters &&
                      typeElement.isInstanceOf[ScParameterizedTypeElement]) =>
                clazz.constructors.toSeq.map {
                  case fun: ScMethodLike =>
                    val params = fun.effectiveParameterClauses
                    if (params.length > index) params(index).effectiveParameters.map(p =>
                      (p.name, subst.subst(p.getType(TypingContext.empty).getOrAny)))
                    else Seq.empty
                }.filter(_.length > 1)
              case Some((clazz: PsiClass, subst)) if !clazz.hasTypeParameters || (clazz.hasTypeParameters &&
                      typeElement.isInstanceOf[ScParameterizedTypeElement]) =>
                clazz.getConstructors.toSeq.map {
                  case c: PsiMethod =>
                    if (index != 0) Seq.empty
                    else c.getParameterList.getParameters.toSeq.map {
                      case p: PsiParameter =>
                        (p.name, subst.subst(ScType.create(p.getType, typeElement.getProject, typeElement.getResolveScope)))
                    }
                }.filter(_.length > 1)
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

  private def checkSignatures(signatures: Seq[Seq[(String, ScType)]], methodLike: ScMethodLike, result: CompletionResultSet)
                             (implicit typeSystem: TypeSystem = methodLike.typeSystem) {
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
