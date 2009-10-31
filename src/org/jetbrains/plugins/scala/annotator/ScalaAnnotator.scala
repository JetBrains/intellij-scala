package org.jetbrains.plugins.scala
package annotator

import collection.mutable.{HashSet, HashMap}
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.util.{Key, TextRange}
import com.intellij.psi.util.{PsiTreeUtil}
import highlighter.{AnnotatorHighlighter}
import importsTracker._
import lang.lexer.ScalaTokenTypes
import lang.psi.api.expr._


import lang.psi.api.statements._
import lang.psi.api.statements.params.{ScClassParameter}
import lang.psi.api.toplevel.typedef._
import lang.psi.api.toplevel.templates.ScTemplateBody
import com.intellij.lang.annotation._

import lang.psi.ScalaPsiUtil
import lang.psi.api.ScalaFile
import lang.psi.api.toplevel.imports.usages.ImportUsed
import lang.psi.api.toplevel.imports.{ScImportExpr, ScImportSelector}
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.resolve._
import com.intellij.codeInspection._
import org.jetbrains.plugins.scala.annotator.intention._
import org.jetbrains.plugins.scala.overrideImplement.ScalaOIUtil
import patterns.ScBindingPattern
import quickfix.ImplementMethodsQuickFix
import quickfix.modifiers.{RemoveModifierQuickFix, AddModifierQuickFix}
import modifiers.ModifierChecker
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import com.intellij.psi._
import tree.TokenSet
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeBoundsOwner
import types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.types.result.{TypeResult, Success, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.types.{Conformance, ScType, FullSignature}

/**
 *    User: Alexander Podkhalyuzin
 *    Date: 23.06.2008
 */

class ScalaAnnotator extends Annotator {
  def annotate(element: PsiElement, holder: AnnotationHolder) {
    if (element.getNode.getFirstChildNode == null && element.getTextRange.getStartOffset == 0) {
      val sFile = element.getContainingFile.asInstanceOf[ScalaFile]
      ImportTracker.getInstance(sFile.getProject).removeAnnotatedFile(sFile)
    }

    element match {
      case x: ScFunction if x.getParent.isInstanceOf[ScTemplateBody] => {
        //todo: unhandled case abstract override
        //checkOverrideMethods(x, holder)
      }
      case x: ScTemplateDefinition => {
        //todo uncomment when lineariztion problems will be fixed
        //checkImplementedMethods(x, holder)
      }
      case ref: ScReferenceElement => {
        ref.qualifier match {
          case None => checkNotQualifiedReferenceElement(ref, holder)
          case Some(_) => checkQualifiedReferenceElement(ref, holder)
        }
      }
      case impExpr: ScImportExpr => {
        checkImportExpr(impExpr, holder)
      }
      case ret: ScReturnStmt => {
        checkExplicitTypeForReturnStatement(ret, holder)
      }
      case ml: ScModifierList => {
        ModifierChecker.checkModifiers(ml, holder)
      }
      case sFile: ScalaFile => {
        ImportTracker.getInstance(sFile.getProject).markFileAnnotated(sFile)
      }
      case sTypeParam: ScTypeBoundsOwner => {
        checkTypeParamBounds(sTypeParam, holder)
      }
      case _ => AnnotatorHighlighter.highlightElement(element, holder)
    }
  }

  private def checkTypeParamBounds(sTypeParam: ScTypeBoundsOwner, holder: AnnotationHolder) = {}

  private def checkNotQualifiedReferenceElement(refElement: ScReferenceElement, holder: AnnotationHolder) {

    def getFix: Seq[IntentionAction] = {
      val facade = JavaPsiFacade.getInstance(refElement.getProject)
      val classes = ScalaImportClassFix.getClasses(refElement, refElement.getProject)
      if (classes.length == 0) return Seq.empty
      return Seq[IntentionAction](new ScalaImportClassFix(classes, refElement))
    }

    val resolve: Array[ResolveResult] = refElement.multiResolve(false)
    def processError(countError: Boolean, fixes: => Seq[IntentionAction]) = {
      //todo remove when resolve of unqualified expression will be fully implemented
      if (refElement.getManager.isInProject(refElement) && resolve.length == 0 &&
              (fixes.length > 0 || countError)) {
        val error = ScalaBundle.message("cannot.resolve", refElement.refName)
        val annotation = holder.createErrorAnnotation(refElement.nameId, error)
        annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
        registerAddImportFix(refElement, annotation, fixes: _*)
      }
    }

    if (resolve.length != 1) {
      refElement match {
        case e: ScReferenceExpression if e.getParent.isInstanceOf[ScPrefixExpr] &&
                e.getParent.asInstanceOf[ScPrefixExpr].operation == e => //todo: this is hide !(Not Boolean)
        case e: ScReferenceExpression if e.getParent.isInstanceOf[ScInfixExpr] &&
                e.getParent.asInstanceOf[ScInfixExpr].operation == e => //todo: this is hide A op B
        case e: ScReferenceExpression => processError(false, getFix)
        case _ => refElement.getParent match {
          case s: ScImportSelector if resolve.length > 0 =>
          case _ => processError(true, getFix)
        }
      }
    }
    else {
      AnnotatorHighlighter.highlightReferenceElement(refElement, holder)
    }
    for (result <- resolve if result.isInstanceOf[ScalaResolveResult];
         scalaResult = result.asInstanceOf[ScalaResolveResult]) {
      registerUsedImports(refElement, scalaResult)
    }
    checkAccessForReference(resolve, refElement, holder)
  }

  private def checkQualifiedReferenceElement(refElement: ScReferenceElement, holder: AnnotationHolder) {
    AnnotatorHighlighter.highlightReferenceElement(refElement, holder)
    val settings: ScalaCodeStyleSettings =
           CodeStyleSettingsManager.getSettings(refElement.getProject).getCustomSettings(classOf[ScalaCodeStyleSettings])
    var resolve: Array[ResolveResult] = null
    resolve = refElement.multiResolve(false)
    for (result <- resolve if result.isInstanceOf[ScalaResolveResult];
         scalaResult = result.asInstanceOf[ScalaResolveResult]) {
      registerUsedImports(refElement, scalaResult)
    }
    checkAccessForReference(resolve, refElement, holder)
  }

  private def checkAccessForReference(resolve: Array[ResolveResult], refElement: ScReferenceElement, holder: AnnotationHolder) {
    if (resolve.length != 1) return
    resolve.apply(0) match {
      case res: ScalaResolveResult => {
        val memb: PsiMember = res.getElement match {
          case member: PsiMember => member
          case bind: ScBindingPattern => {
            ScalaPsiUtil.nameContext(bind) match {
              case member: PsiMember => member
              case _ => return
            }
          }
          case _ => return
        }
        if (!ResolveUtils.isAccessible(memb, refElement)) {
          val error = ScalaBundle.message("element.is.not.accessible", refElement.refName)
          val annotation = holder.createErrorAnnotation(refElement.nameId, error)
          annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
          //todo: fixes for changing access
        }
      }
      case _ =>
    }
  }

  private def registerAddImportFix(refElement: ScReferenceElement, annotation: Annotation, actions: IntentionAction*) {
    for (action <- actions) {
      annotation.registerFix(action)
    }
  }

  private def registerUsedImports(refElement: ScReferenceElement, result: ScalaResolveResult) {
    ImportTracker.getInstance(refElement.getProject).
            registerUsedImports(refElement.getContainingFile.asInstanceOf[ScalaFile], result.importsUsed)
  }

  private def checkImplementedMethods(clazz: ScTemplateDefinition, holder: AnnotationHolder) {
    clazz match {
      case _: ScTrait => return
      case _ =>
    }
    if (clazz.hasModifierProperty("abstract")) return
    if (ScalaOIUtil.getMembersToImplement(clazz).length > 0) {
      val error = clazz match {
        case _: ScClass => ScalaBundle.message("class.must.declared.abstract", clazz.getName)
        case _: ScObject => ScalaBundle.message("object.must.implement", clazz.getName)
        case _: ScNewTemplateDefinition => ScalaBundle.message("anonymous.class.must.declared.abstract")
      }
      val start = clazz.getTextRange.getStartOffset
      val eb = clazz.extendsBlock
      var end = eb.templateBody match {
        case Some(x) => {
          val shifted = eb.findElementAt(x.getStartOffsetInParent - 1) match {case w: PsiWhiteSpace => w case _ => x}
          shifted.getTextRange.getStartOffset
        }
        case None => eb.getTextRange.getEndOffset
      }

      val annotation: Annotation = holder.createErrorAnnotation(new TextRange(start, end), error)
      annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
      annotation.registerFix(new ImplementMethodsQuickFix(clazz))
    }
  }

  private def checkOverrideMethods(method: ScFunction, holder: AnnotationHolder) {
    val signatures = method.superSignatures
    if (signatures.length == 0) {
      if (method.hasModifierProperty("override")) {
        val annotation: Annotation = holder.createErrorAnnotation(method.nameId.getTextRange,
          ScalaBundle.message("method.overrides.nothing", method.getName))
        annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
        annotation.registerFix(new RemoveModifierQuickFix(method, "override"))
      }
    } else {
      if (!method.hasModifierProperty("override") && !method.isInstanceOf[ScFunctionDeclaration]) {
        def isConcrete(signature: FullSignature): Boolean = if (signature.element.isInstanceOf[PsiNamedElement])
          ScalaPsiUtil.nameContext(signature.element.asInstanceOf[PsiNamedElement]) match {
            case _: ScFunctionDefinition => true
            case method: PsiMethod if !method.hasModifierProperty(PsiModifier.ABSTRACT) && !method.isConstructor => true
            case _: ScPatternDefinition => true
            case _: ScVariableDeclaration => true
            case _: ScClassParameter => true
            case _ => false
          } else false
        def isConcretes: Boolean = {
          for (signature <- signatures if isConcrete(signature)) return true
          return false
        }
        if (isConcretes) {
          val annotation: Annotation = holder.createErrorAnnotation(method.nameId.getTextRange,
            ScalaBundle.message("method.needs.override.modifier", method.getName))
          annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
          annotation.registerFix(new AddModifierQuickFix(method, "override"))
        }
      }
    }
  }

  private def checkResultExpression(block: ScBlock, holder: AnnotationHolder) {
    var last = block.getNode.getLastChildNode
    while (last != null && (last.getElementType == ScalaTokenTypes.tRBRACE ||
            (ScalaTokenTypes.WHITES_SPACES_TOKEN_SET contains last.getElementType) ||
            (ScalaTokenTypes.COMMENTS_TOKEN_SET contains last.getElementType))) last = last.getTreePrev
    if (last == null || last.getElementType == ScalaTokenTypes.tSEMICOLON) return //last can be null for xml blocks - they can be empty

    val stat = block.lastStatement match {case None => return case Some(x) => x}
    stat match {
      case _: ScExpression =>
      case _ => {
        val error = ScalaBundle.message("block.must.end.result.expression")
        val annotation: Annotation = holder.createErrorAnnotation(
          new TextRange(stat.getTextRange.getStartOffset, stat.getTextRange.getStartOffset + 3),
          error)
        annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
      }
    }
  }

  private def checkExplicitTypeForReturnStatement(ret: ScReturnStmt, holder: AnnotationHolder) {
    var fun: ScFunction = PsiTreeUtil.getParentOfType(ret, classOf[ScFunction])
    fun match {
      case null => {
        val error = ScalaBundle.message("return.outside.method.definition")
        val annotation: Annotation = holder.createErrorAnnotation(ret.returnKeyword, error)
        annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
      }
      case _ if fun.getNode.getChildren(TokenSet.create(ScalaTokenTypes.tASSIGN)).size == 0 => {
        return //can return anything
        //todo: add warning to not return something except nothing
      }
      case _ => fun.returnTypeElement match {
        case Some(x: ScTypeElement) => {
          import org.jetbrains.plugins.scala.lang.psi.types._
          val funType = fun.returnType
          val exprType: TypeResult[ScType] = ret.expr match {
            case Some(e: ScExpression) => e.getType(TypingContext.empty)
            case None => Success(Unit, None)
          }
          if (!ScalaAnnotator.smartCheckConformance(funType, exprType)) {
            val error = ScalaBundle.message("return.type.does.not.conform", ScType.presentableText(exprType.getOrElse(Nothing)))
            val annotation: Annotation = holder.createErrorAnnotation(ret, error)
            annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
            //todo: add fix to change function return type
          } else return
        }
        case _ => {
          val error = ScalaBundle.message("function.must.define.type.explicitly", fun.getName)
          val annotation: Annotation = holder.createErrorAnnotation(
            new TextRange(ret.getTextRange.getStartOffset, ret.getTextRange.getStartOffset + 6),
            error)
          annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
          //todo: add fix to add function return type
        }
      }
    }
  }

  private def checkImportExpr(impExpr: ScImportExpr, holder: AnnotationHolder) {
    if (impExpr.qualifier == null) {
      val annotation: Annotation = holder.createErrorAnnotation(impExpr.getTextRange,
          ScalaBundle.message("import.expr.should.be.qualified"))
      annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR)
    }
  }
}

object ScalaAnnotator {
  val usedImportsKey: Key[HashSet[ImportUsed]] = Key.create("used.imports.key")

  /**
   * This method will return checked conformance if it's possible to check it.
   * In other way it will return true to avoid red code.
   * Check conformance in case l = r.
   */
  private def smartCheckConformance(l: TypeResult[ScType], r: TypeResult[ScType]): Boolean = {
    for (leftType <- l; rightType <- r) {
      return Conformance.conforms(leftType, rightType)
    }
    return true
  }
}
