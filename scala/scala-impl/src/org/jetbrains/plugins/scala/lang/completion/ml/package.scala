package org.jetbrains.plugins.scala.lang.completion

import com.intellij.psi.impl.compiled.ClsMethodImpl
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi._
import com.intellij.util.text.NameUtilCore
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScPackage
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScReferencePattern}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAssignment, ScCatchBlock, ScPostfixExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition, ScPatternDefinition, ScTypeAlias, ScTypeAliasDefinition, ScTypedDeclaration, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.api.{FunctionType, JavaArrayType, ParameterizedType, PartialFunctionType, StdType, TypeParameterType, UndefinedType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.psi.types.{ScAbstractType, ScCompoundType, ScExistentialArgument, ScExistentialType, ScLiteralType, ScType, ScalaTypeVisitor}

import scala.annotation.tailrec
import scala.collection.mutable

package object ml {

  private val NonNamePattern = """[^a-zA-Z_]""".r
  private val MaxWords = 7

  private[ml] def isSymbolic(name: String): Boolean = name.exists(c => !c.isLetterOrDigit && c != '$')

  private[ml] def extractWords(names: Iterable[String], maxWords: Int = MaxWords): Array[String] = {

    val wordsIterator = for {
      name <- names.iterator if isMeaningful(name)
      namePart <- NonNamePattern.split(name).iterator if isMeaningful(namePart)
      word <- NameUtilCore.nameToWords(namePart).iterator if isMeaningful(word)
    } yield word

    wordsIterator
      .take(maxWords)
      .map(_.toLowerCase)
      .toArray
  }

  private[ml] def extractWords(maybeType: Option[ScType]): Array[String] = {

    val typesSortedByRelevance = maybeType match {
      case Some(FunctionType(returnType, argTypes)) => returnType +: argTypes
      case Some(PartialFunctionType(returnType, argType)) => Seq(returnType, argType)
      case Some(scType) => Seq(scType)
      case _ => Seq.empty
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

  private[ml] def wordsSimilarity(expected: Array[String], actual: Array[String]): Option[Double] = {
    val similarity = if (actual.nonEmpty) expected.map(word => actual.map(relativePrefixMatch(word, _)).max).sum else 0
    if (expected.nonEmpty) Some(similarity / expected.length) else None
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

  private[ml] def isJavaObjectMethod(maybeElement: Option[PsiElement]): Boolean = maybeElement.exists {
    case methodImpl: ClsMethodImpl =>
      Option(methodImpl.getContainingClass).exists(_.qualifiedNameOpt.contains(CommonClassNames.JAVA_LANG_OBJECT))
    case _ => false
  }

  private[ml] def elementKind(maybeElement: Option[PsiElement]): Option[CompletionItem] = {
    import CompletionItem._

    @tailrec
    def isException(psiClass: PsiClass, depth: Int = 5): Boolean = {
      depth >= 0 && psiClass.qualifiedNameOpt.contains(CommonClassNames.JAVA_LANG_THROWABLE) || {
        val superClass = psiClass.getSuperClass
        superClass != null && isException(superClass, depth - 1)
      }
    }

    maybeElement.collectFirst {
      case c: PsiClass if isException(c) => EXCEPTION
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
    }
  }

  private[ml] def argumentCount(maybeElement: Option[PsiElement]): Option[Int] = {
    maybeElement.map {
      case function: ScFunction => function.paramClauses.clauses.headOption.filterNot(_.isImplicit).map(_.parameters.size).getOrElse(0)
      case method: PsiMethod => method.getParameterList.getParametersCount
      case syntheticFunction: ScSyntheticFunction => syntheticFunction.paramClauses.headOption.map(_.size).getOrElse(0)
      case Typeable(FunctionType(_, argumentTypes)) => argumentTypes.size
      case Typeable(PartialFunctionType(_, _)) => 1
      case _ => -1
    }
  }

  private[ml] def isPostfix(maybeElement: Option[PsiElement]): Boolean = {
    maybeElement.exists { element =>
      identifierWithParentsPattern(
        classOf[ScReferenceExpression],
        classOf[ScPostfixExpr]
      ).accepts(element)
    }
  }

  private[ml] def isInsideCatch(maybeElement: Option[PsiElement]): Boolean =
    maybeElement.flatMap(_.parentOfType(classOf[ScCatchBlock])).nonEmpty

  private[ml] def expectedName(maybeElement: Option[PsiElement]): Option[String] = {

    maybeElement
      .flatMap { element =>
        findParendIfRightChild(element, Array(
          classOf[ScTypedDeclaration],
          classOf[ScTypeAliasDefinition],
          classOf[ScAssignment],
          classOf[ScPatternDefinition],
          classOf[ScVariableDefinition],
          classOf[ScFunctionDefinition],
          classOf[ScBindingPattern],
          classOf[ScParameter]
        ))
      }
      .flatMap {
        case typedDeclaration: ScTypedDeclaration => typedDeclaration.declaredElements.headOption.map(_.name)
        case typeAliasDefinition: ScTypeAliasDefinition => Option(typeAliasDefinition.name)
        case assignment: ScAssignment => assignment.referenceName
        case valDefinition: ScPatternDefinition => valDefinition.bindings.headOption.map(_.name)
        case varDefinition: ScVariableDefinition => varDefinition.bindings.headOption.map(_.name)
        case defDefinition: ScFunctionDefinition => Option(defDefinition.name)
        case bindingPattern: ScBindingPattern => Option(bindingPattern.name)
        case parameter: ScParameter => Option(parameter.name)
        case _ => None
      }
  }

  private def findParendIfRightChild(element: PsiElement, parentClasses: Array[Class[_ <: PsiElement]]): Option[PsiElement] = {
    val parent = PsiTreeUtil.getParentOfType(element, parentClasses: _*)

    @tailrec
    def isRightAncestor(child: PsiElement, parent: PsiElement): Boolean = {
      val currentParrent = child.getParent
      if (currentParrent.getLastChild ne child) false
      else if (currentParrent eq parent) true
      else isRightAncestor(currentParrent, parent)
    }

    Option(parent).filter(isRightAncestor(element, _))
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
      `type`.params.filterNot(_.isImplicit).map(_.paramType).foreach(visit)
    }

    override def visitUndefinedType(`type`: UndefinedType): Unit = ()
    override def visitThisType(t: ScThisType): Unit = ()
    override def visitLiteralType(l: ScLiteralType): Unit = ()
    override def visitAbstractType(a: ScAbstractType): Unit = ()

    private def add(name: String): Unit = {
      result ++= extractWords(Some(name), maxWords - result.size)
    }

    private def visit(`type`: ScType): Unit = {
      visited += 1
      if (result.size < maxWords && visited < 15) {
        `type`.visitType(this)
      }
    }
  }
}
