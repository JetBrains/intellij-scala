package org.jetbrains.plugins.scala.conversion.visitors

import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.conversion.ast.ClassConstruction.ClassType
import org.jetbrains.plugins.scala.conversion.ast.ClassConstruction.ClassType.ClassType
import org.jetbrains.plugins.scala.conversion.PrettyPrinter
import org.jetbrains.plugins.scala.conversion.ast._
import org.jetbrains.plugins.scala.conversion.ast.ModifierType.ModifierType

/**
  * Created by Kate Ustyuzhanina
  * on 11/24/15
  */
class SimplePrintVisitor extends IntermediateTreeVisitor {
  override def visit(node: IntermediateNode): Unit = {
    node match {
      case m: MainConstruction => m.children.foreach(visit)
      case t@TypeConstruction(inType) => visitType(t, inType)
      case ParametrizedConstruction(inType, parts) => visitParametrizedType(inType, parts)
      case ArrayConstruction(inType) => visitArrayType(inType)
      case TypeParameters(data) => visitTypeParameters(data)
      case TypeParameterConstruction(name, typez) => visitTypeParameterConstruction(name, typez)
      case AnnotaionConstruction(inAnnotation, attributes, name) => visitAnnotation(inAnnotation, attributes, name)
      case b@BlockConstruction(statements) => visitBlock(b, statements)
      case ClassConstruction(name, primaryConstructor, bodyElements, modifiers, typeParams, initalizers, classType,
      companion, extendsList) => visitClass(name, primaryConstructor, bodyElements,
        modifiers, typeParams, initalizers, classType, companion, extendsList)
      case AnonymousClass(mType, args, body, extendsList) => visitAnonymousClass(mType, args, body, extendsList)
      case Enum(name, modifiers, enumConstants: Seq[String]) => visitEnum(name, modifiers, enumConstants)
      case ArrayAccess(expression, idxExpression) => visitArrayAccess(expression, idxExpression)
      case c@ClassCast(operand, castType, isPrimitive) => visitCastType(c, operand, castType, isPrimitive)
      case ArrayInitializer(expresions: Seq[IntermediateNode]) => visitArrayInitalizer(expresions)
      case BinaryExpressionConstruction(firstPart, secondPart, operation: String) =>
        visitBinary(firstPart, secondPart, operation)
      case ClassObjectAccess(expression) => visitClassObjAccess(expression)
      case InstanceOfConstruction(operand, mtype) => visitInstanceOf(operand, mtype)
      case QualifiedExpression(qualifier, identifier) => visitQualifiedExpression(qualifier, identifier)
      case MethodCallExpression(name, method, args) => visitMethodCall(name, method, args)
      case ExpressionList(data) => visitExpressionList(data)
      case ThisExpression(value) => visitWithExtraWord(value, "this")
      case SuperExpression(value) => visitWithExtraWord(value, "super")
      case LiteralExpression(literal) => printer.append(literal)
      case ParenthesizedExpression(value) => visitParenthizedExpression(value)
      case NewExpression(mtype, arrayInitalizer, arrayDimension) =>
        visitNewExpression(mtype, arrayInitalizer, arrayDimension)
      case AnonymousClassExpression(anonymousClass) => visitAnonimousClassExpression(anonymousClass)
      case PolyadicExpression(args, operation) => visitPoliadic(args, operation)
      case PrefixExpression(operand, signType, canBeSimplified) => visitPrefixPostfix(operand, signType, canBeSimplified)
      case PostfixExpression(operand, signType, canBeSimplified) =>
        visitPrefixPostfix(operand, signType, canBeSimplified, isPostfix = true)
      case FieldConstruction(modifiers, name, ftype, isVar, initalaizer) =>
        visitVariable(modifiers, name, ftype, isVar, initalaizer)
      case LocalVariable(modifiers, name, ftype, isVar, initalaizer) =>
        visitVariable(modifiers, name, ftype, isVar, initalaizer)
      case ConstructorSimply(modifiers, typeParams, params, body) =>
        visitConstructor(modifiers, typeParams, params, body)
      case PrimaryConstruction(params, superCall, body, modifiers) =>
        visitPrimaryConstructor(params, superCall, body, modifiers)
      case MethodConstruction(modifiers, name, typeParams, params, body, retType) =>
        visitMethod(modifiers, name, typeParams, params, body, retType)
      case m@ModifiersConstruction(annotations, modifiers) => visitModifiers(m, annotations, modifiers)
      case SimpleModifier(mtype: ModifierType) => visitSimpleModifier(mtype)
      case ModifierWithExpression(mtype, value) => visitModifierWithExpr(mtype, value)
      case ParameterConstruction(modifiers, name, scCompType, isArray) =>
        visitParameters(modifiers, name, scCompType, isArray)
      case ParameterListConstruction(list) => visitParameterList(list)
      //statements
      case r@JavaCodeReferenceStatement(qualifier, parametrList, name) =>
        visitJavaCodeRef(r, qualifier, parametrList, name)
      case IfStatement(condition, thenBranch, elseBranch) => visitIfStatement(condition, thenBranch, elseBranch)
      case ReturnStatement(value) => visitWithExtraWord(Some(value), "return ")
      case ThrowStatement(value) => visitWithExtraWord(Some(value), "throw ")
      case AssertStatement(condition, description) => visitAssert(condition, description)
      case ImportStatement(importValue, onDemand) => visitImportStatement(importValue, onDemand)
      case ImportStatementList(data) => visitImportStatementList(data)
      case PackageStatement(value) => visitWithExtraWord(Some(value), "package ")
      case ForeachStatement(iterParamName, iteratedValue, body, isJavaCollection) =>
        visitForEach(iterParamName, iteratedValue, body, isJavaCollection)
      case WhileStatement(initialization, condition, body, update, whileType) =>
        visitWhile(initialization, condition, body, update, whileType)
      case TryCatchStatement(resourcesList, tryBlock, catchStatements, finallyStatements, arrow) =>
        visitTryCatch(resourcesList, tryBlock, catchStatements, finallyStatements, arrow)
      case SwitchStatemtnt(expession, body) => visitSwitchStatement(expession, body)
      case SwitchLabelStatement(caseValue, arrow) => visitSwitchLabelStatement(caseValue, arrow)
      case SynchronizedStatement(lock, body) => visitSynchronizedStatement(lock, body)
      case ExpressionListStatement(exprs) => visitExpressionListStatement(exprs)
      case NotSupported(n, msg) => visitNotSupported(n, msg)
      case EmptyConstruction() =>
    }
  }

  def visitAnnotation(inAnnotation: Boolean, attributes: Seq[(Option[String], Option[IntermediateNode])],
                      name: Option[IntermediateNode]): PrettyPrinter = {
    if (inAnnotation) {
      printer.append("new ")
    } else {
      printer.append("@")
    }

    if (name.isDefined) {
      name.get match {
        case deprecated: JavaCodeReferenceStatement if deprecated.name == "Deprecated" =>
          printer.append(deprecated.name.toLowerCase)
        case otherName => visit(otherName)
      }
    }

    if (attributes.nonEmpty) {
      printer.append("(")

      for ((name, value) <- attributes) {
        if (name.isDefined) {
          printer.append(name.get)
          printer.append(" = ")
        }

        if (value.isDefined) {
          visit(value.get)
          printer.append(", ")
        }
      }

      printer.delete(2)
      printer.append(")")
    }
    printer.space()
  }

  def visitBlock(node: BlockConstruction, statements: Seq[IntermediateNode]): PrettyPrinter = {
    printer.append("{\n")
    printWithSeparator(node.beforeStatements.toSeq, "\n", "", "\n", node.beforeStatements.nonEmpty)
    printWithSeparator(statements, "\n", "", "\n", statements.nonEmpty)
    printer.append("}")
  }

  def visitClass(name: String, primaryConstructor: Option[IntermediateNode], bodyElements: Seq[IntermediateNode],
                 modifiers: IntermediateNode, typeParams: Option[Seq[IntermediateNode]],
                 initalizers: Option[Seq[IntermediateNode]], classType: ClassType, companion: IntermediateNode,
                 extendsList: Option[Seq[IntermediateNode]]): PrettyPrinter = {
    if (companion.isInstanceOf[ClassConstruction]) {
      visit(companion)
      printer.newLine()
    }

    visit(modifiers)
    printer.append(classType match {
      case ClassType.CLASS => "class "
      case ClassType.OBJECT => "object "
      case ClassType.INTERFACE => "trait "
      case _ => ""
    })

    printer.append(escapeKeyword(name))
    if (typeParams.isDefined) printWithSeparator(typeParams.get, ", ", "[", "]", typeParams.get.nonEmpty)

    if (primaryConstructor.isDefined) {
      printer.space()
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

    printer.append(" { ")
    if (primaryConstructor.isDefined) {
      val pc = primaryConstructor.get.asInstanceOf[PrimaryConstruction]
      if (pc.body != null) {
        printWithSeparator(pc.body, "\n", "", "\n")
      }
    }
    printWithSeparator(bodyElements, "\n", "", "")
    if (initalizers.isDefined) printWithSeparator(initalizers.get, "\n", "\ntry ", "\n", initalizers.get.nonEmpty)
    printer.append("}")
  }

  def visitAnonymousClass(mType: IntermediateNode, args: IntermediateNode, body: Seq[IntermediateNode],
                          extendsList: Seq[IntermediateNode]) = {
    visit(mType)
    printer.append("(")
    visit(args)
    printer.append(")")

    if (extendsList != null && extendsList.nonEmpty) {
      printer.append(" with ")
      printWithSeparator(extendsList, " with ")
    }
    printWithSeparator(body, " ", "{ ", "}")
  }

  def visitEnum(name: String, modifiers: IntermediateNode, enumConstants: Seq[String]): PrettyPrinter = {
    visit(modifiers)
    printer.append("object ")
    printer.append(escapeKeyword(name))
    printer.append(" extends Enumeration {\n")

    printer.append("type ")
    printer.append(escapeKeyword(name))
    printer.append(" = Value\n")

    if (enumConstants.nonEmpty) {
      printer.append("val ")
      for (str <- enumConstants) {
        printer.append(str)
        printer.append(", ")
      }
      printer.delete(2)
      printer.append(" = Value")
    }
    printer.append("\n}")
  }

  def visitArrayAccess(expression: IntermediateNode, idxExpression: IntermediateNode): PrettyPrinter = {
    visit(expression)
    printer.append("(")
    visit(idxExpression)
    printer.append(")")
  }

  def visitCastType(c: ClassCast, operand: IntermediateNode, castType: IntermediateNode, isPrimitive: Boolean): PrettyPrinter = {
    visit(operand)
    if (c.canSimplify) {
      printer.append(".to")
      visit(castType)
    } else {
      printer.append(".asInstanceOf[")
      visit(castType)
      printer.append("]")
    }
    printer
  }

  def visitArrayInitalizer(expresions: Seq[IntermediateNode]) = {
    printWithSeparator(expresions, ", ", "Array(", ")")
  }

  def visitBinary(firstPart: IntermediateNode, secondPart: IntermediateNode, operation: String) = {
    visit(firstPart)
    printer.append(" ")
    printer.append(operation)
    printer.append(" ")
    visit(secondPart)
  }

  def visitClassObjAccess(expression: IntermediateNode) = {
    printer.append("classOf[")
    visit(expression)
    printer.append("]")
  }

  def visitInstanceOf(operand: IntermediateNode, mtype: IntermediateNode) = {
    visit(operand)
    printer.append(".isInstanceOf[")
    visit(mtype)
    printer.append("]")
  }

  def visitQualifiedExpression(qualifier: IntermediateNode, identifier: IntermediateNode) = {
    if (qualifier != null) {
      visit(qualifier)
      visit(identifier)
    }
    printer
  }

  def visitMethodCall(name: String, method: IntermediateNode, args: IntermediateNode) = {
    visit(method)
    if (args != null)
      visit(args)
    printer
  }

  def visitExpressionList(data: Seq[IntermediateNode]) = {
    printWithSeparator(data, ", ", "(", ")", data.nonEmpty)
  }

  def visitWithExtraWord(value: Option[IntermediateNode], word: String) = {
    printer.append(word)
    if (value.isDefined) visit(value.get)
  }

  def visitParenthizedExpression(value: Option[IntermediateNode]) = {
    printer.append("(")
    if (value.isDefined) visit(value.get)
    printer.append(")")
  }

  def visitNewExpression(mtype: IntermediateNode, arrayInitalizer: Seq[IntermediateNode],
                         arrayDimension: Seq[IntermediateNode]) = {
    if (arrayInitalizer.nonEmpty) {
      visit(mtype)
      printWithSeparator(arrayInitalizer, ", ", "(", ")")
    } else {
      printer.append("new ")
      visit(mtype)
      printWithSeparator(arrayDimension, ", ", "(", ")",
        arrayDimension != null && arrayDimension.nonEmpty && !arrayDimension.head.isInstanceOf[ExpressionList])
    }
  }

  def visitAnonimousClassExpression(anonClass: IntermediateNode) = {
    printer.append("new ")
    visit(anonClass)
  }

  def visitPoliadic(args: Seq[IntermediateNode], operation: String) = {
    printWithSeparator(args, " " + operation + " ")
  }

  def visitPrefixPostfix(operand: IntermediateNode, signType: String,
                         canBeSimplified: Boolean, isPostfix: Boolean = false): Unit = {
    signType match {
      case "++" =>
        if (!canBeSimplified) {
          printer.append("({")
          visit(operand)
          printer.append(" += 1; ")
          visit(operand)
          if (isPostfix) printer.append(" - 1")
          printer.append("})")
        } else {
          visit(operand)
          printer.append(" += 1")
        }
      case "--" =>
        if (!canBeSimplified) {
          printer.append("({")
          visit(operand)
          printer.append(" -= 1; ")
          visit(operand)
          if (isPostfix) printer.append(" + 1")
          printer.append("})")
        } else {
          visit(operand)
          printer.append(" -= 1")
        }
      case _ if !isPostfix =>
        printer.append(signType)
        visit(operand)
    }
  }

  def visitVariable(modifiers: IntermediateNode, name: String,
                    ftype: IntermediateNode, isVar: Boolean,
                    initalaizer: Option[IntermediateNode]) = {
    visit(modifiers)

    if (isVar) {
      printer.append("var")
    } else {
      printer.append("val")
    }
    printer.space()
    printer.append(escapeKeyword(name))
    printer.append(": ")
    visit(ftype)
    printer.append(" = ")
    if (initalaizer.isDefined) {
      visit(initalaizer.get)
    } else {
      printer.append(ftype match {
        case tc: TypeConstruction => tc.getDefaultTypeValue
        case _ => "null"
      })
    }
  }

  def visitConstructor(modifiers: IntermediateNode, typeParams: Seq[IntermediateNode],
                       params: IntermediateNode, body: Option[IntermediateNode]) = {
    printer.append("def ")
    printer.append("this")
    if (typeParams.nonEmpty) {
      printWithSeparator(typeParams, ", ", "[", "]")
    }

    visit(params)
    if (body.isDefined) visit(body.get)
  }

  def visitMethod(modifiers: IntermediateNode, name: String, typeParams: Seq[IntermediateNode],
                  params: IntermediateNode, body: Option[IntermediateNode], retType: IntermediateNode) = {
    visit(modifiers)
    printer.append("def ")
    printer.append(escapeKeyword(name))

    if (typeParams.nonEmpty) {
      printWithSeparator(typeParams, ", ", "[", "]")
    }


    visit(params)
    if (retType != null) {
      printer.append(": ")
      visit(retType)
    }

    if (body.isDefined) {
      if (retType != null) printer.append(" = ")
      visit(body.get)
    }
  }

  def visitPrimaryConstructor(params: Seq[(String, IntermediateNode, Boolean)], superCall: IntermediateNode, body: Seq[IntermediateNode],
                              modifiers: IntermediateNode) = {
    visit(modifiers)
    printer.space()
    if (params.nonEmpty) {
      printer.append("(")
      for ((param, ftype, isVar) <- params) {
        if (isVar)
          printer.append("var ")
        else
          printer.append("val ")
        printer.append(param)
        printer.append(": ")
        visit(ftype)
        printer.append(", ")

      }
      printer.delete(2)
      printer.append(")")
    }

    printer.space()
  }

  def visitModifiers(modifiersConstruction: ModifiersConstruction, annotations: Seq[IntermediateNode], modifiers: Seq[IntermediateNode]) = {
    for (a <- annotations) {
      visit(a)
      printer.space()
    }

    //to prevent situation where access modifiers print earlier then throw
    val sortModifiers = modifiers.collect { case m: Modifier if !modifiersConstruction.accessModifiers.contains(m.modificator) => m } ++
      modifiers.collect { case m: Modifier if modifiersConstruction.accessModifiers.contains(m.modificator) => m }

    for (m <- sortModifiers) {
      if (!modifiersConstruction.withoutList.contains(m.asInstanceOf[Modifier].modificator)) {
        visit(m)
        printer.space()
      }
    }
  }

  def visitSimpleModifier(mtype: ModifierType) = {
    printer.append(mtype match {
      case ModifierType.ABSTRACT => "abstract"
      case ModifierType.PUBLIC => "public"
      case ModifierType.PROTECTED => "protected"
      case ModifierType.PRIVATE => "private"
      case ModifierType.OVERRIDE => "override"
      case ModifierType.FINAL => "final"
      case _ => ""
    })
  }

  def visitModifierWithExpr(mtype: ModifierType, value: IntermediateNode) = {
    mtype match {
      case ModifierType.THROW =>
        printer.append("@throws[")
        visit(value)
        printer.append("]\n")
      case ModifierType.SerialVersionUID =>
        printer.append("@SerialVersionUID(")
        visit(value)
        printer.append(")\n")
      case ModifierType.PRIVATE =>
        printer.append("private[")
        visit(value)
        printer.append("] ")
      case _ =>
    }
  }

  def visitParameters(modifiers: IntermediateNode, name: String,
                      scCompType: IntermediateNode, isArray: Boolean) = {
    visit(modifiers)
    printer.append(escapeKeyword(name))
    printer.append(": ")
    visit(scCompType)
    if (isArray) {
      printer.append("*")
    }
  }

  def visitParameterList(list: Seq[IntermediateNode]) = {
    printWithSeparator(list, ", ", "(", ")", list.nonEmpty)
  }


  def visitIfStatement(condition: Option[IntermediateNode], thenBranch: Option[IntermediateNode],
                       elseBranch: Option[IntermediateNode]) = {

    printer.append("if")
    printer.space()

    printer.append("(")
    if (condition.isDefined) visit(condition.get)
    printer.append(")")
    printer.space()

    if (thenBranch.isDefined) visit(thenBranch.get)
    if (elseBranch.isDefined) {
      printer.newLine()
      printer.append("else")
      printer.space()
      visit(elseBranch.get)
    }
  }

  def visitAssert(condition: Option[IntermediateNode], description: Option[IntermediateNode]) = {
    printer.append("assert (")
    if (condition.isDefined) visit(condition.get)
    if (description.isDefined) {
      printer.append(", ")
      visit(description.get)
    }
    printer.append(")")
  }

  def visitImportStatement(importValue: IntermediateNode, onDemand: Boolean) = {
    printer.append("import ")
    visit(importValue)
    if (onDemand) {
      printer.append("._")
    }
  }

  def visitImportStatementList(imports: Seq[IntermediateNode]) = {
    for (iimport <- imports) {
      visit(iimport)
      printer.newLine()
    }
  }

  def visitWhile(initialization: Option[IntermediateNode], condition: Option[IntermediateNode],
                 body: Option[IntermediateNode], update: Option[IntermediateNode], whileType: Int) = {
    def printDoWhile(): PrettyPrinter = {
      printer.append("do {\n")
      if (body.isDefined) visit(body.get)
      printer.append("\n}")
      if (update.isDefined) {
        printer.newLine()
        visit(update.get)
      }
      printer.append("while")
      printer.space()
      printer.append("(")
      if (condition.isDefined) visit(condition.get)
      printer.append(")")
    }

    def printWhile(): PrettyPrinter = {
      printer.append("while")
      printer.space()
      printer.append("(")
      if (condition.isDefined) visit(condition.get)
      printer.append(")")
      printer.space()
      printer.append("{\n")
      if (body.isDefined) visit(body.get)
      if (update.isDefined) {
        printer.newLine()
        visit(update.get)
      }
      printer.append("\n}")
    }

    if (initialization.isDefined) {
      visit(initialization.get)
      printer.newLine()
    }

    if (whileType == WhileStatement.PRE_TEST_LOOP) {
      printWhile()
    } else if (whileType == WhileStatement.POST_TEST_LOOP) {
      printDoWhile()
    }
  }

  def visitTryCatch(resourcesList: Seq[(String, IntermediateNode)], tryBlock: Option[IntermediateNode],
                    catchStatements: Seq[(IntermediateNode, IntermediateNode)],
                    finallyStatements: Option[Seq[IntermediateNode]], arrow: String) = {
    if (resourcesList != null && resourcesList.nonEmpty) {
      printer.append("try {\n")
      printWithSeparator(resourcesList.map(_._2), "\n", "", "\n")
    }

    printer.append("try ")
    if (tryBlock.isDefined) visit(tryBlock.get)

    if (catchStatements.nonEmpty) {
      printer.append("\ncatch {\n")
      for ((parameter, block) <- catchStatements) {
        printer.append("case ")
        visit(parameter)
        printer.append(s" $arrow ")
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
          case (name: String, variable: IntermediateNode) =>
            val cname = escapeKeyword(name)
            printer.append(s"if ($cname != null) $cname.close()\n")
        }

        printer.append("}")
      }
    } else if (resourcesList.nonEmpty) {
      printer.append(" finally {\n")
      resourcesList.foreach {
        case (name: String, variable: IntermediateNode) =>
          val cname = escapeKeyword(name)
          printer.append(s"if ($cname != null) $cname.close()\n")
      }
      printer.append("}")
    }
    if (resourcesList.nonEmpty) {
      printer.append("\n}")
    }
  }

  def visitSwitchStatement(expession: Option[IntermediateNode], body: Option[IntermediateNode]) = {
    if (expession.isDefined) visit(expession.get)
    printer.append(" match ")
    if (body.isDefined) visit(body.get)
  }

  def visitSwitchLabelStatement(caseValue: Option[IntermediateNode], arrow: String) = {
    printer.append("case ")
    if (caseValue.isDefined) visit(caseValue.get)
    printer.append(s" $arrow ")
  }

  def visitNotSupported(iNode: Option[IntermediateNode], msg: String) = {
    printer.append(msg)
    if (iNode.isDefined) {
      printer.newLine()
      visit(iNode.get)
    }
  }

  def visitSynchronizedStatement(lock: Option[IntermediateNode], body: Option[IntermediateNode]) = {
    if (lock.isDefined) visit(lock.get)
    printer.append(" synchronized ")
    if (body.isDefined) visit(body.get)
  }

  def visitExpressionListStatement(exprs: Seq[IntermediateNode]) = {
    printWithSeparator(exprs, "\n")
  }

  def visitForEach(iterParamName: String, iteratedValue: Option[IntermediateNode],
                   body: Option[IntermediateNode], isJavaCollection: Boolean) = {
    if (isJavaCollection) {
      printer.append("import scala.collection.JavaConversions._\n")
    }

    printer.append("for (")
    printer.append(escapeKeyword(iterParamName))
    printer.append(" <- ")
    if (iteratedValue.isDefined) visit(iteratedValue.get)
    printer.append(") ")
    if (body.isDefined) visit(body.get)
  }

  def visitJavaCodeRef(statement: JavaCodeReferenceStatement, qualifier: Option[IntermediateNode], parametrList: Option[IntermediateNode], name: String) = {
    if (qualifier.isDefined) {
      visit(qualifier.get)
      printer.append(".")
    }
    val begin = printer.length
    name match {
      case "this" => printer.append(name)
      case "super" => printer.append(name)
      case _ => printer.append(escapeKeyword(name))
    }
    val range = new TextRange(begin, printer.length)
    rangedElementsMap.put(statement, range)
    if (parametrList.isDefined) visit(parametrList.get)
  }

  def visitType(t: TypeConstruction, inType: String) = {
    val begin = printer.length
    printer.append(inType)
    val range = new TextRange(begin, printer.length)
    rangedElementsMap.put(t, range)
  }

  def visitArrayType(iNode: IntermediateNode) = {
    printer.append("Array[")
    visit(iNode)
    printer.append("]")
  }

  def visitParametrizedType(iNode: IntermediateNode, parts: Seq[IntermediateNode]) = {
    visit(iNode)
    printWithSeparator(parts, ", ", "[", "]", parts.nonEmpty)
  }

  def printWithSeparator(seq: Seq[IntermediateNode], separator: String): Unit = {
    if (seq != null && seq.nonEmpty) {
      val it = seq.iterator
      while (it.hasNext) {
        visit(it.next())
        if (it.hasNext) printer.append(separator)
      }
    }
  }

  def printWithSeparator(seq: Seq[IntermediateNode], separator: String, before: String, after: String, needAppend: Boolean): Unit = {
    if (needAppend) printer.append(before)
    printWithSeparator(seq, separator)
    if (needAppend) printer.append(after)
  }

  def printWithSeparator(seq: Seq[IntermediateNode], separator: String, before: String, after: String): Unit = {
    printWithSeparator(seq, separator, before, after, needAppend = true)
  }

  def visitTypeParameters(data: Seq[IntermediateNode]) = {
    printWithSeparator(data, ", ", "[", "]", data.nonEmpty)
  }

  def visitTypeParameterConstruction(name: String, typez: Seq[IntermediateNode]) = {
    printer.append(escapeKeyword(name))
    if (typez.nonEmpty) {
      printer.append(" <: ")
      printWithSeparator(typez, " with ")
    }
  }

  override val printer: PrettyPrinter = new PrettyPrinter
}
