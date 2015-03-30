package org.jetbrains.plugins.scala
package conversion


import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.editorActions.ReferenceData
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.conversion.copy.Association
import org.jetbrains.plugins.scala.extensions.PsiMemberExt
import org.jetbrains.plugins.scala.lang.dependency.{DependencyKind, Path}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

import scala.collection.mutable
import scala.collection.mutable.{ArrayBuffer, ListBuffer}

/**
 * Author: Alexander Podkhalyuzin
 * Date: 23.07.2009
 */

object JavaToScala {
  private val context: ThreadLocal[mutable.Stack[(Boolean, String)]] = new ThreadLocal[mutable.Stack[(Boolean, String)]] {
    override def initialValue(): mutable.Stack[(Boolean, String)] = new mutable.Stack[(Boolean, String)]()
  }

  def escapeKeyword(name : String): String = if (ScalaNamesUtil.isKeyword(name)) "`" + name + "`" else name

  class Offset(val value: Int) {
    override def toString = value.toString
  }

  def convertPsiToText(element: PsiElement)
                      (implicit associations: ListBuffer[Association] = new ListBuffer(),
                       refs: Seq[ReferenceData] = Seq.empty, offset: Offset = new Offset(0)): String = {
    if (element == null) return ""
    if (element.getLanguage != JavaLanguage.INSTANCE) return ""

    val res = new StringBuilder("")

    class SpecificOffset(override val value: Int) extends Offset(value)
    implicit def startOffset: SpecificOffset = new SpecificOffset(offset.value + res.length)

    def associationFor(element: PsiElement) = {
      refs.find(ref => new TextRange(ref.startOffset, ref.endOffset) == element.getTextRange).map { ref =>
        val i = startOffset.value
        val range = new TextRange(i, i + element.getTextLength)
        if(ref.staticMemberName == null) {
          Association(DependencyKind.Reference, range, Path(ref.qClassName))
        } else {
          Association(DependencyKind.Reference, range, Path(ref.qClassName, ref.staticMemberName))
        }
      }
    }

    associations ++= associationFor(element).toSeq

    def append(elements: Seq[PsiElement], prefix: String = "(", separator: String = ", ", suffix: String = ")") {
      if (elements.nonEmpty) {
        res.append(prefix)
        val it = elements.iterator
        while(it.hasNext) {
          res.append(convertPsiToText(it.next()))
          if(it.hasNext) res.append(separator)
        }
        res.append(suffix)
      }
    }

    element match {
      case docCommentOwner: PsiDocCommentOwner if docCommentOwner.getDocComment != null =>
        res.append(docCommentOwner.getDocComment.getText).append("\n")
      case _ =>
    }
    element match {
      case f: PsiFile =>
        for (child <- f.getChildren) {
          res.append(convertPsiToText(child)).append("\n")
        }
      case p: PsiTypeParameter =>
        res.append(escapeKeyword(p.getName))
        val typez = new ArrayBuffer[PsiJavaCodeReferenceElement]
        if (p.getExtendsList != null) typez ++= p.getExtendsList.getReferenceElements
        if (typez.length > 0) res.append(" <: ")
        for (tp <- typez) {
          res.append(convertPsiToText(tp)).append(" with ")
        }
        if (typez.length > 0) res.delete(res.length - 5, res.length)
      //statements
      case f: PsiIfStatement =>
        res.append("if (").append(convertPsiToText(f.getCondition)).append(") ").
                append(convertPsiToText(f.getThenBranch))
        if (f.getElseElement != null) {
          res.append("\nelse ").append(convertPsiToText(f.getElseBranch))
        }
      case l: PsiLiteralExpression => res.append(l.getText)
      case e: PsiExpressionStatement => res.append(convertPsiToText(e.getExpression))
      case b: PsiBlockStatement =>
        res.append(convertPsiToText(b.getCodeBlock))
      case b: PsiCodeBlock =>
        res.append("{\n")
        for (st <- b.getStatements) res.append(convertPsiToText(st)).append("\n")
        res.append("}")
      case w: PsiWhileStatement =>
        res.append("while (").append(convertPsiToText(w.getCondition)).append(") ").
                append(convertPsiToText(w.getBody))
      case d: PsiDoWhileStatement =>
        res.append("do ").append(convertPsiToText(d.getBody)).append("while (").
                append(convertPsiToText(d.getCondition)).append(")")
      case r: PsiReturnStatement => res.append("return ").append(convertPsiToText(r.getReturnValue))
      case a: PsiAssertStatement =>
        res.append("assert(").append(convertPsiToText(a.getAssertCondition))
        val v = a.getAssertDescription
        if (v != null) res.append(", ").append(convertPsiToText(v))
        res.append(")")
      case b: PsiBreakStatement =>
        if (b.getLabelIdentifier != null) res.append("break //todo: label break is not supported")
        else res.append("break //todo: break is not supported")
      case c: PsiContinueStatement => res.append("continue //todo: continue is not supported")
      case d: PsiDeclarationStatement =>
        for  (decl <- d.getDeclaredElements) {
          res.append(convertPsiToText(decl)).append("\n")
        }
        if (d.getDeclaredElements.length > 0) res.delete(res.length - 1, res.length)
      case e: PsiExpressionListStatement =>
        for (expr <- e.getExpressionList.getExpressions) {
          res.append(convertPsiToText(expr)).append("\n")
        }
        res.delete(res.length - 1, res.length)
      case f: PsiForStatement =>
        if (f.getInitialization != null && !f.getInitialization.isInstanceOf[PsiEmptyStatement]) {
          res.append("\n{\n").append(convertPsiToText(f.getInitialization)).append("\n")
        }
        val condition: String = f.getCondition match {
          case empty: PsiEmptyStatement => "true"
          case null => "true"
          case _ => convertPsiToText(f.getCondition)
        }
        res.append("while (").append(condition).append(") ")
        if (f.getUpdate != null) {
          res.append("{\n")
        }
        res.append(convertPsiToText(f.getBody))
        if (f.getUpdate != null) {
          res.append("\n").append(convertPsiToText(f.getUpdate)).append("\n}")
        }
        if (f.getInitialization != null && !f.getInitialization.isInstanceOf[PsiEmptyStatement]) {
          res.append("\n}")
        }
      case f: PsiForeachStatement =>
        val iteratedValue: PsiExpression = f.getIteratedValue
        val tp = if (iteratedValue == null) null else iteratedValue.getType
        val isJavaCollection =
          if (tp == null) true else !tp.isInstanceOf[PsiArrayType]
        if (isJavaCollection) {
          res.append("import scala.collection.JavaConversions._\n")
        }
        res.append("for (").append(escapeKeyword(f.getIterationParameter.getName)).append(" <- ").
                append(convertPsiToText(f.getIteratedValue)).append(") ").
                append(convertPsiToText(f.getBody))
      case s: PsiLabeledStatement =>
        res.append(convertPsiToText(s.getStatement)).append("//todo: labels is not supported")
      case t: PsiThrowStatement =>
        res.append("throw ").append(convertPsiToText(t.getException))
      case s: PsiSynchronizedStatement =>
        res.append(convertPsiToText(s.getLockExpression)).append(" synchronized ").
                append(convertPsiToText(s.getBody))
      case s: PsiSwitchLabelStatement =>
        val arrow = ScalaPsiUtil.functionArrow(s.getProject)
        res.append("case ").append(if (s.isDefaultCase) "_" else convertPsiToText(s.getCaseValue)).
                append(s" $arrow ")
      case s: PsiSwitchStatement =>
        res.append(convertPsiToText(s.getExpression)).append(" match ").
                append(convertPsiToText(s.getBody))
      case t: PsiTryStatement =>
        import scala.collection.JavaConversions._
        val resourceList = t.getResourceList
        if (resourceList != null) {
          res.append("try {\n")
          resourceList.getResourceVariables.foreach { r =>
            res.append(convertPsiToText(r)).append("\n")
          }
        }
        res.append("try ").append(convertPsiToText(t.getTryBlock))
        val catchs = t.getCatchSections
        if (catchs.length > 0) {
          res.append("\ncatch {\n")
          for (section: PsiCatchSection <- catchs) {
            val arrow = ScalaPsiUtil.functionArrow(t.getProject)
            res.append("case ").append(convertPsiToText(section.getParameter)).append(s" $arrow ").
                    append(convertPsiToText(section.getCatchBlock))
          }
          res.append("}")
        }
        if (t.getFinallyBlock != null) {
          if (resourceList == null) {
            res.append(" finally ").append(convertPsiToText(t.getFinallyBlock))
          } else {
            res.append(" finally {\n")
            for (st <- t.getFinallyBlock.getStatements) res.append(convertPsiToText(st)).append("\n")
            resourceList.getResourceVariables.foreach { r =>
              val name = escapeKeyword(r.getName)
              res.append(s"if ($name != null) $name.close()").append("\n")
            }
            res.append("}")
          }
        } else if (resourceList != null) {
          res.append(" finally {\n")
          resourceList.getResourceVariables.foreach { r =>
            val name = escapeKeyword(r.getName)
            res.append(s"if ($name != null) $name.close()").append("\n")
          }
          res.append("}")
        }
        if (resourceList != null) {
          res.append("\n}")
        }
      //expressions
      case a: PsiArrayAccessExpression =>
        res.append(convertPsiToText(a.getArrayExpression)).append("(").
                append(convertPsiToText(a.getIndexExpression)).append(")")
      case a: PsiArrayInitializerExpression =>
        res.append("Array(")
        for (init <- a.getInitializers) {
          res.append(convertPsiToText(init)).append(", ")
        }
        res.delete(res.length - 2, res.length)
        res.append(")")
      case a: PsiAssignmentExpression =>
        if (!a.getParent.isInstanceOf[PsiExpression]) {
          res.append(convertPsiToText(a.getLExpression)).append(" ").
                append(a.getOperationSign.getText).append(" ").append(convertPsiToText(a.getRExpression))
        } else {
          res.append("({").append(convertPsiToText(a.getLExpression)).append(" ").
                append(a.getOperationSign.getText).append(" ").append(convertPsiToText(a.getRExpression)).
                  append("; ").append(convertPsiToText(a.getLExpression)).append("})")
        }
      case b: PsiBinaryExpression =>
        def isOk: Boolean = {
          if (b.getLOperand.getType.isInstanceOf[PsiPrimitiveType]) return false
          b.getROperand match {
            case l: PsiLiteralExpression if l.getText == "null" => return false
            case _ =>
          }
          true
        }
        val operation = b.getOperationSign.getText match {
          case "==" if isOk => "eq"
          case "!=" if isOk => "ne"
          case x => x
        }
        res.append(convertPsiToText(b.getLOperand)).append(" ").
                append(operation).append(" ").append(convertPsiToText(b.getROperand))
      case c: PsiClassObjectAccessExpression =>
        res.append("classOf[").append(convertPsiToText(c.getOperand)).append("]")
      case c: PsiConditionalExpression =>
        res.append("if (").append(convertPsiToText(c.getCondition)).append(") ").
                append(convertPsiToText(c.getThenExpression)).append(" else ").append(convertPsiToText(c.getElseExpression))
      case i: PsiInstanceOfExpression =>
        res.append(convertPsiToText(i.getOperand)).append(".isInstanceOf[").
                append(convertPsiToText(i.getCheckType)).append("]")
      case m: PsiMethodCallExpression if m.getMethodExpression.getReferenceName == "equals" &&
        m.getTypeArguments.length == 0 && m.getArgumentList.getExpressions.length == 1 =>
        val parentIsExpr = m.getParent.isInstanceOf[PsiExpression]
        if (parentIsExpr) res.append("(")
        res.append(Option(m.getMethodExpression.getQualifierExpression).
          map(convertPsiToText(_)).getOrElse("this")).append(" == ").
          append(convertPsiToText(m.getArgumentList.getExpressions.apply(0)))
        if (parentIsExpr) res.append(")")
      case m: PsiMethodCallExpression =>
        m.getMethodExpression.resolve() match {
          case method: PsiMethod if method.getName == "parseInt" && m.getArgumentList.getExpressions.length == 1 &&
            method.getContainingClass != null && method.getContainingClass.getQualifiedName == "java.lang.Integer" =>
            res.append(convertPsiToText(m.getArgumentList.getExpressions.apply(0))).append(".toInt")
          case method: PsiMethod if method.getName == "parseDouble" && m.getArgumentList.getExpressions.length == 1 &&
            method.getContainingClass != null && method.getContainingClass.getQualifiedName == "java.lang.Double" =>
            res.append(convertPsiToText(m.getArgumentList.getExpressions.apply(0))).append(".toDouble")
          case method: PsiMethod if method.getName == "round" && m.getArgumentList.getExpressions.length == 1 &&
            method.getContainingClass != null && method.getContainingClass.getQualifiedName == "java.lang.Math" =>
            res.append(convertPsiToText(m.getArgumentList.getExpressions.apply(0))).append(".round")
          case _ =>
            res.append(convertPsiToText(m.getMethodExpression)).append(convertPsiToText(m.getArgumentList))
        }
      case e: PsiExpressionList =>
        if (e.getExpressions.length != 0) {
          res.append("(")
          for (expr <- e.getExpressions) {
            res.append(convertPsiToText(expr)).append(", ")
          }
          res.delete(res.length - 2, res.length)
          res.append(")")
        }
      case p: PsiPrefixExpression =>
        p.getOperationTokenType match {
          case JavaTokenType.PLUSPLUS =>
            if (!canBeSimpified(p)) {
              res.append("({i += 1; i})".replace("i", convertPsiToText(p.getOperand)))
            } else {
              res.append(convertPsiToText(p.getOperand)).append(" += 1")
            }
          case JavaTokenType.MINUSMINUS =>
            if (!canBeSimpified(p)) {
              res.append("({i -= 1; i})".replace("i", convertPsiToText(p.getOperand)))
            } else {
              res.append(convertPsiToText(p.getOperand)).append(" -= 1")
            }
          case _ =>
            res.append(p.getOperationSign.getText).append(convertPsiToText(p.getOperand))
        }
      case p: PsiPostfixExpression =>
        p.getOperationTokenType match {
          case JavaTokenType.PLUSPLUS =>
            if (!canBeSimpified(p)) {
              res.append("({i += 1; i - 1})".replace("i", convertPsiToText(p.getOperand)))
            } else {
              res.append(convertPsiToText(p.getOperand)).append(" += 1")
            }
          case JavaTokenType.MINUSMINUS =>
            if (!canBeSimpified(p)) {
              res.append("({i -= 1; i + 1})".replace("i", convertPsiToText(p.getOperand)))
            } else {
              res.append(convertPsiToText(p.getOperand)).append(" -= 1")
            }
        }
      case p: PsiParenthesizedExpression =>
        res.append("(").append(convertPsiToText(p.getExpression)).append(")")
      case p: PsiReferenceExpression =>
        if (p.getQualifierExpression != null) {
          res.append(convertPsiToText(p.getQualifierExpression)).append(".")
        } else {
          p.resolve() match {
            case f: PsiMember if f.hasModifierProperty("static") =>
              val clazz = f.containingClass
              if (clazz != null && context.get().contains((false, clazz.getQualifiedName))) {
                res.append(escapeKeyword(clazz.getName)).append(".")
              }
            case _ =>
          }
        }
        res.append(escapeKeyword(p.getReferenceName))
        res.append(convertPsiToText(p.getParameterList))
      case t: PsiTypeCastExpression =>
        if (t.getCastType.getType.isInstanceOf[PsiPrimitiveType] && t.getOperand.getType.isInstanceOf[PsiPrimitiveType] &&
          List("Int", "Long", "Double", "Float", "Byte", "Char", "Short").contains(convertPsiToText(t.getCastType))) {
          res.append(convertPsiToText(t.getOperand)).append(".to").append(convertPsiToText(t.getCastType))
        } else {
          res.append(convertPsiToText(t.getOperand)).append(".asInstanceOf[").
            append(convertPsiToText(t.getCastType)).append("]")
        }
      case t: PsiThisExpression =>
        if (t.getQualifier != null) {
          res.append(convertPsiToText(t.getQualifier)).append(".")
        }
        res.append("this")
      case s: PsiSuperExpression =>
        if (s.getQualifier != null) {
          res.append(convertPsiToText(s.getQualifier)).append(".")
        }
        res.append("super")
      case n: PsiNewExpression if n.getAnonymousClass == null =>
        if (n.getArrayInitializer != null) {
          for(ref <- Option(n.getClassReference)) associations ++= associationFor(ref).toSeq
          res.append(ScType.presentableText(ScType.create(n.getType, n.getProject)))
          append(n.getArrayInitializer.getInitializers)
        } else if (n.getArrayDimensions.length > 0) {
          res.append("new ")
          for(ref <- Option(n.getClassReference)) associations ++= associationFor(ref).toSeq
          res.append(ScType.presentableText(ScType.create(n.getType, n.getProject)))
          append(n.getArrayDimensions)
        } else {
          res.append("new ")
          for(ref <- Option(n.getClassReference)) associations ++= associationFor(ref).toSeq
          res.append(ScType.presentableText(ScType.create(n.getType, n.getProject)))
          if (n.getArgumentList != null) {
            if (n.getArgumentList.getExpressions.size == 0) {
              // if the new expression is used as a qualifier, force parentheses for empty argument list
              n.getParent match {
                case r: PsiJavaCodeReferenceElement if n == r.getQualifier => res.append("()")
                case _ =>
              }
            }
            else {
              res.append(convertPsiToText(n.getArgumentList))
            }
          }
        }
      case n: PsiNewExpression =>
        res.append("new ").append(convertPsiToText(n.getAnonymousClass))
      //declarations
      case m: PsiMethod =>
        res.append(convertPsiToText(m.getModifierList)).append(" ")
        res.append(" def ")
        if (!m.isConstructor) res.append(escapeKeyword(m.getName))
        else res.append("this")
        val typeParameters = m.getTypeParameters
        if (typeParameters.nonEmpty) {
          res.append(typeParameters.map(convertPsiToText).mkString("[", ", ", "]"))
        }
        var params = convertPsiToText(m.getParameterList)
        if (params == "" && m.isConstructor) params = "()"
        res.append(params)
        if (!m.isConstructor && m.getReturnType != PsiType.VOID) res.append(" : ").append(convertPsiToText(m.getReturnTypeElement))
        if (m.getBody != null) {
          if (!m.isConstructor && m.getReturnType != PsiType.VOID) res.append(" = ")
          if (m.isConstructor) {
            res.append("{\nthis()\n")
            for (st <- m.getBody.getStatements) res.append(convertPsiToText(st)).append("\n")
            res.append("}")
          } else {
            res.append(convertPsiToText(m.getBody))
          }
        }
      case f: PsiField =>
        res.append(convertPsiToText(f.getModifierList)).append(" ")
        if (f.hasModifierProperty("final")) {
          res.append(" val ")
        } else res.append(" var ")
        res.append(escapeKeyword(f.getName)).append(" : ")
        res.append(convertPsiToText(f.getTypeElement))
        if (f.getInitializer != null) {
          res.append(" = ").append(convertPsiToText(f.getInitializer))
        } else {
          res.append(" = ")
          import org.jetbrains.plugins.scala.lang.psi.types._
          res.append(ScType.create(f.getType, f.getProject) match {
            case Int => "0"
            case Boolean => "false"
            case Long => "0L"
            case Byte => "0"
            case Double => ".0"
            case Float => ".0"
            case Short => "0"
            case Unit => "{}"
            case Char => "0"
            case _ => "null"
          })
        }
      case l: PsiLocalVariable =>
        res.append(convertPsiToText(l.getModifierList)).append(" ")
        if (l.hasModifierProperty("final")) {
          res.append(" val ")
        } else {
          val parent = PsiTreeUtil.getParentOfType(l, classOf[PsiCodeBlock], classOf[PsiBlockStatement])
          var haveUsage = false
          if (parent != null) {
            parent.accept(new JavaRecursiveElementVisitor {
              override def visitPostfixExpression(expression: PsiPostfixExpression) {
                if (expression.getOperationTokenType == JavaTokenType.PLUSPLUS || expression.getOperationTokenType == JavaTokenType.MINUSMINUS) {
                  expression.getOperand match {
                    case ref: PsiReferenceExpression => if (ref.resolve() == l) haveUsage = true
                    case _ =>
                  }
                }
              }

              override def visitPrefixExpression(expression: PsiPrefixExpression) {
                if (expression.getOperationTokenType == JavaTokenType.PLUSPLUS || expression.getOperationTokenType == JavaTokenType.MINUSMINUS) {
                  expression.getOperand match {
                    case ref: PsiReferenceExpression if ref.resolve() == l => haveUsage = true
                    case _ =>
                  }
                }
              }

              override def visitAssignmentExpression(expression: PsiAssignmentExpression) {
                expression.getLExpression match {
                  case ref: PsiReferenceExpression if ref.resolve() == l => haveUsage = true
                  case _ =>
                }
              }
            })
          }
          if (haveUsage) res.append(" var ")
          else res.append(" val ")
        }
        res.append(escapeKeyword(l.getName)).append(" : ")
        res.append(convertPsiToText(l.getTypeElement))
        if (l.getInitializer != null) {
          res.append(" = ").append(convertPsiToText(l.getInitializer))
        } else {
          res.append(" = ")
          import org.jetbrains.plugins.scala.lang.psi.types._
          res.append(ScType.create(l.getType, l.getProject) match {
            case Int => "0"
            case Boolean => "false"
            case Long => "0L"
            case Byte => "0"
            case Double => ".0"
            case Float => ".0"
            case Short => "0"
            case Unit => "{}"
            case Char => "0"
            case _ => "null"
          })
        }
      case p: PsiParameter =>
        val typeText = if (p.isVarArgs) {
          p.getTypeElement.getType match {
            case at: PsiArrayType =>
              val compType = at.getComponentType
              val scCompType = ScType.create(compType, p.getProject)
              ScType.presentableText(scCompType) + "*"
            case _ => convertPsiToText(p.getTypeElement) // should not happen
          }
        } else convertPsiToText(p.getTypeElement)
        res.append(convertPsiToText(p.getModifierList)).append(escapeKeyword(p.getName)).append(" : ").append(typeText)
      /*case a: PsiAnonymousClass => {
        a.get
      }*/
      case c: PsiClass =>
        var forClass = new ArrayBuffer[PsiMember]()
        var forObject = new ArrayBuffer[PsiMember]()
        for (method <- c.getMethods) {
          if (method.hasModifierProperty("static")) {
            forObject += method
          } else forClass += method
        }
        val serialVersionUID = serialVersion(c)
        for (field <- c.getFields if Some(field) != serialVersionUID) {
          if (field.hasModifierProperty("static")) {
            forObject += field
          } else forClass += field
        }
        for (clazz <- c.getInnerClasses) {
          if (clazz.hasModifierProperty("static")) {
            forObject += clazz
          } else forClass += clazz
        }
        forClass = forClass.sortBy(_.getTextOffset)
        forObject = forObject.sortBy(_.getTextOffset)
        if (forObject.nonEmpty && !c.isInstanceOf[PsiAnonymousClass]) {
          context.get().push((true, c.getQualifiedName))
          try {
            val modifiers: String = convertPsiToText(c.getModifierList).replace("abstract", "")
            res.append(modifiers).append(" ")
            res.append("object ")
            res.append(escapeKeyword(c.getName))
            if (c.isEnum) res.append(" extends Enumeration")
            res.append(" {\n")
            if (c.isEnum) {
              res.append("type ").append(escapeKeyword(c.getName)).append(" = Value\n")
              val enumConstants = forObject.filter(_.isInstanceOf[PsiEnumConstant])
              if (enumConstants.nonEmpty) {
                res.append("val ")
                for (memb <- enumConstants) {
                  res.append(escapeKeyword(memb.getName)).append(", ")
                }
                res.delete(res.length - 2, res.length)
                res.append(" = Value\n")
              }
            }
            for (memb <- forObject.filter(!_.isInstanceOf[PsiEnumConstant])) {
              res.append(convertPsiToText(memb)).append("\n")
            }
            for (init <- c.getInitializers) {
              res.append("try ").append(convertPsiToText(init.getBody)).append("\n")
            }
            res.append("}")
          }
          finally {
            context.get().pop()
          }
        }
        if (!c.isInstanceOf[PsiAnonymousClass]) res.append("\n")
        if (forClass.nonEmpty || forObject.isEmpty) {
          context.get().push((false, c.getQualifiedName))
          try {
            c match {
              case clazz: PsiAnonymousClass =>
                val tp = ScType.create(c.asInstanceOf[PsiAnonymousClass].getBaseClassType, c.getProject)
                res.append(ScType.presentableText(tp))
                if (clazz.getArgumentList.getExpressions.length > 0)
                  res.append("(").append(convertPsiToText(clazz.getArgumentList)).append(")")
                else res.append("()")
              case _ =>
                res.append(convertPsiToText(c.getModifierList)).append(" ")
                if (c.isInterface) res.append("trait ") else res.append("class ")
                res.append(escapeKeyword(c.getName))
                val typeParameters = c.getTypeParameters
                if (typeParameters.nonEmpty) {
                  res.append(typeParameters.map(convertPsiToText).mkString("[", ", ", "]"))
                }
            }
            val typez = new ArrayBuffer[PsiJavaCodeReferenceElement]
            if (c.getExtendsList != null) typez ++= c.getExtendsList.getReferenceElements
            if (c.getImplementsList != null) typez ++= c.getImplementsList.getReferenceElements
            if (typez.length > 0) res.append(if (c.isInstanceOf[PsiAnonymousClass]) " with " else " extends ")
            for (tp <- typez) {
              res.append(convertPsiToText(tp)).append(" with ")
            }
            if (typez.length > 0) res.delete(res.length - 5, res.length)
            res.append(" {\n")
            for (memb <- forClass) {
              res.append(convertPsiToText(memb)).append("\n")
            }
            res.append("}")
          } finally {
            context.get().pop()
          }
        }
      case p: PsiJavaCodeReferenceElement =>
        if (p.getQualifier != null) {
          res.append(convertPsiToText(p.getQualifier)).append(".")
        }
        res.append(escapeKeyword(p.getReferenceName))
        res.append(convertPsiToText(p.getParameterList))
      case p: PsiPackageStatement =>
        res.append("package ")
        res.append(convertPsiToText(p.getPackageReference))
      case i: PsiImportStatement =>
        res.append("import ")
        res.append(convertPsiToText(i.getImportReference))
        if (i.isOnDemand) {
          res.append("._")
        }
      case i: PsiImportStaticStatement =>
        res.append("import ")
        res.append(convertPsiToText(i.getImportReference))
        if (i.isOnDemand) {
          res.append("._")
        }
      case i: PsiImportList =>
        for (imp <- i.getAllImportStatements) {
          res.append(convertPsiToText(imp)).append("\n")
        }
      case t: PsiTypeElement =>
        res.append(ScType.presentableText(ScType.create(t.getType, t.getProject)))
        /*if (t.getText.endsWith("[]")) {
          res.append("Array[").append(convertPsiToText(t.getFirstChild)).append("]")
        } else {
          t.getFirstChild match {
            case k: PsiKeyword => {
              k.getText match {
                case "int" => res.append("Int")
                case "long" => res.append("Long")
                case "boolean" => res.append("Boolean")
                case "short" => res.append("Short")
                case "double" => res.append("Double")
                case "void" => res.append("Unit")
                case "float" => res.append("Float")
                case "byte" => res.append("Byte")
                case "char" => res.append("Char")
              }
            }
            case x => res.append(convertPsiToText(x))
          }
        }*/
      case m: PsiModifierList =>
        for {
          a <- m.getAnnotations
          if Option(a.getQualifiedName) != Some("java.lang.Override")
        } {
          res.append(convertPsiToText(a)).append(" ")
        }

        if (m.hasModifierProperty("volatile")) {
          res.append("@volatile\n")
        }
        if (m.hasModifierProperty("transient")) {
          res.append("@transient\n")
        }
        if (m.hasModifierProperty("native")) {
          res.append("@native\n")
        }
        m.getParent match {
          case method: PsiMethod =>
            val references = method.getThrowsList.getReferenceElements
            for (ref <- references) {
              res.append("@throws(classOf[").append(convertPsiToText(ref)).append("])\n")
            }
          case c: PsiClass =>
            serialVersion(c) match {
              case Some(f) =>
                res.append("@SerialVersionUID(").append(convertPsiToText(f.getInitializer)).append(")\n")
              case _ =>
            }
          case _ =>
        }
        if (m.hasModifierProperty("protected")) {
          res.append("protected ")
        } else if (m.hasModifierProperty("private")) {
          res.append("private ")
        } else if (!m.hasModifierProperty("public") && m.getParent != null && m.getParent.getParent != null &&
          m.getParent.getParent.isInstanceOf[PsiClass]) {
          val packageName: String = m.getContainingFile.asInstanceOf[PsiClassOwner].getPackageName
          if (packageName != "") res.append("private").append("[").append(packageName.substring(packageName.lastIndexOf(".") + 1)).append("] ")
        }
        if (m.hasModifierProperty("abstract")) {
          m.getParent match {
            case c: PsiClass =>
              if (!c.isInterface) res.append("abstract ") //abstract is redundant in other places
            case _ =>
          }
        }
        if (m.hasModifierProperty("final")) {
          m.getParent match {
            case _: PsiLocalVariable =>
            case _: PsiParameter =>
            case _ =>
              val stack = context.get()
              if (stack.nonEmpty && !stack.top._1) res.append("final ") //only to classes, not objects
          }
        }
        m.getParent match {
          case method: PsiMethod =>
            if (method.findSuperMethods.exists(!_.hasModifierProperty("abstract"))) res.append("override ")
          case _ =>
        }
      case w: PsiWhiteSpace =>
        res.append(w.getText)
      case annot: PsiAnnotation =>
        PsiTreeUtil.getParentOfType(annot, classOf[PsiAnnotation]) match {
          case parent: PsiAnnotation => res.append("new ")
          case _ => res.append("@")
        }
        val nameReferenceElement: PsiJavaCodeReferenceElement = annot.getNameReferenceElement
        if (nameReferenceElement != null) {
          val name = escapeKeyword(nameReferenceElement.getText)
          name match {
            case "Deprecated" => res.append("deprecated")
            case _ => res.append(name)
          }
        }
        val attributes = annot.getParameterList.getAttributes
        if (attributes.nonEmpty) {
          res.append("(")
          for (attribute <- attributes) {
            if (attribute.getName != null) {
              res.append(escapeKeyword(attribute.getName))
              res.append(" = ")
            }
            val value = attribute.getValue
            value match {
              case a:PsiArrayInitializerMemberValue => res.append(convertPsiToText(value))
              case v:PsiAnnotationMemberValue if isArrayAnnotationParameter(attribute) =>
                    res.append("Array(").append(convertPsiToText(value)).append(")")
              case _ => res.append(convertPsiToText(value))
            }
            res.append(", ")
          }
          res.delete(res.length - 2, res.length)
          res.append(")")
        }
        res.append(" ")
      case v:PsiArrayInitializerMemberValue =>
        res.append("Array")
        append(v.getInitializers)
      case r: PsiReferenceParameterList =>
        append(r.getTypeParameterElements, "[", ", ", "]")
      case p: PsiParameterList =>
        if (p.getParametersCount > 0) {
          append(p.getParameters)
        }
      case comment: PsiComment => res.append(comment.getText)
      case p: PsiPolyadicExpression =>
        var flag = false
        p.getOperands.foreach(operand => {
          if (flag) {
            res.append(" ").append(p.getTokenBeforeOperand(operand).getText).append(" ")
            res.append(convertPsiToText(operand))
          } else {
            res.append(convertPsiToText(operand))
            flag = true
          }
        })
      case e: PsiEmptyStatement =>
      case e: PsiErrorElement =>
      case e => res.append(e.getText)
    }
    res.toString()
  }

  def convertPsisToText(elements: Array[PsiElement],
                       associations: ListBuffer[Association] = new ListBuffer(),
                       refs: Seq[ReferenceData] = Seq.empty): String = {
    val res = new StringBuilder("")
    for (element <- elements) {
      res.append(convertPsiToText(element)(associations, refs, new Offset(res.length)))
    }
    res.toString()
  }

  def isArrayAnnotationParameter(pair: PsiNameValuePair): Boolean = {
    AnnotationUtil.getAnnotationMethod(pair) match {
      case method: PsiMethod =>
        val returnType = method.getReturnType
        returnType != null && returnType.isInstanceOf[PsiArrayType]
      case _ => false
    }
  }

  private def serialVersion(c: PsiClass): Option[PsiField] = {
    val serialField = c.findFieldByName("serialVersionUID", false)
    if (serialField != null && serialField.getType.isAssignableFrom(PsiType.LONG) &&
      serialField.hasModifierProperty("static") && serialField.hasModifierProperty("final") &&
      serialField.hasInitializer) {
      Some(serialField)
    } else None
  }

  /**
   * @param expr prefix or postfix expression
   * @return true if this expression is under block
   */
  private def canBeSimpified(expr: PsiExpression): Boolean = {
    expr.getParent match {
      case b: PsiExpressionStatement =>
        b.getParent match {
          case b: PsiBlockStatement => true
          case b: PsiCodeBlock => true
          case _ => false
        }
      case _ => false
    }
  }
}
