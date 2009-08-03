package org.jetbrains.plugins.scala.conversion


import collection.mutable.{ArrayBuffer, HashSet}
import com.intellij.lang.StdLanguages
import com.intellij.psi._
import lang.refactoring.util.ScalaNamesUtil
/**
 * @author: Alexander Podkhalyuzin
 * Date: 23.07.2009
 */

object JavaToScala {
  def convertPsiToText(element: PsiElement): String = {
    if (element == null) return ""
    if (element.getLanguage != StdLanguages.JAVA) return ""
    val res = new StringBuilder("")
    element match {
      case f: PsiFile => {
        for (child <- f.getChildren) {
          res.append(convertPsiToText(child)).append("\n")
        }
      }
      //statements
      case f: PsiIfStatement => {
        res.append("if (").append(convertPsiToText(f.getCondition)).append(") ").
                append(convertPsiToText(f.getThenBranch))
        if (f.getElseElement != null) {
          res.append("\nelse ").append(convertPsiToText(f.getElseBranch))
        }
      }
      case l: PsiLiteralExpression => res.append(l.getText)
      case e: PsiExpressionStatement => res.append(convertPsiToText(e.getExpression))
      case b: PsiBlockStatement => res.append(convertPsiToText(b.getCodeBlock))
      case b: PsiCodeBlock => {
        res.append("{\n")
        for (st <- b.getStatements) res.append(convertPsiToText(st)).append("\n")
        res.append("}")
      }
      case w: PsiWhileStatement => {
        res.append("while (").append(convertPsiToText(w.getCondition)).append(") ").
                append(convertPsiToText(w.getBody))
      }
      case d: PsiDoWhileStatement => {
        res.append("do ").append(convertPsiToText(d.getBody)).append("while (").
                append(convertPsiToText(d.getCondition)).append(")")
      }
      case r: PsiReturnStatement => res.append("return ").append(convertPsiToText(r.getReturnValue))
      case a: PsiAssertStatement => {
        res.append("assert(").append(a.getAssertCondition)
        if (a.getAssertDescription != null) res.append(", ").append(a.getAssertDescription)
        res.append(")")
      }
      case b: PsiBreakStatement => {
        if (b.getLabelIdentifier != null) res.append("//todo: label break is not supported")
        else res.append("//todo: break is not supported")
      }
      case c: PsiContinueStatement => res.append("//todo: continue is not supported")
      case d: PsiDeclarationStatement => {
        for  (decl <- d.getDeclaredElements) {
          res.append(convertPsiToText(decl)).append("\n")
        }
        res.delete(res.length - 1, res.length)
      }
      case e: PsiExpressionListStatement => {
        for (expr <- e.getExpressionList.getExpressions) {
          res.append(convertPsiToText(expr)).append("\n")
        }
        res.delete(res.length - 1, res.length)
      }
      case f: PsiForStatement => {
        if (f.getInitialization != null) {
          res.append("{\n").append(convertPsiToText(f.getInitialization)).append("\n")
        }
        res.append("while (").append(convertPsiToText(f.getCondition)).append(") ")
        if (f.getUpdate != null) {
          res.append("{\n")
        }
        res.append(convertPsiToText(f.getBody))
        if (f.getUpdate != null) {
          res.append("\n").append(convertPsiToText(f.getUpdate)).append("\n}")
        }
        if (f.getInitialization != null) {
          res.append("\n}")
        }
      }
      case f: PsiForeachStatement => {
        res.append("for (").append(f.getIterationParameter.getName).append(" <- ").
                append(convertPsiToText(f.getIteratedValue)).append(") ").
                append(convertPsiToText(f.getBody))
      }
      case s: PsiLabeledStatement => {
        res.append(convertPsiToText(s.getStatement)).append("//todo: labels is not supported")
      }
      case t: PsiThrowStatement => {
        res.append("throw ").append(convertPsiToText(t.getException))
      }
      case s: PsiSynchronizedStatement => {
        res.append(convertPsiToText(s.getLockExpression)).append(" synchronyzed ").
                append(convertPsiToText(s.getBody))
      }
      case s: PsiSwitchLabelStatement => {
        res.append("case ").append(if (s.isDefaultCase) "_" else convertPsiToText(s.getCaseValue)).
                append(" => ")
      }
      case s: PsiSwitchStatement => {
        res.append(convertPsiToText(s.getExpression)).append(" match ").
                append(convertPsiToText(s.getBody))
      }
      case t: PsiTryStatement => {
        //todo:
      }
      //expressions
      case a: PsiArrayAccessExpression => {
        res.append(convertPsiToText(a.getArrayExpression)).append("(").
                append(convertPsiToText(a.getIndexExpression)).append(")")
      }
      case a: PsiArrayInitializerExpression => {
        res.append("Array(")
        for (init <- a.getInitializers) {
          res.append(convertPsiToText(init)).append(", ")
        }
        res.delete(res.length - 2, res.length)
        res.append(")")
      }
      case a: PsiAssignmentExpression => {
        res.append(convertPsiToText(a.getLExpression)).append(" ").
                append(a.getOperationSign.getText).append(" ").append(convertPsiToText(a.getRExpression))
      }
      case b: PsiBinaryExpression => {
        res.append(convertPsiToText(b.getLOperand)).append(" ").
                append(b.getOperationSign.getText).append(" ").append(convertPsiToText(b.getROperand))
      }
      case c: PsiClassObjectAccessExpression => {
        res.append("classOf[").append(convertPsiToText(c.getOperand)).append("]")
      }
      case c: PsiConditionalExpression => {
        res.append("if (").append(convertPsiToText(c.getCondition)).append(") ").
                append(convertPsiToText(c.getThenExpression)).append(convertPsiToText(c.getElseExpression))
      }
      case i: PsiInstanceOfExpression => {
        res.append(convertPsiToText(i.getOperand)).append(".isInstanceOf[").
                append(convertPsiToText(i.getCheckType)).append("]")
      }
      case m: PsiMethodCallExpression => {
        res.append(convertPsiToText(m.getMethodExpression)).append(convertPsiToText(m.getArgumentList))
      }
      case e: PsiExpressionList => {
        if (e.getExpressions.length != 0) {
          res.append("(")
          for (expr <- e.getExpressions) {
            res.append(convertPsiToText(expr)).append(", ")
          }
          res.delete(res.length - 2, res.length)
          res.append(")")
        }
      }
      case p: PsiPrefixExpression => {
        p.getOperationTokenType match {
          case JavaTokenType.PLUSPLUS => {
            res.append("{i += 1; i - 1}".replace("i", convertPsiToText(p.getOperand)))
          }
          case JavaTokenType.MINUSMINUS => {
            res.append("{i -= 1; i + 1}".replace("i", convertPsiToText(p.getOperand)))
          }
          case _ => {
            res.append(p.getOperationSign.getText).append(convertPsiToText(p.getOperand))
          }
        }
      }
      case p: PsiPostfixExpression => {
        p.getOperationTokenType match {
          case JavaTokenType.PLUSPLUS => {
            res.append("{i += 1; i}".replace("i", convertPsiToText(p.getOperand)))
          }
          case JavaTokenType.MINUSMINUS => {
            res.append("{i -= 1; i}".replace("i", convertPsiToText(p.getOperand)))
          }
        }
      }
      case p: PsiParenthesizedExpression => {
        res.append("(").append(convertPsiToText(p.getExpression)).append(")")
      }
      case p: PsiReferenceExpression => {
        if (p.getQualifierExpression != null) {
          res.append(convertPsiToText(p.getQualifierExpression)).append(".")
        }
        val name = p.getReferenceName
        if (ScalaNamesUtil.isKeyword(name)) {
          res.append("`").append(name).append("`")
        } else res.append(name)
        res.append(convertPsiToText(p.getParameterList))
      }
      case t: PsiTypeCastExpression => {
        res.append(convertPsiToText(t.getOperand)).append(".asInstanceOf[").
                append(convertPsiToText(t.getCastType)).append("]")
      }
      case t: PsiThisExpression => {
        if (t.getQualifier != null) {
          res.append(convertPsiToText(t.getQualifier)).append(".")
        }
        res.append("this")
      }
      case s: PsiSuperExpression => {
        if (s.getQualifier != null) {
          res.append(convertPsiToText(s.getQualifier)).append(".")
        }
        res.append("super")
      }
      case n: PsiNewExpression => {
        //todo:
      }
      //declarations
      case m: PsiMethod => {
        res.append(convertPsiToText(m.getModifierList)).append(" ")
        res.append(" def ")
        if (!m.isConstructor) res.append(m.getName)
        else res.append("this")
        res.append(convertPsiToText(m.getParameterList)).
                append(": ").append(convertPsiToText(m.getReturnTypeElement))
        if (m.getBody != null) {
          res.append(" = ")
          if (m.isConstructor) res.append("{\nthis()\n")
          res.append(convertPsiToText(m.getBody))
          if (m.isConstructor) res.append("\n}")
        }
      }
      case f: PsiField => {
        res.append(convertPsiToText(f.getModifierList)).append(" ")
        if (f.hasModifierProperty("final")) {
          res.append(" val ")
        } else res.append(" var ")
        res.append(f.getName).append(": ")
        res.append(convertPsiToText(f.getTypeElement))
        if (f.getInitializer != null) {
          res.append(" = ").append(convertPsiToText(f.getInitializer))
        }
      }
      case l: PsiLocalVariable => {
        res.append(convertPsiToText(l.getModifierList)).append(" ")
        if (l.hasModifierProperty("final")) {
          res.append(" val ")
        } else res.append(" var ")
        res.append(l.getName).append(": ")
        res.append(convertPsiToText(l.getTypeElement))
        if (l.getInitializer != null) {
          res.append(" = ").append(convertPsiToText(l.getInitializer))
        }
      }
      case p: PsiParameter => {
        res.append(p.getName).append(": ").append(convertPsiToText(p.getTypeElement))
      }
      case c: PsiClass => {
        var forClass = new HashSet[PsiMember]()
        var forObject = new HashSet[PsiMember]()
        for (method <- c.getAllMethods) {
          if (method.hasModifierProperty("static")) {
            forObject += method
          } else forClass += method
        }
        for (field <- c.getAllFields) {
          if (field.hasModifierProperty("static")) {
            forObject += field
          } else forClass += field
        }
        for (clazz <- c.getAllInnerClasses) {
          if (clazz.hasModifierProperty("static")) {
            forObject += clazz
          } else forClass += clazz
        }
        if (!forObject.isEmpty) {
          res.append(convertPsiToText(c.getModifierList)).append(" ")
          res.append("object ")
          res.append(c.getName)
          res.append(" {\n")
          for (memb <- forObject) {
            res.append(convertPsiToText(memb)).append("\n")
          }
          res.append("}")
        }
        res.append("\n")
        if (!forClass.isEmpty || forObject.isEmpty) {
          res.append(convertPsiToText(c.getModifierList)).append(" ")
          if (c.isInterface) res.append("trait ") else res.append("class ")
          res.append(c.getName)
          val typez = new ArrayBuffer[PsiJavaCodeReferenceElement]
          typez ++= c.getExtendsList.getReferenceElements
          typez ++= c.getImplementsList.getReferenceElements
          if (typez.length > 0) res.append(" extends ")
          for (tp <- typez) {
            res.append(convertPsiToText(tp)).append(" with ")
          }
          if (typez.length > 0) res.delete(res.length - 5, res.length)
          res.append(" {\n")
          for (memb <- forClass) {
            res.append(convertPsiToText(memb)).append("\n")
          }
          res.append("}")
        }
      }
      case p: PsiJavaCodeReferenceElement => {
        if (p.getQualifier != null) {
          res.append(convertPsiToText(p.getQualifier)).append(".")
        }
        val name = p.getReferenceName
        if (ScalaNamesUtil.isKeyword(name)) {
          res.append("`").append(name).append("`")
        } else res.append(name)
        res.append(convertPsiToText(p.getParameterList))
      }
      case p: PsiPackageStatement => {
        res.append("package ")
        res.append(convertPsiToText(p.getPackageReference))
      }
      case i: PsiImportStatement => {
        res.append("import ")
        res.append(convertPsiToText(i.getImportReference))
        if (i.isOnDemand) {
          res.append("._")
        }
      }
      case i: PsiImportStaticStatement => {
        res.append("improt ")
        res.append(convertPsiToText(i.getImportReference))
        if (i.isOnDemand) {
          res.append("._")
        }
      }
      case i: PsiImportList => {
        for (imp <- i.getAllImportStatements) {
          res.append(convertPsiToText(imp)).append("\n")
        }
      }
      case t: PsiTypeElement => {
        //todo:
      }
      case m: PsiModifierList => {
        //todo:
      }
      case w: PsiWhiteSpace => {
        res.append(w.getText)
      }
      case r: PsiReferenceParameterList => {
        //todo:
      }
      case p: PsiParameterList => {
        //todo:
      }
      case e => {
        throw new UnsupportedOperationException("PsiElement: " +  e + " is not supported for this" +
                " converter.")
      }
      //case e => res.append(e.toString)
    }
    return res.toString
  }

  def convertPsiToText(elements: Array[PsiElement]): String = {
    val res = new StringBuilder("")
    for (element <- elements) {
      res.append(convertPsiToText(element)).append("\n")
    }
    res.delete(res.length - 1, res.length)
    return res.toString
  }
}