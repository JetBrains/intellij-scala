package org.jetbrains.plugins.scala
package conversion

import java.util.regex.Pattern

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.editorActions.ReferenceData
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.search.{GlobalSearchScope, LocalSearchScope}
import com.intellij.psi.util.{PsiTreeUtil, PsiUtil}
import org.jetbrains.plugins.scala.conversion.ast._
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, PsiMemberExt, PsiMethodExt}
import org.jetbrains.plugins.scala.lang.dependency.Path
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.{Constructor, ScConstructor}
import org.jetbrains.plugins.scala.lang.psi.impl.source.ScalaCodeFragment
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import scala.collection.{JavaConverters, mutable}
import scala.language.postfixOps

/**
  * Author: Alexander Podkhalyuzin
  * Date: 23.07.2009
  */
object JavaToScala {

  case class AssociationHelper(node: IntermediateNode, path: Path)

  private val context: ThreadLocal[mutable.Stack[(Boolean, String)]] = new ThreadLocal[mutable.Stack[(Boolean, String)]] {
    override def initialValue(): mutable.Stack[(Boolean, String)] = new mutable.Stack[(Boolean, String)]()
  }

  def findVariableUsage(elementToFind: PsiElement, maybeElement: Option[PsiElement]): Seq[PsiReferenceExpression] =
    maybeElement.fold(Seq.empty[PsiReferenceExpression]) { element =>
      import JavaConverters._

      ReferencesSearch.search(elementToFind, new LocalSearchScope(element))
        .findAll()
        .asScala
        .toSeq
        .collect {
          case el: PsiReferenceExpression => el
        }
    }

  def isVar(element: PsiModifierListOwner, parent: Option[PsiElement]): Boolean = {
    def usageInConstructorParams(usage: PsiReferenceExpression): Boolean = {
      def correspondedConstructorParams: Seq[PsiParameter] = {
        Option(PsiTreeUtil.getParentOfType(usage, classOf[PsiMethod]))
          .collect { case Constructor(m) => m.parameters }
          .getOrElse(Seq.empty[PsiParameter])
      }

      val params = correspondedConstructorParams
      if (params.nonEmpty) {
        val rightPart = usage.getParent match {
          case ae: PsiAssignmentExpression if (ae.getOperationSign.getTokenType == JavaTokenType.EQ)
            && ae.getRExpression.isInstanceOf[PsiReferenceExpression] =>
            Option(ae.getRExpression.asInstanceOf[PsiReferenceExpression]).flatMap(e => Option(e.resolve()))
          case _ => None
        }

        rightPart match {
          case Some(param: PsiParameter) => !params.contains(param)
          case _ => true
        }

      } else true
    }

    val possibleVal = element.hasModifierProperty(PsiModifier.FINAL)
    val possibleVar = element.hasModifierProperty(PsiModifier.PUBLIC) || element.hasModifierProperty(PsiModifier.PROTECTED)

    val references = findVariableUsage(element, parent).filter((el: PsiReferenceExpression) => PsiUtil.isAccessedForWriting(el))

    references.length match {
      case 0 if possibleVal => false
      case 0 if possibleVar => true
      case 0 => false
      case 1 if possibleVal => if (element.isInstanceOf[PsiField]) usageInConstructorParams(references.head) else false
      case 1 if possibleVar => true
      case _ => true
    }
  }

  trait ExternalProperties {}

  case class WithReferenceExpression(yep: Boolean) extends ExternalProperties

  def convertTypePsiToIntermediate(`type`: PsiType, psiElement: PsiElement, project: Project)
                                  (implicit associations: mutable.ListBuffer[AssociationHelper] = mutable.ListBuffer(),
                                   refs: Seq[ReferenceData] = Seq.empty,
                                   usedComments: mutable.HashSet[PsiElement] = new mutable.HashSet[PsiElement](),
                                   textMode: Boolean = false): IntermediateNode = {
    Option(`type`).map {
      case _: PsiLambdaParameterType => EmptyConstruction()
      case _: PsiDisjunctionType =>
        DisjunctionTypeConstructions(
          PsiTreeUtil.getChildrenOfType(psiElement, classOf[PsiTypeElement])
            .map(t => convertTypePsiToIntermediate(t.getType, t, project)))
      case t =>
        val iNode = TypeConstruction.createIntermediateTypePresentation(t, project)
        handleAssociations(psiElement, iNode)
        iNode
    }.getOrElse(EmptyConstruction())
  }

  def convertPsiToIntermediate(element: PsiElement, externalProperties: ExternalProperties)
                              (implicit associations: mutable.ListBuffer[AssociationHelper] = mutable.ListBuffer(),
                               refs: Seq[ReferenceData] = Seq.empty,
                               usedComments: mutable.HashSet[PsiElement] = new mutable.HashSet[PsiElement](),
                               textMode: Boolean = false): IntermediateNode = {
    if (element == null || usedComments.contains(element)) return EmptyConstruction()
    if (element.getLanguage != JavaLanguage.INSTANCE) EmptyConstruction()
    val result: IntermediateNode = element match {
      case f: PsiFile =>
        val m = MainConstruction()
        m.addChildren(f.getChildren.map(convertPsiToIntermediate(_, externalProperties)))
        m
      case e: PsiExpressionStatement => convertPsiToIntermediate(e.getExpression, externalProperties)
      case l: PsiLiteralExpression => LiteralExpression(l.getText)
      case n: PsiIdentifier => NameIdentifier(n.getText)
      case t: PsiTypeElement => convertTypePsiToIntermediate(t.getType, t, t.getProject)
      case w: PsiWhiteSpace => LiteralExpression(w.getText)
      case r: PsiReturnStatement => ReturnStatement(convertPsiToIntermediate(r.getReturnValue, externalProperties))
      case t: PsiThrowStatement => ThrowStatement(convertPsiToIntermediate(t.getException, externalProperties))
      case i: PsiImportStatement => handleImport(i)
      case i: PsiImportStaticStatement => handleImport(i)
      case i: PsiImportList => ImportStatementList(i.getAllImportStatements.map(handleImport).distinct)
      case a: PsiAssignmentExpression =>
        BinaryExpressionConstruction(convertPsiToIntermediate(a.getLExpression, externalProperties),
          convertPsiToIntermediate(a.getRExpression, externalProperties), a.getOperationSign.getText, inExpression = false)
      case e: PsiExpressionListStatement =>
        ExpressionListStatement(e.getExpressionList.getExpressions.map(convertPsiToIntermediate(_, externalProperties)))
      case d: PsiDeclarationStatement => ExpressionListStatement(d.getDeclaredElements.map(convertPsiToIntermediate(_, externalProperties)))
      case b: PsiBlockStatement => convertPsiToIntermediate(b.getCodeBlock, externalProperties)
      case s: PsiSynchronizedStatement =>
        val lock = Option(s.getLockExpression).map(convertPsiToIntermediate(_, externalProperties))
        val body = Option(s.getBody).map(convertPsiToIntermediate(_, externalProperties))
        SynchronizedStatement(lock, body)
      case b: PsiCodeBlock =>
        BlockConstruction(b.getStatements.map(convertPsiToIntermediate(_, externalProperties)))
      case t: PsiTypeParameter =>
        TypeParameterConstruction(convertPsiToIntermediate(t.getNameIdentifier, externalProperties),
          t.getExtendsList.getReferenceElements.map(convertPsiToIntermediate(_, externalProperties)))
      case i: PsiIfStatement =>
        val condition = Option(i.getCondition).map(convertPsiToIntermediate(_, externalProperties))
        val thenBranch = Option(i.getThenBranch).map(convertPsiToIntermediate(_, externalProperties))
        val elseBranch = Option(i.getElseBranch).map(convertPsiToIntermediate(_, externalProperties))
        IfStatement(condition, thenBranch, elseBranch)
      case c: PsiConditionalExpression =>
        val condition = Option(c.getCondition).map(convertPsiToIntermediate(_, externalProperties))
        val thenBranch = Option(c.getThenExpression).map(convertPsiToIntermediate(_, externalProperties))
        val elseBranch = Option(c.getElseExpression).map(convertPsiToIntermediate(_, externalProperties))
        IfStatement(condition, thenBranch, elseBranch)
      case w: PsiWhileStatement =>
        val condition = Option(w.getCondition).map(convertPsiToIntermediate(_, externalProperties))
        val body = Option(w.getBody).map(convertPsiToIntermediate(_, externalProperties))
        WhileStatement(None, condition, body, None, WhileStatement.PRE_TEST_LOOP)
      case w: PsiDoWhileStatement =>
        val condition = Option(w.getCondition).map(convertPsiToIntermediate(_, externalProperties))
        val body = Option(w.getBody).map(convertPsiToIntermediate(_, externalProperties))
        WhileStatement(None, condition, body, None, WhileStatement.POST_TEST_LOOP)
      case f: PsiForStatement =>
        val initialization = Option(f.getInitialization).map(convertPsiToIntermediate(_, externalProperties))
        val condition = Some(f.getCondition match {
          case _: PsiEmptyStatement => LiteralExpression("true")
          case null => LiteralExpression("true")
          case _ => convertPsiToIntermediate(f.getCondition, externalProperties)
        })
        val body = Option(f.getBody).map(convertPsiToIntermediate(_, externalProperties))
        val update = Option(f.getUpdate).map(convertPsiToIntermediate(_, externalProperties))
        WhileStatement(initialization, condition, body, update, WhileStatement.PRE_TEST_LOOP)
      case a: PsiAssertStatement =>
        val condition = Option(a.getAssertCondition).map(convertPsiToIntermediate(_, externalProperties))
        val description = Option(a.getAssertDescription).map(convertPsiToIntermediate(_, externalProperties))
        AssertStatement(condition, description)
      case s: PsiSwitchLabelStatement =>
        val caseValue = if (s.isDefaultCase)
          Some(LiteralExpression("_"))
        else
          Option(s.getCaseValue).map(convertPsiToIntermediate(_, externalProperties))
        SwitchLabelStatement(caseValue, ScalaPsiUtil.functionArrow(s.getProject))
      case s: PsiSwitchStatement =>
        def statements = Option(s.getBody).map(_.getStatements)

        def defaultStatement = SwitchLabelStatement(Some(LiteralExpression("_")), ScalaPsiUtil.functionArrow(s.getProject))

        val expr = Option(s.getExpression).map(convertPsiToIntermediate(_, externalProperties))
        val body = Option(s.getBody).map(convertPsiToIntermediate(_, externalProperties))

        if (statements.exists(_.length == 0)) SwitchStatemtnt(expr, Some(defaultStatement)) else SwitchStatemtnt(expr, body)
      case p: PsiPackageStatement => PackageStatement(convertPsiToIntermediate(p.getPackageReference, externalProperties))
      case f: PsiForeachStatement =>
        val tp = Option(f.getIteratedValue).flatMap((e: PsiExpression) => Option(e.getType))
        val isJavaCollection = if (tp.isEmpty) true else !tp.get.isInstanceOf[PsiArrayType]

        val iteratedValue = Option(f.getIteratedValue).map(convertPsiToIntermediate(_, externalProperties))
        val body = Option(f.getBody).map(convertPsiToIntermediate(_, externalProperties))
        val name = convertPsiToIntermediate(f.getIterationParameter.getNameIdentifier, externalProperties)
        ForeachStatement(name, iteratedValue, body, isJavaCollection)
      case r: PsiReferenceExpression =>
        val args = Option(r.getParameterList).map(convertPsiToIntermediate(_, externalProperties))

        def nameForReference: String = {
          val nameWithPrefix: String = if (textMode && r.getQualifier == null) r.resolve() match {
            case clazz: PsiClass => ScalaPsiUtil.nameWithPrefixIfNeeded(clazz)
            case _ => r.getReferenceName
          } else r.getReferenceName

          if (externalProperties.isInstanceOf[WithReferenceExpression])
            fieldParameterMap.getOrElse(r.getReferenceName, nameWithPrefix)
          else nameWithPrefix
        }

        val refName: String = nameForReference

        var iResult = JavaCodeReferenceStatement(None, args, refName)
        if (r.getQualifierExpression != null) {
          val t = Option(r.getQualifierExpression).map(convertPsiToIntermediate(_, externalProperties))
          iResult = JavaCodeReferenceStatement(t, args, refName)
        } else {
          r.resolve() match {
            case f: PsiMember
              if f.hasModifierProperty("static") =>
              val clazz = f.containingClass
              if (clazz != null && context.get().contains((false, clazz.qualifiedName))) {
                val name = Option(clazz.getNameIdentifier).map(convertPsiToIntermediate(_, externalProperties))
                iResult = JavaCodeReferenceStatement(name, args, refName)
              }

            case _ =>
          }
        }

        handleAssociations(r, iResult)
        iResult
      case p: PsiJavaCodeReferenceElement =>
        val qualifier = Option(p.getQualifier).map(convertPsiToIntermediate(_, externalProperties))
        val args = Option(p.getParameterList).map(convertPsiToIntermediate(_, externalProperties))
        JavaCodeReferenceStatement(qualifier, args, p.getReferenceName)
      case be: PsiBinaryExpression =>
        def isOk: Boolean = {
          if (be.getLOperand.getType.isInstanceOf[PsiPrimitiveType]) return false
          be.getROperand match {
            case l: PsiLiteralExpression if l.getText == "null" => return false
            case _ =>
          }
          true
        }

        def inExpression: Boolean = Option(be.getParent) match {
          case Some(_: PsiExpression) => true
          case _ => false
        }

        val operation = be.getOperationSign.getText match {
          case "==" if isOk => "eq"
          case "!=" if isOk => "ne"
          case x => x
        }

        BinaryExpressionConstruction(
          convertPsiToIntermediate(be.getLOperand, externalProperties),
          convertPsiToIntermediate(be.getROperand, externalProperties),
          operation, inExpression)
      case c: PsiTypeCastExpression =>
        ClassCast(
          convertPsiToIntermediate(c.getOperand, externalProperties), convertPsiToIntermediate(c.getCastType, externalProperties),
          c.getCastType.getType.isInstanceOf[PsiPrimitiveType] && c.getOperand.getType.isInstanceOf[PsiPrimitiveType])
      case a: PsiArrayAccessExpression =>
        ArrayAccess(
          convertPsiToIntermediate(a.getArrayExpression, externalProperties),
          convertPsiToIntermediate(a.getIndexExpression, externalProperties))
      case a: PsiArrayInitializerExpression =>
        ArrayInitializer(a.getInitializers.map(convertPsiToIntermediate(_, externalProperties)))
      case c: PsiClassObjectAccessExpression => ClassObjectAccess(convertPsiToIntermediate(c.getOperand, externalProperties))
      case i: PsiInstanceOfExpression =>
        InstanceOfConstruction(
          convertPsiToIntermediate(i.getOperand, externalProperties),
          convertPsiToIntermediate(i.getCheckType, externalProperties))
      case m: PsiMethodCallExpression =>
        def isSuper: Boolean = m.getMethodExpression.getQualifierExpression.isInstanceOf[PsiSuperExpression]

        m.getMethodExpression.resolve() match {
          case method: PsiMethod if method.getName == "parseInt" && m.getArgumentList.getExpressions.length == 1 &&
            method.getContainingClass != null && method.getContainingClass.qualifiedName == "java.lang.Integer" =>
            ClassCast(convertPsiToIntermediate(m.getArgumentList.getExpressions.apply(0), externalProperties),
              TypeConstruction("Int"), isPrimitive = true)
          case method: PsiMethod if method.getName == "parseDouble" && m.getArgumentList.getExpressions.length == 1 &&
            method.getContainingClass != null && method.getContainingClass.qualifiedName == "java.lang.Double" =>
            ClassCast(convertPsiToIntermediate(m.getArgumentList.getExpressions.apply(0), externalProperties),
              TypeConstruction("Double"), isPrimitive = true)
          case method: PsiMethod if method.getName == "round" && m.getArgumentList.getExpressions.length == 1 &&
            method.getContainingClass != null && method.getContainingClass.qualifiedName == "java.lang.Math" =>
            MethodCallExpression.build(
              convertPsiToIntermediate(m.getArgumentList.getExpressions.apply(0), externalProperties), ".round", null)
          case method: PsiMethod if method.getName == "equals" && m.getTypeArguments.isEmpty && !isSuper
            && m.getArgumentList.getExpressions.length == 1 =>
            MethodCallExpression.build(
              Option(m.getMethodExpression.getQualifierExpression).map(convertPsiToIntermediate(_, externalProperties))
                .getOrElse(LiteralExpression("this")),
              " == ", convertPsiToIntermediate(m.getArgumentList.getExpressions.apply(0), externalProperties))
          case _ =>
            MethodCallExpression(m.getMethodExpression.getQualifiedName,
              convertPsiToIntermediate(m.getMethodExpression, externalProperties),
              convertPsiToIntermediate(m.getArgumentList, externalProperties),
              (m.getType == PsiType.VOID) && m.getArgumentList.getExpressions.isEmpty)
        }
      case t: PsiThisExpression =>
        ThisExpression(Option(t.getQualifier).map(convertPsiToIntermediate(_, externalProperties)))
      case s: PsiSuperExpression =>
        SuperExpression(Option(s.getQualifier).map(convertPsiToIntermediate(_, externalProperties)))
      case e: PsiExpressionList =>
        ExpressionList(e.getExpressions.map(convertPsiToIntermediate(_, externalProperties)))
      case lambda: PsiLambdaExpression =>
        FunctionalExpression(
          convertPsiToIntermediate(lambda.getParameterList, externalProperties),
          convertPsiToIntermediate(lambda.getBody, externalProperties))
      case l: PsiLocalVariable =>
        val parent = Option(PsiTreeUtil.getParentOfType(l, classOf[PsiCodeBlock], classOf[PsiBlockStatement]))
        val needVar = if (parent.isEmpty) false else isVar(l, parent)
        val initalizer = Option(l.getInitializer).map(convertPsiToIntermediate(_, externalProperties))
        val name = convertPsiToIntermediate(l.getNameIdentifier, externalProperties)
        LocalVariable(handleModifierList(l), name, convertTypePsiToIntermediate(l.getType, l.getTypeElement, l.getProject),
          needVar, initalizer)
      case enumConstant: PsiEnumConstant =>
        EnumConstruction(convertPsiToIntermediate(enumConstant.getNameIdentifier, externalProperties))
      case f: PsiField =>
        val modifiers = handleModifierList(f)
        val needVar = isVar(f, Option(f.getContainingClass))
        val initalizer = Option(f.getInitializer).map(convertPsiToIntermediate(_, externalProperties))
        val name = convertPsiToIntermediate(f.getNameIdentifier, externalProperties)
        FieldConstruction(modifiers, name, convertTypePsiToIntermediate(f.getType, f.getTypeElement, f.getProject),
          needVar, initalizer)
      case p: PsiParameterList =>
        ParameterListConstruction(p.getParameters.map(convertPsiToIntermediate(_, externalProperties)))
      case m: PsiMethod =>
        def body: Option[IntermediateNode] = {
          if (m.isConstructor) {
            getFirstStatement(m).map(_.getExpression).flatMap {
              case mc: PsiMethodCallExpression if mc.getMethodExpression.getQualifiedName == "this" =>
                Some(convertPsiToIntermediate(m.getBody, externalProperties))
              case _ =>
                getStatements(m).map(statements => BlockConstruction(LiteralExpression("this()")
                  +: statements.map(convertPsiToIntermediate(_, externalProperties))))
            }
          } else {
            Option(m.getBody).map(convertPsiToIntermediate(_, externalProperties))
          }
        }

        def convertMethodReturnType =
          if (m.getReturnType != PsiType.VOID || ScalaCodeStyleSettings.getInstance(m.getProject).ENFORCE_FUNCTIONAL_SYNTAX_FOR_UNIT)
            Some(convertPsiToIntermediate(m.getReturnTypeElement, externalProperties))
          else None

        if (m.isConstructor) {
          ConstructorSimply(handleModifierList(m), m.getTypeParameters.map(convertPsiToIntermediate(_, externalProperties)),
            m.parameters.map(convertPsiToIntermediate(_, externalProperties)), body)
        } else {
          val name = convertPsiToIntermediate(m.getNameIdentifier, externalProperties)
          MethodConstruction(handleModifierList(m), name, m.getTypeParameters.map(convertPsiToIntermediate(_, externalProperties)),
            m.parameters.map(convertPsiToIntermediate(_, externalProperties)), body, convertMethodReturnType)
        }
      case c: PsiClass => createClass(c, externalProperties)
      case p: PsiParenthesizedExpression =>
        val expr = Option(p.getExpression).map(convertPsiToIntermediate(_, externalProperties))
        ParenthesizedExpression(expr)
      case v: PsiArrayInitializerMemberValue =>
        ArrayInitializer(v.getInitializers.map(convertPsiToIntermediate(_, externalProperties)).toSeq)
      case annot: PsiAnnotation =>
        def isArrayAnnotationParameter(pair: PsiNameValuePair): Boolean = {
          AnnotationUtil.getAnnotationMethod(pair) match {
            case method: PsiMethod =>
              val returnType = method.getReturnType
              returnType != null && returnType.isInstanceOf[PsiArrayType]
            case _ => false
          }
        }

        val attributes = annot.getParameterList.getAttributes
        val attrResult = mutable.ArrayBuffer[(Option[IntermediateNode], Option[IntermediateNode])]()
        for (attribute <- attributes) {
          val value = Option(attribute.getValue) match {
            case Some(v: PsiAnnotationMemberValue) if isArrayAnnotationParameter(attribute) =>
              ArrayInitializer(Seq(convertPsiToIntermediate(v, externalProperties)))
            case Some(_) => convertPsiToIntermediate(attribute.getValue, externalProperties)
            case _ => null
          }
          attrResult += ((Option(attribute.getNameIdentifier).map(convertPsiToIntermediate(_, externalProperties)), Option(value)))
        }

        val inAnnotation = PsiTreeUtil.getParentOfType(annot, classOf[PsiAnnotation]) != null

        val name = Option(annot.getNameReferenceElement).map(convertPsiToIntermediate(_, externalProperties))
        AnnotaionConstruction(inAnnotation, attrResult, name)
      case p: PsiParameter =>
        val modifiers = handleModifierList(p)
        val name = convertPsiToIntermediate(p.getNameIdentifier, externalProperties)

        val `type` = convertTypePsiToIntermediate(p.getType, p.getTypeElement, p.getProject)
        if (p.isVarArgs) {
          p.getType match {
            case at: PsiArrayType =>
              ParameterConstruction(modifiers, name,
                convertTypePsiToIntermediate(at.getComponentType, p.getTypeElement.getInnermostComponentReferenceElement, p.getProject),
                None, isArray = true)
            case t =>
              ParameterConstruction(modifiers, name, `type`, None, isArray = false) // should not happen
          }
        } else
          ParameterConstruction(modifiers, name, `type`, None, isArray = false)

      case n: PsiNewExpression =>
        if (n.getAnonymousClass != null) {
          return AnonymousClassExpression(convertPsiToIntermediate(n.getAnonymousClass, externalProperties))
        }

        val iType = convertTypePsiToIntermediate(n.getType, n.getClassReference, n.getProject)
        if (n.getArrayInitializer != null) {
          NewExpression(iType, n.getArrayInitializer.getInitializers.map(convertPsiToIntermediate(_, externalProperties)))
        } else if (n.getArrayDimensions.nonEmpty) {
          NewExpression(iType, n.getArrayDimensions.map(convertPsiToIntermediate(_, externalProperties)),
            withArrayInitalizer = false)
        } else {
          val argList: Seq[IntermediateNode] = if (n.getArgumentList != null) {
            if (n.getArgumentList.getExpressions.isEmpty) {
              n.getParent match {
                case r: PsiJavaCodeReferenceElement if n == r.getQualifier => Seq(LiteralExpression("()"))
                case _ => null
              }
            } else {
              Seq(convertPsiToIntermediate(n.getArgumentList, externalProperties))
            }
          } else null
          NewExpression(iType, argList, withArrayInitalizer = false)
        }
      case t: PsiTryStatement =>
        val resourcesVariables = mutable.ArrayBuffer[(String, IntermediateNode)]()
        Option(t.getResourceList).foreach { resourceList =>
          val it = resourceList.iterator
          while (it.hasNext) {
            val next = it.next()
            next match {
              case varible: PsiResourceVariable =>
                resourcesVariables += ((varible.getName, convertPsiToIntermediate(varible, externalProperties)))
              case _ =>
            }
          }
        }
        val tryBlock = Option(t.getTryBlock).map((c: PsiCodeBlock) => convertPsiToIntermediate(c, externalProperties))
        val catches = t.getCatchSections.map((cb: PsiCatchSection) =>
          (convertPsiToIntermediate(cb.getParameter, externalProperties),
            convertPsiToIntermediate(cb.getCatchBlock, externalProperties)))
        val finallys = Option(t.getFinallyBlock).map((f: PsiCodeBlock) => f.getStatements.map(convertPsiToIntermediate(_, externalProperties)).toSeq)
        TryCatchStatement(resourcesVariables, tryBlock, catches, finallys, ScalaPsiUtil.functionArrow(t.getProject))
      case p: PsiPrefixExpression =>
        PrefixExpression(convertPsiToIntermediate(p.getOperand, externalProperties), p.getOperationSign.getText, canBeSimpified(p))
      case p: PsiPostfixExpression =>
        PostfixExpression(convertPsiToIntermediate(p.getOperand, externalProperties), p.getOperationSign.getText, canBeSimpified(p))
      case p: PsiPolyadicExpression =>
        val tokenValue = if (p.getOperands.nonEmpty) {
          p.getTokenBeforeOperand(p.getOperands.apply(1)).getText
        } else ""
        PolyadicExpression(p.getOperands.map(convertPsiToIntermediate(_, externalProperties)), tokenValue)
      case r: PsiReferenceParameterList => TypeParameters(r.getTypeParameterElements.map(convertPsiToIntermediate(_, externalProperties)))
      case b: PsiBreakStatement =>
        if (b.getLabelIdentifier != null)
          NotSupported(None, "break " + b.getLabelIdentifier.getText + "// todo: label break is not supported")
        else NotSupported(None, "break //todo: break is not supported")
      case c: PsiContinueStatement =>
        if (c.getLabelIdentifier != null)
          NotSupported(None, "continue " + c.getLabelIdentifier.getText + " //todo: continue is not supported")
        else NotSupported(None, "continue //todo: continue is not supported")
      case s: PsiLabeledStatement =>
        val statements = Option(s.getStatement).map(convertPsiToIntermediate(_, externalProperties))
        NotSupported(statements, s.getLabelIdentifier.getText + " //todo: labels is not supported")
      case _: PsiEmptyStatement => EmptyConstruction()
      case _: PsiErrorElement => EmptyConstruction()
      case e => LiteralExpression(e.getText)
    }


    result.setComments(CommentsCollector.allCommentsForElement(element))
    result
  }


  def handleAssociations(element: PsiElement, result: IntermediateNode)
                        (implicit associations: mutable.ListBuffer[AssociationHelper] = mutable.ListBuffer(),
                         references: Seq[ReferenceData] = Seq.empty): Unit = {
    // TODO: eliminate amount of call
    for {
      target <- Option(element)
      range = target.getTextRange

      reference <- references.find { ref =>
        ref.startOffset == range.getStartOffset &&
          ref.endOffset == range.getEndOffset
      }
    } associations += AssociationHelper(result, Path(reference.qClassName, Option(reference.staticMemberName)))

    val associationMap = result match {
      case parametrizedConstruction: ParametrizedConstruction => parametrizedConstruction.associationMap
      case arrayConstruction: ArrayConstruction => arrayConstruction.associationMap
      case _ => Seq.empty
    }

    associations ++= associationMap.collect {
      case (node, Some(entity)) => AssociationHelper(node, Path(entity))
    }
  }

  val fieldParameterMap = mutable.HashMap.empty[String, String]

  import ClassConstruction.ClassType._

  def createClass(inClass: PsiClass, externalProperties: ExternalProperties)
                 (implicit associations: mutable.ListBuffer[AssociationHelper] = mutable.ListBuffer(),
                  refs: Seq[ReferenceData] = Seq.empty,
                  usedComments: mutable.HashSet[PsiElement] = new mutable.HashSet[PsiElement](),
                  textMode: Boolean = false): IntermediateNode = {

    def extendList: Seq[(PsiClassType, PsiJavaCodeReferenceElement)] = {
      val typez = mutable.ArrayBuffer[(PsiClassType, PsiJavaCodeReferenceElement)]()
      if (inClass.getExtendsList != null) typez ++= inClass.getExtendsList.getReferencedTypes.zip(inClass.getExtendsList.getReferenceElements)
      if (inClass.getImplementsList != null) typez ++= inClass.getImplementsList.getReferencedTypes.zip(inClass.getImplementsList.getReferenceElements)
      typez
    }

    def collectClassObjectMembers(): (Seq[PsiMember], Seq[PsiMember]) = {
      var forClass = mutable.ArrayBuffer[PsiMember]()
      var forObject = mutable.ArrayBuffer[PsiMember]()
      for (method <- inClass.getMethods) {
        if (method.hasModifierProperty("static") || inClass.isEnum) forObject += method else forClass += method
      }

      val serialVersionUID = serialVersion(inClass)
      for (field <- inClass.getFields if !serialVersionUID.contains(field)) {
        if (field.hasModifierProperty("static") || inClass.isEnum) forObject += field else forClass += field
      }

      for (clazz <- inClass.getInnerClasses) {
        if (clazz.hasModifierProperty("static") || inClass.isEnum) forObject += clazz else forClass += clazz
      }

      forClass = forClass.sortBy(_.getTextOffset)
      forObject = forObject.sortBy(_.getTextOffset)
      (forClass, forObject)
    }

    val name = convertPsiToIntermediate(inClass.getNameIdentifier, externalProperties)

    def handleObject(objectMembers: Seq[PsiMember]): IntermediateNode = {
      def handleAsEnum(modifiers: IntermediateNode): IntermediateNode = {
        Enum(name, modifiers, objectMembers.map(m => convertPsiToIntermediate(m, externalProperties)))
      }

      def handleAsObject(modifiers: IntermediateNode): IntermediateNode = {
        val membersOut = objectMembers.filter(!_.isInstanceOf[PsiEnumConstant]).map(convertPsiToIntermediate(_, externalProperties))
        val initializers = inClass.getInitializers.map((x: PsiClassInitializer) => convertPsiToIntermediate(x.getBody, externalProperties))
        val primaryConstructor = None
        val typeParams = None
        val companionObject = EmptyConstruction()
        ClassConstruction(name, primaryConstructor, membersOut, modifiers,
          typeParams, Some(initializers), OBJECT, companionObject, None)
      }

      if (objectMembers.nonEmpty && !inClass.isInstanceOf[PsiAnonymousClass]) {
        context.get().push((true, inClass.qualifiedName))
        try {
          val modifiers = handleModifierList(inClass)
          val updatedModifiers = modifiers.asInstanceOf[ModifiersConstruction].without(ModifierType.ABSTRACT)
          if (inClass.isEnum) handleAsEnum(updatedModifiers) else handleAsObject(updatedModifiers)
        } finally {
          context.get().pop()
        }
      } else {
        EmptyConstruction()
      }
    }

    def couldFindInstancesForClass: Boolean = {
      def isParentValid(ref: PsiReference): Boolean =
        Option(ref.getElement).flatMap(element => Option(PsiTreeUtil.getParentOfType(element, classOf[PsiNewExpression], classOf[ScConstructor]))).exists {
          case n: PsiNewExpression if Option(n.getClassReference).contains(ref) => true
          case e: ScConstructor if e.reference.contains(ref) => true
          case _ => false
        }

      def withInstances = {
        import JavaConverters._
        ReferencesSearch
          .search(inClass, GlobalSearchScope.projectScope(inClass.getProject))
          .findAll()
          .asScala
          .exists(isParentValid)
      }

      if (textMode) {
        val p = Pattern.compile(s"new\\s+${inClass.getName}")
        p.matcher(inClass.getContainingFile.getText).find()
      } else withInstances
    }

    def handleAsClass(classMembers: Seq[PsiMember], objectMembers: Seq[PsiMember],
                      companionObject: IntermediateNode, extendList: Seq[(PsiClassType, PsiJavaCodeReferenceElement)]): IntermediateNode = {

      def handleAnonymousClass(clazz: PsiAnonymousClass): IntermediateNode = {
        val tp = convertTypePsiToIntermediate(clazz.getBaseClassType, clazz.getBaseClassReference, clazz.getProject)
        val argList = convertPsiToIntermediate(clazz.getArgumentList, externalProperties)
        AnonymousClass(tp, argList, classMembers.map(convertPsiToIntermediate(_, externalProperties)),
          extendList.map(el => convertTypePsiToIntermediate(el._1, el._2, clazz.getProject)))
      }

      def sortMembers(): Seq[PsiMember] = {
        def isConstructor(member: PsiMember): Boolean =
          member match {
            case Constructor(_) => true
            case _ => false
          }

        def sort(targetMap: mutable.HashMap[PsiMethod, PsiMethod]): Seq[PsiMember] = {
          def compareAsConstructors(left: PsiMethod, right: PsiMethod) = {
            val rightFromMap = targetMap.get(left)
            if (rightFromMap.isDefined && rightFromMap.get == right) {
              false // right constructor must be upper then left
            } else {
              val leftFromMap = targetMap.get(right)
              if (leftFromMap.isDefined && leftFromMap.get == left) {
                true
              } else {
                compareByOrder(right, left)
              }
            }
          }

          def compareByOrder(left: PsiMember, right: PsiMember): Boolean =
            classMembers.indexOf(left) > classMembers.indexOf(right)


          if (targetMap.isEmpty)
            classMembers
          else classMembers.sortWith {
            (left, right) =>
              if (isConstructor(left) && isConstructor(right)) {
                compareAsConstructors(left.asInstanceOf[PsiMethod], right.asInstanceOf[PsiMethod])
              } else {
                compareByOrder(right, left)
              }
          }
        }

        val constructorsCallMap = buildConstructorTargetMap(inClass.getConstructors.sortBy(_.getTextOffset))
        sort(constructorsCallMap)
      }

      def updateMembersAndConvert(dropMembes: Option[Seq[PsiMember]]): Seq[IntermediateNode] = {
        val sortedMembers = sortMembers()
        val updatedMembers = dropMembes.map(el => sortedMembers.filter(!el.contains(_))).getOrElse(sortedMembers)
        updatedMembers.map(convertPsiToIntermediate(_, externalProperties))
      }

      def getDropComments(maybeDropMembes: Option[Seq[PsiMember]]) =
        maybeDropMembes.map { dropMembes =>
          dropMembes.flatMap(CommentsCollector.getAllInsideComments).map(CommentsCollector.convertComment)
        }

      def convertExtendList(): Seq[IntermediateNode] =
        extendList.map { case (a, b) =>
          convertTypePsiToIntermediate(a, b, inClass.getProject)
        }

      if (classMembers.nonEmpty || objectMembers.isEmpty || extendList.nonEmpty || couldFindInstancesForClass) {
        context.get().push((false, inClass.qualifiedName))
        try {
          inClass match {
            case clazz: PsiAnonymousClass => handleAnonymousClass(clazz)
            case _ =>
              val typeParams = inClass.getTypeParameters.map(convertPsiToIntermediate(_, externalProperties))
              val modifiers = handleModifierList(inClass)
              val (dropMembers, primaryConstructor) = handlePrimaryConstructor(inClass.getConstructors)
              val classType = if (inClass.isInterface) INTERFACE else CLASS
              val members = updateMembersAndConvert(dropMembers)

              for {
                constructor <- primaryConstructor
                comments <- getDropComments(dropMembers)
              } constructor.comments.afterComments ++= comments

              ClassConstruction(name, primaryConstructor, members, modifiers, Some(typeParams),
                None, classType, companionObject, Some(convertExtendList()))
          }
        } finally {
          context.get().pop()
        }
      } else {
        companionObject
      }
    }

    val (classMembers, objectMembers) = collectClassObjectMembers()
    val companionObject = handleObject(objectMembers)
    handleAsClass(classMembers, objectMembers, companionObject, extendList)
  }

  def getFirstStatement(constructor: PsiMethod): Option[PsiExpressionStatement] = {
    Option(constructor.getBody).map(_.getStatements)
      .flatMap(_.headOption).collect { case exp: PsiExpressionStatement => exp }
  }

  // build map of constructor and constructor that it call
  def buildConstructorTargetMap(constructors: Seq[PsiMethod]): mutable.HashMap[PsiMethod, PsiMethod] = {
    val toTargetConstructorMap = new mutable.HashMap[PsiMethod, PsiMethod]()

    for (constructor <- constructors) {

      val refExpr = getFirstStatement(constructor).map(_.getExpression).flatMap {
        case mc: PsiMethodCallExpression if mc.getMethodExpression.getQualifiedName == "this" =>
          Some(mc.getMethodExpression)
        case _ => None
      }

      refExpr.foreach { expr =>
        Option(expr.resolve()).collect { case Constructor(m) =>
          val finalTarget: PsiMethod = toTargetConstructorMap.getOrElse(m, m)
          toTargetConstructorMap.put(constructor, finalTarget)
        }
      }
    }

    toTargetConstructorMap
  }

  //primary constructor may apply only when there is one constructor with params
  def handlePrimaryConstructor(constructors: Seq[PsiMethod])
                              (implicit associations: mutable.ListBuffer[AssociationHelper] = mutable.ListBuffer(),
                               refs: Seq[ReferenceData] = Seq.empty,
                               usedComments: mutable.HashSet[PsiElement] = new mutable.HashSet[PsiElement]()): (Option[Seq[PsiMember]], Option[PrimaryConstruction]) = {

    val dropFields = mutable.ArrayBuffer[PsiField]()

    def createPrimaryConstructor(constructor: PsiMethod): PrimaryConstruction = {
      def notContains(statement: PsiStatement, where: Seq[PsiExpressionStatement]): Boolean = {
        !statement.isInstanceOf[PsiExpressionStatement] ||
          (statement.isInstanceOf[PsiExpressionStatement] && !where.contains(statement))
      }

      def getSuperCall(dropStatements: mutable.ArrayBuffer[PsiExpressionStatement]): IntermediateNode = {
        val firstStatement = getFirstStatement(constructor)
        val maybeSuperCall: Option[PsiMethodCallExpression] = firstStatement.map(_.getExpression).flatMap {
          case mc: PsiMethodCallExpression if mc.getMethodExpression.getQualifiedName == "super" => Some(mc)
          case _ => None
        }

        maybeSuperCall.map { superCall =>
          dropStatements += firstStatement.get
          convertPsiToIntermediate(superCall.getArgumentList, null)
        }.orNull
      }

      def getCorrespondedFieldInfo(param: PsiParameter): Seq[(PsiField, PsiExpressionStatement)] = {
        val dropInfo = mutable.ArrayBuffer[(PsiField, PsiExpressionStatement)]()

        findVariableUsage(param, Option(constructor.getBody)).foreach { usage =>
          val parent = Option(usage.getParent)
          val field: Option[PsiField] = parent.flatMap {
            case ae: PsiAssignmentExpression if (ae.getOperationSign.getTokenType == JavaTokenType.EQ) && ae.getLExpression.isInstanceOf[PsiReferenceExpression] =>
              ae.getLExpression.asInstanceOf[PsiReferenceExpression].resolve() match {
                case f: PsiField if f.getContainingClass == constructor.getContainingClass && f.getInitializer == null => Some(f)
                case _ => None
              }
            case _ => None
          }

          val statement: Option[PsiExpressionStatement] =
            parent
              .flatMap(p => Option(p.getParent))
              .collect { case p: PsiExpressionStatement => p }

          field.zip(statement).foreach { case (f, s) if s.getParent == constructor.getBody =>
            dropInfo += ((f, s))
            if (f.getName != param.getName) fieldParameterMap += ((param.getName, f.getName))
          }
        }

        dropInfo
      }

      def createContructor: PrimaryConstruction = {
        val params = constructor.parameters
        val updatedParams = mutable.ArrayBuffer[IntermediateNode]()
        val dropStatements = mutable.ArrayBuffer[PsiExpressionStatement]()
        for (param <- params) {
          val fieldInfo = getCorrespondedFieldInfo(param)
          val updatedField = if (fieldInfo.isEmpty) {
            val p = convertPsiToIntermediate(param, null).asInstanceOf[ParameterConstruction]
            p.isVar = Some(false)
            p
          } else {
            fieldInfo.foreach {
              case (field, statement) =>
                dropFields += field
                dropStatements += statement
            }
            val fieldConverted = convertPsiToIntermediate(fieldInfo.head._1, WithReferenceExpression(true)).asInstanceOf[FieldConstruction]
            val param = ParameterConstruction(EmptyConstruction(), fieldConverted.name, fieldConverted.ftype, Some(fieldConverted.isVar), isArray = false)
            param.setComments(fieldConverted.comments)
            param
          }
          updatedParams += updatedField
        }

        val superCall = getSuperCall(dropStatements)

        getStatements(constructor).map {
          statements =>
            PrimaryConstruction(updatedParams, superCall,
              Option(statements.filter(notContains(_, dropStatements))
                .map(convertPsiToIntermediate(_, WithReferenceExpression(true)))), handleModifierList(constructor))
        }.orNull
      }

      createContructor
    }

    //If can't choose one - return emptyConstructor
    def GetComplexPrimaryConstructor(): PsiMethod = {
      val possibleConstructors = buildConstructorTargetMap(constructors)
      val candidates = constructors.filter(!possibleConstructors.contains(_))

      def tryFindWithoutParamConstructor(): PsiMethod = {
        val emptyParamsConstructors = constructors.filter(_.getParameterList.getParametersCount == 0)
        emptyParamsConstructors.length match {
          case 1 => emptyParamsConstructors.head
          case _ => null
        }
      }

      // we expected to have one primary constructor
      // or try to use constructor with empty parameters if it is defined
      // and there are other constructors
      candidates.length match {
        case 1 => candidates.head
        case _ => tryFindWithoutParamConstructor()
      }
    }

    constructors.length match {
      case 0 => (None, None)
      case 1 =>
        val updatedConstructor = createPrimaryConstructor(constructors.head)
        (Some(constructors.head +: dropFields), Some(updatedConstructor))
      case _ =>
        val pc = GetComplexPrimaryConstructor()
        if (pc != null) {
          val updatedConstructor = createPrimaryConstructor(pc)
          (Some(pc +: dropFields), Some(updatedConstructor))
        }
        else (None, None)
    }
  }

  val SIMPLE_MODIFIERS_MAP = Map(
    (PsiModifier.VOLATILE, ModifierType.VOLATILE),
    (PsiModifier.PRIVATE, ModifierType.PRIVATE),
    (PsiModifier.PROTECTED, ModifierType.PROTECTED),
    (PsiModifier.TRANSIENT, ModifierType.TRANSIENT),
    (PsiModifier.NATIVE, ModifierType.NATIVE)
  )

  def handleModifierList(owner: PsiModifierListOwner)
                        (implicit associations: mutable.ListBuffer[AssociationHelper] = mutable.ListBuffer(),
                         refs: Seq[ReferenceData] = Seq.empty,
                         usedComments: mutable.HashSet[PsiElement] = new mutable.HashSet[PsiElement]()): IntermediateNode = {

    val annotationDropList = Seq("java.lang.Override", "org.jetbrains.annotations.Nullable",
      "org.jetbrains.annotations.NotNull", "org.jetbrains.annotations.NonNls")

    def handleAnnotations: Seq[IntermediateNode] = {
      val annotations = mutable.ArrayBuffer[IntermediateNode]()
      for {
        a <- owner.getModifierList.getAnnotations
        optValue = Option(a.getQualifiedName).map(annotationDropList.contains(_))
        if optValue.exists(_ == false)
      } {
        annotations.append(convertPsiToIntermediate(a, null))
      }
      annotations
    }

    def handleModifiers: Seq[IntermediateNode] = {
      val modifiers = mutable.ArrayBuffer[IntermediateNode]()

      val simpleList = SIMPLE_MODIFIERS_MAP.filter {
        case (psiType, _) => owner.hasModifierProperty(psiType)
      }.values

      modifiers ++= simpleList.map(SimpleModifier)

      owner match {
        case method: PsiMethod =>
          val references = method.getThrowsList.getReferenceElements
          for (ref <- references) {
            modifiers.append(ModifierWithExpression(ModifierType.THROW, convertPsiToIntermediate(ref, null)))
          }

          if (method.findSuperMethods.exists(!_.hasModifierProperty("abstract")
            || ScalaProjectSettings.getInstance(method.getProject).isAddOverrideToImplementInConverter)) {
            modifiers.append(SimpleModifier(ModifierType.OVERRIDE))
          }

        case c: PsiClass =>
          serialVersion(c) match {
            case Some(f) =>
              modifiers.append(ModifierWithExpression(ModifierType.SerialVersionUID, convertPsiToIntermediate(f.getInitializer, null)))
            case _ =>
          }

          if ((!c.isInterface) && c.hasModifierProperty(PsiModifier.ABSTRACT))
            modifiers.append(SimpleModifier(ModifierType.ABSTRACT))

        case _ =>
      }

      if (!owner.hasModifierProperty(PsiModifier.PUBLIC) &&
        !owner.hasModifierProperty(PsiModifier.PRIVATE) &&
        !owner.hasModifierProperty(PsiModifier.PROTECTED) &&
        owner.getParent != null && owner.getParent.isInstanceOf[PsiClass]) {
        val packageName: String = owner.getContainingFile.asInstanceOf[PsiClassOwner].getPackageName
        if (packageName != "")
          modifiers.append(ModifierWithExpression(ModifierType.PRIVATE,
            LiteralExpression(packageName.substring(packageName.lastIndexOf(".") + 1))))
      }

      if (owner.hasModifierProperty(PsiModifier.FINAL) && context.get.nonEmpty && !context.get.top._1) {
        owner match {
          case _: PsiLocalVariable =>
          case _: PsiParameter =>
          case _ =>
            modifiers.append(SimpleModifier(ModifierType.FINAL)) //only to classes, not objects
        }
      }

      modifiers
    }

    val ml = ModifiersConstruction(handleAnnotations, handleModifiers)
    ml.setComments(CommentsCollector.allCommentsForElement(owner.getModifierList))
    ml
  }

  import visitors.PrintWithComments

  def convertPsisToText(elements: Array[PsiElement],
                        dropElements: mutable.HashSet[PsiElement] = new mutable.HashSet[PsiElement](), textMode: Boolean = false): String = {
    val resultNode = new MainConstruction
    for (part <- elements) {
      resultNode.addChild(convertPsiToIntermediate(part, null)(mutable.ListBuffer(), Seq.empty, dropElements, textMode = textMode))
    }

    val PrintWithComments(text) = resultNode
    text
  }

  def convertPsiToText(element: PsiElement): String = {
    val PrintWithComments(text) = convertPsiToIntermediate(element, null)(textMode = true)

    val file = new ScalaCodeFragment(element.getProject, text)
    ConverterUtil.cleanCode(file, element.getProject, 0, file.getTextLength)
    file.getText
  }

  private def handleImport(iimport: PsiImportStatementBase): IntermediateNode = {
    Option(iimport.getImportReference).map(_.getQualifiedName) match {
      case Some(qName) if ScalaCodeStyleSettings.getInstance(iimport.getProject).hasImportWithPrefix(qName) =>
        ImportStatement(LiteralExpression(qName.split('.').init.mkString(".")), iimport.isOnDemand)
      case Some(name) =>
        ImportStatement(LiteralExpression(name), iimport.isOnDemand)
      case _ => EmptyConstruction()
    }
  }

  private def getStatements(m: PsiMethod): Option[Array[PsiStatement]] = Option(m.getBody).map(_.getStatements)

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
          case _: PsiBlockStatement => true
          case _: PsiCodeBlock => true
          case _ => false
        }
      case _ => false
    }
  }
}
