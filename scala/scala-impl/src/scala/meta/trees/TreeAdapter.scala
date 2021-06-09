package scala.meta.trees

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.{api => p, types => ptype}

import scala.collection.immutable.Seq
import scala.language.postfixOps
import scala.meta.{Defn, ScalaMetaBundle, Type}
import scala.meta.trees.error._
import scala.{meta => m}

trait TreeAdapter {
  self: TreeConverter =>

  def ideaToMeta(tree: PsiElement): m.Tree = {
    ProgressManager.checkCanceled()

    tree match {
      case t: ScValueDeclaration      => toVal(t)
      case t: ScVariableDeclaration   => toVar(t)
      case t: ScTypeAliasDeclaration  => toTypeDecl(t)
      case t: ScTypeAliasDefinition   => toTypeDefn(t)
      case t: ScFunctionDeclaration   => toFunDecl(t)
      case t: ScPatternDefinition     => toPatternDefinition(t)
      case t: ScVariableDefinition    => toVarDefn(t)
      case t: ScFunctionDefinition    => toFunDefn(t)
      case t: ScMacroDefinition       => toMacroDefn(t)
      case t: ScPrimaryConstructor    => ctor(Some(t))
      case t: ScTrait                 => toTrait(t)
      case t: ScClass                 => toClass(t)
      case t: ScObject                => toObject(t)
      case t: ScAnnotation            => toAnnot(t)
      case t: ScExpression            => expression(Some(t)).get
      case t: ScImportStmt            => m.Import(List(t.importExprs.map(imports):_*))

      case t: PsiClass => toClass(t)
      case t: PsiMethod => t ???

      case other => other ?!
    }
  }

  def toAnnotCtor(annot: ScAnnotation): m.Term.New =
    m.Term.New(toCtor(annot.constructorInvocation))

  def toMacroDefn(t: ScMacroDefinition): m.Defn.Macro =
    t.definedReturnType match {
      case Right(value) =>
        m.Defn.Macro(
          convertMods(t),
          toTermName(t),
          t.typeParameters.map(toTypeParams).toList,
          (t.paramClauses.clauses.map(convertParamClause)).toList,
          Option(toType(value)),
          t.macroImplReference.map(getQualifiedReference).get
        )
      case _ => unreachable(ScalaMetaBundle.message("macro.definition.must.have.return.type.defined"))
    }

  def toFunDefn(t: ScFunctionDefinition): m.Defn.Def = {
    m.Defn.Def(convertMods(t), toTermName(t),
      (t.typeParameters.map(toTypeParams)).toList,
      (t.paramClauses.clauses.map(convertParamClause)).toList,
      t.returnTypeElement.map(toType),
      expression(t.body).getOrElse(m.Term.Block(Nil))
    )
  }

  // Java conversion - beware: we cannot convert java method bodies, so just return a lazy ???
  def toMethodDefn(t: PsiMethod): m.Defn.Def = {
    t ???
  }

  def toVarDefn(t: ScVariableDefinition): m.Defn.Var = {
    m.Defn.Var(convertMods(t), t.pList.patterns.map(pattern).toList, t.declaredType.map(toType(_)), expression(t.expr))
  }

  def toFunDecl(t: ScFunctionDeclaration): m.Decl.Def = {
    m.Decl.Def(convertMods(t), toTermName(t), t.typeParameters.map(toTypeParams).toList,
      t.paramClauses.clauses.map(convertParamClause).toList,
      t.returnTypeElement.map(toType).getOrElse(toStdTypeName(ptype.api.Unit(t.projectContext))))
  }

  def toTypeDefn(t: ScTypeAliasDefinition): m.Defn.Type = {
    m.Defn.Type(convertMods(t), toTypeName(t), t.typeParameters.map(toTypeParams).toList, toType(t.aliasedTypeElement.get))
  }

  def toTypeDecl(t: ScTypeAliasDeclaration): m.Decl.Type = {
    m.Decl.Type(convertMods(t), toTypeName(t), t.typeParameters.map(toTypeParams).toList, typeBounds(t))
  }

  def toVar(t: ScVariableDeclaration): m.Decl.Var =
    m.Decl.Var(convertMods(t), t.getIdList.fieldIds.map { it => m.Pat.Var(toTermName(it)) }.toList, toType(t.typeElement.get))

  def toVal(t: ScValueDeclaration): m.Decl.Val =
    m.Decl.Val(convertMods(t), t.getIdList.fieldIds.map { it => m.Pat.Var(toTermName(it)) }.toList, toType(t.typeElement.get))

  def toTrait(t: ScTrait): m.Tree = {
    val defn = m.Defn.Trait(
      convertMods(t),
      toTypeName(t),
      t.typeParameters.map(toTypeParams).toList,
      m.Ctor.Primary(Nil, m.Name.Anonymous(), Nil),
      template(t.physicalExtendsBlock)
    )
    t.baseCompanion match {
      case Some(obj: ScObject) => m.Term.Block(List(defn, toObject(obj)))
      case _      => defn
    }
  }

  def toClass(c: ScClass): m.Tree = {
    val defn = m.Defn.Class(
      convertMods(c),
      toTypeName(c),
      c.typeParameters.map(toTypeParams).toList,
      ctor(c.constructor),
      template(c.physicalExtendsBlock)
    )
    c.baseCompanion match {
      case Some(obj: ScObject) => m.Term.Block(List(toObject(obj)))
      case _      => defn
    }
  }

  def toClass(c: PsiClass): Defn.Class = m.Defn.Class(
    convertMods(c.getModifierList),
    toTypeName(c),
    c.getTypeParameters.map(toTypeParams).toList,
    ctor(c),
    template(c.getAllMethods)
  )

  def toObject(o: ScObject): Defn.Object = m.Defn.Object(
    convertMods(o),
    toTermName(o),
    template(o.physicalExtendsBlock)
  )

  def ctor(pc: Option[ScPrimaryConstructor]): m.Ctor.Primary = {
    pc match {
      case Some(ctor) => m.Ctor.Primary(convertMods(ctor), toPrimaryCtorName(ctor), ctor.parameterList.clauses.map(convertParamClause).toList)
      case None => unreachable(ScalaMetaBundle.message("no.primary.constructor.in.class"))
    }
  }

  // FIXME: we don't have explicit information on what ctor has been used, so just select first one
  def ctor(c: PsiClass): m.Ctor.Primary = {
//    m.Ctor.Primary(Seq.empty, m.Ctor.Ref.Name(c.getName).withDenot())
     c ???
  }

  def caseClause(c: patterns.ScCaseClause): m.Case = {
    m.Case(pattern(c.pattern.get), c.guard.map(it => expression(it.expr.get)), expression(c.expr.get).asInstanceOf[m.Term.Block])
  }

  def pattern(pt: patterns.ScPattern): m.Pat = {
    import p.base.patterns._

    import m.Pat._
    def compose(lst: List[ScPattern]): m.Pat = lst match {
      case x :: Nil => pattern(x)
      case x :: xs  => Alternative(pattern(x), compose(xs))
      case _ => ???
    }
    // WHY??(((
    def arg(pt: patterns.ScPattern): m.Pat = pt match {
      case _: ScSeqWildcardPattern       =>  SeqWildcard()
      case _: ScWildcardPattern   =>  Wildcard()
      case ScStableReferencePattern(reference) => toTermName(reference)
      case t: ScPattern           => pattern(t)
    }
    pt match {
      case t: ScReferencePattern  => Var(toTermName(t))
      case t: ScConstructorPattern=> Extract(toTermName(t.ref), t.args.patterns.map(arg).toList)
      case t: ScNamingPattern     => Bind(Var(toTermName(t)), arg(t.named))
      case t@ ScTypedPattern(_: types.ScWildcardTypeElement) => Typed(if (t.isWildcard) Wildcard() else Var(toTermName(t)), Type.Placeholder(Type.Bounds(None, None)))
      case t@ ScTypedPattern(te)  => Typed(if (t.isWildcard) Wildcard() else Var(toTermName(t)), toType(te))
      case ScLiteralPattern(scLiteral)    =>  literal(scLiteral)
      case t: ScTuplePattern      =>  Tuple(t.patternList.get.patterns.map(pattern).toList)
      case t: ScWildcardPattern   =>  Wildcard()
      case t: ScCompositePattern  =>  compose(t.subpatterns.toList)
      case t: ScInfixPattern      =>  ExtractInfix(pattern(t.left), toTermName(t.operation), t.rightOption.map(pt => List(pattern(pt))).getOrElse(Nil))
      case ScStableReferencePattern(reference) => toTermName(reference)
      case t: ScPattern => t ?!
    }
  }

  def template(t: p.toplevel.templates.ScExtendsBlock): m.Template = {
    val exprs   = t.templateBody.map (it => List(it.exprs.map(expression): _*))
    val members = t.templateBody.map (it => it.members.map(ideaToMeta(_).asInstanceOf[m.Stat]).toList)
    val early   = t.earlyDefinitions.map (it => it.members.map(ideaToMeta(_).asInstanceOf[m.Stat]).toList).getOrElse(Nil)
    val ctor = t.templateParents
      .flatMap(_.children.find(_.isInstanceOf[ScConstructorInvocation]))
      .map(c=>toCtor(c.asInstanceOf[ScConstructorInvocation]))
      .toList
    val mixins = t.templateParents.map(x => x.typeElementsWithoutConstructor.map(toType).map(toCtor).toList).getOrElse(Nil)
    val self    = t.selfType match {
      case Some(tpe: ptype.ScType) =>
        m.Self(m.Term.Name("self"), Some(toType(tpe)))
      case None =>
        m.Self(m.Name.Anonymous(), None)
    }
//     FIXME: preserve expression and member order
    val stats = (exprs, members) match {
      case (Some(exp), Some(hld)) => hld ++ exp
      case (Some(exp), None)  => exp
      case (None, Some(hld))  => hld
      case (None, None)       => Nil
    }
    m.Template(early, ctor ++ mixins, self, stats)
  }

  // Java conversion
  def template(arr: Array[PsiMethod]): m.Template = {
    ???
  }

  def newTemplate(t: ScTemplateDefinition): m.Template = {
    val early = t.extendsBlock.earlyDefinitions
      .map (it => it.members.map(ideaToMeta(_).asInstanceOf[m.Stat]).toList)
      .getOrElse(Nil)
    val ctor = t.extendsBlock.templateParents match {
      case Some(parents) =>
        parents.constructorInvocation match {
          case Some(constrInvocation) => toCtor(constrInvocation)
          case None => unreachable(ScalaMetaBundle.message("no.constructor.found.in.class", t.qualifiedName))
        }
      case None => unreachable(ScalaMetaBundle.message("class.has.no.parents", t.qualifiedName))
    }
    val self    = t.selfType match {
      case Some(tpe: ptype.ScType) => m.Self(m.Name.Anonymous(), Some(toType(tpe)))
      case None                    => m.Self(m.Name.Anonymous(), None)
    }
    m.Template(early, List(ctor), self, Nil)
  }

  def toCtor(constrInvocation: ScConstructorInvocation): m.Init = {
    val tp = toType(constrInvocation.typeElement)
    val ctorCall = toCtor(tp)
    if (constrInvocation.arguments.isEmpty) {
      ctorCall
    } else {
      constrInvocation.arguments.foldLeft(ctorCall) { (result, exprList) =>
        val args = exprList.exprs.map(callArgs).toList
        result.copy(argss = result.argss :+ args)
      }
    }
  }

  private def toCtor(tp: m.Type): m.Init =
    m.Init(tp, m.Name.Anonymous(), List.empty)

  def toAnnot(annot: ScAnnotation): m.Mod.Annot =
    m.Mod.Annot(toCtor(annot.constructorInvocation))

  def expression(e: ScExpression): m.Term = {
    import p.expr._
    import p.expr.xml._
    e match {
      case t: ScLiteral =>
        import m.Lit

        literal(t) match {
          case value: Lit.Symbol if paradiseCompatibilityHacks => // apparently, Lit.Symbol is no more in paradise
            m.Term.Apply(
              m.Term.Select(
                m.Term.Name("scala"),
                m.Term.Name("Symbol")
              ),
              List(Lit.String(value.toString)) // symbol literals in meta contain a string as their value
            )
          case value => value
        }
      case _: ScUnitExpr =>
        m.Lit.Unit()
      case t: ScReturn =>
        m.Term.Return(expression(t.expr).getOrElse(m.Lit.Unit()))
      case t: ScConstrBlockExpr =>
        t ???
      case t: ScBlockExpr if t.hasCaseClauses =>
        m.Term.PartialFunction(t.caseClauses.get.caseClauses.map(caseClause).toList)
      case t: ScBlock =>
        m.Term.Block(t.statements.map(ideaToMeta(_).asInstanceOf[m.Stat]).toList)
      case t: ScMethodCall =>
        t.withSubstitutionCaching { tp =>
          m.Term.Apply(expression(t.getInvokedExpr), t.args.exprs.map(callArgs).toList)
        }
      case ScInfixExpr.withAssoc(base, operation, argument) =>
        m.Term.ApplyInfix(expression(base), toTermName(operation), Nil, List(expression(argument)))
      case t: ScPrefixExpr =>
        m.Term.ApplyUnary(toTermName(t.operation), expression(t.operand))
      case t: ScPostfixExpr =>
        t.withSubstitutionCaching { tp =>
          m.Term.Apply(m.Term.Select(expression(t.operand), toTermName(t.operation)), Nil)
        }
      case t: ScIf =>
        val unit = m.Lit.Unit()
        m.Term.If(
          expression(t.condition.get),
          t.thenExpression.map(expression).getOrElse(unit),
          t.elseExpression.map(expression).getOrElse(unit)
        )
      case t: ScDo =>
        m.Term.Do(t.body.map(expression).getOrElse(m.Term.Placeholder()),
            t.condition.map(expression).getOrElse(m.Term.Placeholder()))
      case t: ScWhile =>
        m.Term.While(t.condition.map(expression).getOrElse(throw new AbortException(Some(t), ScalaMetaBundle.message("empty.while.condition"))),
            t.expression.map(expression).getOrElse(m.Term.Block(Nil)))
      case t: ScFor =>
        m.Term.For(t.enumerators.map(enumerators).getOrElse(Nil),
          t.body.map(expression).getOrElse(m.Term.Block(Nil)))
      case t: ScMatch =>
        m.Term.Match(expression(t.expression.get), t.clauses.map(caseClause).toList)
      case t: ScReferenceExpression if t.qualifier.isDefined =>
        m.Term.Select(expression(t.qualifier.get), toTermName(t))
      case t: ScReferenceExpression =>
        toTermName(t)
      case t: ScSuperReference =>
        m.Term.Super(t.drvTemplate.map(ind).getOrElse(m.Name.Anonymous()), getSuperName(t))
      case t: ScThisReference =>
        m.Term.This(t.reference.map(ind).getOrElse(m.Name.Anonymous()))
      case t: ScNewTemplateDefinition =>
        m.Term.New(newTemplate(t).inits.head)
      case t: ScFunctionExpr =>
        m.Term.Function(t.parameters.map(convertParam).toList, expression(t.result).get)
      case t: ScTuple =>
        m.Term.Tuple(t.exprs.map(expression).toList)
      case t: ScThrow =>
        m.Term.Throw(expression(t.expression).getOrElse(throw new AbortException(t, ScalaMetaBundle.message("empty.throw.expression"))))
      case t@ScTry(tryBlock, catchBlock, finallyBlock) =>
        val fblk = finallyBlock.collect  {
          case ScFinallyBlock(expr) => expression(expr)
        }
        def tryTerm = expression(tryBlock).getOrElse(unreachable)
        val res = catchBlock match {
          case Some(ScCatchBlock(clauses)) if clauses.caseClauses.size == 1 =>
            m.Term.TryWithHandler(tryTerm, clauses.caseClause.expr.map(expression).getOrElse(unreachable), fblk)
          case Some(ScCatchBlock(clauses)) =>
            m.Term.Try(tryTerm, clauses.caseClauses.map(caseClause).toList, fblk)
          case None =>
            m.Term.Try(tryTerm, List.empty, fblk)
          case _ => unreachable
        }
        res
      case t: ScGenericCall =>
        m.Term.ApplyType(ideaToMeta(t.referencedExpr).asInstanceOf[m.Term], t.arguments.map(toType).toList)
      case t: ScParenthesisedExpr =>
        t.innerElement.map(expression).getOrElse(unreachable)
      case t: ScAssignment =>
        m.Term.Assign(expression(t.leftExpression).asInstanceOf[m.Term.Ref], expression(t.rightExpression.get))
      case _: ScUnderscoreSection =>
        m.Term.Placeholder()
      case t: ScTypedExpression =>
        m.Term.Ascribe(expression(t.expr), t.typeElement.map(toType).getOrElse(unreachable))
      case t: ScXmlExpr =>
        t ???
      case other: ScalaPsiElement => other ?!
    }
  }

  def callArgs(e: ScExpression): m.Term = {
    e match {
      case t: ScAssignment => m.Term.Assign(toTermName(t.leftExpression), expression(t.rightExpression).get)
      case _: ScUnderscoreSection => m.Term.Placeholder()
      case t: ScTypedExpression if t.isSequenceArg => m.Term.Repeated(expression(t.expr))
      case _ => expression(e)
    }
  }

  def enumerators(en: ScEnumerators): List[m.Enumerator] = {
    def toEnumerator(nm: ScEnumerator): m.Enumerator = {
      nm match {
        case e: ScGenerator =>
          val expr = e.expr.getOrElse(unreachable(ScalaMetaBundle.message("generator.has.no.expression")))
          m.Enumerator.Generator(pattern(e.pattern), expression(expr))
        case e: ScGuard =>
          val expr = e.expr.getOrElse(unreachable(ScalaMetaBundle.message("guard.has.no.condition")))
          m.Enumerator.Guard(expression(expr))
        case e: ScForBinding =>
          val expr = e.expr.getOrElse(unreachable(ScalaMetaBundle.message("forbinding.has.no.expression")))
          m.Enumerator.Val(pattern(e.pattern), expression(expr))
        case _ => unreachable
      }
    }
    en.children.collect { case enum: ScEnumerator => enum }.map(toEnumerator).toList
  }

  def toParams(argss: List[ScArgumentExprList]): List[List[m.Term.Param]] = {
    argss.map { args =>
      args.matchedParameters.toList map { case (_, param) =>
        m.Term.Param(param.psiParam.map(p => convertMods(p.getModifierList)).getOrElse(List.empty), toParamName(param), Some(toType(param.paramType)), None)
      }
    }
  }

  def expression(tree: Option[ScExpression]): Option[m.Term] = {
    tree match {
      case Some(_: ScUnderscoreSection) => None
      case Some(expr) => Some(expression(expr))
      case None => None
    }
  }

  def getQualifier(q: ScStableCodeReference): m.Term.Ref = {
    q.pathQualifier match {
      case Some(_: ScSuperReference) =>
        m.Term.Select(m.Term.Super(m.Name.Anonymous(), m.Name.Anonymous()), toTermName(q))
      case Some(_: ScThisReference) =>
        m.Term.Select(m.Term.This(m.Name.Anonymous()), toTermName(q))
      case Some(parent:ScStableCodeReference) =>
        m.Term.Select(getQualifier(parent), toTermName(q))
      case None        => toTermName(q)
      case Some(other) => other ?!
    }
  }

  def getQualifiedReference(q: ScStableCodeReference): m.Term.Ref = {
    q.pathQualifier match {
      case None => toTermName(q)
      case Some(_) => m.Term.Select(getQualifier(q), toTermName(q))
    }
  }

  def imports(t: p.toplevel.imports.ScImportExpr):m.Importer = {
    def selector(sel: p.toplevel.imports.ScImportSelector): m.Importee =
      if (sel.isWildcardSelector) m.Importee.Wildcard()
      else {
        val importedName = sel.importedName.getOrElse {
          throw new AbortException(ScalaMetaBundle.message("imported.name.is.null"))
        }
        val reference = sel.reference.getOrElse {
          throw new AbortException(ScalaMetaBundle.message("reference.is.null"))
        }

        if (sel.isAliasedImport && importedName == "_")
          m.Importee.Unimport(ind(reference))
        else if (sel.isAliasedImport)
          m.Importee.Rename(m.Name.Indeterminate(reference.qualName), m.Name.Indeterminate(importedName))
        else
          m.Importee.Name(m.Name.Indeterminate(importedName))
      }
    if (t.selectors.nonEmpty) {
      val importees = t.selectors.map(selector).toList
      m.Importer(getQualifier(t.qualifier.get), importees)
    } else if (t.hasWildcardSelector)
      m.Importer(getQualifier(t.qualifier.get), List(m.Importee.Wildcard()))
    else
      m.Importer(getQualifier(t.qualifier.get), List(m.Importee.Name(m.Name.Indeterminate(t.importedNames.head))))
  }

  def literal(literal: ScLiteral): m.Lit = {
    import m.Lit._

    literal.getValue match {
      case value: Integer => Int(value)
      case value: java.lang.Long => Long(value)
      case value: java.lang.Float => Float(value)
      case value: java.lang.Double => Double(value)
      case value: java.lang.Boolean => Boolean(value)
      case value: Character => Char(value)
      case value: java.lang.Byte => Byte(value)
      case value: java.lang.String => String(value)
      case value: scala.Symbol => Symbol(value)
      case null => Null()
      case _ => literal ?!
    }
  }

  def toPatternDefinition(t: ScPatternDefinition): m.Tree = {
    if (t.bindings.exists(_.isVal))
      m.Defn.Val(convertMods(t), t.pList.patterns.map(pattern).toList, t.typeElement.map(toType), expression(t.expr).get)
    else if (t.bindings.exists(_.isVar))
      m.Defn.Var(convertMods(t), t.pList.patterns.map(pattern).toList, t.typeElement.map(toType), expression(t.expr))
    else
      unreachable
  }

  def convertMods(t: p.toplevel.ScModifierListOwner): List[m.Mod] = {
    def extractClassParameter(param: params.ScClassParameter): Seq[m.Mod] = {
      if      (param.isVar) Seq(m.Mod.VarParam())
      else if (param.isVal) Seq(m.Mod.ValParam())
      else Seq.empty
    }
    if (t.getModifierList == null) return Nil // workaround for 9ec0b8a44
    val name = t.getModifierList.accessModifier match {
      case Some(mod) => mod.idText match {
        case Some(qual) => m.Name.Indeterminate(qual)
        case None       => m.Name.Anonymous()
      }
      case None => m.Name.Anonymous()
    }
    val classParam = t match {
      case param: params.ScClassParameter => extractClassParameter(param)
      case _ => Seq.empty
    }

    val common = for {
      modifier <- t.getModifierList.accessModifier.toSeq
      term = if (modifier.isThis) m.Term.This(name)
      else name

      newTerm = if (modifier.isPrivate) m.Mod.Private(term)
      else m.Mod.Protected(term)
    } yield newTerm

    val caseMod = if (t.hasModifierPropertyScala("case")) Seq(m.Mod.Case()) else Nil
    val finalMod = if (t.hasModifierPropertyScala("final")) Seq(m.Mod.Final()) else Nil
    val implicitMod = if(t.hasModifierPropertyScala("implicit")) Seq(m.Mod.Implicit()) else Nil
    val sealedMod = if (t.hasModifierPropertyScala("sealed")) Seq(m.Mod.Sealed()) else Nil
    val annotations: Seq[m.Mod.Annot] = t match {
      case ah: ScAnnotationsHolder => Seq(ah.annotations.filterNot(_ == annotationToSkip).map(toAnnot):_*)
      case _ => Seq.empty
    }
    val overrideMod = if (t.hasModifierProperty("override")) Seq(m.Mod.Override()) else Nil
    (annotations ++ implicitMod ++ sealedMod ++ finalMod ++ caseMod ++ overrideMod ++ common ++ classParam).toList
  }

  // Java conversion
  def convertMods(t: PsiModifierList): List[m.Mod] = {
    val mods = scala.collection.mutable.ListBuffer[m.Mod]()
    if (t.hasModifierProperty("private"))    mods += m.Mod.Private(m.Name.Indeterminate.apply("this"))
    if (t.hasModifierProperty("protected"))  mods += m.Mod.Protected(m.Name.Indeterminate.apply("this"))
    if (t.hasModifierProperty("abstract"))   mods += m.Mod.Abstract()
    mods.toList
  }

  def convertParamClause(paramss: params.ScParameterClause): List[m.Term.Param] = {
    paramss.parameters.map(convertParam).toList
  }

  protected def convertParam(param: params.ScParameter): m.Term.Param = {
    val mods = convertMods(param) ++ (if (param.isImplicitParameter) Seq(m.Mod.Implicit()) else Seq.empty)
    val default = param.getActualDefaultExpression.map(expression)
    if (param.isVarArgs)
      m.Term.Param(mods, toTermName(param), param.typeElement.map(tp => m.Type.Repeated(toType(tp))), default)
    else
      m.Term.Param(mods, toTermName(param), param.typeElement.map(toType), default)
  }
}
