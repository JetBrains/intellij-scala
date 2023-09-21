package org.jetbrains.plugins.scala.lang.completion

import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.impl.compiled.ClsMethodImpl
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.{InheritanceUtil, PsiTreeUtil}
import com.intellij.util.ArrayUtil.EMPTY_STRING_ARRAY
import com.intellij.util.text.NameUtilCore
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScReferencePattern}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScTemplateBody, ScTemplateParents}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.api.{ScFile, ScPackage}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable

import scala.annotation.tailrec
import scala.collection.mutable

package object ml {

  private val NonNamePattern = """[^a-zA-Z_]""".r
  private val MaxWords = 7

  private val KeywordsByElementType: Map[IElementType, Keyword] = {
    import Keyword._
    import ScalaTokenType._
    import ScalaTokenTypes._

    Map(
      kABSTRACT -> ABSRACT,
      kCASE -> CASE,
      kCATCH -> CATCH,
      ClassKeyword -> CLASS,
      kDEF -> DEF,
      kDO -> DO,
      kELSE -> ELSE,
      kEXTENDS -> EXTENDS,
      kFALSE -> UNKNOWN,
      kFINAL -> FINAL,
      kFINALLY -> FINALLY,
      kFOR_SOME -> UNKNOWN,
      kFOR -> FOR,
      kIF -> IF,
      kIMPLICIT -> IMPLICIT,
      kIMPORT -> IMPORT,
      kLAZY -> LAZY,
      kMATCH -> MATCH,
      kNULL -> NULL,
      NewKeyword -> NEW,
      ObjectKeyword -> OBJECT,
      kOVERRIDE -> OVERRIDE,
      kPACKAGE -> PACKAGE,
      kPRIVATE -> PRIVATE,
      kPROTECTED -> PROTECTED,
      kRETURN -> RETURN,
      kSEALED -> SEALED,
      kSUPER -> UNKNOWN,
      kTHIS -> UNKNOWN,
      kTHROW -> THROW,
      TraitKeyword -> TRAIT,
      kTRUE -> UNKNOWN,
      kTRY -> TRY,
      kTYPE -> TYPE,
      kVAL -> VAL,
      kVAR -> VAR,
      kWHILE -> WHILE,
      kWITH -> WITH,
      kYIELD -> YIELD,
    )
  }

  private[ml] val KeywordsByName = KeywordsByElementType.map {
    case (elementType, keyword) => elementType.toString -> keyword
  }.withDefaultValue(Keyword.UNKNOWN)

  private[ml] def isSymbolic(name: String): Boolean = name.exists(c => !c.isLetterOrDigit && c != '$')

  private[ml] def extractWords(name: String,
                               maxWords: Int = MaxWords): Array[String] =
    if (isMeaningful(name)) {
      val wordsIterator = for {
        namePart <- NonNamePattern.split(name).iterator
        if isMeaningful(namePart)
        word <- NameUtilCore.nameToWords(namePart).iterator
        if isMeaningful(word)
      } yield word

      wordsIterator
        .take(maxWords)
        .map(_.toLowerCase)
        .toArray
    } else {
      EMPTY_STRING_ARRAY
    }

  private[ml] def extractWords(`type`: ScType): Array[String] = {
    val typesSortedByRelevance = `type` match {
      case FunctionType(returnType, argTypes) => returnType +: argTypes
      case PartialFunctionType(returnType, argType) => Seq(returnType, argType)
      case _ => Seq(`type`)
    }

    val words = mutable.Set.empty[String]

    for (scType <- typesSortedByRelevance) {
      val wordsLeft = MaxWords - words.size
      if (wordsLeft > 0) {
        val visitor = new TypeNamesExtractor(wordsLeft)

        scType.visitType(visitor)

        words ++= visitor.words
      }
    }

    words.toArray
  }

  private[ml] def wordsSimilarity(expected: Array[String],
                                  actual: Array[String]): Double = expected.length match {
    case 0 => -1
    case length =>
      val similarity = if (actual.isEmpty) 0
      else expected.map(word => actual.map(relativePrefixMatch(word, _)).max).sum

      similarity / length
  }

  private def relativePrefixMatch(str1: String, str2: String): Double = {
    val minLength = str1.length min str2.length
    val maxLength = str1.length max str2.length
    var result = 0.0
    var i = 0

    while (i < minLength) {
      if (str1(i) == str2(i)) {
        result += 1
        i += 1
      }
      else {
        i = minLength
      }

    }

    if (maxLength > 0) result / maxLength else 0
  }

  private def isMeaningful(word: String): Boolean = {
    word.length > 2 && word != "get" && word != "set" && !isSymbolic(word)
  }

  private[ml] def isJavaObjectMethod(element: PsiElement): Boolean = element match {
    case methodImpl: ClsMethodImpl =>
      Option(methodImpl.getContainingClass)
        .exists(CommonClassNames.JAVA_LANG_OBJECT == _.getQualifiedName)
    case _ => false
  }

  private[ml] def elementKind(element: PsiElement): CompletionItem = {
    import CompletionItem._

    element match {
      case c: PsiClass if InheritanceUtil.isInheritor(c, CommonClassNames.JAVA_LANG_THROWABLE) => EXCEPTION
      case _: ScPackage => PACKAGE
      case _: ScObject => OBJECT
      case _: ScTrait => TRAIT
      case _: ScClass => CLASS
      case _: ScTypeAlias => TYPE_ALIAS
      case _: ScFunction => FUNCTION
      case _: ScSyntheticFunction => SYNTHETHIC_FUNCTION
      case f: ScFieldId if f.isVar => VARIABLE
      case _: ScFieldId => VALUE
      case r: ScReferencePattern if r.isVar => VARIABLE
      case r: ScReferencePattern if r.isVal => VALUE
      case _: PsiPackage => PACKAGE
      case c: PsiClass if c.isInterface => TRAIT
      case _: PsiClass => CLASS
      case _: PsiMethod => FUNCTION
      case f: PsiField if f.getModifierList.hasModifierProperty(PsiModifier.FINAL) => VALUE
      case _: PsiField => VARIABLE
      case _ => UNKNOWN
    }
  }

  private[ml] def location(element: PsiElement): Location = {
    import Location._

    element.getParent match {
      case reference: ScReferenceExpression if reference.isQualified => REFERENCE
      case _ =>
        PsiTreeUtil.getParentOfType(element,
          classOf[ScArgumentExprList],
          classOf[ScAssignment],
          classOf[ScBlock],
          classOf[ScFile],
          classOf[ScFunctionDefinition],
          classOf[ScFor],
          classOf[ScIf],
          classOf[ScInfixExpr],
          classOf[ScPatternDefinition],
          classOf[ScParameterClause],
          classOf[ScPostfixExpr],
          classOf[ScTemplateBody],
          classOf[ScTemplateParents],
          classOf[ScTry],
          classOf[ScVariableDefinition],
        ) match {
          case _: ScArgumentExprList => ARGUMENT
          case assignment: ScAssignment if isRightAncestor(element, assignment) => EXPRESSION
          case _: ScBlock => BLOCK
          case _: ScFile => FILE
          case functionDefinition: ScFunctionDefinition if isRightAncestor(element, functionDefinition) => EXPRESSION
          case scFor: ScFor if scFor.body.exists(expr => PsiTreeUtil.isAncestor(expr, element, true)) => EXPRESSION
          case _: ScFor => FOR
          case scIf: ScIf if scIf.condition.exists(expr => PsiTreeUtil.isAncestor(expr, element, true)) => IF
          case _: ScIf => EXPRESSION
          case infixExpr: ScInfixExpr if isRightAncestor(element, infixExpr) => ARGUMENT
          case definition: ScPatternDefinition if isRightAncestor(element, definition) => EXPRESSION
          case _: ScParameterClause => PARAMETER
          case postfixExpr: ScPostfixExpr if isRightAncestor(element, postfixExpr) => REFERENCE
          case _: ScTemplateBody => CLASS_BODY
          case _: ScTemplateParents => CLASS_PARENTS
          case scTry: ScTry if scTry.finallyBlock.exists(_.expression.exists(expr => PsiTreeUtil.isAncestor(expr, element, true))) => EXPRESSION
          case scTry: ScTry if scTry.expression.exists(expr => PsiTreeUtil.isAncestor(expr, element, true)) => EXPRESSION
          case _: ScTry => CATCH
          case variableDefinition: ScVariableDefinition if isRightAncestor(element, variableDefinition) => EXPRESSION
          case _ => UNKNOWN
        }
    }
  }

  private[ml] def argumentsCount(element: PsiElement): Int = element match {
    case function: ScFunction => function.paramClauses.clauses.headOption.filterNot(_.isImplicit).map(_.parameters.size).getOrElse(0)
    case method: PsiMethod => method.getParameterList.getParametersCount
    case syntheticFunction: ScSyntheticFunction => syntheticFunction.paramClauses.headOption.map(_.size).getOrElse(0)
    case Typeable(FunctionType(_, argumentTypes)) => argumentTypes.size
    case Typeable(PartialFunctionType(_, _)) => 1
    case _ => -1
  }

  private[ml] def previousKeyword(element: PsiElement): Keyword =
    findLeftmostLeaf(element.getNode).flatMap { node =>
      KeywordsByElementType.get(node.getElementType)
    }.getOrElse(Keyword.UNKNOWN)

  private[ml] def expectedName(element: PsiElement): Option[String] =
    PsiTreeUtil.getParentOfType(
      element,
      classOf[ScTypedDeclaration],
      classOf[ScTypeAliasDefinition],
      classOf[ScAssignment],
      classOf[ScPatternDefinition],
      classOf[ScVariableDefinition],
      classOf[ScFunctionDefinition],
      classOf[ScBindingPattern],
      classOf[ScParameter]
    ) match {
      case declaration: ScTypedDeclaration if isRightAncestor(element, declaration) => declaration.declaredElements.headOption.map(_.name)
      case typeAlias: ScTypeAliasDefinition if isRightAncestor(element, typeAlias) => Option(typeAlias.name)
      case assignment: ScAssignment if isRightAncestor(element, assignment) => assignment.referenceName
      case value: ScPatternDefinition if isRightAncestor(element, value) => value.bindings.headOption.map(_.name)
      case variable: ScVariableDefinition if isRightAncestor(element, variable) => variable.bindings.headOption.map(_.name)
      case function: ScFunctionDefinition if isRightAncestor(element, function) => Option(function.name)
      case bindingPattern: ScBindingPattern if isRightAncestor(element, bindingPattern) => Option(bindingPattern.name)
      case parameter: ScParameter if isRightAncestor(element, parameter) => Option(parameter.name)
      case _ => None
    }

  @tailrec
  private[this] def isRightAncestor(child: PsiElement, parent: PsiElement): Boolean = {
    val currentParent = child.getParent
    if (currentParent.getLastChild ne child) false
    else if (currentParent eq parent) true
    else isRightAncestor(currentParent, parent)
  }

  private def findLeftmostLeaf(node: ASTNode, allowWhitespace: Boolean = false): Option[ASTNode] = {

    def suitableLeafNode(candidate: ASTNode): Boolean = {
      candidate.getLastChildNode == null &&
        (allowWhitespace || !isWhitespace(candidate)) &&
        node != candidate
    }

    def findLeafNode(node: ASTNode, checkChilds: Boolean): Option[ASTNode] = {
      if (node == null) None
      else if (suitableLeafNode(node)) Some(node)
      else {
        (if (checkChilds) findLeafNode(node.getLastChildNode, checkChilds = true) else None) orElse
          findLeafNode(node.getTreePrev, checkChilds = true) orElse
          findLeafNode(node.getTreeParent, checkChilds = false)
      }
    }

    findLeafNode(node, checkChilds = false)
  }

  private def isWhitespace(node: ASTNode): Boolean = node.getElementType match {
    case TokenType.WHITE_SPACE | ScalaTokenTypes.tWHITE_SPACE_IN_LINE => true
    case _ => false
  }

  private class TypeNamesExtractor(maxWords: Int) extends ScalaTypeVisitor {
    private val result = mutable.Set.empty[String]
    private var visited = 0

    def words: Seq[String] = result.toSeq

    override def visitStdType(`type`: StdType): Unit = add(`type`.name)
    override def visitTypeParameterType(`type`: TypeParameterType): Unit = add(`type`.name)
    override def visitDesignatorType(d: ScDesignatorType): Unit = add(d.element.name)
    override def visitExistentialArgument(s: ScExistentialArgument): Unit = add(s.name)

    override def visitJavaArrayType(`type`: JavaArrayType): Unit = visit(`type`.argument)
    override def visitCompoundType(c: ScCompoundType): Unit = c.components.foreach(visit)
    override def visitExistentialType(e: ScExistentialType): Unit = visit(e.simplify())
    override def visitTypePolymorphicType(t: ScTypePolymorphicType): Unit = visit(t.internalType)

    override def visitProjectionType(p: ScProjectionType): Unit = {
      add(p.element.name)
      visit(p.projected)
    }

    override def visitParameterizedType(`type`: ParameterizedType): Unit = {
      visit(`type`.designator)
      `type`.typeArguments.foreach(visit)
    }

    override def visitMethodType(`type`: ScMethodType): Unit = {
      visit(`type`)
      `type`.params.filterNot(_.isImplicitOrContextParameter).map(_.paramType).foreach(visit)
    }

    override def visitUndefinedType(`type`: UndefinedType): Unit = ()
    override def visitThisType(t: ScThisType): Unit = ()
    override def visitLiteralType(l: ScLiteralType): Unit = ()
    override def visitAbstractType(a: ScAbstractType): Unit = ()

    private def add(name: String): Unit = {
      result ++= extractWords(name, maxWords - result.size)
    }

    private def visit(`type`: ScType): Unit = {
      visited += 1
      if (result.size < maxWords && visited < 15) {
        `type`.visitType(this)
      }
    }
  }
}
