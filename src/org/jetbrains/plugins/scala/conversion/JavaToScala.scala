package org.jetbrains.plugins.scala
package conversion


import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.editorActions.ReferenceData
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.{PsiTreeUtil, PsiUtil}
import com.intellij.psi.{PsiLambdaExpression, _}
import org.jetbrains.plugins.scala.conversion.ast.ClassConstruction.ClassType
import org.jetbrains.plugins.scala.conversion.ast._
import org.jetbrains.plugins.scala.conversion.copy.AssociationHelper
import org.jetbrains.plugins.scala.conversion.visitors.PrintWithComments
import org.jetbrains.plugins.scala.debugger.evaluation.ScalaCodeFragment
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, PsiMemberExt, PsiMethodExt}
import org.jetbrains.plugins.scala.lang.dependency.{DependencyKind, Path}
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import scala.collection.mutable
import scala.collection.mutable.{ArrayBuffer, ListBuffer}
import scala.language.postfixOps

/**
  * Author: Alexander Podkhalyuzin
  * Date: 23.07.2009
  */
object JavaToScala {
  private val context: ThreadLocal[mutable.Stack[(Boolean, String)]] = new ThreadLocal[mutable.Stack[(Boolean, String)]] {
    override def initialValue(): mutable.Stack[(Boolean, String)] = new mutable.Stack[(Boolean, String)]()
  }

  def findVariableUsage(elementToFind: PsiElement, elementWhereFindOption: Option[PsiElement]): Seq[PsiReferenceExpression] = {
    import scala.collection.JavaConverters._
    def searchReference(elementWhereFind: PsiElement) = {
      ReferencesSearch.search(elementToFind, new LocalSearchScope(elementWhereFind)).findAll().asScala.toSeq
        .collect { case el: PsiReferenceExpression => el }
    }

    elementWhereFindOption.map(elementWhereFind =>
      searchReference(elementWhereFind)).getOrElse(Seq[PsiReferenceExpression]())
  }

  def isVar(element: PsiModifierListOwner, parent: Option[PsiElement]): Boolean = {
    def usageInConstructorParams(usage: PsiReferenceExpression): Boolean = {
      def correspondedConstructorParams: Seq[PsiParameter] = {
        val constructor = Option(PsiTreeUtil.getParentOfType(usage, classOf[PsiMethod]))
        if (constructor.isDefined && constructor.get.isConstructor) {
          constructor.get.parameters
        } else {
          Seq[PsiParameter]()
        }
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
                                  (implicit associations: ListBuffer[AssociationHelper] = new ListBuffer(),
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

  def convertPsiToIntermdeiate(element: PsiElement, externalProperties: ExternalProperties)
                              (implicit associations: ListBuffer[AssociationHelper] = new ListBuffer(),
                               refs: Seq[ReferenceData] = Seq.empty,
                               usedComments: mutable.HashSet[PsiElement] = new mutable.HashSet[PsiElement](),
                               textMode: Boolean = false): IntermediateNode = {
    if (element == null || usedComments.contains(element)) return EmptyConstruction()
    if (element.getLanguage != JavaLanguage.INSTANCE) EmptyConstruction()
    val result: IntermediateNode = element match {
      case f: PsiFile =>
        val m = MainConstruction()
        m.addChildren(f.getChildren.map(convertPsiToIntermdeiate(_, externalProperties)))
        m
      case e: PsiExpressionStatement => convertPsiToIntermdeiate(e.getExpression, externalProperties)
      case l: PsiLiteralExpression => LiteralExpression(l.getText)
      case n: PsiIdentifier => NameIdentifier(n.getText)
      case t: PsiTypeElement => convertTypePsiToIntermediate(t.getType, t, t.getProject)
      case w: PsiWhiteSpace => LiteralExpression(w.getText)
      case r: PsiReturnStatement => ReturnStatement(convertPsiToIntermdeiate(r.getReturnValue, externalProperties))
      case t: PsiThrowStatement => ThrowStatement(convertPsiToIntermdeiate(t.getException, externalProperties))
      case i: PsiImportStatement => handleImport(i)
      case i: PsiImportStaticStatement => handleImport(i)
      case i: PsiImportList => ImportStatementList(i.getAllImportStatements.map(handleImport).distinct)
      case a: PsiAssignmentExpression =>
        BinaryExpressionConstruction(convertPsiToIntermdeiate(a.getLExpression, externalProperties),
          convertPsiToIntermdeiate(a.getRExpression, externalProperties), a.getOperationSign.getText, inExpression = false)
      case e: PsiExpressionListStatement =>
        ExpressionListStatement(e.getExpressionList.getExpressions.map(convertPsiToIntermdeiate(_, externalProperties)))
      case d: PsiDeclarationStatement => ExpressionListStatement(d.getDeclaredElements.map(convertPsiToIntermdeiate(_, externalProperties)))
      case b: PsiBlockStatement => convertPsiToIntermdeiate(b.getCodeBlock, externalProperties)
      case s: PsiSynchronizedStatement =>
        val lock = Option(s.getLockExpression).map(convertPsiToIntermdeiate(_, externalProperties))
        val body = Option(s.getBody).map(convertPsiToIntermdeiate(_, externalProperties))
        SynchronizedStatement(lock, body)
      case b: PsiCodeBlock =>
        BlockConstruction(b.getStatements.map(convertPsiToIntermdeiate(_, externalProperties)))
      case t: PsiTypeParameter =>
        TypeParameterConstruction(convertPsiToIntermdeiate(t.getNameIdentifier, externalProperties),
          t.getExtendsList.getReferenceElements.map(convertPsiToIntermdeiate(_, externalProperties)))
      case i: PsiIfStatement =>
        val condition = Option(i.getCondition).map(convertPsiToIntermdeiate(_, externalProperties))
        val thenBranch = Option(i.getThenBranch).map(convertPsiToIntermdeiate(_, externalProperties))
        val elseBranch = Option(i.getElseBranch).map(convertPsiToIntermdeiate(_, externalProperties))
        IfStatement(condition, thenBranch, elseBranch)
      case c: PsiConditionalExpression =>
        val condition = Option(c.getCondition).map(convertPsiToIntermdeiate(_, externalProperties))
        val thenBranch = Option(c.getThenExpression).map(convertPsiToIntermdeiate(_, externalProperties))
        val elseBranch = Option(c.getElseExpression).map(convertPsiToIntermdeiate(_, externalProperties))
        IfStatement(condition, thenBranch, elseBranch)
      case w: PsiWhileStatement =>
        val condition = Option(w.getCondition).map(convertPsiToIntermdeiate(_, externalProperties))
        val body = Option(w.getBody).map(convertPsiToIntermdeiate(_, externalProperties))
        WhileStatement(None, condition, body, None, WhileStatement.PRE_TEST_LOOP)
      case w: PsiDoWhileStatement =>
        val condition = Option(w.getCondition).map(convertPsiToIntermdeiate(_, externalProperties))
        val body = Option(w.getBody).map(convertPsiToIntermdeiate(_, externalProperties))
        WhileStatement(None, condition, body, None, WhileStatement.POST_TEST_LOOP)
      case f: PsiForStatement =>
        val initialization = Option(f.getInitialization).map(convertPsiToIntermdeiate(_, externalProperties))
        val condition = Some(f.getCondition match {
          case _: PsiEmptyStatement => LiteralExpression("true")
          case null => LiteralExpression("true")
          case _ => convertPsiToIntermdeiate(f.getCondition, externalProperties)
        })
        val body = Option(f.getBody).map(convertPsiToIntermdeiate(_, externalProperties))
        val update = Option(f.getUpdate).map(convertPsiToIntermdeiate(_, externalProperties))
        WhileStatement(initialization, condition, body, update, WhileStatement.PRE_TEST_LOOP)
      case a: PsiAssertStatement =>
        val condition = Option(a.getAssertCondition).map(convertPsiToIntermdeiate(_, externalProperties))
        val description = Option(a.getAssertDescription).map(convertPsiToIntermdeiate(_, externalProperties))
        AssertStatement(condition, description)
      case s: PsiSwitchLabelStatement =>
        val caseValue = if (s.isDefaultCase)
          Some(LiteralExpression("_"))
        else
          Option(s.getCaseValue).map(convertPsiToIntermdeiate(_, externalProperties))
        SwitchLabelStatement(caseValue, ScalaPsiUtil.functionArrow(s.getProject))
      case s: PsiSwitchStatement =>
        def statements = Option(s.getBody).map(_.getStatements)

        def defaultStatement = SwitchLabelStatement(Some(LiteralExpression("_")), ScalaPsiUtil.functionArrow(s.getProject))

        val expr = Option(s.getExpression).map(convertPsiToIntermdeiate(_, externalProperties))
        val body = Option(s.getBody).map(convertPsiToIntermdeiate(_, externalProperties))

        if (statements.exists(_.length == 0)) SwitchStatemtnt(expr, Some(defaultStatement)) else SwitchStatemtnt(expr, body)
      case p: PsiPackageStatement => PackageStatement(convertPsiToIntermdeiate(p.getPackageReference, externalProperties))
      case f: PsiForeachStatement =>
        val tp = Option(f.getIteratedValue).flatMap((e: PsiExpression) => Option(e.getType))
        val isJavaCollection = if (tp.isEmpty) true else !tp.get.isInstanceOf[PsiArrayType]

        val iteratedValue = Option(f.getIteratedValue).map(convertPsiToIntermdeiate(_, externalProperties))
        val body = Option(f.getBody).map(convertPsiToIntermdeiate(_, externalProperties))
        val name = convertPsiToIntermdeiate(f.getIterationParameter.getNameIdentifier, externalProperties)
        ForeachStatement(name, iteratedValue, body, isJavaCollection)
      case r: PsiReferenceExpression =>
        val args = Option(r.getParameterList).map(convertPsiToIntermdeiate(_, externalProperties))

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
          val t = Option(r.getQualifierExpression).map(convertPsiToIntermdeiate(_, externalProperties))
          iResult = JavaCodeReferenceStatement(t, args, refName)
        } else {
          r.resolve() match {
            case f: PsiMember
              if f.hasModifierProperty("static") =>
              val clazz = f.containingClass
              if (clazz != null && context.get().contains((false, clazz.qualifiedName))) {
                val name = Option(clazz.getNameIdentifier).map(convertPsiToIntermdeiate(_, externalProperties))
                iResult = JavaCodeReferenceStatement(name, args, refName)
              }

            case _ =>
          }
        }

        handleAssociations(r, iResult)
        iResult
      case p: PsiJavaCodeReferenceElement =>
        val qualifier = Option(p.getQualifier).map(convertPsiToIntermdeiate(_, externalProperties))
        val args = Option(p.getParameterList).map(convertPsiToIntermdeiate(_, externalProperties))
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
          convertPsiToIntermdeiate(be.getLOperand, externalProperties),
          convertPsiToIntermdeiate(be.getROperand, externalProperties),
          operation, inExpression)
      case c: PsiTypeCastExpression =>
        ClassCast(
          convertPsiToIntermdeiate(c.getOperand, externalProperties), convertPsiToIntermdeiate(c.getCastType, externalProperties),
          c.getCastType.getType.isInstanceOf[PsiPrimitiveType] && c.getOperand.getType.isInstanceOf[PsiPrimitiveType])
      case a: PsiArrayAccessExpression =>
        ArrayAccess(
          convertPsiToIntermdeiate(a.getArrayExpression, externalProperties),
          convertPsiToIntermdeiate(a.getIndexExpression, externalProperties))
      case a: PsiArrayInitializerExpression =>
        ArrayInitializer(a.getInitializers.map(convertPsiToIntermdeiate(_, externalProperties)))
      case c: PsiClassObjectAccessExpression => ClassObjectAccess(convertPsiToIntermdeiate(c.getOperand, externalProperties))
      case i: PsiInstanceOfExpression =>
        InstanceOfConstruction(
          convertPsiToIntermdeiate(i.getOperand, externalProperties),
          convertPsiToIntermdeiate(i.getCheckType, externalProperties))
      case m: PsiMethodCallExpression =>
        m.getMethodExpression.resolve() match {
          case method: PsiMethod if method.getName == "parseInt" && m.getArgumentList.getExpressions.length == 1 &&
            method.getContainingClass != null && method.getContainingClass.qualifiedName == "java.lang.Integer" =>
            ClassCast(convertPsiToIntermdeiate(m.getArgumentList.getExpressions.apply(0), externalProperties),
              TypeConstruction("Int"), isPrimitive = true)
          case method: PsiMethod if method.getName == "parseDouble" && m.getArgumentList.getExpressions.length == 1 &&
            method.getContainingClass != null && method.getContainingClass.qualifiedName == "java.lang.Double" =>
            ClassCast(convertPsiToIntermdeiate(m.getArgumentList.getExpressions.apply(0), externalProperties),
              TypeConstruction("Double"), isPrimitive = true)
          case method: PsiMethod if method.getName == "round" && m.getArgumentList.getExpressions.length == 1 &&
            method.getContainingClass != null && method.getContainingClass.qualifiedName == "java.lang.Math" =>
            MethodCallExpression.build(
              convertPsiToIntermdeiate(m.getArgumentList.getExpressions.apply(0), externalProperties), ".round", null)
          case method: PsiMethod if method.getName == "equals" && m.getTypeArguments.isEmpty
            && m.getArgumentList.getExpressions.length == 1 =>
            MethodCallExpression.build(
              Option(m.getMethodExpression.getQualifierExpression).map(convertPsiToIntermdeiate(_, externalProperties))
                .getOrElse(LiteralExpression("this")),
              " == ", convertPsiToIntermdeiate(m.getArgumentList.getExpressions.apply(0), externalProperties))
          case _ =>
            MethodCallExpression(m.getMethodExpression.getQualifiedName,
              convertPsiToIntermdeiate(m.getMethodExpression, externalProperties),
              convertPsiToIntermdeiate(m.getArgumentList, externalProperties),
              (m.getType == PsiType.VOID) && m.getArgumentList.getExpressions.isEmpty)
        }
      case t: PsiThisExpression =>
        ThisExpression(Option(t.getQualifier).map(convertPsiToIntermdeiate(_, externalProperties)))
      case s: PsiSuperExpression =>
        SuperExpression(Option(s.getQualifier).map(convertPsiToIntermdeiate(_, externalProperties)))
      case e: PsiExpressionList =>
        ExpressionList(e.getExpressions.map(convertPsiToIntermdeiate(_, externalProperties)))
      case lambda: PsiLambdaExpression =>
        FunctionalExpression(
          convertPsiToIntermdeiate(lambda.getParameterList, externalProperties),
          convertPsiToIntermdeiate(lambda.getBody, externalProperties))
      case l: PsiLocalVariable =>
        val parent = Option(PsiTreeUtil.getParentOfType(l, classOf[PsiCodeBlock], classOf[PsiBlockStatement]))
        val needVar = if (parent.isEmpty) false else isVar(l, parent)
        val initalizer = Option(l.getInitializer).map(convertPsiToIntermdeiate(_, externalProperties))
        val name = convertPsiToIntermdeiate(l.getNameIdentifier, externalProperties)
        LocalVariable(handleModifierList(l), name, convertTypePsiToIntermediate(l.getType, l.getTypeElement, l.getProject),
          needVar, initalizer)
      case enumConstant: PsiEnumConstant =>
        EnumConstruction(convertPsiToIntermdeiate(enumConstant.getNameIdentifier, externalProperties))
      case f: PsiField =>
        val modifiers = handleModifierList(f)
        val needVar = isVar(f, Option(f.getContainingClass))
        val initalizer = Option(f.getInitializer).map(convertPsiToIntermdeiate(_, externalProperties))
        val name = convertPsiToIntermdeiate(f.getNameIdentifier, externalProperties)
        FieldConstruction(modifiers, name, convertTypePsiToIntermediate(f.getType, f.getTypeElement, f.getProject),
          needVar, initalizer)
      case p: PsiParameterList =>
        ParameterListConstruction(p.getParameters.map(convertPsiToIntermdeiate(_, externalProperties)))
      case m: PsiMethod =>
        def body: Option[IntermediateNode] = {
          if (m.isConstructor) {
            getFirstStatement(m).map(_.getExpression).flatMap {
              case mc: PsiMethodCallExpression if mc.getMethodExpression.getQualifiedName == "this" =>
                Some(convertPsiToIntermdeiate(m.getBody, externalProperties))
              case _ =>
                getStatements(m).map(statements => BlockConstruction(LiteralExpression("this()")
                  +: statements.map(convertPsiToIntermdeiate(_, externalProperties))))
            }
          } else {
            Option(m.getBody).map(convertPsiToIntermdeiate(_, externalProperties))
          }
        }

        def convertMethodReturnType =
          if (m.getReturnType != PsiType.VOID || ScalaCodeStyleSettings.getInstance(m.getProject).ENFORCE_FUNCTIONAL_SYNTAX_FOR_UNIT)
            Some(convertPsiToIntermdeiate(m.getReturnTypeElement, externalProperties))
          else None

        if (m.isConstructor) {
          ConstructorSimply(handleModifierList(m), m.getTypeParameters.map(convertPsiToIntermdeiate(_, externalProperties)),
            m.parameters.map(convertPsiToIntermdeiate(_, externalProperties)), body)
        } else {
          val name = convertPsiToIntermdeiate(m.getNameIdentifier, externalProperties)
          MethodConstruction(handleModifierList(m), name, m.getTypeParameters.map(convertPsiToIntermdeiate(_, externalProperties)),
            m.parameters.map(convertPsiToIntermdeiate(_, externalProperties)), body, convertMethodReturnType)
        }
      case c: PsiClass => createClass(c, externalProperties)
      case p: PsiParenthesizedExpression =>
        val expr = Option(p.getExpression).map(convertPsiToIntermdeiate(_, externalProperties))
        ParenthesizedExpression(expr)
      case v: PsiArrayInitializerMemberValue =>
        ArrayInitializer(v.getInitializers.map(convertPsiToIntermdeiate(_, externalProperties)).toSeq)
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
        val attrResult = new ArrayBuffer[(Option[IntermediateNode], Option[IntermediateNode])]()
        for (attribute <- attributes) {
          val value = Option(attribute.getValue) match {
            case Some(v: PsiAnnotationMemberValue) if isArrayAnnotationParameter(attribute) =>
              ArrayInitializer(Seq(convertPsiToIntermdeiate(v, externalProperties)))
            case Some(_) => convertPsiToIntermdeiate(attribute.getValue, externalProperties)
            case _ => null
          }
          attrResult += ((Option(attribute.getNameIdentifier).map(convertPsiToIntermdeiate(_, externalProperties)), Option(value)))
        }

        val inAnnotation = PsiTreeUtil.getParentOfType(annot, classOf[PsiAnnotation]) != null

        val name = Option(annot.getNameReferenceElement).map(convertPsiToIntermdeiate(_, externalProperties))
        AnnotaionConstruction(inAnnotation, attrResult, name)
      case p: PsiParameter =>
        val modifiers = handleModifierList(p)
        val name = convertPsiToIntermdeiate(p.getNameIdentifier, externalProperties)

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
          return AnonymousClassExpression(convertPsiToIntermdeiate(n.getAnonymousClass, externalProperties))
        }

        val iType = convertTypePsiToIntermediate(n.getType, n.getClassReference, n.getProject)
        if (n.getArrayInitializer != null) {
          NewExpression(iType, n.getArrayInitializer.getInitializers.map(convertPsiToIntermdeiate(_, externalProperties)))
        } else if (n.getArrayDimensions.nonEmpty) {
          NewExpression(iType, n.getArrayDimensions.map(convertPsiToIntermdeiate(_, externalProperties)),
            withArrayInitalizer = false)
        } else {
          val argList: Seq[IntermediateNode] = if (n.getArgumentList != null) {
            if (n.getArgumentList.getExpressions.isEmpty) {
              n.getParent match {
                case r: PsiJavaCodeReferenceElement if n == r.getQualifier => Seq(LiteralExpression("()"))
                case _ => null
              }
            } else {
              Seq(convertPsiToIntermdeiate(n.getArgumentList, externalProperties))
            }
          } else null
          NewExpression(iType, argList, withArrayInitalizer = false)
        }
      case t: PsiTryStatement =>
        val resourceList = Option(t.getResourceList)
        val resourcesVariables = new ArrayBuffer[(String, IntermediateNode)]()
        if (resourceList.isDefined) {
          val it = resourceList.get.iterator
          while (it.hasNext) {
            val next = it.next()
            next match {
              case varible: PsiResourceVariable =>
                resourcesVariables += ((varible.getName, convertPsiToIntermdeiate(varible, externalProperties)))
              case _ =>
            }
          }
        }
        val tryBlock = Option(t.getTryBlock).map((c: PsiCodeBlock) => convertPsiToIntermdeiate(c, externalProperties))
        val catches = t.getCatchSections.map((cb: PsiCatchSection) =>
          (convertPsiToIntermdeiate(cb.getParameter, externalProperties),
            convertPsiToIntermdeiate(cb.getCatchBlock, externalProperties)))
        val finallys = Option(t.getFinallyBlock).map((f: PsiCodeBlock) => f.getStatements.map(convertPsiToIntermdeiate(_, externalProperties)).toSeq)
        TryCatchStatement(resourcesVariables, tryBlock, catches, finallys, ScalaPsiUtil.functionArrow(t.getProject))
      case p: PsiPrefixExpression =>
        PrefixExpression(convertPsiToIntermdeiate(p.getOperand, externalProperties), p.getOperationSign.getText, canBeSimpified(p))
      case p: PsiPostfixExpression =>
        PostfixExpression(convertPsiToIntermdeiate(p.getOperand, externalProperties), p.getOperationSign.getText, canBeSimpified(p))
      case p: PsiPolyadicExpression =>
        val tokenValue = if (p.getOperands.nonEmpty) {
          p.getTokenBeforeOperand(p.getOperands.apply(1)).getText
        } else ""
        PolyadicExpression(p.getOperands.map(convertPsiToIntermdeiate(_, externalProperties)), tokenValue)
      case r: PsiReferenceParameterList => TypeParameters(r.getTypeParameterElements.map(convertPsiToIntermdeiate(_, externalProperties)))
      case b: PsiBreakStatement =>
        if (b.getLabelIdentifier != null)
          NotSupported(None, "break " + b.getLabelIdentifier.getText + "// todo: label break is not supported")
        else NotSupported(None, "break //todo: break is not supported")
      case c: PsiContinueStatement =>
        if (c.getLabelIdentifier != null)
          NotSupported(None, "continue " + c.getLabelIdentifier.getText + " //todo: continue is not supported")
        else NotSupported(None, "continue //todo: continue is not supported")
      case s: PsiLabeledStatement =>
        val statements = Option(s.getStatement).map(convertPsiToIntermdeiate(_, externalProperties))
        NotSupported(statements, s.getLabelIdentifier.getText + " //todo: labels is not supported")
      case _: PsiEmptyStatement => EmptyConstruction()
      case _: PsiErrorElement => EmptyConstruction()
      case e => LiteralExpression(e.getText)
    }


    result.setComments(CommentsCollector.allCommentsForElement(element))
    result
  }


  def handleAssociations(element: PsiElement, result: IntermediateNode)
                        (implicit associations: ListBuffer[AssociationHelper] = new ListBuffer(),
                         refs: Seq[ReferenceData] = Seq.empty,
                         usedComments: mutable.HashSet[PsiElement] = new mutable.HashSet[PsiElement]()) = {
    // TODO: eliminate amout of call
    associations ++= associationFor(element, result)

    result match {
      case parametrizedConstruction: ParametrizedConstruction =>
        associations ++= parametrizedConstruction.getAssociations.map {
          case (node, path) => AssociationHelper(DependencyKind.Reference, node, Path(path))
        }
      case arrayConstruction: ArrayConstruction =>
        associations ++= arrayConstruction.getAssociations.map {
          case (node, path) => AssociationHelper(DependencyKind.Reference, node, Path(path))
        }
      case _ =>
    }
  }

  def associationFor(optRange: Option[TextRange], result: IntermediateNode)
                    (implicit associations: ListBuffer[AssociationHelper] = new ListBuffer(),
                     refs: Seq[ReferenceData] = Seq.empty,
                     usedComments: mutable.HashSet[PsiElement] = new mutable.HashSet[PsiElement]()): Option[AssociationHelper] = {
    optRange.flatMap { range =>
      refs.find(ref => new TextRange(ref.startOffset, ref.endOffset) == range).map { ref =>
        Option(ref.staticMemberName) match {
          case Some(sName) => AssociationHelper(DependencyKind.Reference, result, Path(ref.qClassName, sName))
          case _ => AssociationHelper(DependencyKind.Reference, result, Path(ref.qClassName))
        }
      }
    }
  }

  implicit def psiToOptTextRange(psiElement: PsiElement): Option[TextRange] = {
    Option(psiElement).map(_.getTextRange)
  }

  val fieldParameterMap = new mutable.HashMap[String, String]()

  def createClass(inClass: PsiClass, externalProperties: ExternalProperties)
                 (implicit associations: ListBuffer[AssociationHelper] = new ListBuffer(),
                  refs: Seq[ReferenceData] = Seq.empty,
                  usedComments: mutable.HashSet[PsiElement] = new mutable.HashSet[PsiElement](),
                  textMode: Boolean = false): IntermediateNode = {

    def extendList: Seq[(PsiClassType, PsiJavaCodeReferenceElement)] = {
      val typez = new ArrayBuffer[(PsiClassType,  PsiJavaCodeReferenceElement)]
      if (inClass.getExtendsList != null) typez ++= inClass.getExtendsList.getReferencedTypes.zip(inClass.getExtendsList.getReferenceElements)
      if (inClass.getImplementsList != null) typez ++= inClass.getImplementsList.getReferencedTypes.zip(inClass.getImplementsList.getReferenceElements)
      typez
    }
    def collectClassObjectMembers(): (Seq[PsiMember], Seq[PsiMember]) = {
      var forClass = new ArrayBuffer[PsiMember]()
      var forObject = new ArrayBuffer[PsiMember]()
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

    val name = convertPsiToIntermdeiate(inClass.getNameIdentifier, externalProperties)
    def handleObject(objectMembers: Seq[PsiMember]): IntermediateNode = {
      def handleAsEnum(modifiers: IntermediateNode): IntermediateNode = {
        Enum(name, modifiers, objectMembers.map(m => convertPsiToIntermdeiate(m, externalProperties)))
      }

      def handleAsObject(modifiers: IntermediateNode): IntermediateNode = {
        val membersOut = objectMembers.filter(!_.isInstanceOf[PsiEnumConstant]).map(convertPsiToIntermdeiate(_, externalProperties))
        val initializers = inClass.getInitializers.map((x: PsiClassInitializer) => convertPsiToIntermdeiate(x.getBody, externalProperties))
        val primaryConstructor = None
        val typeParams = None
        val companionObject = EmptyConstruction()
        ClassConstruction(name, primaryConstructor, membersOut, modifiers,
          typeParams, Some(initializers), ClassType.OBJECT, companionObject, None)
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

    def handleAsClass(classMembers: Seq[PsiMember], objectMembers: Seq[PsiMember],
                      companionObject: IntermediateNode, extendList: Seq[(PsiClassType, PsiJavaCodeReferenceElement)]): IntermediateNode = {

      def handleAnonymousClass(clazz: PsiAnonymousClass): IntermediateNode = {
        val tp = convertTypePsiToIntermediate(clazz.getBaseClassType, clazz.getBaseClassReference, clazz.getProject)
        val argList = convertPsiToIntermdeiate(clazz.getArgumentList, externalProperties)
        AnonymousClass(tp, argList, classMembers.map(convertPsiToIntermdeiate(_, externalProperties)),
          extendList.map(el => convertTypePsiToIntermediate(el._1, el._2, clazz.getProject)))
      }

      def sortMembers(): Seq[PsiMember] = {
        def isConstructor(member: PsiMember): Boolean =
          member match {
            case m: PsiMethod if m.isConstructor => true
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
        updatedMembers.map(convertPsiToIntermdeiate(_, externalProperties))
      }

      def getDropComments(dropMembes: Option[Seq[PsiMember]]): Option[ArrayBuffer[LiteralExpression]] = {
        if (dropMembes.isDefined) {
          val result = new ArrayBuffer[PsiElement]()
          result ++= dropMembes.get
          Some(result.flatMap(CommentsCollector.getAllInsideComments).map(CommentsCollector.convertComment))
        } else {
          None
        }
      }

      if (classMembers.nonEmpty || objectMembers.isEmpty) {
        context.get().push((false, inClass.qualifiedName))
        try {
          inClass match {
            case clazz: PsiAnonymousClass => handleAnonymousClass(clazz)
            case _ =>
              val typeParams = inClass.getTypeParameters.map(convertPsiToIntermdeiate(_, externalProperties))
              val modifiers = handleModifierList(inClass)
              val (dropMembers, primaryConstructor) = handlePrimaryConstructor(inClass.getConstructors)
              val classType = if (inClass.isInterface) ClassType.INTERFACE else ClassType.CLASS
              val members = updateMembersAndConvert(dropMembers)

              val dropComments = getDropComments(dropMembers)
              if (primaryConstructor.isDefined && dropComments.isDefined) primaryConstructor.get.setAfterComments(dropComments.get)
              ClassConstruction(name, primaryConstructor, members, modifiers, Some(typeParams),
                None, classType, companionObject, Some(extendList.map(el => convertTypePsiToIntermediate(el._1, el._2, inClass.getProject))))
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
        val target = Option(expr.resolve()).flatMap {
          case m: PsiMethod => Some(m)
          case _ => None
        }

        if (target.isDefined && target.get.isConstructor) {
          val finalTarget: PsiMethod = toTargetConstructorMap.getOrElse(target.get, target.get)

          toTargetConstructorMap.put(constructor, finalTarget)
        }
      }
    }
    toTargetConstructorMap
  }

  //primary constructor may apply only when there is one constructor with params
  def handlePrimaryConstructor(constructors: Seq[PsiMethod])
                              (implicit associations: ListBuffer[AssociationHelper] = new ListBuffer(),
                               refs: Seq[ReferenceData] = Seq.empty,
                               usedComments: mutable.HashSet[PsiElement] = new mutable.HashSet[PsiElement]()): (Option[Seq[PsiMember]], Option[PrimaryConstruction]) = {

    val dropFields = new ArrayBuffer[PsiField]()
    def createPrimaryConstructor(constructor: PsiMethod): PrimaryConstruction = {
      def notContains(statement: PsiStatement, where: Seq[PsiExpressionStatement]): Boolean = {
        !statement.isInstanceOf[PsiExpressionStatement] ||
          (statement.isInstanceOf[PsiExpressionStatement] && !where.contains(statement))
      }

      def getSuperCall(dropStatements: ArrayBuffer[PsiExpressionStatement]): IntermediateNode = {
        val firstStatement = getFirstStatement(constructor)
        val isSuper = firstStatement.map(_.getExpression).flatMap {
          case mc: PsiMethodCallExpression if mc.getMethodExpression.getQualifiedName == "super" =>
            Some(mc)
          case _ => None
        }
        if (isSuper.isDefined) {
          dropStatements += firstStatement.get
          convertPsiToIntermdeiate(isSuper.get.getArgumentList, null)
        } else {
          null
        }
      }

      def getCorrespondedFieldInfo(param: PsiParameter): Seq[(PsiField, PsiExpressionStatement)] = {
        val dropInfo = new ArrayBuffer[(PsiField, PsiExpressionStatement)]()
        val usages = findVariableUsage(param, Option(constructor.getBody))

        for (usage <- usages) {
          val parent = Option(usage.getParent)

          val leftPart = parent.flatMap {
            case ae: PsiAssignmentExpression if (ae.getOperationSign.getTokenType == JavaTokenType.EQ)
              && ae.getLExpression.isInstanceOf[PsiReferenceExpression] =>
              Some(ae.getLExpression.asInstanceOf[PsiReferenceExpression])
            case _ => None
          }

          val field = if (leftPart.isDefined) leftPart.get.resolve() match {
            case f: PsiField if f.getContainingClass == constructor.getContainingClass && f.getInitializer == null =>
              Some(f)
            case _ => None
          } else None

          var statement: Option[PsiExpressionStatement] =
            if (field.isDefined && parent.isDefined && parent.get.getParent.isInstanceOf[PsiExpressionStatement]) {
              Some(parent.get.getParent.asInstanceOf[PsiExpressionStatement])
            } else None

          if (statement.isDefined && statement.get.getParent != constructor.getBody) {
            statement = None
          }

          if (field.isDefined && statement.isDefined) {
            dropInfo += ((field.get, statement.get))
            if (field.get.getName != param.getName)
              fieldParameterMap += ((param.getName, field.get.getName))
          }
        }
        dropInfo
      }

      def createContructor: PrimaryConstruction = {
        val params = constructor.parameters
        val updatedParams = new ArrayBuffer[IntermediateNode]()
        val dropStatements = new ArrayBuffer[PsiExpressionStatement]()
        for (param <- params) {
          val fieldInfo = getCorrespondedFieldInfo(param)
          val updatedField = if (fieldInfo.isEmpty) {
            val p = convertPsiToIntermdeiate(param, null).asInstanceOf[ParameterConstruction]
            p.isVar = Some(false)
            p
          } else {
            fieldInfo.foreach {
              case (field, statement) =>
                dropFields += field
                dropStatements += statement
            }
            val fieldConverted = convertPsiToIntermdeiate(fieldInfo.head._1, WithReferenceExpression(true)).asInstanceOf[FieldConstruction]
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
                .map(convertPsiToIntermdeiate(_, WithReferenceExpression(true)))), handleModifierList(constructor))
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
                        (implicit associations: ListBuffer[AssociationHelper] = new ListBuffer(),
                         refs: Seq[ReferenceData] = Seq.empty,
                         usedComments: mutable.HashSet[PsiElement] = new mutable.HashSet[PsiElement]()): IntermediateNode = {

    val annotationDropList = Seq("java.lang.Override", "org.jetbrains.annotations.Nullable",
      "org.jetbrains.annotations.NotNull", "org.jetbrains.annotations.NonNls")

    def handleAnnotations: Seq[IntermediateNode] = {
      val annotations = new ArrayBuffer[IntermediateNode]()
      for {
        a <- owner.getModifierList.getAnnotations
        optValue = Option(a.getQualifiedName).map(annotationDropList.contains(_))
        if optValue.isDefined && !optValue.get
      } {
        annotations.append(convertPsiToIntermdeiate(a, null))
      }
      annotations
    }

    def handleModifiers: Seq[IntermediateNode] = {
      val modifiers = new ArrayBuffer[IntermediateNode]()

      val simpleList = SIMPLE_MODIFIERS_MAP.filter {
        case (psiType, _) => owner.hasModifierProperty(psiType)
      }.values

      modifiers ++= simpleList.map(SimpleModifier)

      owner match {
        case method: PsiMethod =>
          val references = method.getThrowsList.getReferenceElements
          for (ref <- references) {
            modifiers.append(ModifierWithExpression(ModifierType.THROW, convertPsiToIntermdeiate(ref, null)))
          }

          if (method.findSuperMethods.exists(!_.hasModifierProperty("abstract")
            || ScalaProjectSettings.getInstance(method.getProject).isAddOverrideToImplementInConverter)) {
            modifiers.append(SimpleModifier(ModifierType.OVERRIDE))
          }

        case c: PsiClass =>
          serialVersion(c) match {
            case Some(f) =>
              modifiers.append(ModifierWithExpression(ModifierType.SerialVersionUID, convertPsiToIntermdeiate(f.getInitializer, null)))
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

  def convertPsisToText(elements: Array[PsiElement],
                        dropElements: mutable.HashSet[PsiElement] = new mutable.HashSet[PsiElement]()): String = {
    val resultNode = new MainConstruction
    for (part <- elements) {
      resultNode.addChild(convertPsiToIntermdeiate(part, null)(new ListBuffer(), Seq.empty, dropElements, textMode = true))
    }
    val visitor = new PrintWithComments
    visitor.visit(resultNode)
    visitor.stringResult
  }

  def convertPsiToText(element: PsiElement): String = {
    val visitor = new PrintWithComments
    visitor.visit(convertPsiToIntermdeiate(element, null)(textMode = true))

    val text = visitor.stringResult

    val file = new ScalaCodeFragment(element.getProject, text)

    ConverterUtil.cleanCode(file, element.getProject, 0, file.getText.length)
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
