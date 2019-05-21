package org.jetbrains.plugins.scala
package conversion
package visitors

import com.intellij.openapi.util._

import scala.collection.mutable

/**
 * Created by Kate Ustyuzhanina
 * on 11/24/15
 */
class SimplePrintVisitor protected() {

  import SimplePrintVisitor._
  import ast._
  import ClassConstruction.ClassType._
  import ModifierType._
  import lang.refactoring.util.ScalaNamesUtil.escapeKeyword

  private val printer = mutable.StringBuilder.newBuilder

  private val nodesToRanges: mutable.Map[IntermediateNode, TextRange] =
    mutable.HashMap.empty.withDefaultValue(TextRange.create(0, 0))

  final def apply(): String =
    text.StringUtil.convertLineSeparators(printer.toString)

  final def apply(node: IntermediateNode): TextRange = nodesToRanges(node)

  private def update(node: IntermediateNode, text: String): Unit = {
    nodesToRanges(node) = TextRange.from(printer.length, text.length)
  }

  protected def visit(node: IntermediateNode): Unit = node match {
    case m: MainConstruction => m.children.foreach(visit)
    case t@TypeConstruction(inType) => visitType(t, inType)
    case ParametrizedConstruction(inType, parts) => visitParametrizedType(inType, parts)
    case ArrayConstruction(inType) => visitArrayType(inType)
    case TypeParameters(data) => visitTypeParameters(data)
    case TypeParameterConstruction(name, types) => visitTypeParameterConstruction(name, types)
    case AnnotaionConstruction(inAnnotation, attributes, name) => visitAnnotation(inAnnotation, attributes, name)
    case b@BlockConstruction(statements) => visitBlock(b, statements)
    case c@ClassConstruction(name, primaryConstructor, bodyElements, modifiers, typeParams, initializers, classType,
    companion, extendsList) => visitClass(c, name, primaryConstructor, bodyElements,
      modifiers, typeParams, initializers, classType, companion, extendsList)
    case a@AnonymousClass(mType, args, body, extendsList) => visitAnonymousClass(a, mType, args, body, extendsList)
    case e@Enum(name, modifiers, members) => visitEnum(e, name, modifiers, members)
    case ArrayAccess(expression, idxExpression) => visitArrayAccess(expression, idxExpression)
    case c@ClassCast(operand, castType, isPrimitive) => visitCastType(c, operand, castType, isPrimitive)
    case ArrayInitializer(expressions: Seq[IntermediateNode]) => visitArrayInitializer(expressions)
    case BinaryExpressionConstruction(firstPart, secondPart, operation: String, inExpression: Boolean) =>
      visitBinary(firstPart, secondPart, operation, inExpression)
    case ClassObjectAccess(expression) => visitClassObjAccess(expression)
    case InstanceOfConstruction(operand, mtype) => visitInstanceOf(operand, mtype)
    case QualifiedExpression(qualifier, identifier) => visitQualifiedExpression(qualifier, identifier)
    case MethodCallExpression(name, method, args, withSideEffects) => visitMethodCall(name, method, args, withSideEffects)
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
    case PrefixExpression(operand, signType, canBeSimplified) => visitPrefixPostfix(operand, signType, canBeSimplified)
    case PostfixExpression(operand, signType, canBeSimplified) =>
      visitPrefixPostfix(operand, signType, canBeSimplified, isPostfix = true)
    case FieldConstruction(modifiers, name, ftype, isVar, initialaizer) =>
      visitVariable(modifiers, name, ftype, isVar, initialaizer)
    case LocalVariable(modifiers, name, ftype, isVar, initialaizer) =>
      visitVariable(modifiers, name, ftype, isVar, initialaizer)
    case ConstructorSimply(modifiers, typeParams, params, body) =>
      visitConstructor(modifiers, typeParams, params, body)
    case PrimaryConstruction(params, superCall, body, modifiers) =>
      visitPrimaryConstructor(params, superCall, body, modifiers)
    case MethodConstruction(modifiers, name, typeParams, params, body, retType) =>
      visitMethod(modifiers, name, typeParams, params, body, retType)
    case m@ModifiersConstruction(annotations, modifiers) => visitModifiers(m, annotations, modifiers)
    case SimpleModifier(mtype: ModifierType) => visitSimpleModifier(mtype)
    case ModifierWithExpression(mtype, value) => visitModifierWithExpr(mtype, value)
    case ParameterConstruction(modifiers, name, scCompType, isVar, isArray) =>
      visitParameters(modifiers, name, scCompType, isVar, isArray)
    case ParameterListConstruction(list) => visitParameterList(list)
    //statements
    case r@JavaCodeReferenceStatement(qualifier, parameterList, name) =>
      visitJavaCodeRef(r, qualifier, parameterList, name)
    case IfStatement(condition, thenBranch, elseBranch) => visitIfStatement(condition, thenBranch, elseBranch)
    case ReturnStatement(value) => visitWithExtraWord(Some(value), "return ")
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
    case SwitchStatemtnt(expression, body) => visitSwitchStatement(expression, body)
    case SwitchLabelStatement(caseValue, arrow) => visitSwitchLabelStatement(caseValue, arrow)
    case SynchronizedStatement(lock, body) => visitSynchronizedStatement(lock, body)
    case ExpressionListStatement(exprs) => visitExpressionListStatement(exprs)
    case EnumConstruction(name) => visit(name)
    case NotSupported(n, msg) => visitNotSupported(n, msg)
    case EmptyConstruction() =>
  }

  protected def visitAnnotation(inAnnotation: Boolean, attributes: Seq[(Option[IntermediateNode], Option[IntermediateNode])],
                                name: Option[IntermediateNode]): Unit = {
    if (inAnnotation) {
      printer.append("new ")
    } else {
      printer.append("@")
    }

    if (name.isDefined) {
      name.get match {
        case JavaCodeReferenceStatement(_, _, Some(name@"Deprecated")) =>
          printer.append(name.toLowerCase)
        case otherName => visit(otherName)
      }
    }

    if (attributes.nonEmpty) {
      printer.appendLeftParenthesis()

      val iterator = attributes.iterator
      while (iterator.hasNext) {
        val (maybeName, maybeValue) = iterator.next()
        if (maybeName.isDefined) {
          visit(maybeName.get)
          printer.appendSpace().append("=").appendSpace()
        }

        if (maybeValue.isDefined) {
          visit(maybeValue.get)
          if (iterator.hasNext) printer.append(",").appendSpace()
        }
      }

      printer.appendRightParenthesis()
    }
    printer.appendSpace()
  }

  protected def visitBlock(node: BlockConstruction, statements: Seq[IntermediateNode]): Unit = {
    printWithSeparator(node.beforeStatements ++ statements, "\n", "", "\n")
  }

  protected def visitClass(c: ClassConstruction, name: IntermediateNode, primaryConstructor: Option[IntermediateNode],
                           bodyElements: Seq[IntermediateNode], modifiers: IntermediateNode,
                           typeParams: Option[Seq[IntermediateNode]], initializers: Option[Seq[IntermediateNode]],
                           classType: ClassType, companion: IntermediateNode, extendsList: Option[Seq[IntermediateNode]]): Unit = {
    visitClassHeader()
    printBodyWithBraces(c)(visitClassBody())

    def visitClassHeader(): Unit = {
      if (companion.isInstanceOf[ClassConstruction]) {
        visit(companion)
        printer.appendNewLine()
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

      if (primaryConstructor.isDefined) {
        printer.appendSpace()
        visit(primaryConstructor.get)
      }

      if (extendsList.isDefined && extendsList.get.nonEmpty) {
        printer.append(" extends ")

        visit(extendsList.get.head)
        if (primaryConstructor.isDefined) {
          val pc = primaryConstructor.get.asInstanceOf[PrimaryConstruction]
          if (pc.superCall != null) visit(pc.superCall)
        }
        if (extendsList.get.tail.nonEmpty) printer.append(" with ")
        printWithSeparator(extendsList.get.tail, " with ")
      }
    }

    def visitClassBody(): Unit = {
      if (primaryConstructor.isDefined) {
        val pc = primaryConstructor.get.asInstanceOf[PrimaryConstruction]
        if (pc.body.isDefined) {
          printWithSeparator(pc.body.get, "\n", "", "\n")
        }
      }
      printWithSeparator(bodyElements, "\n", "", "\n", bodyElements.nonEmpty)
      if (initializers.isDefined) printWithSeparator(initializers.get, "\n", "\ntry ", "\n", initializers.get.nonEmpty)
    }
  }

  protected def visitAnonymousClass(ac: AnonymousClass, mType: IntermediateNode, args: IntermediateNode, body: Seq[IntermediateNode],
                                    extendsList: Seq[IntermediateNode]): Unit = {
    visit(mType)
    printer.appendLeftParenthesis()
    visit(args)
    printer.appendRightParenthesis()

    if (extendsList != null && extendsList.nonEmpty) {
      printer.appendSpace()
        .append("with")
        .appendSpace()
      printWithSeparator(extendsList, " with ")
    }

    printBodyWithBraces(ac) {
      printWithSeparator(body, " ")
    }
  }

  protected def visitEnum(e: Enum, name: IntermediateNode, modifiers: IntermediateNode, members: Seq[IntermediateNode]): Unit = {
    visit(modifiers)
    printer.append("object ")
    visit(name)
    printer.append(" extends Enumeration ")

    def visitEnumBody(): Unit = {
      printer.append("type ")
      visit(name)
      printer.append(" = Value")
        .appendNewLine()

      val enumConstants = members.collect { case el: EnumConstruction => el }
      if (enumConstants.nonEmpty) {
        printer.append("val ")
        printWithSeparator(enumConstants, ",")
        printer.append(" = Value")
          .appendNewLine()
      }

      members.filter(!_.isInstanceOf[EnumConstruction]).foreach(visit)
    }

    printBodyWithBraces(e)(visitEnumBody())
  }

  protected def visitArrayAccess(expression: IntermediateNode, idxExpression: IntermediateNode): Unit = {
    visit(expression)
    printer.appendLeftParenthesis()
    visit(idxExpression)
    printer.appendRightParenthesis()
  }

  protected def visitCastType(c: ClassCast, operand: IntermediateNode, castType: IntermediateNode, isPrimitive: Boolean): Unit = {
    visit(operand)
    if (c.canSimplify) {
      printer.append(".to")
      visit(castType)
    } else {
      printer.append(".asInstanceOf")
        .appendLeftBracket()
      visit(castType)
      printer.appendRightBracket()
    }
  }

  protected def visitArrayInitializer(expressions: Seq[IntermediateNode]): Unit = {
    printWithSeparator(expressions, ", ", "Array(", ")")
  }

  protected def visitBinary(firstPart: IntermediateNode, secondPart: IntermediateNode, operation: String, inExpression: Boolean): Any = {
    val specialOperations = Seq("eq", "ne")
    if (inExpression && specialOperations.contains(operation)) printer.appendLeftParenthesis()
    visit(firstPart)
    printer.appendSpace()
      .append(operation)
      .appendSpace()
    visit(secondPart)
    if (inExpression && specialOperations.contains(operation)) printer.appendRightParenthesis()
  }

  protected def visitClassObjAccess(expression: IntermediateNode): Unit = {
    printer.append("classOf")
      .appendLeftBracket()
    visit(expression)
    printer.appendRightBracket()
  }

  protected def visitInstanceOf(operand: IntermediateNode, mtype: IntermediateNode): Unit = {
    visit(operand)
    printer.append(".isInstanceOf")
      .appendLeftBracket()
    visit(mtype)
    printer.appendRightBracket()
  }

  protected def visitQualifiedExpression(qualifier: IntermediateNode, identifier: IntermediateNode): Unit = {
    if (qualifier != null) {
      visit(qualifier)
      visit(identifier)
    }
  }

  protected def visitMethodCall(name: String, method: IntermediateNode, args: IntermediateNode, withSideEffects: Boolean): Any = {
    visit(method)
    if (args != null)
      visit(args)
    if (withSideEffects) printer.append("()")
  }

  protected def visitExpressionList(data: Seq[IntermediateNode]): Unit = {
    printWithSeparator(data, ", ", "(", ")", data.nonEmpty)
  }

  protected def visitWithExtraWord(value: Option[IntermediateNode], word: String): Unit = {
    printer.append(word)
    if (value.isDefined) visit(value.get)
  }

  protected def visitParenthesizedExpression(value: Option[IntermediateNode]): Unit = {
    printer.appendLeftParenthesis()
    if (value.isDefined) visit(value.get)
    printer.appendRightParenthesis()
  }

  protected def visitNewExpression(mtype: IntermediateNode, arrayInitializer: Seq[IntermediateNode],
                                   arrayDimension: Seq[IntermediateNode]): Unit = {
    if (arrayInitializer.nonEmpty) {
      visit(mtype)
      printWithSeparator(arrayInitializer, ", ", "(", ")")
    } else {
      printer.append("new ")
      visit(mtype)
      printWithSeparator(arrayDimension, ", ", "(", ")",
        arrayDimension != null && arrayDimension.nonEmpty &&
          !arrayDimension.head.isInstanceOf[ExpressionList] && arrayDimension.head != LiteralExpression("()"))
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
      case BlockConstruction(_) =>
        def constructHelperFunction(): Unit = {
          printer.appendLeftBrace()
            .appendNewLine()
            .append("def")
            .appendSpace()
            .append("foo")
          visit(params)
          printer.appendSpace()
            .append("=")
            .appendSpace()
            .appendNewLine()
          printBodyWithBraces(body)(visit(body))
          printer.appendNewLine()
        }

        def constructFunctionCall(): Unit = {
          val pNames: Seq[IntermediateNode] = params.asInstanceOf[ParameterListConstruction].list.collect {
            case p: ParameterConstruction => p
          }.map(_.name)

          printer.append("foo")
          printWithSeparator(pNames, ",", "(", ")")
          printer.append("} ")
        }

        constructHelperFunction()
        constructFunctionCall()
      case _ => visit(body)
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
          printer.appendLeftBrace()
          visit(operand)
          printer.append(" += 1; ")
          visit(operand)
          if (isPostfix) printer.append(" - 1")
          printer.appendRightBrace()
        } else {
          visit(operand)
          printer.append(" += 1")
        }
      case "--" =>
        if (!canBeSimplified) {
          printer.appendLeftBrace()
          visit(operand)
          printer.append(" -= 1; ")
          visit(operand)
          if (isPostfix) printer.append(" + 1")
          printer.appendRightBrace()
        } else {
          visit(operand)
          printer.append(" -= 1")
        }
      case _ if !isPostfix =>
        printer.append(signType)
        printer.appendLeftParenthesis()
        visit(operand)
        printer.appendRightParenthesis()
    }
  }

  protected def visitVariable(modifiers: IntermediateNode, name: IntermediateNode,
                              ftype: IntermediateNode, isVar: Boolean,
                              initializer: Option[IntermediateNode]): Any = {
    visit(modifiers)

    if (isVar) {
      printer.append("var")
    } else {
      printer.append("val")
    }
    printer.appendSpace()
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

  protected def visitConstructor(modifiers: IntermediateNode, typeParams: Seq[IntermediateNode],
                                 params: Seq[IntermediateNode], body: Option[IntermediateNode]): Unit = {
    printer.append("def")
      .appendSpace()
      .append("this")
    if (typeParams.nonEmpty) {
      printWithSeparator(typeParams, ", ", "[", "]")
    }

    printWithSeparator(params, ", ", "(", ")", params.nonEmpty)

    body.foreach { b =>
      printBodyWithBraces(b)(visit(b))
    }
  }

  protected def visitMethod(modifiers: IntermediateNode, name: IntermediateNode, typeParams: Seq[IntermediateNode],
                            params: Seq[IntermediateNode], body: Option[IntermediateNode], retType: Option[IntermediateNode]): Unit = {
    visit(modifiers)
    printer.append("def ")
    visit(name)

    if (typeParams.nonEmpty) {
      printWithSeparator(typeParams, ", ", "[", "]")
    }

    printWithSeparator(params, ", ", "(", ")", params.nonEmpty || (retType.contains(TypeConstruction("Unit")) || retType.isEmpty))

    retType.foreach { rt =>
      printer.append(": ")
      visit(rt)
    }

    body.foreach { b =>
      if (retType.isDefined) printer.append(" = ")
      printBodyWithBraces(b)(visit(b))
    }
  }

  protected def visitPrimaryConstructor(params: Seq[IntermediateNode], superCall: IntermediateNode, body: Option[Seq[IntermediateNode]],
                                        modifiers: IntermediateNode): Unit = {
    visit(modifiers)
    printer.appendSpace()
      .appendLeftParenthesis()
    printWithSeparator(params, ", ")
    printer.appendSpace()
      .appendRightParenthesis()
  }

  protected def visitModifiers(modifiersConstruction: ModifiersConstruction, annotations: Seq[IntermediateNode], modifiers: Seq[IntermediateNode]): Unit = {
    for (a <- annotations) {
      visit(a)
      printer.appendSpace()
    }

    //to prevent situation where access modifiers print earlier then throw
    val sortModifiers = modifiers.collect { case m: Modifier if !modifiersConstruction.accessModifiers.contains(m.modificator) => m } ++
      modifiers.collect { case m: Modifier if modifiersConstruction.accessModifiers.contains(m.modificator) => m }

    for (m <- sortModifiers) {
      if (!modifiersConstruction.withoutList.contains(m.asInstanceOf[Modifier].modificator)) {
        visit(m)
        printer.appendSpace()
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

  protected def visitModifierWithExpr(mtype: ModifierType, value: IntermediateNode): Any = {
    mtype match {
      case THROW =>
        printer.append("@throws")
          .appendLeftBracket()
        visit(value)
        printer.appendRightBracket()
          .appendNewLine()
      case SerialVersionUID =>
        printer.append("@SerialVersionUID(")
        visit(value)
        printer.appendRightParenthesis()
          .appendNewLine()
      case PRIVATE =>
        printer.append("private")
          .appendLeftBracket()
        visit(value)
        printer.appendRightBracket()
          .appendSpace()
      case _ =>
    }
  }

  protected def visitParameters(modifiers: IntermediateNode, name: IntermediateNode,
                                scCompType: IntermediateNode, isVar: Option[Boolean], isArray: Boolean): Any = {
    def visitDisjunctionType(disjunctionTypeConstructions: DisjunctionTypeConstructions): Unit = {
      visit(name)
      printer.append("@").appendLeftParenthesis()

      val iterator = disjunctionTypeConstructions.parts.iterator
      while (iterator.hasNext) {
        printer.append("_:").appendSpace()
        visit(iterator.next())
        if (iterator.hasNext) printer.appendSpace().append("|").appendSpace()
      }

      printer.appendRightParenthesis()
    }

    visit(modifiers)
    if (isVar.isDefined) {
      printer.append(if (isVar.get) "var" else "val")
        .appendSpace()
    }

    scCompType match {
      case disjuncit: DisjunctionTypeConstructions => visitDisjunctionType(disjuncit)
      case _ =>
        visit(name)

        if (!scCompType.isInstanceOf[EmptyConstruction]) {
          printer.append(":").appendSpace()
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

    printer.append("if")
    printer.appendSpace()

    printer.appendLeftParenthesis()
    if (condition.isDefined) visit(condition.get)
    printer.appendRightParenthesis()
    printer.appendSpace()


    thenBranch.foreach { t =>
      printBodyWithBraces(t)(visit(t))
    }

    elseBranch.foreach { e =>
      printer.appendNewLine()
      printer.append("else")
      printer.appendSpace()
      printBodyWithBraces(e)(visit(e))
    }
  }

  protected def visitAssert(condition: Option[IntermediateNode], description: Option[IntermediateNode]): Unit = {
    printer.append("assert").appendSpace().appendLeftParenthesis()

    if (condition.isDefined) visit(condition.get)
    if (description.isDefined) {
      printer.append(",").appendSpace()
      visit(description.get)
    }
    printer.appendRightParenthesis()
  }

  protected def visitImportStatement(importValue: IntermediateNode, onDemand: Boolean): Any = {
    printer.append("import ")
    visit(importValue)
    if (onDemand) {
      printer.append("._")
    }
  }

  protected def visitImportStatementList(imports: Seq[IntermediateNode]): Unit = {
    for (importNods <- imports) {
      visit(importNods)
      printer.appendNewLine()
    }
  }

  protected def visitWhile(w: WhileStatement, initialization: Option[IntermediateNode], condition: Option[IntermediateNode],
                           body: Option[IntermediateNode], update: Option[IntermediateNode], whileType: Int): Unit = {
    def printDoWhile(): Unit = {
      printer.append("do {")
        .appendNewLine()

      body.foreach { b =>
        printBodyWithBraces(b)(visit(b))
      }

      printer.appendNewLine()
        .appendRightBrace()
      if (update.isDefined) {
        printer.appendNewLine()
        visit(update.get)
      }
      printer.append("while")
      printer.appendSpace()
      printer.appendLeftParenthesis()

      condition.foreach { c =>
        printBodyWithBraces(c)(visit(c))
      }

      printer.appendRightParenthesis()
    }

    def printWhile(): Unit = {
      printer.append("while")
      printer.appendSpace()
      printer.appendLeftParenthesis()

      condition.foreach { c =>
        printBodyWithBraces(c)(visit(c))
      }

      printer.appendRightParenthesis()
      printer.appendSpace()
      printBodyWithBraces(w) {
        body.foreach { b =>
          visit(b)
        }

        update.foreach { u =>
          printer.appendNewLine()
          visit(u)
        }
      }
    }

    initialization.foreach { i =>
      visit(i)
      printer.appendNewLine()
    }

    if (whileType == WhileStatement.PRE_TEST_LOOP) printWhile()
    else if (whileType == WhileStatement.POST_TEST_LOOP) printDoWhile()
  }

  protected def visitTryCatch(resourcesList: Seq[(String, IntermediateNode)], tryBlock: Seq[IntermediateNode],
                              catchStatements: Seq[(IntermediateNode, IntermediateNode)],
                              finallyStatements: Option[Seq[IntermediateNode]], arrow: String): Any = {
    if (resourcesList != null && resourcesList.nonEmpty) {
      printer.append("try")
        .appendSpace()
        .appendLeftBrace()
        .appendNewLine()
      printWithSeparator(resourcesList.map(_._2), "\n", "", "\n")
    }

    printer.append("try ")
    tryBlock match {
      case Seq() => printer.append("{\n}")
      case Seq(stmt) =>
        visit(stmt)
        printer.appendNewLine()
      case stmts =>
        printer.appendLeftBrace()
        printWithSeparator(stmts, "\n")
        printer.appendRightBrace()
    }

    if (catchStatements.nonEmpty) {
      printer.appendSpace()
        .append("catch")
        .appendSpace()
        .appendLeftBrace()
        .appendNewLine()
      catchStatements.foreach { case (parameter, block) =>
        printer.append("case ")
        visit(parameter)
        printer.append(s" $arrow ")
        printer.appendNewLine()
        visit(block)
      }
      printer.appendRightBrace()
    }

    if (finallyStatements.isDefined) {
      if (resourcesList == null) {
        printer.append(" finally ")
        printWithSeparator(finallyStatements.get, "\n")
      } else {
        printer.appendSpace()
          .append("finally")
          .appendSpace()
          .appendLeftBrace()
          .appendNewLine()
        printWithSeparator(finallyStatements.get, "\n", "", "\n")
        resourcesList.foreach {
          case (name: String, _: IntermediateNode) =>
            val cname = escapeKeyword(name)
            printer.append(s"if ($cname != null) $cname.close()")
              .appendNewLine()
        }

        printer.appendRightBrace()
      }
    } else if (resourcesList.nonEmpty) {
      printer.appendSpace()
        .append("finally")
        .appendSpace()
        .appendLeftBrace()
        .appendNewLine()
      resourcesList.foreach {
        case (name: String, _: IntermediateNode) =>
          val cname = escapeKeyword(name)
          printer.append(s"if ($cname != null) $cname.close()")
            .appendNewLine()
      }
      printer.appendRightBrace()
    }
    if (resourcesList.nonEmpty) {
      printer.append("\n}")
    }
  }

  protected def visitSwitchStatement(expression: Option[IntermediateNode], body: Option[IntermediateNode]): Unit = {
    expression.foreach(visit)
    printer.append(" match ")
    body.foreach { b =>
      printBodyWithBraces(b)(visit(b))
    }
  }

  protected def visitSwitchLabelStatement(caseValue: Option[IntermediateNode], arrow: String): Unit = {
    printer.append("case ")
    if (caseValue.isDefined) visit(caseValue.get)
    printer.append(s" $arrow ")
  }

  protected def visitNotSupported(iNode: Option[IntermediateNode], msg: String): Unit = {
    printer.append(msg)
    if (iNode.isDefined) {
      printer.appendNewLine()
      visit(iNode.get)
    }
  }

  protected def visitSynchronizedStatement(lock: Option[IntermediateNode], body: Option[IntermediateNode]): Unit = {
    if (lock.isDefined) visit(lock.get)
    printer.append(" synchronized ")
    if (body.isDefined) visit(body.get)
  }

  protected def visitExpressionListStatement(exprs: Seq[IntermediateNode]): Unit = {
    printWithSeparator(exprs, "\n")
  }

  protected def visitForEach(iterParamName: IntermediateNode, iteratedValue: Option[IntermediateNode],
                             body: Option[IntermediateNode], isJavaCollection: Boolean): Unit = {
    if (isJavaCollection) {
      printer.append("import scala.collection.JavaConversions._")
        .appendNewLine()
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

    this (statement) = escapedName
    printer.append(escapedName)

    if (parameterList.isDefined) visit(parameterList.get)
  }

  protected def visitType(t: TypeConstruction, inType: String): Unit = {
    this (t) = inType
    printer.append(inType)
  }

  protected def visitArrayType(iNode: IntermediateNode): Unit = {
    printer.append("Array")
      .appendLeftBracket()
    visit(iNode)
    printer.appendRightBracket()
  }

  protected def visitParametrizedType(iNode: IntermediateNode, parts: Seq[IntermediateNode]): Unit = {
    visit(iNode)
    printWithSeparator(parts, ", ", "[", "]", parts.nonEmpty)
  }

  protected def printWithSeparator(seq: Seq[IntermediateNode], separator: String): Unit = {
    if (seq != null && seq.nonEmpty) {
      val it = seq.iterator
      while (it.hasNext) {
        visit(it.next())
        if (it.hasNext) printer.append(separator)
      }
    }
  }

  protected def printWithSeparator(seq: Seq[IntermediateNode], separator: String, before: String, after: String, needAppend: Boolean): Unit = {
    if (needAppend) printer.append(before)
    printWithSeparator(seq, separator)
    if (needAppend) printer.append(after)
  }

  protected def printWithSeparator(seq: Seq[IntermediateNode], separator: String, before: String, after: String): Unit = {
    printWithSeparator(seq, separator, before, after, needAppend = true)
  }

  protected def visitTypeParameters(data: Seq[IntermediateNode]): Unit = {
    printWithSeparator(data, ", ", "[", "]", data.nonEmpty)
  }

  protected def visitTypeParameterConstruction(name: IntermediateNode, types: Seq[IntermediateNode]): Unit = {
    visit(name)
    if (types.nonEmpty) {
      printer.appendSpace()
        .append("<:")
        .appendSpace()
      printWithSeparator(types, " with ")
    }
  }

  protected def printBodyWithBraces(node: IntermediateNode)
                                   (printBodyFunction: => Unit): Unit = {
    printer.appendSpace()
      .appendLeftBrace()
      .appendSpace()
    printBodyFunction
    printer.appendRightBrace()
  }
}

object SimplePrintVisitor {

  //noinspection TypeAnnotation
  private implicit class StringBuilderExt(private val builder: mutable.StringBuilder) extends AnyVal {

    def appendSpace() = this (' ')

    def appendNewLine() = this ('\n')

    def appendLeftParenthesis() = this ('(')

    def appendRightParenthesis() = this (')')

    def appendLeftBrace() = this ('{')

    def appendRightBrace() = this ('}')

    def appendLeftBracket() = this ('[')

    def appendRightBracket() = this (']')

    private def apply(char: Char) = builder.append(char)
  }

}
