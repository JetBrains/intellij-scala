// Copy of org.jetbrains.plugins.scala.text.ClassPrinter

package org.jetbrains.plugins.scala.semantic

import com.intellij.psi.{PsiElement, PsiFile, PsiMember, PsiMethod, PsiNamedElement, PsiPackage}
import org.jetbrains.plugins.scala.annotator.{ScalaAnnotator, template}
import org.jetbrains.plugins.scala.extensions.{&, IterableOnceExt, ObjectExt, Parent, PsiClassExt, PsiElementExt, PsiMemberExt, PsiNamedElementExt, ReferenceTarget}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.{InvocationDetails, InvocationDetailsOwner}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{Sc3TypedPattern, ScCompositePattern, ScExtractorPattern, ScLiteralPattern, ScNamingPattern, ScPattern, ScReferencePattern, ScStableReferencePattern, ScTuplePattern, ScTypedPattern, ScWildcardPattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSelfTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, ScConstructorInvocation, ScInterpolatedStringLiteral, ScLiteral, ScPrimaryConstructor, ScReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScSignatureClause.{TermClause, TypeClause}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter, ScParameterClause, ScTypeParam, ScTypeParamClause}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScEnumCase, ScExtension, ScFunction, ScFunctionDefinition, ScSignatureClause, ScTypeAlias, ScTypeAliasDefinition, ScValue, ScValueOrVariable, ScValueOrVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScEnum, ScGiven, ScGivenDefinition, ScMember, ScObject, ScTemplateDefinition, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScNamedElement, ScPackaging, ScTypeBoundsOwner, ScTypeParametersOwner, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.ValueClassType.isValueClass
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.api.{FunctionType, ParameterizedType, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{Parameter, ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.psi.types.{Context, ScLiteralType, ScType, ScTypeExt, TypePresentationContext}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.project.ScalaFeatures.forPsiOrDefault
import org.jetbrains.plugins.scala.semantic.ClassPrinter.{GeneratedClassTag, Keywords, isIdentifier}

import scala.annotation.tailrec

private class ClassPrinter(isScala3: Boolean, extendsSeparator: String = " ", withPrivate: Boolean = true, normalize: Boolean = false)(highlight: PsiElement => Seq[String]) {
  def printTo(sb: StringBuilder, cls: ScTypeDefinition, indent: String, listener: CharSequence => Unit): Unit = {
    val annotations = cls.annotations.map(a => "\n" + indent + textOf(a)).mkString

    val modifiers = {
      val s = textOfModifiers(cls, (if (cls.isObject) ScalaPsiUtil.superValsSignatures(cls) else ScalaPsiUtil.superTypeSignatures(cls)).nonEmpty)
      if (normalize && cls.is[ScClass] && (cls.hasModifierPropertyScala("implicit") || isValueClass(cls))) s.replace("final ", "")
      else if (normalize && cls.is[ScObject] && cls.hasModifierPropertyScala("case")) s.replace("final ", "")
      else s
    }

    val keyword = cls match {
      case _: ScEnum => "enum"
      case _: ScTrait => "trait"
      case _: ScClass => "class"
      case _: ScObject => "object"
      case _: ScGiven => "given"
      case _ => ""
    }

    val isGiven = cls.isInstanceOf[ScGiven]

    val isAnonymous = isGiven && cls.name.startsWith("given_") // .isAnonymous?

    val name = if (isAnonymous) "" else normalized(cls.name)

    val tps = if (cls.typeParameters.isEmpty) "" else cls.typeParameters.map(textOf).mkString("[", ", ", "]")

    // TODO Don't add "Foo.this." in primary constructors, see ScalaTypePresentation.innerTypeText, SCL-25555
    val ps = cls.constructors.filterByType[ScPrimaryConstructor].map(textOf(_, inCaseClass = modifiers.contains("case")).replace(name + ".this.", "")).mkString

    val parents = {
      val parentClauses = cls.extendsBlock.templateParents.map(_.parentClauses).getOrElse(Seq.empty)
      val classParent =
        if (normalize && isScala3 && !cls.isInterface && cls.superClass.isEmpty) cls.allSupers.find(!_.isInterface).filter(_.qualifiedName != "java.lang.Object").map(ScDesignatorType(_)).toList
        else Seq.empty
      // TODO Don't add "Foo.this." in class parents, see ScalaTypePresentation.innerTypeText, SCL-25555
      if (parentClauses.isEmpty) "" else (if (isGiven) (if (isAnonymous && tps.isEmpty && ps.isEmpty) "" else ": ") else s"${extendsSeparator}extends ") +
        (classParent.map(textOf(_, parens = 1)) ++ parentClauses.map(textOfConstructorInvocation(_, indent, emptyParens = false))).mkString(if (cls.isScala3 && !isGiven) ", " else s"${extendsSeparator}with ").replace(name + ".this.", "")
    }

    val derivations = {
      val refs = cls.extendsBlock.derivesClause.map(_.derivedReferences).getOrElse(Seq.empty)
      val fqns = refs.map(_.resolve()).collect { case f: ScTemplateDefinition => f.qualifiedName }
      if (fqns.isEmpty) "" else s"${extendsSeparator}derives " + fqns.mkString(", ")
    }

    val selfType = cls.selfType.map(t => s" ${cls.selfTypeElement.map(_.name).getOrElse("this")}: " + textOf(t) + " =>").mkString

    val givenClauses = cls match {
      case g: ScGivenDefinition => g.clauses.map(_.clauses).getOrElse(Seq.empty).map(textOf(_, inPrivateConstructor = false, inCaseClass = false)).mkString
      case _ => ""
    }

    sb ++= annotations + "\n" + indent + modifiers + keyword + " " + name + tps + ps + givenClauses + parents + derivations + (if (isGiven) " with" else "")
    listener(sb)

    sb ++= " {" + selfType

    val previousLength = sb.length

    printTo(sb, cls.extendsBlock, indent, listener)

    cls match {
      case e: ScEnum =>
        e.cases.foreach { c =>
          sb ++= textOf(c, indent)
          listener(sb)
        }
      case _ =>
    }

    if (sb.length > previousLength) {
      sb ++= indent + "}"
    } else {
      if (selfType.isEmpty) {
        sb.setLength(sb.length - 2)
      } else {
        sb ++= "\n"
        sb ++= indent + "}"
      }
    }

    sb ++= highlighted(cls)("")
    listener(sb)

    sb ++= "\n"
  }

  private def printTo(sb: StringBuilder, extendsBlock: ScExtendsBlock, indent: String, listener: CharSequence => Unit): Unit =
    extendsBlock.templateBody.map(_.children.toSeq).getOrElse(Seq.empty).foreach {
      case m: ScMember if withPrivate || !isPrivate(m) => m match {
        case f: ScFunction =>
          sb ++= textOf(f, indent)
        case v: ScValueOrVariable =>
          sb ++= textOf(v, v.declaredElements.head, indent)
        case t: ScTypeAlias =>
          sb ++= textOf(t, indent)
        case t: ScExtension =>
          sb ++= textOf(t, indent)
        case td: ScTypeDefinition =>
          printTo(sb, td, indent + "  ", listener)
      }
      listener(sb)
      case e: ScExpression =>
        sb ++= "\n" + indent + "  " + textOfExpression(e, indent) + "\n"
        listener(sb)
      case _ =>
    }

  private def isPrivate(e: ScModifierListOwner): Boolean =
    e.getModifierList.accessModifier.exists(_.isUnqualifiedPrivateOrThis)

  private def textOf(pc: ScPrimaryConstructor, inCaseClass: Boolean): String = highlighted(pc) {
    val modifiers = textOfModifiers(pc)
    val inPrivateConstructor = isPrivate(pc)
    val clauses = {
      val signatureClausesInParameterList = pc.signatureClauses.filter {
        case TypeClause(clause) => !pc.leadingTypeParametersClause.contains(clause)
        case TermClause(_)      => true
      }
      textOf(signatureClausesInParameterList, inPrivateConstructor, inCaseClass)
    }
    val annotations = pc.annotations.map(textOf(_, emptyParens = clauses.nonEmpty && modifiers.isEmpty)).mkString(" ")
    val s = (if (annotations.isEmpty) "" else " " + annotations) +
      (if (modifiers.isEmpty) "" else " " + modifiers) +
      (if (annotations.nonEmpty && modifiers.isEmpty) " " else "") +
      (if ((annotations.nonEmpty || modifiers.nonEmpty) && clauses.isEmpty) "()" else clauses)
    if (normalize && !inCaseClass && s == "()") "" else s
  }

  private def textOf(c: ScEnumCase, indent: String): String = highlighted(c) {
    val tps = if (c.typeParameters.isEmpty) "" else c.typeParameters.map(textOf).mkString("[", ", ", "]")
    val ps = c.constructors.filterByType[ScPrimaryConstructor].map(textOf(_, inCaseClass = true)).mkString
    "\n" + indent + "  " + "case " + c.name + tps + ps + "\n"
  }

  private def textOf(f: ScFunction, indent: String): String = highlighted(f) {
    val isGiven = f.isInstanceOf[ScGiven]
    val isAnonymous = isGiven && f.name.startsWith("given_") // .isAnonymous?
    val annotations = f.annotations.map(a => "\n" + indent + "  " + textOf(a)).mkString
    val modifiers = textOfModifiers(f, f.superMethod.isDefined)
    val keyword = if (isGiven) "given " else "def "
    val name = if (isAnonymous) "" else normalized(f.name)
    val signature = textOf(f.signatureClauses, inPrivateConstructor = false, inCaseClass = false)
    val tpe = if (f.isConstructor) "" else (if (signature.isEmpty) spaceAfter(name) else "") + (if (isAnonymous && signature.isEmpty) "" else ": ") +
      textOf(if (f.returnTypeElement.isDefined) f.returnType else f.returnType.map(_.removeAliasDefinitionsIn(f)))
    val rhs = f match {
      case ScFunctionDefinition.withBody(body) =>
        val rhs = textOfStatement(normalized(body), indent + "  ")
        if (rhs.startsWith("{")) " = " + rhs else if (!f.isLocal) " = " + rhs else " = " + rhs.trim
      case _ => ""
    }
    annotations + "\n" + indent + "  " + modifiers + keyword + name + signature + tpe + rhs + "\n"
  }

  private def textOfStatement(s: ScBlockStatement, indent: String): String = {
    val text = s match {
      case t: ScTypeDefinition =>
        val sb = new StringBuilder()
        printTo(sb, t, "", _ => ())
        sb.toString.stripSuffix("\n")
      case f: ScFunction => textOf(f, indent).stripSuffix("\n")
      case v: ScValueOrVariable => v.declaredElements.headOption.map(textOf(v, _, indent).stripSuffix("\n")).getOrElse("")
      case t: ScTypeAlias => textOf(t, indent).stripSuffix("\n")
      case _: ScImportStmt => ""
      case b: ScBlock => textOfExpression(b, indent.stripSuffix("  "))
      case e: ScExpression => "\n" + indent + "  " + textOfExpression(e, indent)
      case _ => "<stmt>"
    }
    if (s.is[ScExpression]) text else highlighted(s)(text)
  }

  private def textOfExpression(e: ScExpression, indent: String, etaExpand: Boolean = true): String = highlighted(e) {
    val text = e match {
      case p: ScParenthesisedExpr => p.innerElement.map(textOfExpression(_, indent)).getOrElse("")
      case b: ScBlockExpr => "{" + b.statements.map(s => textOfStatement(s, indent + "  ")).mkString("") +
        b.caseClauses.map("\n" + _.caseClauses.map(c => indent + "  " + "  case " + textOfPattern(c.pattern.get) + c.guard.flatMap(_.expr).map(" if " + textOfExpression(_, indent)).getOrElse("") + " =>" + textOfExpression(c.expr.get, indent + "  ")).mkString("\n")).getOrElse("") +
        "\n" + indent + "  " + "}"
      case b: ScBlock => b.statements.map(s => textOfStatement(s, indent + "  ")).mkString("")
      case r: ScReturn => "return" + r.expr.map(" " + textOfExpression(_, indent)).getOrElse("")
      case t: ScTypedExpression =>
        val s = textOfExpression(t.expr, indent)
        if (t.isSequenceArg) s + ": _*"
        else "(" + s + ": " + t.typeElement.flatMap(_.`type`().toOption.map(textOf(_))).getOrElse("NotInferred") + ")"
      case u: ScUnitExpr => "()"
      case u: ScThisReference => "this"
      case s: ScSuperReference => "super" + s.staticSuper.map("[" + textOf(_) + "]").getOrElse("")
      case l: ScInterpolatedStringLiteral => l.desugaredExpression.map(p => textOfExpression(p._2, indent)).getOrElse("")
      case f: ScFor => f.desugared(forDisplay = true).map(textOfExpression(_, indent)).getOrElse("")
      case l: ScLiteral => if (l.getValue == null) "null" else l.literalType.asInstanceOf[ScLiteralType].value.presentation
      case u: ScUnderscoreSection => u.bindingExpr match {
        case Some(b) => etaExpansionOf(b, indent).getOrElse("<expr>")
        case None => "_"
      }
      case e @ NonValueType(_: ScMethodType | ScTypePolymorphicType(_: ScMethodType, _)) & ExpectedType(FunctionType(_, _)) if etaExpand => etaExpansionOf(e, indent).getOrElse("<expr>")
      case e: ScIf =>
        "if (" + e.condition.map(e => textOfExpression(normalized(e), indent)).getOrElse("") + ") " + e.thenExpression.map(e => textOfExpression(normalized(e), indent)).getOrElse("") + (e.elseExpression match {
          case Some(e) => " else " + textOfExpression(normalized(e), indent)
          case None => ""
        })
      case e: ScTry =>
        "try " + e.expression.map(e => textOfExpression(normalized(e), indent)).getOrElse("") +
          e.catchBlock.flatMap(_.expression).map(e => textOfExpression(normalized(e), indent)).map(" catch " + _).getOrElse("") +
          e.finallyBlock.flatMap(_.expression).map(e => textOfExpression(normalized(e), indent)).map(" finally " + _).getOrElse("")
      case e: ScWhile =>
        "while (" + e.condition.map(e => textOfExpression(normalized(e), indent)).getOrElse("") + ") " +
          e.expression.map(e => textOfExpression(normalized(e), indent)).getOrElse("")
      //an invocation writes its arguments where the callee takes them, so the expression the call
      //belongs to writes all of its clauses, in the order of the signature of the callee
      case owner@Call(call) => inlining(call.target) {
        val callee  = textOfCallee(owner, call, indent)
        val clauses = textOfClauses(call, indent)
        owner match {
          //`a += b` assigns the result of `a.+(b)` to `a`, which the source writes as one operator
          case infix: ScInfixExpr if isCompoundAssignment(infix, call) =>
            call.thisExpr.map(textOfExpression(_, indent)).getOrElse("") + " = " + callee.stripSuffix("=") + clauses
          case _ => callee + clauses
        }
      }
      case sc: ScAssignment =>
        def syntaxText = textOfExpression(sc.leftExpression, indent) + " = " + sc.rightExpression.map(textOfExpression(_, indent)).getOrElse("")
        def desugaredText = sc.mirrorMethodCall.map(textOfExpression(_, indent)).getOrElse(syntaxText)
        sc.leftExpression match {
          case expr: ScReferenceExpression => expr.bind() match {
            case Some(result) if result.isNamedParameter => result.name + " = " + sc.rightExpression.map(textOfExpression(_, indent)).getOrElse("")
            case Some(result) if !result.element.nameContext.is[ScFunction] => syntaxText
            case _ => desugaredText
          }
          case _ => desugaredText
        }
      //a reference that is no call of its own, the qualifier of one or a reference to a value, since
      //the call a reference names is written out by textOfCallee
      case r: ScReferenceExpression => r.qualifier match {
        case Some(q) => textOfExpression(q, indent) + "." + r.refName
        case None => textOfReference(r)
      }
      case t: ScThrow => "throw " + textOfExpression(t.expression.get, indent)
      case e: ScNewTemplateDefinition =>
        val hasMembers = e.extendsBlock.members.exists(m => withPrivate || !isPrivate(m))
        val isAbstract = template.superRefs(e).headOption.exists { case (_, cls) => template.isAbstract(cls) }
        "new " + e.firstConstructorInvocation
          .map(textOfConstructorInvocation(_, indent, emptyParens = !(hasMembers || isAbstract)))
          .getOrElse("") + (if (!hasMembers) (if (isAbstract) " {}" else "") else
          " {" + {
            val sb = new StringBuilder()
            printTo(sb, e.extendsBlock, indent + "  ", _ => ())
            if (sb.nonEmpty) sb ++= indent + "  "
            sb.toString
          } + "}")
      case e: ScFunctionExpr =>
        "(" + e.parameters.map(p => p.name + ": " + textOf(if (p.typeElement.isDefined) p.`type`().get else p.`type`().get.removeAliasDefinitionsIn(e))).mkString(", ") + ") => " + e.result.map(textOfExpression(_, indent)).getOrElse("")
      case e: ScTuple =>
        textOfTuple(e.exprs, indent)
      case m: ScMatch =>
        m.expression.map(textOfExpression(_, indent + "  ")).getOrElse("") + " match {\n" +
          m.clauses.map(c => indent + "  " + "  case " + textOfPattern(c.pattern.get) + c.guard.flatMap(_.expr).map(" if " + textOfExpression(_, indent)).getOrElse("") + " =>" + textOfExpression(c.expr.get, indent + "  ")).mkString("\n") +
        "\n" + indent + "  }"
      case e => "<expr>"
    }

    //a call writes the clauses the implicit search filled in where the callee takes them, in the
    //order of the signature rather than after everything the source writes, see textOfClauses
    e.implicitConversion().map(textOfImplicitConversion(_, text, e)).getOrElse(text)
  }

  private def textOfReference(r: ScReference): String =
    //A synthetic apply resolves from the function's type; its inner result retains the named receiver's qualifier.
    r.bind().map(result => textOfReferenceTo(result.mostInnerResolveResult, r)).getOrElse("<Cannot resolve reference>")

  private def textOfReferenceTo(result: ScalaResolveResult, place: PsiElement): String = {
    result.getActualElement match {
      case e: ScSelfTypeElement => e.nameContext match {
        case named: ScNamedElement => if (named.name == "<anonymous>") "this" else named.name + ".this"
        case _ => e.name
      }
      case e: PsiNamedElement => e.nameContext match {
        case p: PsiPackage if p.getName == null => "_root_"
        case p: ScClassParameter if p.containingClass.extendsBlock.templateParents.exists(place.contexts.contains) => p.name
        case m: ScMember if m.isLocal => e.name
        case m: PsiMember =>
          if (ScalaPsiUtil.hasStablePath(e)) m.qualifiedNameOpt.getOrElse("<Cannot determine fully-qualified name>") else {
            result.fromType match {
              case Some(tpe) =>
                val tpe2 = tpe match {
                  case ScProjectionType(projected, element) if element == e => projected
                  case t => t
                }
                val typeText = tpe2.canonicalText(TypePresentationContext(place))(using Context(place))
                val qualifier = typeText.stripPrefix("_root_.").stripSuffix(".type")
                qualifier + "." + e.name
              case None =>
                e.name
            }
          }
        case _ => e.name
      }
      case _ => result.name
    }
  }

  private def textOfPattern(p: ScPattern): String = highlighted(p) {
    p match {
      case _: ScWildcardPattern => "_"
      case p: ScNamingPattern => p.name + " @ " + textOfPattern(p.named)
      case p: ScLiteralPattern => textOfExpression(p.getLiteral, "")
      case p: ScStableReferencePattern => p.referenceExpression.map(textOfExpression(_, "")).getOrElse("")
      case p: ScTuplePattern => "(" + p.patternList.map(_.patterns.map(textOfPattern).mkString(", ")).getOrElse("") + ")"
      case p: ScTypedPattern => p.name + ": " + textOf(p.`type`())
      case p: Sc3TypedPattern => textOfPattern(p.pattern) + ": " + textOf(p.`type`())
      case p: ScReferencePattern => p.name
      case p: ScExtractorPattern => textOfReference(p.ref) + "(" + p.argPatterns.map(textOfPattern).mkString(", ") + ")"
      case p: ScCompositePattern => "(" + p.subpatterns.map(textOfPattern).mkString(" | ") + ")"
      case _ => "<pattern>"
    }
  }

  @tailrec
  private def containingFileOf(e: PsiElement): PsiFile = {
    val file = e.getContainingFile
    if (file == null) return null
    val fileContext = file.getContext
    if (fileContext == null) file else containingFileOf(fileContext)
  }

  private def textOfConstructorInvocation(ci: ScConstructorInvocation, indent: String, emptyParens: Boolean = true) = {
    val tpe = {
      val elementType = ci.typeElement.`type`()
      if (ci.typeArgList.nonEmpty) elementType else elementType.map {
        case ParameterizedType(designator, args) => ParameterizedType(designator, args.map(_.removeAliasDefinitionsIn(ci)))
        case t => t
      }
    }
    val autoTupledLists = InvocationDetails.of(ci).argumentClauses.collect {
      case clause: InvocationDetails.ValueClause if clause.isAutoTupling => clause.argsElement
    }.toSet

    tpe.map(textOf(_, parens = 1)).getOrElse("NotInferred") + (ci.arguments match {
      case Seq() => if (emptyParens) "()" else ""
      case Seq(list) if list.exprs.isEmpty && !autoTupledLists(list) => if (emptyParens) "()" else ""
      case lists => lists.map { list =>
        val arguments =
          if (autoTupledLists(list)) textOfTuple(list.exprs, indent)
          else list.exprs.map(textOfExpression(_, indent)).mkString(", ")
        "(" + arguments + ")"
      }.mkString
    }) +
      textOfImplicitArguments(implicitArgumentsOf(ci), ci)
  }

  private def textOfImplicitConversion(function: ScalaResolveResult, expression: String, place: PsiElement): String = inlining(Some(function)) {
    val typeArgText = function.element match {
      case owner: ScTypeParametersOwner if owner.typeParameters.nonEmpty =>
        owner.typeParameters.map(tp => function.substitutor(TypeParameterType(tp))).map(t => textOf(t.removeAliasDefinitionsIn(place))).mkString("[", ", ", "]")
      case _ => ""
    }
    textOfReferenceTo(function, place) + (if (function.element.is[ScFunction]) "" else ".apply") +
      typeArgText + "(" + expression + ")" + textOfImplicitArguments(function.implicitArguments.map(_.args), place)
  }

  private def textOfImplicitArguments(args: Seq[Seq[ScalaResolveResult]], place: PsiElement): String = args
    .map { clause =>
      clause.map { arg =>
        inlining(Some(arg)) {
          val typeArgText = arg.element match {
            case owner: ScTypeParametersOwner if owner.typeParameters.nonEmpty =>
              owner.typeParameters.map(tp => arg.substitutor(TypeParameterType(tp))).map(t => textOf(t.removeAliasDefinitionsIn(place))).mkString("[", ", ", "]")
            case _ => ""
          }
          val prefix = textOfReferenceTo(arg, place)
          val inner = prefix + typeArgText + textOfImplicitArguments(arg.implicitArguments.map(_.args), place) match {
            case GeneratedClassTag(tpe) => s"scala.reflect.ClassTag.apply[$tpe](classOf[$tpe])" // Workaround for SCL-14358
            case s => s
          }
          arg.implicitConversion.map(textOfImplicitConversion(_, inner, place)).getOrElse(inner)
        }
      }.mkString(", ")
    }
    .map("(using " + _ + ")").mkString

  /** An expression that is a call, with the details of it. Only that one expression reports them. */
  private object Call {
    def unapply(owner: InvocationDetailsOwner): Option[InvocationDetails] = InvocationDetails.of(owner)
  }

  /**
   * The syntax of a call without its arguments: the expression it is applied to where the callee does
   * not name one, `a` of `a + b`, and the callee as the source names it, which is the element the
   * first clause of the call anchors at.
   */
  private def textOfCallee(owner: InvocationDetailsOwner, call: InvocationDetails, indent: String): String = {
    //the callee is the element the first clause of the call anchors at, and the operator or the
    //expression itself where the call has no clause to anchor one at, `f` of `def f: Int`
    val callee = call.argumentClauses.headOption.map(_.anchor).filterNot(_ == owner).getOrElse {
      owner match {
        case invocation: MethodInvocation => invocation.getInvokedExpr
        case expression                   => expression
      }
    }

    val calleeText = callee match {
      //the source does not name the callee of an assignment at all, `a(i) = b` calling `update`
      case _ if call.isUpdate || call.isAssignmentCall     => "." + call.target.fold("")(_.name)
      //The operator can belong to an inner clause, as in `(receiver op first)(second)`.
      case reference: ScReferenceExpression
        if reference.getParent.asOptionOf[ScSugarCallExpr].exists(_.operation == reference) => reference.refName
      case reference: ScReferenceExpression                => textOfCalleeReference(reference, call, indent)
      case expression: ScExpression if expression != owner => textOfExpression(expression, indent)
      //the `this` of a self invocation is no expression to write out
      case element                                         => element.getText
    }

    //the receiver is written out only where the callee does not name it already, `O.f` naming `O`
    //an update or setter prints only the synthetic method name above, so its receiver is still needed
    val receiver = call.thisExpr
      .filter(receiver => call.isUpdate || call.isAssignmentCall || !callee.elements.contains(receiver))
      .map(textOfExpression(_, indent))
      .getOrElse("")

    val separator = if (receiver.isEmpty || calleeText.startsWith(".")) "" else "."

    //a prefix expression names the operator without the `unary_` the callee is declared with
    val unary = if (owner.is[ScPrefixExpr]) "unary_" else ""

    receiver + separator + unary + calleeText
  }

  /**
   * Whether the source writes one operator where the compiler assigns the result of a call, `a += b`
   * assigning `a.+(b)` to `a` since no `+=` is defined.
   */
  private def isCompoundAssignment(infix: ScInfixExpr, call: InvocationDetails): Boolean =
    infix.operation.refName.endsWith("=") && !call.target.exists(_.name.endsWith("="))

  /** The callee of a call as the source names it, with an `apply` it does not name written out. */
  private def textOfCalleeReference(reference: ScReferenceExpression, call: InvocationDetails, indent: String): String =
    (reference.qualifier match {
      case Some(qualifier) => textOfExpression(qualifier, indent) + "." + reference.refName
      case None            => textOfReference(reference)
    }) + (if (call.isApply) ".apply" else "")

  /** The clauses of a call, in the order of the signature of the callee. */
  private def textOfClauses(call: InvocationDetails, indent: String): String =
    call.argumentClauses.map {
      case clause: InvocationDetails.TypeClause =>
        clause.arguments.map(argument => textOf(typeOfArgument(argument, call))).mkString("[", ", ", "]")
      case clause: InvocationDetails.ValueClause =>
        val using = if (isImplicitClause(clause.target)) "using " else ""
        val arguments = clause.arguments.filterNot(isDefaultArgument).map(_._1)
        val text =
          if (clause.isAutoTupling) textOfTuple(arguments, indent)
          else arguments.map(textOfArgument(_, indent)).mkString(", ")
        "(" + using + text + ")"
      case clause: InvocationDetails.ImplicitValueClause =>
        textOfImplicitArguments(Seq(clause.arguments), call.origin)
      //a clause the call leaves unapplied writes nothing, the function it evaluates to takes it
      case _: InvocationDetails.EtaExpandedClause => ""
      case _: InvocationDetails.AutoAppliedClause => "()"
    }.mkString

  /** Auto-tupling writes the same tuple construction as an explicit tuple, or `()` for no arguments. */
  private def textOfTuple(arguments: Seq[ScExpression], indent: String): String =
    if (arguments.isEmpty) "()"
    else {
      val types = arguments.map(e => e.`type`().map(t => textOf(t.removeAliasDefinitionsIn(e))).getOrElse("NotInferred"))
      "scala.Tuple" + arguments.size + ".apply" + types.mkString("[", ", ", "]") +
        arguments.map(textOfExpression(_, indent)).mkString("(", ", ", ")")
    }

  /**
   * Whether a call applies a parameter with the default of it, in which case the argument is the
   * expression at the declaration of the parameter and the source of the call writes nothing.
   */
  private def isDefaultArgument(argument: (ScExpression, Parameter)): Boolean = argument match {
    case (expression, parameter) =>
      parameter.isDefault && parameter.paramInCode.flatMap(_.getDefaultExpression).contains(expression)
  }

  /** An argument of a value clause, with the name of the parameter where the source writes one. */
  private def textOfArgument(argument: ScExpression, indent: String): String =
    argument.getContext match {
      //a named argument is an assignment of which the call matches the right side with the
      //parameter, the `true` of `f(discardOld = true)`, so the name is in the context of it
      case assignment: ScAssignment if isNamedArgument(assignment, argument) =>
        textOfExpression(assignment, indent)
      case _ => textOfExpression(argument, indent)
    }

  /**
   * Whether an argument is the right side of an assignment the source writes to name the parameter
   * it applies, rather than the value an update or a setter call assigns, the `b` of `a(i) = b`,
   * which is an argument of that assignment itself.
   */
  private def isNamedArgument(assignment: ScAssignment, argument: ScExpression): Boolean =
    assignment.rightExpression.contains(argument) && InvocationDetails.of(assignment).isEmpty

  /** A type argument written out, with the aliases of it removed where the source leaves it out. */
  private def typeOfArgument(argument: InvocationDetails.TypeArgument, call: InvocationDetails): ScType =
    if (argument.isExplicit) argument.tpe
    else                     argument.tpe.removeAliasDefinitionsIn(call.origin)

  /** Whether a clause of the callee takes its arguments implicitly, with `using` or with `implicit`. */
  private def isImplicitClause(target: InvocationDetails.ArgumentClauseTarget.Value): Boolean = target match {
    case InvocationDetails.ArgumentClauseTarget.ScalaParameters(parameters) => parameters.isImplicit
    case _                                                                  => false
  }

  /**
   * The clauses of implicit arguments the search filled in for the call `element` reports, which the
   * source leaves out and the printer writes out.
   */
  private def implicitArgumentsOf(element: PsiElement): Seq[Seq[ScalaResolveResult]] =
    element.asOptionOf[InvocationDetailsOwner]
      .flatMap(owner => InvocationDetails.of(owner))
      .toSeq
      .flatMap(_.argumentClauses)
      .collect { case clause: InvocationDetails.ImplicitValueClause => clause.arguments }

  private def etaExpansionOf(e: ScExpression, indent: String) = referenceLevelIn(e).flatMap { case (reference, level) =>
    reference.bind().flatMap { result =>
      Some(result.element).collect {
        case f: ScFunction =>
          val omittedClauses = f.clauses.map(_.clauses).getOrElse(Seq.empty).drop(level)
          val bindingLists = omittedClauses.map(_.parameters.map(p => p.name + ": " + textOf(p.`type`().map(result.substitutor(_).removeAliasDefinitionsIn(e)))).mkString("(", ", ", ")"))
          val argumentLists = omittedClauses.map(_.parameters.map(_.name).mkString("(", ", ", ")"))
          s"${bindingLists.mkString(" => ")} => ${textOfExpression(e, indent, etaExpand = false)}${argumentLists.mkString}"
        case m: PsiMethod =>
          val omittedParameters = m.getParameterList.getParameters
          val bindingList = omittedParameters.map(p => p.getName + ": " + p.getType.getCanonicalText).mkString("(", ", ", ")")
          val argumentList = omittedParameters.map(_.getName).mkString("(", ", ", ")")
          s"$bindingList => ${textOfExpression(e, indent, etaExpand = false)}$argumentList"
      }
    }
  }

  @tailrec
  private def referenceLevelIn(e: ScExpression, level: Int = 0): Option[(ScReferenceExpression, Int)] = e match {
    case r: ScReferenceExpression => Some((r, level))
    case c: ScMethodCall => referenceLevelIn(c.getEffectiveInvokedExpr, level + 1)
    case g: ScGenericCall => referenceLevelIn(g.referencedExpr, level)
    case ScParenthesisedExpr(inner) => referenceLevelIn(inner, level)
    case _ => None
  }

  private def normalized(e: ScExpression): ScExpression = e match {
    case b: ScBlock if normalize => b.statements match {
      case Seq(e: ScExpression) => e
      case statements if statements.length > 1 && statements.init.forall(_.is[ScImportStmt]) => statements.last.asInstanceOf[ScExpression]
      case _ => b
    }
    case e => e
  }

  private def inlining(result: Option[ScalaResolveResult])(f: => String): String = result match {
    case Some(r) => r.element match {
      case m: ScMember if !m.isLocal && m.hasModifierProperty("transparent") && m.hasModifierProperty("inline") => "???"
      case _ => f
    }
    case _ => f
  }

  private def textOf(e: ScExtension, indent: String): String = {
    val signature = textOf(e.signatureClauses, inPrivateConstructor = false, inCaseClass = false)
    val methods = e.extensionMethods.map(textOf(_, indent + "  ")).mkString
    "\n" + indent + "  " + "extension " + signature + methods
  }

  private def textOf(v: ScValueOrVariable, symbol: ScTypedDefinition, indent: String): String = highlighted(v) {
    val annotations = v.annotations.map(a => "\n" + indent + "  " + textOf(a)).mkString
    val modifiers = textOfModifiers(v, ScalaPsiUtil.superValsSignatures(symbol).nonEmpty)
    val keyword = if (v.is[ScValue]) "val " else "var "
    val symbolType = symbol.`type`()
    val name = normalized(symbol.name)
    val tpe = spaceAfter(name) + ": " + textOf(if (v.typeElement.isDefined) symbolType else symbolType.map(_.removeAliasDefinitionsIn(v)))
    val rhs = v match {
      case ScValueOrVariableDefinition.withExpr(expr) =>
        val rhs = textOfStatement(normalized(expr), indent + "  ")
        if (rhs.startsWith("{")) " = " + rhs else if (!v.isLocal) " = " + rhs else " = " + rhs.trim
      case _ => ""
    }
    annotations + "\n" + indent + "  " + modifiers + keyword + name + tpe + rhs + "\n"
  }

  private def printTo(sb: StringBuilder, alias: ScTypeAlias): Unit = {
    sb ++= textOf(alias, "").split("\n").map(_.stripPrefix("  ")).mkString("\n")
    sb ++= "\n"
  }

  private def textOf(t: ScTypeAlias, indent: String): String = highlighted(t) {
      val annotations = t.annotations.map(a => "\n" + indent + "  " + textOf(a)).mkString
      val modifiers = textOfModifiers(t, ScalaPsiUtil.superTypeSignatures(t).nonEmpty)
      val name = normalized(t.name)
      val tps = if (t.typeParameters.isEmpty) "" else t.typeParameters.map(textOf).mkString("[", ", ", "]")
      val bounds = textOfBoundsIn(t)
      val rhs = t match {
        case definition: ScTypeAliasDefinition if !(normalize && definition.isOpaque) => " = " + textOf(definition.aliasedType)
        case _ => ""
      }
      annotations + "\n" + indent + "  " + modifiers + "type " + name + tps + bounds + rhs + "\n"
  }

  private def textOf(p: ScTypeParam): String = highlighted(p) {
    val annotations = p.annotations.map(textOf).mkString(" ")
    val variance = if (p.isCovariant) "+" else if (p.isContravariant) "-" else ""
    val name = normalized(p.name)
    val clauses = p.typeParametersClause.map(_.typeParameters.map(textOf).mkString("[", ", ", "]")).mkString
    val typeBounds = textOfBoundsIn(p)
    val contextBound = p.contextBound.map(t => ": " + textOf(t)).mkString
    (if (annotations.isEmpty) "" else annotations + " ") + variance + name + clauses + typeBounds + contextBound
  }

  private def textOfBoundsIn(o: ScTypeBoundsOwner): String = {
    val lower = o.lowerTypeElement.flatMap(_.`type`().toOption).map(textOf(_)).filter(_ != "scala.Nothing").map(" >: " + _).getOrElse("")
    val upper = o.upperTypeElement.flatMap(_.`type`().toOption).map(textOf(_)).filter(_ != "scala.Any").map(" <: " + _).getOrElse("")
    lower + upper
  }

  private def textOf(clause: ScParameterClause, inPrivateConstructor: Boolean, inCaseClass: Boolean): String = highlighted(clause) {
    val ps = clause.parameters.filter(p => withPrivate || !inPrivateConstructor || ((inCaseClass || p.isVal || p.isVar) && !isPrivate(p)))
    val isEffectivelyImplicit = ps.exists(_.name.startsWith("evidence$")) // SCL-25836
    ps.map(textOf(_, inCaseClass)).mkString(if (ps.nonEmpty) (if (clause.hasImplicitKeyword || isEffectivelyImplicit) "(implicit " else if (clause.hasUsingKeyword) "(using " else "(") else "(", ", ", ")")
  }

  private def textOf(clause: ScTypeParamClause): String = highlighted(clause) {
    clause.typeParameters.map(textOf).mkString("[", ", ", "]")
  }

  private def textOf(
    signatureClauses: Seq[ScSignatureClause],
    inPrivateConstructor: Boolean,
    inCaseClass: Boolean
  ): String = {
    var isFirstTermClause = true

    signatureClauses.map {
      case TypeClause(clause) =>
        textOf(clause)
      case TermClause(clause) =>
        val text = textOf(clause, inPrivateConstructor, inCaseClass && isFirstTermClause)
        isFirstTermClause = false
        text
    }.mkString
  }

  private def textOf(p: ScParameter, inCaseClass: Boolean): String = highlighted(p) {
    val annotations = p.annotations.map(textOf).mkString(" ")
    val modifiers = {
      lazy val hasSupers = p.is[ScClassParameter] && ScalaPsiUtil.superValsSignatures(p).nonEmpty
      val s = {
        val s0 = textOfModifiers(p, hasSupers)
        p.owner match {
          case _: ScPrimaryConstructor if normalize && !(inCaseClass || p.isVal || p.isVar) && isField(p) => "private[this] " + (if (s0.isEmpty) "" else s0 + " ") + "val "
          case _ => if (inCaseClass && !p.isVal && hasSupers) s0 + "val " else s0
        }
      }
      if (withPrivate) s else s.replace("private[this] ", "").replace("private ", "")
    }
    val keyword =
      if (withPrivate || !isPrivate(p)) if (p.isVal) (if (!normalize || !(inCaseClass && modifiers.isEmpty)) "val " else "") else if (p.isVar) "var " else ""
      else ""
    val name = normalized(p.name)
    val byName = if (p.isCallByNameParameter) "=> " else ""
    val tpe = textOf(p.`type`())
    val isAnonymous = p.isAnonymous
    val repeated = if (p.isRepeatedParameter) "*" else ""
    val default = p.getActualDefaultExpression.map(" = " + textOfExpression(_, "")).getOrElse("")
    (if (annotations.isEmpty) "" else annotations + " ") + modifiers + keyword + (if (isAnonymous) "" else name + spaceAfter(name) + ": ") + byName + tpe + repeated + default
  }

  private def isField(p: ScParameter): Boolean = {
    val containingClass = p.contexts.findByType[ScTypeDefinition].get
    val parentClauses = containingClass.extendsBlock.templateParents.map(_.parentClauses).getOrElse(Seq.empty)
    (parentClauses ++ containingClass.members.filterNot(_.names.contains("this"))).exists(_.elements.exists { case ReferenceTarget(e) if e == p => true; case _ => false } )
  }

  private def textOf(annotation: ScAnnotation): String =
    textOf(annotation, emptyParens = false)

  private def textOf(annotation: ScAnnotation, emptyParens: Boolean): String = highlighted(annotation) {
    "@" + textOfConstructorInvocation(annotation.constructorInvocation, "", emptyParens)
  }

  private def textOfModifiers(owner: ScModifierListOwner, hasSupers: => Boolean = false): String = {
    val ml = owner.getModifierList
    def scope = ml.getParent match {
      case Parent(p: ScPackaging) => p.packageName.split('.').lastOption.getOrElse("")
      case Parent(c: ScNamedElement) => c.name
      case _ => ""
    }
    def qualifier = ml.accessModifier
      .flatMap(m => if (m.isThis || (normalize && m.isPrivate && m.getReference == null && isEffectivelyThis(owner))) Some("this") else m.idText)
      .filter(q => !normalize || q != scope)
      .map("[" + _ + "]")
      .getOrElse("")
    (if (ml.isAbstract && ml.isOverride) "abstract " else "") +
      (if (ml.isOverride || hasSupers) "override " else "") +
      (if (ml.isPrivate) "private" + qualifier + " " else "") +
      (if (ml.isProtected) "protected" + qualifier + " " else "") +
      (if (ml.isImplicit) "implicit " else "") +
      (if (ml.isFinal) "final " else "") +
      (if (ml.isSealed) "sealed " else "") +
      (if (ml.isOpen) "open " else "") +
      (if (ml.isAbstract && !ml.isOverride) "abstract " else "") +
      (if (ml.isLazy) "lazy " else "") +
      (if (ml.isTransparent) "transparent " else "") +
      (if (ml.isOpaque) "opaque " else "") +
      (if (ml.isInline) "inline " else "") +
      (if (ml.isCase) "case " else "")
  }

  private def isEffectivelyThis(owner: ScModifierListOwner): Boolean = owner match {
    case _: ScClassParameter => false
    case _: ScPrimaryConstructor => false
    case m: ScMember if m.names.contains("this") => false
    case m: ScMember => m.containingClass match {
      case cls: ScTypeDefinition => cls.isEffectivelyFinal || cls.elements.forall {
        case r @ ReferenceTarget(e: PsiNamedElement) if e.nameContext == m => r match {
          case r: ScReference => r.qualifier.forall(isThisQualifier(_, cls))
          case _ => true
        }
        case _ => true
      }
      case _: ScNewTemplateDefinition => true
      case _ => false
    }
    case _ => false
  }

  private def isThisQualifier(qualifier: PsiElement, containingClass: ScTypeDefinition): Boolean = qualifier match {
    case t: ScThisReference => t.refTemplate.contains(containingClass)
    case r: ScReferenceExpression => Option(r.resolve()).exists {
      case self: ScSelfTypeElement => self.contexts.contains(containingClass)
      case _ => false
    }
    case _ => false
  }

  private def textOf(tpe: ScType, parens: Int = 0): String = (tpe match {
    case FunctionType(_, _) if !tpe.isAliasType && parens > 0 => "(" + tpe.canonicalText + ")"
//    case AliasType(ta, _, _) => ta.containingClass.name + ".this." + ta.name
    case _ =>
      tpe.canonicalText(context)
  }).replace("_root_.", "")

  private val context = TypePresentationContext.emptyContextIn(isScala3)

  private def textOf(tr: TypeResult): String = tr match {
    case Left(f) => f.toString
    case Right(t) => textOf(t)
  }

  // Should be part of .name, SCL-21919
  private def normalized(name: String): String = if (normalize && name.startsWith("`")) {
    val unquoted = name.stripPrefix("`").stripSuffix("`")
    if (!Keywords(unquoted) && isIdentifier(unquoted)) unquoted
    else name
  } else {
    name
  }

  private def spaceAfter(name: String): String =
    if (name.lastOption.exists(c => !c.isLetterOrDigit && c != '`')) " " else ""

  private def highlighted(e: PsiElement)(s: String): String =
    s + highlight(e).map("/* " + _ + " */").mkString
}

private object ClassPrinter {
  private val Keywords = Set(
    ":", "=", "=>", "=>>", "?=>", "<-", "<:", "<%", ">:", "#", "@", "abstract", "case", "catch", "class", "def", "do", "else", "enum", "export", "extends", "extension",
    "false", "final", "finally", "for", "forSome", "given", "if", "implicit", "import", "lazy", "macro", "match", "new", "null", "object", "override", "package",
    "private", "protected", "return", "sealed", "super", "then", "this", "throw", "trait", "true", "try", "type", "val", "var", "while", "with", "yield",
  )

  private val GeneratedClassTag = raw"scala\.reflect\.ClassTag\[(.+)]".r

  private def isIdentifier(s: String): Boolean = s.nonEmpty && {
    if (ScalaNamesUtil.isIdentifierStart(s(0))) {
      val lastIdCharIdx = s.takeWhile(ScalaNamesUtil.isIdentifierPart).length - 1
      if (lastIdCharIdx < 0 || lastIdCharIdx == s.length - 1) true
      else if (s.charAt(lastIdCharIdx) != '_') false
      else s.drop(lastIdCharIdx + 1).forall(ScalaNamesUtil.isOpCharacter)
    } else if (ScalaNamesUtil.isOpCharacter(s(0))) {
      s.forall(ScalaNamesUtil.isOpCharacter)
    } else {
      false
    }
  }

  def textOf(cls: ScTypeDefinition, isScala3: Boolean, listener: CharSequence => Unit = _ => ()): String = {
    val annotator = new ScalaAnnotator()
    textOfCompilationUnit(cls, isScala3 = isScala3, withPrivate = true, normalize = true, listener) { element =>
      val holder = new AnnotatorHolderMock(cls.getContainingFile)
      annotator.annotate(element, typeAware = true, checkShouldInspect = false, treatAsSource = true)(using holder)
      holder.errorAnnotations.map(_.message)
    }
  }

  // Copy of org.jetbrains.plugins.scala.text.TextToTextTestBase.textOfCompilationUnit
  private def textOfCompilationUnit(cls: ScTypeDefinition, isScala3: Boolean, withPrivate: Boolean, normalize: Boolean, listener: CharSequence => Unit)(highlight: PsiElement => Seq[String]): String = {
    val packageName = {
      val fqn = cls.qualifiedName
      if (fqn.contains('.')) fqn.substring(0, fqn.lastIndexOf('.')) else ""
    }

    val companionTypeAlias = ScalaPsiManager.instance(cls.getProject).getTopLevelDefinitionsByPackage(packageName, cls.getResolveScope).collect {
      case a: ScTypeAlias if a.name == cls.name => a
    }

    val sb = new StringBuilder()

    if (packageName.nonEmpty) sb ++= "package " + packageName + "\n"

    val printer = new ClassPrinter(isScala3 = isScala3, withPrivate = withPrivate, normalize = normalize)(highlight)
    ((companionTypeAlias.toSeq :+ cls) ++ cls.baseCompanionTypeDefinition.toSeq).sortBy(_.getTextOffset).foreach {
      case td: ScTypeDefinition => printer.printTo(sb, td, "", listener)
      case ta: ScTypeAlias => printer.printTo(sb, ta)
    }

    if (packageName.isEmpty) sb.delete(0, 1)
    sb.setLength(sb.length - 1)

    sb.toString
  }
}
