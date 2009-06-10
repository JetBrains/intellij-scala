package org.jetbrains.plugins.scala.lang.psi

import annotations.NotNull
import api.base.patterns.ScCaseClause
import api.base.types.ScTypeElement
import api.base.{ScLiteral, ScConstructor, ScStableCodeReferenceElement, ScModifierList}
import api.expr._
import api.expr.xml.ScXmlExpr

import api.statements.params.{ScClassParameter, ScParameter, ScParameterClause}
import api.toplevel.typedef.{ScTypeDefinition, ScTemplateDefinition, ScClass, ScObject}
import com.intellij.openapi.project.Project
import impl.toplevel.typedef.TypeDefinitionMembers
import _root_.org.jetbrains.plugins.scala.lang.psi.types._
import _root_.org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import api.statements._
import api.toplevel.templates.ScTemplateBody
import api.toplevel.{ScNamedElement, ScTyped}
import com.intellij.openapi.util.Key
import com.intellij.psi._
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.{CachedValueProvider, CachedValue, PsiFormatUtil}
import lang.psi.impl.ScalaPsiElementFactory
import lexer.ScalaTokenTypes
import parser.parsing.expressions.InfixExpr
import parser.util.ParserUtils
import search.GlobalSearchScope
import structureView.ScalaElementPresentation
/**
 * User: Alexander Podkhalyuzin
 * Date: 06.10.2008
 */

object ScalaPsiUtil {
  def genericCallSubstitutor(tp: Seq[String], gen: ScGenericCall): ScSubstitutor = {
    val typeArgs: Seq[ScTypeElement] = gen.typeArgs.typeArgs
    val map = new collection.mutable.HashMap[String, ScType]
    for (i <- 0 to Math.min(tp.length, typeArgs.length) - 1) {
      map += Tuple(tp(i), typeArgs(i).calcType)
    }
    new ScSubstitutor(Map(map.toSeq: _*), Map.empty, Map.empty)
  }

  def namedElementSig(x: PsiNamedElement): Signature = new Signature(x.getName, Seq.empty, 0, Array[PsiTypeParameter](), ScSubstitutor.empty)

  def superValsSignatures(x: PsiNamedElement): Seq[FullSignature] = {
    val empty = Seq.empty
    val typed = x match {case x: ScTyped => x case _ => return empty}
    val context: PsiElement = nameContext(typed) match {
      case value: ScValue if value.getParent.isInstanceOf[ScTemplateBody] => value
      case value: ScVariable if value.getParent.isInstanceOf[ScTemplateBody] => value
      case _ => return empty
    }
    val clazz = context.asInstanceOf[PsiMember].getContainingClass
    val s = new FullSignature(namedElementSig(x), typed.calcType,
      x.asInstanceOf[NavigatablePsiElement], clazz)
    val t = (TypeDefinitionMembers.getSignatures(clazz).get(s): @unchecked) match {
      //partial match
      case Some(x) => x.supers.map{_.info}
    }
    return t
  }

  def nameContext(x: PsiNamedElement): PsiElement = {
    var parent = x.getParent
    def isAppropriatePsiElement(x: PsiElement): Boolean = {
      x match {
        case _: ScValue | _: ScVariable | _: ScTypeAlias | _: ScParameter | _: PsiMethod | _: ScCaseClause => true
        case _ => false
      }
    }
    if (isAppropriatePsiElement(x)) return x
    while (parent != null && !isAppropriatePsiElement(parent)) parent = parent.getParent
    return parent
  }

  def adjustTypes(element: PsiElement): Unit = {
    for (child <- element.getChildren) {
      child match {
        case x: ScStableCodeReferenceElement => x.resolve match {
          case clazz: PsiClass =>
            x.replace(ScalaPsiElementFactory.createReferenceFromText(clazz.getName, clazz.getManager)).
                asInstanceOf[ScStableCodeReferenceElement].bindToElement(clazz)
          case _ =>
        }
        case _ => adjustTypes(child)
      }
    }
  }

  def getMethodPresentableText(method: PsiMethod): String = {
    val buffer = new StringBuffer("")
    method match {
      case method: ScFunction => {
        return ScalaElementPresentation.getMethodPresentableText(method, false)
      }
      case _ => {
        val PARAM_OPTIONS: Int = PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.TYPE_AFTER
        return PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY,
          PARAM_OPTIONS | PsiFormatUtil.SHOW_PARAMETERS, PARAM_OPTIONS)
      }
    }
  }

  def getModifiersPresentableText(modifiers: ScModifierList): String = {
    if (modifiers == null) return ""
    val buffer = new StringBuilder("")
    for (modifier <- modifiers.getNode.getChildren(null) if !isLineTerminator(modifier.getPsi)) buffer.append(modifier.getText + " ")
    return buffer.toString
  }

  def isLineTerminator(element: PsiElement): Boolean = {
    element match {
      case _: PsiWhiteSpace if element.getText.indexOf('\n') != -1 => return true
      case _ =>
    }
    return element.getNode.getElementType == ScalaTokenTypes.tLINE_TERMINATOR
  }

  def getApplyMethods(clazz: PsiClass): Seq[PhysicalSignature] = {
    (for ((n: PhysicalSignature, _) <- TypeDefinitionMembers.getMethods(clazz)
              if n.method.getName == "apply" &&
                    (clazz.isInstanceOf[ScObject] || !n.method.hasModifierProperty("static"))) yield n).toSeq
  }

  def getUnapplyMethods(clazz: PsiClass): Seq[PhysicalSignature] = {
    (for ((n: PhysicalSignature, _) <- TypeDefinitionMembers.getMethods(clazz)
              if (n.method.getName == "unapply" || n.method.getName == "unapplySeq") &&
                    (clazz.isInstanceOf[ScObject] || n.method.hasModifierProperty("static"))) yield n).toSeq
  }

  def getUpdateMethods(clazz: PsiClass): Seq[PhysicalSignature] = {
    (for ((n: PhysicalSignature, _) <- TypeDefinitionMembers.getMethods(clazz)
              if n.method.getName == "update" &&
                    (clazz.isInstanceOf[ScObject] || !n.method.hasModifierProperty("static"))) yield n).toSeq
  }

  /**
   * This method try to conform given expression to method's first parameter clause.
   * @return all methods which can by applied to given expressions
   */
  def getMethodsConformingToMethodCall(methods: Seq[PhysicalSignature], args: Seq[ScExpression], subst: PsiMethod => ScSubstitutor): Seq[PhysicalSignature] = {
    def check(sign: PhysicalSignature): Boolean = {
      val meth = sign.method
      meth match {
        case fun: ScFunction => {
          val clauses: Seq[ScParameterClause] = fun.paramClauses.clauses
          if (clauses.length == 0) {
            if (args.length == 0) return true
            else return false
          } else {
            val clause: ScParameterClause = clauses.apply(0)
            val methodParams: Seq[ScParameter] = clause.parameters
            val length = methodParams.length
            if (length == 0) {
              if (args.length == 0) return true
              else return false
            }
            //so method have not zero params
            //length sould be equal or last parameter should be repeated
            if (!(length == args.length ||
                    (length < args.length && methodParams(length - 1).isRepeatedParameter))) return false
            for (i <- 0 to args.length - 1) {
              val parameter: ScParameter = methodParams(Math.min(i, length -1))
              val typez: ScType = parameter.typeElement match {
                case Some(te) => subst(meth).subst(te.calcType)
                case None => types.Any
              }
              if (!(args(i).getType: ScType).conforms(typez)) return false
            }
            return true
          }
        }
        case meth: PsiMethod => {
          val methodParams = meth.getParameterList.getParameters
          val length: Int = methodParams.length
          if (length == 0) {
            if (methodParams.length == 0) return true
            else return false
          }
          //so method have not zero params
          //length sould be equal or last parameter should be repeated
          if (!(length == args.length || (
                  length < args.length && methodParams.apply(length - 1).isVarArgs
                  ))) return false
          for (i <- 0 to args.length - 1) {
            val parameter: PsiParameter = methodParams(Math.min(i, length - 1))
            val typez: ScType = subst(meth).subst(ScType.create(parameter.getType, meth.getProject))
            if (!(args(i).getType: ScType).conforms(typez)) return false
          }
          return true
        }
      }
    }
    for (method <- methods if check(method)) yield method
  }

  /**
   * For one classOf use PsiTreeUtil.getParenteOfType instead
   */
  def getParentOfType(element: PsiElement, classes: Class[_ <: PsiElement]*): PsiElement = {
    getParentOfType(element, false, classes: _*)
  }

  /**
   * For one classOf use PsiTreeUtil.getParenteOfType instead
   */
  def getParentOfType(element: PsiElement, strict: Boolean, classes: Class[_ <: PsiElement]*): PsiElement = {
    var el: PsiElement = if (!strict) element else {
      if (element == null) return null
      element.getParent
    }
    while (el != null && classes.find(_.isInstance(el)) == None) el = el.getParent
    return el
  }

  def getCompanionModule(td: ScTemplateDefinition): Option[ScTypeDefinition] = {
    val name = td.getName
    val scope = td.getParent
    td match {
      case _: ScClass => {
        scope.getChildren.map((child: PsiElement) => child match {
          case td: ScTypeDefinition => td
          case _ => null: ScTypeDefinition
        }).find((child: ScTypeDefinition) =>
                child.isInstanceOf[ScObject] && child.asInstanceOf[ScObject].getName == name)
      }
      case _: ScObject => {
        scope.getChildren.map((child: PsiElement) => child match {
          case td: ScTypeDefinition => td
          case _ => null: ScTypeDefinition
        }).find((child: ScTypeDefinition) =>
                child.isInstanceOf[ScClass] && child.asInstanceOf[ScClass].getName == name)
      }
      case _ => None
    }
  }

  def getPsiSubstitutor(subst: ScSubstitutor, project: Project): PsiSubstitutor = {
    case class PseudoPsiSubstitutor(substitutor: ScSubstitutor) extends PsiSubstitutor {
      def putAll(parentClass: PsiClass, mappings: Array[PsiType]): PsiSubstitutor = PsiSubstitutor.EMPTY

      def isValid: Boolean = true

      def put(classParameter: PsiTypeParameter, mapping: PsiType): PsiSubstitutor = PsiSubstitutor.EMPTY

      def getSubstitutionMap: java.util.Map[PsiTypeParameter, PsiType] = new java.util.HashMap[PsiTypeParameter, PsiType]()

      def substitute(`type` : PsiType): PsiType = {
        ScType.toPsi(substitutor.subst(ScType.create(`type`, project)), project,
          GlobalSearchScope.allScope(project))
      }

      def substitute(typeParameter: PsiTypeParameter): PsiType = {
        ScType.toPsi(substitutor.subst(new ScTypeParameterType(typeParameter, substitutor)),
          project, GlobalSearchScope.allScope(project))
      }

      def putAll(another: PsiSubstitutor): PsiSubstitutor = PsiSubstitutor.EMPTY

      def substituteWithBoundsPromotion(typeParameter: PsiTypeParameter): PsiType = substitute(typeParameter)
    }

    PseudoPsiSubstitutor(subst)
  }

    def needParentheses(from: ScExpression, expr: ScExpression): Boolean = {
    val parent = from.getParent
    (parent, expr) match { //true only for other cases
      case _ if !parent.isInstanceOf[ScExpression] => false
      case (_: ScTuple, _) => false
      case (_: ScReferenceExpression, _: ScBlockExpr) => false
      case (_: ScReferenceExpression, _: ScNewTemplateDefinition) if expr.getText.endsWith(")") ||
              expr.getText.endsWith("}") || expr.getText.endsWith("]") => false
      case (_: ScReferenceExpression, _: ScReferenceExpression) => false
      case (_: ScReferenceExpression, _: ScGenericCall) => false
      case (_: ScReferenceExpression, _: ScMethodCall) => false
      case (_: ScReferenceExpression, _: ScLiteral) => false
      case (_: ScReferenceExpression, _) if expr.getText == "_" => false
      case (_: ScReferenceExpression, _: ScTuple) => false
      case (_: ScReferenceExpression, _: ScXmlExpr) => false
      case (_: ScReferenceExpression, _) => true
      case (_: ScGenericCall, _: ScReferenceExpression) => false
      case (_: ScGenericCall, _: ScMethodCall) => false
      case (_: ScGenericCall, _: ScLiteral) => false
      case (_: ScGenericCall, _: ScTuple) => false
      case (_: ScGenericCall, _: ScXmlExpr) => false
      case (_: ScGenericCall, _) if expr.getText == "_" => false
      case (_: ScGenericCall, _: ScBlockExpr) => false
      case (_: ScGenericCall, _) => true
      case (_: ScMethodCall, _: ScReferenceExpression) => false
      case (_: ScMethodCall, _: ScMethodCall) => false
      case (_: ScMethodCall, _: ScGenericCall) => false
      case (_: ScMethodCall, _: ScLiteral) => false
      case (_: ScMethodCall, _) if expr.getText == "_" => false
      case (_: ScMethodCall, _: ScXmlExpr) => false
      case (_: ScMethodCall, _: ScTuple) => false
      case (_: ScMethodCall, _) => true
      case (_: ScUnderscoreSection, _: ScReferenceExpression) => false
      case (_: ScUnderscoreSection, _: ScMethodCall) => false
      case (_: ScUnderscoreSection, _: ScGenericCall) => false
      case (_: ScUnderscoreSection, _: ScLiteral) => false
      case (_: ScUnderscoreSection, _) if expr.getText == "_" => false
      case (_: ScUnderscoreSection, _: ScXmlExpr) => false
      case (_: ScUnderscoreSection, _: ScTuple) => false
      case (_: ScUnderscoreSection, _) => true
      case (_: ScBlock, _) => false
      case (_: ScPrefixExpr, _: ScBlockExpr) => false
      case (_: ScPrefixExpr, _: ScNewTemplateDefinition) => false
      case (_: ScPrefixExpr, _: ScUnderscoreSection) => false
      case (_: ScPrefixExpr, _: ScReferenceExpression) => false
      case (_: ScPrefixExpr, _: ScGenericCall) => false
      case (_: ScPrefixExpr, _: ScMethodCall) => false
      case (_: ScPrefixExpr, _: ScLiteral) => false
      case (_: ScPrefixExpr, _) if expr.getText == "_" => false
      case (_: ScPrefixExpr, _: ScTuple) => false
      case (_: ScPrefixExpr, _: ScXmlExpr) => false
      case (_: ScPrefixExpr, _) => true
      case (_: ScInfixExpr, _: ScBlockExpr) => false
      case (_: ScInfixExpr, _: ScNewTemplateDefinition) => false
      case (_: ScInfixExpr, _: ScUnderscoreSection) => false
      case (_: ScInfixExpr, _: ScReferenceExpression) => false
      case (_: ScInfixExpr, _: ScGenericCall) => false
      case (_: ScInfixExpr, _: ScMethodCall) => false
      case (_: ScInfixExpr, _: ScLiteral) => false
      case (_: ScInfixExpr, _) if expr.getText == "_" => false
      case (_: ScInfixExpr, _: ScTuple) => false
      case (_: ScInfixExpr, _: ScXmlExpr) => false
      case (_: ScInfixExpr, _: ScPrefixExpr) => false
      case (par: ScInfixExpr, child: ScInfixExpr) => {
        import ParserUtils._
        import InfixExpr._
        if (par.lOp == from) {
          val lid = par.operation.getText
          val rid = child.operation.getText
          if (priority(lid) < priority(rid)) true
          else if (priority(rid) < priority(lid)) false
          else if (associate(lid) != associate(rid)) true
          else if (associate(lid) == -1) true
          else false
        }
        else {
          val lid = child.operation.getText
          val rid = par.operation.getText
          if (priority(lid) < priority(rid)) false
          else if (priority(rid) < priority(lid)) true
          else if (associate(lid) != associate(rid)) true
          else if (associate(lid) == -1) false
          else true
        }
      }
      case (_: ScInfixExpr, _) => true
      case (_: ScPostfixExpr, _: ScBlockExpr) => false
      case (_: ScPostfixExpr, _: ScNewTemplateDefinition) => false
      case (_: ScPostfixExpr, _: ScUnderscoreSection) => false
      case (_: ScPostfixExpr, _: ScReferenceExpression) => false
      case (_: ScPostfixExpr, _: ScGenericCall) => false
      case (_: ScPostfixExpr, _: ScMethodCall) => false
      case (_: ScPostfixExpr, _: ScLiteral) => false
      case (_: ScPostfixExpr, _) if expr.getText == "_" => false
      case (_: ScPostfixExpr, _: ScTuple) => false
      case (_: ScPostfixExpr, _: ScXmlExpr) => false
      case (_: ScPostfixExpr, _: ScPrefixExpr) => false
      case (_: ScPostfixExpr, _) => true
      case (_: ScTypedStmt, _: ScBlockExpr) => false
      case (_: ScTypedStmt, _: ScNewTemplateDefinition) => false
      case (_: ScTypedStmt, _: ScUnderscoreSection) => false
      case (_: ScTypedStmt, _: ScReferenceExpression) => false
      case (_: ScTypedStmt, _: ScGenericCall) => false
      case (_: ScTypedStmt, _: ScMethodCall) => false
      case (_: ScTypedStmt, _: ScLiteral) => false
      case (_: ScTypedStmt, _) if expr.getText == "_" => false
      case (_: ScTypedStmt, _: ScTuple) => false
      case (_: ScTypedStmt, _: ScXmlExpr) => false
      case (_: ScTypedStmt, _: ScPrefixExpr) => false
      case (_: ScTypedStmt, _: ScInfixExpr) => false
      case (_: ScTypedStmt, _: ScPostfixExpr) => false
      case (_: ScTypedStmt, _) => true
      case (_: ScMatchStmt, _: ScBlockExpr) => false
      case (_: ScMatchStmt, _: ScNewTemplateDefinition) => false
      case (_: ScMatchStmt, _: ScUnderscoreSection) => false
      case (_: ScMatchStmt, _: ScReferenceExpression) => false
      case (_: ScMatchStmt, _: ScGenericCall) => false
      case (_: ScMatchStmt, _: ScMethodCall) => false
      case (_: ScMatchStmt, _: ScLiteral) => false
      case (_: ScMatchStmt, _) if expr.getText == "_" => false
      case (_: ScMatchStmt, _: ScTuple) => false
      case (_: ScMatchStmt, _: ScXmlExpr) => false
      case (_: ScMatchStmt, _: ScPrefixExpr) => false
      case (_: ScMatchStmt, _: ScInfixExpr) => false
      case (_: ScMatchStmt, _: ScPostfixExpr) => false
      case (_: ScMatchStmt, _) => true
      case _ => false
    }
  }
}