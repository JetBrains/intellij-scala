package scala.meta.trees

import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.{ScDocComment, ScDocInlinedTag, ScDocSyntaxElement, ScDocTag}

import scala.collection.immutable.Seq
import scala.language.postfixOps
import scala.{meta=>m}
import scala.{Seq => _}

trait TreeConverterBuilder {
  self: TreeConverter =>


  def convert(elem:PsiElement) = {
    val v = new ScalaRecursiveElementVisitor {
      var ret: m.Tree = _

      override def visitTypeAliasDefinition(alias: ScTypeAliasDefinition) = super.visitTypeAliasDefinition(alias)

      override def visitElement(element: PsiElement) = {
        super.visitElement(element)
      }

      override def visitTypeAlias(alias: ScTypeAlias) = super.visitTypeAlias(alias)

      override def visitTypeAliasDeclaration(alias: ScTypeAliasDeclaration) = super.visitTypeAliasDeclaration(alias)

      override def visitParameters(parameters: ScParameters) = super.visitParameters(parameters)

      override def visitModifierList(modifierList: ScModifierList) = super.visitModifierList(modifierList)

      override def visitConstructor(constr: ScConstructor) = super.visitConstructor(constr)

      override def visitFunctionDefinition(fun: ScFunctionDefinition) = {
        fun.body.get.accept(this)
        val body = ret.asInstanceOf[m.Term]
        ret = m.Defn.Def(convertMods(fun), toTermName(fun),
          Seq(fun.typeParameters map toTypeParams:_*),
          Seq(fun.paramClauses.clauses.map(convertParamClause):_*),
          fun.definedReturnType.map(toType(_)).toOption,
          body
        )
      }

      override def visitFunctionDeclaration(fun: ScFunctionDeclaration) = super.visitFunctionDeclaration(fun)

      override def visitMacroDefinition(fun: ScMacroDefinition) = super.visitMacroDefinition(fun)

      override def visitCatchBlock(c: ScCatchBlock) = super.visitCatchBlock(c)

      override def visitFile(file: PsiFile) = super.visitFile(file)

      //Override also visitReferenceExpression! and visitTypeProjection!
      override def visitReference(ref: ScReferenceElement) = super.visitReference(ref)

      override def visitParameter(parameter: ScParameter) = super.visitParameter(parameter)

      override def visitClassParameter(parameter: ScClassParameter) = super.visitClassParameter(parameter)

      override def visitPatternDefinition(pat: ScPatternDefinition) = super.visitPatternDefinition(pat)

      override def visitValueDeclaration(v: ScValueDeclaration) = super.visitValueDeclaration(v)

      override def visitVariableDefinition(varr: ScVariableDefinition) = super.visitVariableDefinition(varr)

      override def visitVariableDeclaration(varr: ScVariableDeclaration) = super.visitVariableDeclaration(varr)

      override def visitVariable(varr: ScVariable) = super.visitVariable(varr)

      override def visitValue(v: ScValue) = super.visitValue(v)

      override def visitCaseClause(cc: ScCaseClause) = super.visitCaseClause(cc)

      override def visitPattern(pat: ScPattern) = super.visitPattern(pat)

      override def visitEnumerator(enum: ScEnumerator) = super.visitEnumerator(enum)

      override def visitGenerator(gen: ScGenerator) = super.visitGenerator(gen)

      override def visitGuard(guard: ScGuard) = super.visitGuard(guard)

      override def visitFunction(fun: ScFunction) = super.visitFunction(fun)

      override def visitTypeDefinition(typedef: ScTypeDefinition) = super.visitTypeDefinition(typedef)

      override def visitImportExpr(expr: ScImportExpr) = super.visitImportExpr(expr)

      override def visitSelfInvocation(self: ScSelfInvocation) = super.visitSelfInvocation(self)

      override def visitAnnotation(annotation: ScAnnotation) = super.visitAnnotation(annotation)

      // Expressions
      override def visitExpression(expr: ScExpression) = super.visitExpression(expr)

      override def visitThisReference(t: ScThisReference) = super.visitThisReference(t)

      override def visitSuperReference(t: ScSuperReference) = super.visitSuperReference(t)

      override def visitPostfixExpression(p: ScPostfixExpr) = super.visitPostfixExpression(p)

      override def visitPrefixExpression(p: ScPrefixExpr) = super.visitPrefixExpression(p)

      override def visitIfStatement(stmt: ScIfStmt) = super.visitIfStatement(stmt)

      override def visitLiteral(l: ScLiteral) = super.visitLiteral(l)

      override def visitAssignmentStatement(stmt: ScAssignStmt) = super.visitAssignmentStatement(stmt)

      override def visitMethodCallExpression(call: ScMethodCall) = super.visitMethodCallExpression(call)

      override def visitGenericCallExpression(call: ScGenericCall) = super.visitGenericCallExpression(call)

      override def visitInfixExpression(infix: ScInfixExpr) = super.visitInfixExpression(infix)

      override def visitWhileStatement(ws: ScWhileStmt) = super.visitWhileStatement(ws)

      override def visitReturnStatement(ret: ScReturnStmt) = super.visitReturnStatement(ret)

      override def visitMatchStatement(ms: ScMatchStmt) = super.visitMatchStatement(ms)

      override def visitForExpression(expr: ScForStatement) = super.visitForExpression(expr)

      override def visitDoStatement(stmt: ScDoStmt) = super.visitDoStatement(stmt)

      override def visitFunctionExpression(stmt: ScFunctionExpr) = super.visitFunctionExpression(stmt)

      override def visitThrowExpression(throwStmt: ScThrowStmt) = super.visitThrowExpression(throwStmt)

      override def visitTryExpression(tryStmt: ScTryStmt) = super.visitTryExpression(tryStmt)

      override def visitExprInParent(expr: ScParenthesisedExpr) = super.visitExprInParent(expr)

      override def visitNewTemplateDefinition(templ: ScNewTemplateDefinition) = super.visitNewTemplateDefinition(templ)

      override def visitTypedStmt(stmt: ScTypedStmt) = super.visitTypedStmt(stmt)

      override def visitTupleExpr(tuple: ScTuple) = super.visitTupleExpr(tuple)

      override def visitBlockExpression(block: ScBlockExpr) = super.visitBlockExpression(block)

      override def visitUnderscoreExpression(under: ScUnderscoreSection) = super.visitUnderscoreExpression(under)

      override def visitConstrBlock(constr: ScConstrBlock) = super.visitConstrBlock(constr)

      //type elements
      override def visitTypeElement(te: ScTypeElement) = super.visitTypeElement(te)

      override def visitSimpleTypeElement(simple: ScSimpleTypeElement) = super.visitSimpleTypeElement(simple)

      override def visitWildcardTypeElement(wildcard: ScWildcardTypeElement) = super.visitWildcardTypeElement(wildcard)

      override def visitTupleTypeElement(tuple: ScTupleTypeElement) = super.visitTupleTypeElement(tuple)

      override def visitParenthesisedTypeElement(parenthesised: ScParenthesisedTypeElement) = super.visitParenthesisedTypeElement(parenthesised)

      override def visitParameterizedTypeElement(parameterized: ScParameterizedTypeElement) = super.visitParameterizedTypeElement(parameterized)

      override def visitInfixTypeElement(infix: ScInfixTypeElement) = super.visitInfixTypeElement(infix)

      override def visitFunctionalTypeElement(fun: ScFunctionalTypeElement) = super.visitFunctionalTypeElement(fun)

      override def visitExistentialTypeElement(exist: ScExistentialTypeElement) = super.visitExistentialTypeElement(exist)

      override def visitCompoundTypeElement(compound: ScCompoundTypeElement) = super.visitCompoundTypeElement(compound)

      override def visitAnnotTypeElement(annot: ScAnnotTypeElement) = super.visitAnnotTypeElement(annot)

      override def visitTypeVariableTypeElement(tvar: ScTypeVariableTypeElement) = super.visitTypeVariableTypeElement(tvar)

      //scaladoc
      override def visitDocComment(s: ScDocComment) = super.visitDocComment(s)

      override def visitScaladocElement(s: ScalaPsiElement) = super.visitScaladocElement(s)

      override def visitWikiSyntax(s: ScDocSyntaxElement) = super.visitWikiSyntax(s)

      override def visitInlinedTag(s: ScDocInlinedTag) = super.visitInlinedTag(s)

      override def visitTag(s: ScDocTag) = super.visitTag(s)

      //xml
      override def visitXmlStartTag(s: ScXmlStartTag) = super.visitXmlStartTag(s)

      override def visitXmlEndTag(s: ScXmlEndTag) = super.visitXmlEndTag(s)
    }
    elem.accept(v)
    v.ret
  }
}
