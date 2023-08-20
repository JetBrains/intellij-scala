package org.jetbrains.plugins.scala.conversion.visitors

import com.intellij.openapi.util._
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.plugins.scala.conversion.ast._
import org.jetbrains.plugins.scala.extensions.IterableOnceExt
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil.escapeKeyword

import scala.collection.mutable

/**
 * NOTE: this is a stateful class, it's not threadsafe, it's instances are not reusable
 */
//noinspection ScalaWeakerAccess,InstanceOf,DuplicatedCode
class SimplePrintVisitor protected() {

  import ClassConstruction.ClassType._
  import ModifierType._
  import SimplePrintVisitor._

  private val printer = new mutable.StringBuilder()

  private val nodesToRanges: mutable.Map[IntermediateNode, TextRange] =
    mutable.HashMap.empty.withDefaultValue(TextRange.create(0, 0))

  final def result(): String =
    StringUtil.convertLineSeparators(printer.toString)

  def getRange(node: IntermediateNode): TextRange =
    nodesToRanges(node)

  private def setRange(node: IntermediateNode, text: String): Unit = {
    nodesToRanges(node) = TextRange.from(printer.length, text.length)
  }

  protected def visit(node: IntermediateNode): Unit = node match {
    case m: MainConstruction => m.children.foreach(visit)
    case t@TypeConstruction(inType) => visitType(t, inType)
    case ParametrizedConstruction(inType, parts) => visitParametrizedType(inType, parts)
    case ArrayConstruction(inType) => visitArrayType(inType)
    case TypeParameters(data) => visitTypeParameters(data)
    case TypeParameterConstruction(name, types) =>
      visitTypeParameterConstruction(name, types)
    case AnnotationConstruction(inAnnotation, attributes, name) =>
      visitAnnotation(inAnnotation, attributes, name)
    case BlockConstruction(statements) =>
      visitBlockStatements(statements)
    case c@ClassConstruction(name, primaryConstructor, bodyElements, modifiers, typeParams, initializers, classType, companion, extendsList) =>
      visitClass(c, name, primaryConstructor, bodyElements,
        modifiers, typeParams, initializers, classType, companion, extendsList)
    case a@AnonymousClass(mType, args, body, extendsList) => visitAnonymousClass(a, mType, args, body, extendsList)
    case e@Enum(name, modifiers, members) => visitEnum(e, name, modifiers, members)
    case ArrayAccess(expression, idxExpression) => visitArrayAccess(expression, idxExpression)
    case c@ClassCast(operand, castType, _) => visitCastType(c, operand, castType)
    case ArrayInitializer(expressions: Seq[IntermediateNode]) => visitArrayInitializer(expressions)
    case BinaryExpressionConstruction(firstPart, secondPart, operation: String, inExpression: Boolean) =>
      visitBinary(firstPart, secondPart, operation, inExpression)
    case ClassObjectAccess(expression) => visitClassObjAccess(expression)
    case InstanceOfConstruction(operand, mtype) => visitInstanceOf(operand, mtype)
    case QualifiedExpression(qualifier, identifier) => visitQualifiedExpression(qualifier, identifier)
    case MethodCallExpression(method, args, withSideEffects) => visitMethodCall(method, args, withSideEffects)
    case ExpressionList(data) => visitExpressionList(data)
    case ThisExpression(value) => visitWithExtraWord(value, "this")
    case SuperExpression(value) => visitWithExtraWord(value, "super")
    case LiteralExpression(literal) => printer.append(literal)
    case NameIdentifier(name) => printer.append(escapeKeyword(name))
    case ParenthesizedExpression(value) => visitParenthesizedExpression(value)
    case NewExpression(mtype, arrayInitializer, arrayDimension) =>
      visitNewExpression(mtype, arrayInitializer, arrayDimension)
    case AnonymousClassExpression(anonymousClass) => visitAnonymousClassExpression(anonymousClass)
    case FunctionalExpression(params, body) => visitFunctionalExpression(params, body)
    case PolyadicExpression(args, operation) => visitPoliadic(args, operation)
    case RangeExpression(from, to, inclusive, descending) => visitRange(from, to, inclusive, descending)
    case PrefixExpression(operand, signType, canBeSimplified) => visitPrefixPostfix(operand, signType, canBeSimplified)
    case PostfixExpression(operand, signType, canBeSimplified) =>
      visitPrefixPostfix(operand, signType, canBeSimplified, isPostfix = true)
    case FieldConstruction(modifiers, name, ftype, isVar, initialaizer) =>
      visitVariable(modifiers, name, ftype, isVar, initialaizer)
    case LocalVariable(modifiers, name, ftype, isVar, initialaizer) =>
      visitVariable(modifiers, name, ftype, isVar, initialaizer)
    case ConstructorSimply(_, typeParams, params, body) =>
      visitConstructor(typeParams, params, body)
    case PrimaryConstructor(params, _, _, modifiers) =>
      visitPrimaryConstructor(params, modifiers)
    case MethodConstruction(modifiers, name, typeParams, params, body, retType) =>
      visitMethod(modifiers, name, typeParams, params, body, retType)
    case m@ModifiersConstruction(annotations, modifiers) =>
      visitModifiers(m, annotations, modifiers)
    case SimpleModifier(mtype: ModifierType) => visitSimpleModifier(mtype)
    case ModifierWithExpression(mtype, value) => visitModifierWithExpr(mtype, value)
    case ParameterConstruction(modifiers, name, scCompType, isVar, isArray) =>
      visitParameters(modifiers, name, scCompType, isVar, isArray)
    case ParameterListConstruction(list) => visitParameterList(list)
    //statements
    case r@JavaCodeReferenceStatement(qualifier, parameterList, name) =>
      visitJavaCodeRef(r, qualifier, parameterList, name)
    case IfStatement(condition, thenBranch, elseBranch) => visitIfStatement(condition, thenBranch, elseBranch)
    case r@ReturnStatement(value) =>
      visitWithExtraWord(Some(value), if (r.canDropReturnKeyword) "" else "return ")
    case ThrowStatement(value) => visitWithExtraWord(Some(value), "throw ")
    case AssertStatement(condition, description) => visitAssert(condition, description)
    case ImportStatement(importValue, onDemand) => visitImportStatement(importValue, onDemand)
    case ImportStatementList(data) => visitImportStatementList(data)
    case PackageStatement(value) => visitWithExtraWord(Some(value), "package ")
    case ForeachStatement(iterParamName, iteratedValue, body, isJavaCollection) =>
      visitForEach(iterParamName, iteratedValue, body, isJavaCollection)
    case w@WhileStatement(initialization, condition, body, update, whileType) =>
      visitWhile(w, initialization, condition, body, update, whileType)
    case TryCatchStatement(resourcesList, tryBlock, catchStatements, finallyStatements, arrow) =>
      visitTryCatch(resourcesList, tryBlock, catchStatements, finallyStatements, arrow)
    case sb: SwitchBlock =>
      visitSwitchBlock(sb)
    case SwitchLabelStatement(caseValues, guardExpression, arrow, body) =>
      visitSwitchLabelStatement(caseValues, guardExpression, arrow, body)
    case SynchronizedStatement(lock, body) =>
      visitSynchronizedStatement(lock, body)
    case ExpressionListStatement(exprs) => visitExpressionListStatement(exprs)
    case EnumConstruction(name) => visit(name)
    case NotSupported(n, msg) => visitNotSupported(n, msg)
    case EmptyConstruction() =>
    case EmptyTypeNode() =>
  }

  protected def visitAnnotation(
    inAnnotation: Boolean,
    attributes: Seq[(Option[IntermediateNode], Option[IntermediateNode])],
    name: Option[IntermediateNode]
  ): Unit = {
    if (inAnnotation) {
      printer.append("new ")
    } else {
      printer.append("@")
    }

    if (name.isDefined) {
      name.get match {
        case JavaCodeReferenceStatement(_, _, Some(name@"Deprecated")) =>
          printer.append(name.toLowerCase)
        case otherName =>
          visit(otherName)
      }
    }

    if (attributes.nonEmpty) {
      printer.append("(")

      val iterator = attributes.iterator
      while (iterator.hasNext) {
        val (maybeName, maybeValue) = iterator.next()
        if (maybeName.isDefined) {
          visit(maybeName.get)
          printer.append(" ").append("=").append(" ")
        }

        if (maybeValue.isDefined) {
          visit(maybeValue.get)
          if (iterator.hasNext) printer.append(",").append(" ")
        }
      }

      printer.append(")")
    }
    printer.append(" ")
  }

  protected def visitBlockStatements(statements: Seq[IntermediateNode]): Unit = {
    printWithSeparator(statements, "\n", "", "\n")
  }

  protected def visitClass(
    c: ClassConstruction,
    name: NameIdentifier,
    primaryConstructor: Option[PrimaryConstructor],
    bodyElements: Seq[IntermediateNode],
    modifiers: ModifiersConstruction,
    typeParams: Option[Seq[IntermediateNode]],
    initializers: Option[Seq[IntermediateNode]],
    classType: ClassType,
    companion: IntermediateNode,
    extendsList: Option[Seq[IntermediateNode]]
  ): Unit = {
    visitClassHeader()
    printBodyWithBraces(c)(visitClassBody())

    def visitClassHeader(): Unit = {
      if (companion.isInstanceOf[ClassConstruction]) {
        visit(companion)
        printer.append("\n")
      }

      visit(modifiers)
      printer.append(classType match {
        case CLASS => "class "
        case OBJECT => "object "
        case INTERFACE => "trait "
        case _ => ""
      })

      visit(name)
      if (typeParams.isDefined) printWithSeparator(typeParams.get, ", ", "[", "]", typeParams.get.nonEmpty)

      primaryConstructor.foreach { pc =>
        printer.append(" ")
        visit(pc)
      }

      if (extendsList.isDefined && extendsList.get.nonEmpty) {
        printer.append(" extends ")

        visit(extendsList.get.head)
        primaryConstructor.foreach { pc =>
          if (pc.superCall != null) {
            visit(pc.superCall)
          }
        }
        if (extendsList.get.tail.nonEmpty) printer.append(" with ")
        printWithSeparator(extendsList.get.tail, " with ")
      }
    }

    def visitClassBody(): Unit = {
      for {
        pc <- primaryConstructor
        constructorBody <- pc.body
      } {
        visit(constructorBody)
      }
      printWithSeparator(bodyElements, "\n", "", "\n", bodyElements.nonEmpty)
      initializers.foreach { init =>
        printWithSeparator(init, "\n", "\ntry ", "\n", init.nonEmpty)
      }
    }
  }

  protected def visitAnonymousClass(ac: AnonymousClass, mType: IntermediateNode, args: IntermediateNode, body: Seq[IntermediateNode],
                                    extendsList: Seq[IntermediateNode]): Unit = {
    visit(mType)
    printer.append("(")
    visit(args)
    printer.append(")")

    if (extendsList != null && extendsList.nonEmpty) {
      printer.append(" ")
        .append("with")
        .append(" ")
      printWithSeparator(extendsList, " with ")
    }

    printBodyWithBraces(ac) {
      printWithSeparator(body, " ")
    }
  }

  protected def visitEnum(e: Enum, name: NameIdentifier, modifiers: ModifiersConstruction, members: Seq[IntermediateNode]): Unit = {
    visit(modifiers)
    printer.append("object ")
    visit(name)
    printer.append(" extends Enumeration ")

    def visitEnumBody(): Unit = {
      printer.append("type ")
      visit(name)
      printer.append(" = Value")
      printer.append("\n")

      val enumConstants = members.collect { case el: EnumConstruction => el }
      if (enumConstants.nonEmpty) {
        printer.append("val ")
        printWithSeparator(enumConstants, ",")
        printer.append(" = Value")
        printer.append("\n")
      }

      members.filter(!_.isInstanceOf[EnumConstruction]).foreach(visit)
    }

    printBodyWithBraces(e)(visitEnumBody())
  }

  protected def visitArrayAccess(expression: IntermediateNode, idxExpression: IntermediateNode): Unit = {
    visit(expression)
    printer.append("(")
    visit(idxExpression)
    printer.append(")")
  }

  protected def visitCastType(c: ClassCast, operand: IntermediateNode, castType: IntermediateNode): Unit = {
    visit(operand)
    if (c.canSimplify) {
      printer.append(".to")
      visit(castType)
    } else {
      printer.append(".asInstanceOf[")
      visit(castType)
      printer.append("]")
    }
  }

  protected def visitArrayInitializer(expressions: Seq[IntermediateNode]): Unit = {
    printWithSeparator(expressions, ", ", "Array(", ")")
  }

  protected def visitBinary(firstPart: IntermediateNode, secondPart: IntermediateNode, operation: String, inExpression: Boolean): Unit = {
    val specialOperations = Seq("eq", "ne")

    if (inExpression && specialOperations.contains(operation))
      printer.append("(")

    visit(firstPart)
    printer.append(s" $operation ")
    visit(secondPart)

    if (inExpression && specialOperations.contains(operation))
      printer.append(")")
  }

  protected def visitClassObjAccess(expression: IntermediateNode): Unit = {
    printer.append("classOf[")
    visit(expression)
    printer.append("]")
  }

  protected def visitInstanceOf(operand: IntermediateNode, mtype: IntermediateNode): Unit = {
    visit(operand)
    printer.append(".isInstanceOf[")
    visit(mtype)
    printer.append("]")
  }

  protected def visitQualifiedExpression(qualifier: IntermediateNode, identifier: IntermediateNode): Unit = {
    if (qualifier != null) {
      visit(qualifier)
      visit(identifier)
    }
  }

  protected def visitMethodCall(method: IntermediateNode, args: IntermediateNode, withSideEffects: Boolean): Unit = {
    visit(method)
    if (args != null)
      visit(args)
    if (withSideEffects)
      printer.append("()")
  }

  protected def visitExpressionList(data: Seq[IntermediateNode]): Unit = {
    printWithSeparator(data, ", ", "(", ")", data.nonEmpty)
  }

  protected def visitWithExtraWord(value: Option[IntermediateNode], word: String): Unit = {
    printer.append(word)
    if (value.isDefined)
      visit(value.get)
  }

  protected def visitParenthesizedExpression(value: Option[IntermediateNode]): Unit = {
    printer.append("(")
    if (value.isDefined)
      visit(value.get)
    printer.append(")")
  }

  protected def visitNewExpression(mtype: IntermediateNode, arrayInitializer: Seq[IntermediateNode],
                                   arrayDimension: Seq[IntermediateNode]): Unit = {
    if (arrayInitializer.nonEmpty) {
      visit(mtype)
      printWithSeparator(arrayInitializer, ", ", "(", ")")
    } else {
      printer.append("new ")
      visit(mtype)
      val needAppend = arrayDimension != null && arrayDimension.nonEmpty &&
        !arrayDimension.head.isInstanceOf[ExpressionList] && arrayDimension.head != LiteralExpression("()")
      printWithSeparator(arrayDimension, ", ", "(", ")", needAppend)
    }
  }

  protected def visitAnonymousClassExpression(anonClass: IntermediateNode): Unit = {
    printer.append("new ")
    visit(anonClass)
  }

  protected def visitFunctionalExpression(params: IntermediateNode, body: IntermediateNode): Unit = {
    visit(params)
    printer.append(" => ")

    body match {
      case bc@BlockConstruction(statements) =>
        def constructHelperFunction(): Unit = {
          printer.append("def foo")
          visit(params)
          printer.append(" = \n")
          printBodyWithBraces(body)(visitBlockStatements(statements))
          printer.append("\n")
        }

        def constructFunctionCall(): Unit = {
          val pNames: Seq[IntermediateNode] =
            params.asInstanceOf[ParameterListConstruction].list
              .filterByType[ParameterConstruction]
              .map(_.name)

          printer.append("foo")
          printWithSeparator(pNames, ",", "(", ")")
        }

        printer.append("{\n")
        if (hasReturnStatementsInNonTailPosition(bc)) {
          //if we have return in the non-tail position we need a helper function
          constructHelperFunction()
          constructFunctionCall()
        } else {
          //otherwise we can just drop return keyword
          val allReturns = returnStatementsInSameScope(bc, stopAtFirst = false)
          allReturns.foreach(_.canDropReturnKeyword = true)

          visitBlockStatements(statements)
        }
        printer.append("\n} ")
      case _ =>
        visit(body)
    }
  }

  private def hasReturnStatementsInNonTailPosition(node: IntermediateNode): Boolean = {
    val children: Seq[IntermediateNode] = getChildren(node)
    if (children.isEmpty)
      return false

    node match {
      case _: BlockConstruction =>
        val initNodes = children.init
        val lastNode = children.last
        val hasReturnInTheMiddle = initNodes.exists(hasReturnStatements)
        if (hasReturnInTheMiddle)
          true
        else
          hasReturnStatementsInNonTailPosition(lastNode)
      case _ =>
        children.exists(hasReturnStatementsInNonTailPosition)
    }
  }

  private def hasReturnStatements(node: IntermediateNode): Boolean =
    returnStatementsInSameScope(node, stopAtFirst = true).nonEmpty

  private def returnStatementsInSameScope(node: IntermediateNode, stopAtFirst: Boolean): Seq[ReturnStatement] = {
    val result = new mutable.ArrayBuffer[ReturnStatement]
    breadthFirst(node, {
      //stop at local functions and classes:
      //if there is return inside, it will belong to some definition inside the class/def
      case _: MethodConstruction => false
      case _: ClassLikeConstruction => false
      case r: ReturnStatement =>
        result += r
        !stopAtFirst
      case _ => true
    }).foreach(_ => ()) //consume iterator
    result.toSeq
  }

  protected def visitRange(from: IntermediateNode, to: IntermediateNode, inclusive: Boolean, descending: Boolean): Unit = {
    visit(from)
    if (inclusive) {
      printer.append(" to ")
    } else {
      printer.append(" until ")
    }
    visit(to)
    if (descending) {
      printer.append(" by -1")
    }
  }

  protected def visitPoliadic(args: Seq[IntermediateNode], operation: String): Unit = {
    printWithSeparator(args, " " + operation + " ")
  }

  protected def visitPrefixPostfix(operand: IntermediateNode, signType: String,
                                   canBeSimplified: Boolean, isPostfix: Boolean = false): Unit = {
    signType match {
      case "++" =>
        if (!canBeSimplified) {
          printer.append("{")
          visit(operand)
          printer.append(" += 1; ")
          visit(operand)
          if (isPostfix) printer.append(" - 1")
          printer.append("}")
        } else {
          visit(operand)
          printer.append(" += 1")
        }
      case "--" =>
        if (!canBeSimplified) {
          printer.append("{")
          visit(operand)
          printer.append(" -= 1; ")
          visit(operand)
          if (isPostfix)
            printer.append(" + 1")
          printer.append("}")
        } else {
          visit(operand)
          printer.append(" -= 1")
        }
      case _ if !isPostfix =>
        printer.append(signType)
        printer.append("(")
        visit(operand)
        printer.append(")")
    }
  }

  protected def visitVariable(modifiers: ModifiersConstruction, name: NameIdentifier,
                              ftype: IntermediateNode, isVar: Boolean,
                              initializer: Option[IntermediateNode]): Unit = {
    visit(modifiers)

    if (isVar) {
      printer.append("var")
    } else {
      printer.append("val")
    }
    printer.append(" ")
    visit(name)
    printer.append(": ")
    visit(ftype)
    printer.append(" = ")
    if (initializer.isDefined) {
      visit(initializer.get)
    } else {
      printer.append(ftype match {
        case tc: TypeConstruction => tc.getDefaultTypeValue
        case _ => "null"
      })
    }
  }

  protected def visitConstructor(
    typeParams: Seq[IntermediateNode],
    params: Seq[IntermediateNode],
    body: Option[IntermediateNode]
  ): Unit = {
    printer.append("def this")
    if (typeParams.nonEmpty) {
      printWithSeparator(typeParams, ", ", "[", "]")
    }

    printWithSeparator(params, ", ", "(", ")", params.nonEmpty)

    body.foreach { b =>
      printBodyWithBraces(b)(visit(b))
    }
  }

  protected def visitMethod(modifiers: ModifiersConstruction, name: NameIdentifier, typeParams: Seq[IntermediateNode],
                            params: Seq[IntermediateNode], body: Option[IntermediateNode], retType: Option[IntermediateNode]): Unit = {
    visit(modifiers)
    printer.append("def ")
    visit(name)

    if (typeParams.nonEmpty) {
      printWithSeparator(typeParams, ", ", "[", "]")
    }

    printWithSeparator(params, ", ", "(", ")", needAppend = params.nonEmpty || (retType.contains(TypeConstruction("Unit")) || retType.isEmpty))

    retType.foreach { rt =>
      printer.append(": ")
      visit(rt)
    }

    body.foreach { b =>
      if (retType.isDefined)
        printer.append(" = ")
      printBodyWithBraces(b)(visit(b))
    }
  }

  protected def visitPrimaryConstructor(
    params: Seq[IntermediateNode],
    modifiers: ModifiersConstruction
  ): Unit = {
    visit(modifiers)
    printer.append(" ")
    if (params.nonEmpty) {
      printer.append("(")
      printWithSeparator(params, ", ")
      printer.append(")")
    }
  }

  protected def visitModifiers(
    modifiersConstruction: ModifiersConstruction,
    annotations: Seq[IntermediateNode],
    modifiers: Seq[Modifier]
  ): Unit = {
    //some modifiers in Java are annotations in Scala, e.g. `transient` -> `@transient`
    val (scalaExtraAnnotations, scalaModifiers) =
      modifiers.foldLeft((Seq.empty[String], Seq.empty[Modifier])) { case ((scalaAnnotations, scalaModifiers), javaModifier) =>
        JavaModifierToScalaAnnotation.get(javaModifier.modificator) match {
          case Some(a) => (scalaAnnotations :+ a, scalaModifiers)
          case None => (scalaAnnotations, scalaModifiers :+ javaModifier)
        }
      }

    val scalaExtraAnnotationConstructions: Seq[AnnotationConstruction] =
      scalaExtraAnnotations.map { name =>
        AnnotationConstruction(inAnnotation = false, Nil, Some(JavaCodeReferenceStatement(None, None, Some(name))))
      }

    for (a <- annotations ++ scalaExtraAnnotationConstructions) {
      visit(a)
      printer.append(" ")
    }

    //to prevent situation where access modifiers print earlier then throw
    val (accessModifiers, otherModifiers) = scalaModifiers.partition(m => ModifierType.AccessModifiers.contains(m.modificator))
    val modifiersSorted = otherModifiers ++ accessModifiers

    for (m <- modifiersSorted) {
      if (!modifiersConstruction.withoutList.contains(m.modificator)) {
        visit(m)
        printer.append(" ")
      }
    }
  }

  protected def visitSimpleModifier(mtype: ModifierType): Unit = {
    printer.append(mtype match {
      case ABSTRACT => "abstract"
      case PUBLIC => "public"
      case PROTECTED => "protected"
      case PRIVATE => "private"
      case OVERRIDE => "override"
      case FINAL => "final"
      case _ => ""
    })
  }

  protected def visitModifierWithExpr(mtype: ModifierType, value: IntermediateNode): Unit = {
    mtype match {
      case THROW =>
        printer.append("@throws[")
        visit(value)
        printer.append("]")
        printer.append("\n")
      case SerialVersionUID =>
        printer.append("@SerialVersionUID(")
        visit(value)
        printer.append(")")
        printer.append("\n")
      case PRIVATE =>
        printer.append("private[")
        visit(value)
        printer.append("]")
        printer.append(" ")
      case _ =>
    }
  }

  protected def visitParameters(modifiers: ModifiersConstruction, name: NameIdentifier,
                                scCompType: IntermediateNode, isVar: Option[Boolean], isArray: Boolean): Unit = {
    def visitDisjunctionType(disjunctionTypeConstructions: DisjunctionTypeConstructions): Unit = {
      visit(name)
      printer.append("@(")

      val iterator = disjunctionTypeConstructions.parts.iterator
      while (iterator.hasNext) {
        printer.append("_: ")
        visit(iterator.next())
        if (iterator.hasNext)
          printer.append(" | ")
      }

      printer.append(")")
    }

    visit(modifiers)
    if (isVar.isDefined) {
      printer.append(if (isVar.get) "var" else "val")
      printer.append(" ")
    }

    scCompType match {
      case disjuncit: DisjunctionTypeConstructions => visitDisjunctionType(disjuncit)
      case _ =>
        visit(name)

        if (!scCompType.isInstanceOf[EmptyTypeNode]) {
          printer.append(": ")
          visit(scCompType)
        }

        if (isArray) {
          printer.append("*")
        }
    }
  }

  protected def visitParameterList(list: Seq[IntermediateNode]): Unit = {
    printWithSeparator(list, ", ", "(", ")")
  }


  protected def visitIfStatement(condition: Option[IntermediateNode], thenBranch: Option[IntermediateNode],
                                 elseBranch: Option[IntermediateNode]): Unit = {

    printer.append("if ")

    printer.append("(")
    if (condition.isDefined) visit(condition.get)
    printer.append(")")
    printer.append(" ")

    thenBranch.foreach { t =>
      printBodyWithBraces(t)(visit(t))
    }

    elseBranch.foreach { e =>
      printer.append("\n")
      printer.append("else  ")
      printBodyWithBraces(e)(visit(e))
    }
  }

  protected def visitAssert(condition: Option[IntermediateNode], description: Option[IntermediateNode]): Unit = {
    printer.append("assert (")

    if (condition.isDefined) visit(condition.get)
    if (description.isDefined) {
      printer.append(", ")
      visit(description.get)
    }
    printer.append(")")
  }

  protected def visitImportStatement(importValue: IntermediateNode, onDemand: Boolean): Unit = {
    printer.append("import ")
    visit(importValue)
    if (onDemand) {
      printer.append("._")
    }
  }

  protected def visitImportStatementList(imports: Seq[IntermediateNode]): Unit = {
    for (importNods <- imports) {
      visit(importNods)
      printer.append("\n")
    }
  }

  protected def visitWhile(w: WhileStatement, initialization: Option[IntermediateNode], condition: Option[IntermediateNode],
                           body: Option[IntermediateNode], update: Option[IntermediateNode], whileType: Int): Unit = {
    def printDoWhile(): Unit = {
      printer.append("do {\n")
      body.foreach { b =>
        printBodyWithBraces(b)(visit(b))
      }
      printer.append("\n}")

      if (update.isDefined) {
        printer.append("\n")
        visit(update.get)
      }
      printer.append("while (")
      condition.foreach(visit)
      printer.append(")")
    }

    def printWhile(): Unit = {
      printer.append("while (")
      condition.foreach(visit)
      printer.append(") ")
      printBodyWithBraces(w) {
        body.foreach { b =>
          visit(b)
        }

        update.foreach { u =>
          printer.append("\n")
          visit(u)
        }
      }
    }

    initialization.foreach { i =>
      visit(i)
      printer.append("\n")
    }

    if (whileType == WhileStatement.PRE_TEST_LOOP) printWhile()
    else if (whileType == WhileStatement.POST_TEST_LOOP) printDoWhile()
  }

  protected def visitTryCatch(
    resourcesList: Seq[(String, IntermediateNode)],
    tryBlock: Option[BlockConstruction],
    catchStatements: Seq[(IntermediateNode, IntermediateNode)],
    finallyStatements: Option[Seq[IntermediateNode]],
    arrow: String
  ): Unit = {
    if (resourcesList != null && resourcesList.nonEmpty) {
      printer.append("try {\n")
      printWithSeparator(resourcesList.map(_._2), "\n", "", "\n")
    }

    printer.append("try ")
    val statements = tryBlock.map(_.statements).getOrElse(Nil)
    statements match {
      case Seq() =>
        printer.append("{\n}")
      case Seq(stmt) =>
        visit(stmt)
        printer.append("\n")
      case stmts =>
        printer.append("{")
        printWithSeparator(stmts, "\n")
        printer.append("}")
    }

    if (catchStatements.nonEmpty) {
      printer.append(" catch {\n")
      catchStatements.foreach { case (parameter, block) =>
        printer.append("case ")
        visit(parameter)
        printer.append(s" $arrow \n")
        visit(block)
      }
      printer.append("}")
    }

    if (finallyStatements.isDefined) {
      if (resourcesList == null) {
        printer.append(" finally ")
        printWithSeparator(finallyStatements.get, "\n")
      } else {
        printer.append(" finally {\n")
        printWithSeparator(finallyStatements.get, "\n", "", "\n")
        resourcesList.foreach {
          case (name: String, _: IntermediateNode) =>
            val cname = escapeKeyword(name)
            printer.append(s"if ($cname != null) $cname.close()\n")
        }

        printer.append("}")
      }
    } else if (resourcesList.nonEmpty) {
      printer.append(" finally {\n")
      resourcesList.foreach {
        case (name: String, _: IntermediateNode) =>
          val cname = escapeKeyword(name)
          printer.append(s"if ($cname != null) $cname.close()\n")
      }
      printer.append("}")
    }
    if (resourcesList.nonEmpty) {
      printer.append("\n}")
    }
  }

  protected def visitSwitchBlock(switch: SwitchBlock): Unit = {
    switch.expression.foreach(visit)
    printer.append(" match ")
    switch.body.foreach { b =>
      printBodyWithBraces(b)(visit(b))
    }
  }

  protected def visitSwitchLabelStatement(caseValues: Seq[IntermediateNode],
                                          guardExpression: Option[IntermediateNode],
                                          arrow: String,
                                          body: Option[IntermediateNode]): Unit = {
    printWithSeparator(caseValues, " | ", "case ", "")
    guardExpression.foreach { guard =>
      printer.append(" if ")
      visit(guard)
    }
    printer.append(s" $arrow ")
    body.foreach(visit)
  }

  protected def visitNotSupported(iNode: Option[IntermediateNode], msg: String): Unit = {
    printer.append(msg)
    printer.append("\n")
    if (iNode.isDefined) {
      visit(iNode.get)
    }
  }

  protected def visitSynchronizedStatement(lock: Option[IntermediateNode], body: Option[IntermediateNode]): Unit = {
    if (lock.isDefined) {
      visit(lock.get)
      printer.append(".")
    }
    printer.append("synchronized {")
    if (body.isDefined) {
      visit(body.get)
    }
    printer.append("}")
  }

  protected def visitExpressionListStatement(exprs: Seq[IntermediateNode]): Unit = {
    printWithSeparator(exprs, "\n")
  }

  protected def visitForEach(iterParamName: IntermediateNode, iteratedValue: Option[IntermediateNode],
                             body: Option[IntermediateNode], isJavaCollection: Boolean): Unit = {
    if (isJavaCollection) {
      printer.append("import scala.collection.JavaConversions._")
      printer.append("\n")
    }

    printer.append("for (")
    visit(iterParamName)
    printer.append(" <- ")
    if (iteratedValue.isDefined) visit(iteratedValue.get)
    printer.append(") ")

    body.foreach { b =>
      printBodyWithBraces(b)(visit(b))
    }
  }

  protected def visitJavaCodeRef(statement: JavaCodeReferenceStatement,
                                 qualifier: Option[IntermediateNode],
                                 parameterList: Option[IntermediateNode],
                                 nameOpt: Option[String]): Unit = {
    if (qualifier.isDefined) {
      visit(qualifier.get)
      printer.append(".")
    }

    val escapedName = nameOpt
      .map {
        case n@("this" | "super") => n
        case b => escapeKeyword(b)
      }
      .getOrElse("")

    this.setRange(statement, escapedName)
    printer.append(escapedName)

    if (parameterList.isDefined) {
      visit(parameterList.get)
    }
  }

  protected def visitType(t: TypeConstruction, inType: String): Unit = {
    this.setRange(t, inType)
    printer.append(inType)
  }

  protected def visitArrayType(iNode: IntermediateNode): Unit = {
    printer.append("Array[")
    visit(iNode)
    printer.append("]")
  }

  protected def visitParametrizedType(iNode: IntermediateNode, parts: Seq[IntermediateNode]): Unit = {
    visit(iNode)
    printWithSeparator(parts, ", ", "[", "]", parts.nonEmpty)
  }

  protected def printWithSeparator(seq: IterableOnce[IntermediateNode], separator: String): Unit = {
    if (seq != null) {
      val it = seq.iterator
      while (it.hasNext) {
        visit(it.next())
        if (it.hasNext) printer.append(separator)
      }
    }
  }

  protected def printWithSeparator(seq: IterableOnce[IntermediateNode], separator: String, before: String, after: String, needAppend: Boolean): Unit = {
    if (needAppend) printer.append(before)
    printWithSeparator(seq, separator)
    if (needAppend) printer.append(after)
  }

  protected def printWithSeparator(seq: IterableOnce[IntermediateNode], separator: String, before: String, after: String): Unit = {
    printWithSeparator(seq, separator, before, after, needAppend = true)
  }

  protected def visitTypeParameters(data: Iterable[IntermediateNode]): Unit = {
    printWithSeparator(data, ", ", "[", "]", data.nonEmpty)
  }

  protected def visitTypeParameterConstruction(name: NameIdentifier, types: Seq[IntermediateNode]): Unit = {
    visit(name)
    if (types.nonEmpty) {
      printer.append(" <: ")
      printWithSeparator(types, " with ")
    }
  }

  protected def printBodyWithBraces(node: IntermediateNode)
                                   (printBodyFunction: => Unit): Unit = {
    printer.append(" { ")
    printBodyFunction
    printer.append("}")
  }
}

object SimplePrintVisitor {

  private val JavaModifierToScalaAnnotation: Map[ModifierType.Value, String] = Map(
    ModifierType.VOLATILE -> "volatile",
    ModifierType.TRANSIENT -> "transient",
    ModifierType.NATIVE -> "native",
  )
}
