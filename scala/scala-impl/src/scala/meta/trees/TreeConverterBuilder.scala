package scala.meta.trees

import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaPsiElement, ScalaRecursiveElementVisitor}
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.{ScDocComment, ScDocInlinedTag, ScDocSyntaxElement, ScDocTag}

import scala.collection.immutable.Seq
import scala.language.postfixOps
import scala.meta.Tree
import scala.{meta => m, Seq => _}

trait TreeConverterBuilder {
  self: TreeConverter =>


  def convert(elem:PsiElement): Tree = {
    var ret: m.Tree = null
    val v: ScalaRecursiveElementVisitor = new ScalaRecursiveElementVisitor {
      override def visitTypeAliasDefinition(alias: ScTypeAliasDefinition): Unit = super.visitTypeAliasDefinition(alias)

      override def visitElement(element: PsiElement): Unit = {
        super.visitElement(element)
      }

      override def visitTypeAlias(alias: ScTypeAlias): Unit = super.visitTypeAlias(alias)

      override def visitTypeAliasDeclaration(alias: ScTypeAliasDeclaration): Unit = super.visitTypeAliasDeclaration(alias)

      override def visitParameters(parameters: ScParameters): Unit = super.visitParameters(parameters)

      override def visitModifierList(modifierList: ScModifierList): Unit = super.visitModifierList(modifierList)

      override def visitConstructorInvocation(constrInvocation: ScConstructorInvocation): Unit = super.visitConstructorInvocation(constrInvocation)

      override def visitFunctionDefinition(fun: ScFunctionDefinition): Unit = {
        fun.body.get.accept(this)
        val body = ret.asInstanceOf[m.Term]
        ret = m.Defn.Def(convertMods(fun), toTermName(fun),
          fun.typeParameters.map(toTypeParams).toList,
          fun.paramClauses.clauses.map(convertParamClause).toList,
          fun.definedReturnType.toOption.map(toType(_)),
          body
        )
      }

      override def visitFunctionDeclaration(fun: ScFunctionDeclaration): Unit = super.visitFunctionDeclaration(fun)

      override def visitMacroDefinition(fun: ScMacroDefinition): Unit = super.visitMacroDefinition(fun)

      override def visitCatchBlock(c: ScCatchBlock): Unit = super.visitCatchBlock(c)

      override def visitFile(file: PsiFile): Unit = super.visitFile(file)

      //Override also visitReferenceExpression! and visitTypeProjection!
      override def visitReference(ref: ScReference): Unit = super.visitReference(ref)

      override def visitParameter(parameter: ScParameter): Unit = super.visitParameter(parameter)

      override def visitClassParameter(parameter: ScClassParameter): Unit = super.visitClassParameter(parameter)

      override def visitPatternDefinition(pat: ScPatternDefinition): Unit = super.visitPatternDefinition(pat)

      override def visitValueDeclaration(v: ScValueDeclaration): Unit = super.visitValueDeclaration(v)

      override def visitVariableDefinition(varr: ScVariableDefinition): Unit = super.visitVariableDefinition(varr)

      override def visitVariableDeclaration(varr: ScVariableDeclaration): Unit = super.visitVariableDeclaration(varr)

      override def visitVariable(varr: ScVariable): Unit = super.visitVariable(varr)

      override def visitValue(v: ScValue): Unit = super.visitValue(v)

      override def visitCaseClause(cc: ScCaseClause): Unit = super.visitCaseClause(cc)

      override def visitPattern(pat: ScPattern): Unit = super.visitPattern(pat)

      override def visitForBinding(forBinding: ScForBinding): Unit = super.visitForBinding(forBinding)

      override def visitGenerator(gen: ScGenerator): Unit = super.visitGenerator(gen)

      override def visitGuard(guard: ScGuard): Unit = super.visitGuard(guard)

      override def visitFunction(fun: ScFunction): Unit = super.visitFunction(fun)

      override def visitTypeDefinition(typedef: ScTypeDefinition): Unit = super.visitTypeDefinition(typedef)

      override def visitImportExpr(expr: ScImportExpr): Unit = super.visitImportExpr(expr)

      override def visitSelfInvocation(self: ScSelfInvocation): Unit = super.visitSelfInvocation(self)

      override def visitAnnotation(annotation: ScAnnotation): Unit = super.visitAnnotation(annotation)

      // Expressions
      override def visitExpression(expr: ScExpression): Unit = super.visitExpression(expr)

      override def visitThisReference(t: ScThisReference): Unit = super.visitThisReference(t)

      override def visitSuperReference(t: ScSuperReference): Unit = super.visitSuperReference(t)

      override def visitPostfixExpression(p: ScPostfixExpr): Unit = super.visitPostfixExpression(p)

      override def visitPrefixExpression(p: ScPrefixExpr): Unit = super.visitPrefixExpression(p)

      override def visitIf(stmt: ScIf): Unit = super.visitIf(stmt)

      override def visitLiteral(l: ScLiteral): Unit = super.visitLiteral(l)

      override def visitAssignment(stmt: ScAssignment): Unit = super.visitAssignment(stmt)

      override def visitMethodCallExpression(call: ScMethodCall): Unit = super.visitMethodCallExpression(call)

      override def visitGenericCallExpression(call: ScGenericCall): Unit = super.visitGenericCallExpression(call)

      override def visitInfixExpression(infix: ScInfixExpr): Unit = super.visitInfixExpression(infix)

      override def visitWhile(ws: ScWhile): Unit = super.visitWhile(ws)

      override def visitReturn(ret: ScReturn): Unit = super.visitReturn(ret)

      override def visitMatch(ms: ScMatch): Unit = super.visitMatch(ms)

      override def visitFor(expr: ScFor): Unit = super.visitFor(expr)

      override def visitDo(stmt: ScDo): Unit = super.visitDo(stmt)

      override def visitFunctionExpression(stmt: ScFunctionExpr): Unit = super.visitFunctionExpression(stmt)

      override def visitThrow(throwStmt: ScThrow): Unit = super.visitThrow(throwStmt)

      override def visitTry(tryStmt: ScTry): Unit = super.visitTry(tryStmt)

      override def visitParenthesisedExpr(expr: ScParenthesisedExpr): Unit = super.visitParenthesisedExpr(expr)

      override def visitNewTemplateDefinition(templ: ScNewTemplateDefinition): Unit = super.visitNewTemplateDefinition(templ)

      override def visitTypedStmt(stmt: ScTypedExpression): Unit = super.visitTypedStmt(stmt)

      override def visitTuple(tuple: ScTuple): Unit = super.visitTuple(tuple)

      override def visitBlockExpression(block: ScBlockExpr): Unit = super.visitBlockExpression(block)

      override def visitUnderscoreExpression(under: ScUnderscoreSection): Unit = super.visitUnderscoreExpression(under)

      override def visitConstrBlock(constr: ScConstrBlock): Unit = super.visitConstrBlock(constr)

      //type elements
      override def visitTypeElement(te: ScTypeElement): Unit = super.visitTypeElement(te)

      override def visitSimpleTypeElement(simple: ScSimpleTypeElement): Unit = super.visitSimpleTypeElement(simple)

      override def visitWildcardTypeElement(wildcard: ScWildcardTypeElement): Unit = super.visitWildcardTypeElement(wildcard)

      override def visitTupleTypeElement(tuple: ScTupleTypeElement): Unit = super.visitTupleTypeElement(tuple)

      override def visitParenthesisedTypeElement(parenthesised: ScParenthesisedTypeElement): Unit = super.visitParenthesisedTypeElement(parenthesised)

      override def visitParameterizedTypeElement(parameterized: ScParameterizedTypeElement): Unit = super.visitParameterizedTypeElement(parameterized)

      override def visitInfixTypeElement(infix: ScInfixTypeElement): Unit = super.visitInfixTypeElement(infix)

      override def visitFunctionalTypeElement(fun: ScFunctionalTypeElement): Unit = super.visitFunctionalTypeElement(fun)

      override def visitExistentialTypeElement(exist: ScExistentialTypeElement): Unit = super.visitExistentialTypeElement(exist)

      override def visitCompoundTypeElement(compound: ScCompoundTypeElement): Unit = super.visitCompoundTypeElement(compound)

      override def visitAnnotTypeElement(annot: ScAnnotTypeElement): Unit = super.visitAnnotTypeElement(annot)

      override def visitTypeVariableTypeElement(tvar: ScTypeVariableTypeElement): Unit = super.visitTypeVariableTypeElement(tvar)

      //scaladoc
      override def visitDocComment(s: ScDocComment): Unit = super.visitDocComment(s)

      override def visitScaladocElement(s: ScalaPsiElement): Unit = super.visitScaladocElement(s)

      override def visitWikiSyntax(s: ScDocSyntaxElement): Unit = super.visitWikiSyntax(s)

      override def visitInlinedTag(s: ScDocInlinedTag): Unit = super.visitInlinedTag(s)

      override def visitTag(s: ScDocTag): Unit = super.visitTag(s)

      //xml
      override def visitXmlStartTag(s: ScXmlStartTag): Unit = super.visitXmlStartTag(s)

      override def visitXmlEndTag(s: ScXmlEndTag): Unit = super.visitXmlEndTag(s)
    }
    elem.accept(v)
    ret
  }
}
