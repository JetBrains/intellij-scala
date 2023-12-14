package org.jetbrains.plugins.scala.conversion

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.editorActions.ReferenceData
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.search.{GlobalSearchScope, LocalSearchScope}
import com.intellij.psi.util.{JavaPsiPatternUtil, PsiTreeUtil, PsiUtil}
import com.siyeh.ig.psiutils.{ControlFlowUtils, CountingLoop, ExpressionUtils}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.conversion.ast.CommentsCollector.UsedComments
import org.jetbrains.plugins.scala.conversion.ast.{ModifierType, _}
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, ObjectExt, PsiClassExt, PsiMemberExt, PsiMethodExt}
import org.jetbrains.plugins.scala.lang.dependency.DependencyPath
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.{Constructor, ScConstructorInvocation}
import org.jetbrains.plugins.scala.lang.psi.types.api.PsiTypeConstants
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.util.UnloadableThreadLocal

import java.util.regex.Pattern
import scala.collection.immutable.ArraySeq
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.language.postfixOps

//noinspection InstanceOf,ScalaWrongPlatformMethodsUsage
object JavaToScala {

  case class AssociationHelper(node: IntermediateNode, path: DependencyPath)

  private val context: UnloadableThreadLocal[java.util.Stack[(Boolean, String)]] = new UnloadableThreadLocal(new java.util.Stack)

  private def findVariableUsage(elementToFind: PsiElement, maybeElement: Option[PsiElement]): Seq[PsiReferenceExpression] =
    maybeElement.fold(Seq.empty[PsiReferenceExpression]) { element =>
      ReferencesSearch.search(elementToFind, new LocalSearchScope(element))
        .findAll()
        .asScala
        .toSeq
        .collect {
          case el: PsiReferenceExpression => el
        }
    }

  private def isVar(element: PsiModifierListOwner, parent: Option[PsiElement]): Boolean = {
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

  private trait ExternalProperties {}

  private case class WithReferenceExpression(yep: Boolean) extends ExternalProperties

  private def convertTypePsiToIntermediate(t: PsiTypeElement)
                                          (implicit conversionContext: ConversionContext): TypeNode =
    convertTypePsiToIntermediate(t.getType, t, t.getProject)

  private def convertTypePsiToIntermediate(
    `type`: PsiType,
    psiElement: PsiElement,
    project: Project
  )(implicit conversionContext: ConversionContext): TypeNode = {
    Option(`type`).map {
      case _: PsiLambdaParameterType => EmptyTypeNode()
      case _: PsiDisjunctionType =>
        DisjunctionTypeConstructions(
          PsiTreeUtil.getChildrenOfType(psiElement, classOf[PsiTypeElement])
            .map(t => convertTypePsiToIntermediate(t.getType, t, project))
            .toIndexedSeq
        )
      case t =>
        val iNode = TypeConstruction.createIntermediateTypePresentation(t, project, conversionContext.textMode)
        handleAssociations(psiElement, iNode)
        iNode
    }.getOrElse(EmptyTypeNode())
  }

  def convertPsiToIntermediatePublic(
    element: PsiElement,
    externalProperties: ExternalProperties
  )(implicit
    associations: mutable.ListBuffer[AssociationHelper] = mutable.ListBuffer(),
    refs: Seq[ReferenceData] = Seq.empty,
    dropElements: mutable.Set[PsiElement] = new mutable.HashSet[PsiElement](),
    textMode: Boolean = false
  ): IntermediateNode = {
    implicit val context: ConversionContext = new ConversionContext(
      textMode,
      associations,
      refs,
      dropElements,
      new UsedComments(dropElements.filterByType[PsiComment])
    )
    convertPsiToIntermediate(element, externalProperties)
  }

  private class ConversionContext(
    val textMode: Boolean = false,
    val associations: mutable.ListBuffer[AssociationHelper] = mutable.ListBuffer(),
    val references: Seq[ReferenceData] = Seq.empty,
    val dropElements: mutable.Set[PsiElement] = new mutable.HashSet[PsiElement](),
    val usedComments: UsedComments = new UsedComments,
  )

  private def convertPsiToIntermediate(
    element: PsiElement,
    externalProperties: ExternalProperties
  )(implicit conversionContext: ConversionContext): IntermediateNode = {
    if (element == null || conversionContext.dropElements.contains(element))
      return EmptyConstruction()
    if (element.getLanguage != JavaLanguage.INSTANCE)
      return EmptyConstruction()

    //NOTE: we need to calculate the comments before calculating IntermediateNode result
    //because some comments can be attached to different nodes and we would like them to occur in the most top-level parent nodes
    val comments = CommentsCollector.allCommentsForElement(element)(conversionContext.usedComments)

    val result: IntermediateNode = element match {
      case f: PsiFile =>
        val children = f.getChildren.map(convertPsiToIntermediate(_, externalProperties)).toSeq
        MainConstruction(children)
      case unnamedClass: PsiUnnamedClass =>
        val children = unnamedClass.getChildren.map(convertPsiToIntermediate(_, externalProperties)).toSeq
        MainConstruction(children)
      case e: PsiExpressionStatement => convertPsiToIntermediate(e.getExpression, externalProperties)
      case l: PsiLiteralExpression => LiteralExpression(l.getText)
      case n: PsiIdentifier =>
        convertToIdentifier(n)
      case t: PsiTypeElement =>
        convertTypePsiToIntermediate(t)
      case w: PsiWhiteSpace => LiteralExpression(w.getText)
      case r: PsiReturnStatement => ReturnStatement(convertPsiToIntermediate(r.getReturnValue, externalProperties))
      case t: PsiThrowStatement => ThrowStatement(convertPsiToIntermediate(t.getException, externalProperties))
      case i: PsiImportStatement => handleImport(i)
      case i: PsiImportStaticStatement => handleImport(i)
      case i: PsiImportList => ImportStatementList(i.getAllImportStatements.map(handleImport).distinct.toIndexedSeq)
      case a: PsiAssignmentExpression =>
        BinaryExpressionConstruction(convertPsiToIntermediate(a.getLExpression, externalProperties),
          convertPsiToIntermediate(a.getRExpression, externalProperties), a.getOperationSign.getText, inExpression = false)
      case e: PsiExpressionListStatement =>
        ExpressionListStatement(e.getExpressionList.getExpressions.map(convertPsiToIntermediate(_, externalProperties)).toIndexedSeq)
      case d: PsiDeclarationStatement => ExpressionListStatement(d.getDeclaredElements.map(convertPsiToIntermediate(_, externalProperties)).toIndexedSeq)
      case b: PsiBlockStatement =>
        convertToBlockConstruction(b.getCodeBlock, externalProperties)
      case s: PsiSynchronizedStatement =>
        val lock = Option(s.getLockExpression).map(convertPsiToIntermediate(_, externalProperties))
        val body = Option(s.getBody).map(convertPsiToIntermediate(_, externalProperties))
        SynchronizedStatement(lock, body)
      case b: PsiCodeBlock =>
        convertToBlockConstruction(b, externalProperties)
      case t: PsiTypeParameter =>
        convertToTypeParameterConstruction(t, externalProperties)
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
        //noinspection DuplicatedCode
        val condition = Option(w.getCondition).map(convertPsiToIntermediate(_, externalProperties))
        val body = Option(w.getBody).map(convertPsiToIntermediate(_, externalProperties))
        WhileStatement(None, condition, body, None, WhileStatement.PRE_TEST_LOOP)
      case w: PsiDoWhileStatement =>
        //noinspection DuplicatedCode
        val condition = Option(w.getCondition).map(convertPsiToIntermediate(_, externalProperties))
        val body = Option(w.getBody).map(convertPsiToIntermediate(_, externalProperties))
        WhileStatement(None, condition, body, None, WhileStatement.POST_TEST_LOOP)
      case f: PsiForStatement =>
        val countingLoop = CountingLoop.from(f)
        val body = Option(f.getBody).map(convertPsiToIntermediate(_, externalProperties))
        if (countingLoop != null) {
          val name = convertPsiToIntermediate(countingLoop.getCounter.getNameIdentifier, externalProperties)

          val iteratedValue = RangeExpression(
            convertPsiToIntermediate(countingLoop.getInitializer, externalProperties),
            convertPsiToIntermediate(countingLoop.getBound, externalProperties),
            countingLoop.isIncluding, countingLoop.isDescending)
          ForeachStatement(name, Some(iteratedValue), body, isJavaCollection = false)
        } else {
          val initialization = Option(f.getInitialization).map(convertPsiToIntermediate(_, externalProperties))
          val condition = Some(f.getCondition match {
            case _: PsiEmptyStatement => LiteralExpression("true")
            case null => LiteralExpression("true")
            case _ => convertPsiToIntermediate(f.getCondition, externalProperties)
          })
          val update = Option(f.getUpdate).map(convertPsiToIntermediate(_, externalProperties))
          WhileStatement(initialization, condition, body, update, WhileStatement.PRE_TEST_LOOP)
        }
      case a: PsiAssertStatement =>
        val condition = Option(a.getAssertCondition).map(convertPsiToIntermediate(_, externalProperties))
        val description = Option(a.getAssertDescription).map(convertPsiToIntermediate(_, externalProperties))
        AssertStatement(condition, description)
      case s: PsiSwitchLabelStatementBase =>
        val caseValues = if (s.isDefaultCase)
          Seq(LiteralExpression("_"))
        else
          Option(s.getCaseLabelElementList)
            .map(it => it.getElements.iterator.map(convertPsiToIntermediate(_, externalProperties)).toSeq)
            .getOrElse(Seq.empty)
        val body : Option[PsiStatement] = s match {
          case rs: PsiSwitchLabeledRuleStatement => Option(rs.getBody)
          case _ => None
        }
        val guardExpression = s.getGuardExpression.toOption
        SwitchLabelStatement(
          caseValues,
          guardExpression.map(convertPsiToIntermediate(_, externalProperties)),
          ScalaPsiUtil.functionArrow(s.getProject),
          body.map(convertPsiToIntermediate(_, externalProperties))
        )
      case s: PsiSwitchBlock =>
        val statements: Option[Array[PsiStatement]] =
          Option(s.getBody).map(_.getStatements)

        def defaultStatement: SwitchLabelStatement =
          SwitchLabelStatement(Seq(LiteralExpression("_")), None, ScalaPsiUtil.functionArrow(s.getProject))

        val expr = Option(s.getExpression).map(convertPsiToIntermediate(_, externalProperties))
        val body = Option(s.getBody).map(convertPsiToIntermediate(_, externalProperties))
        val hasEmptyStatement = statements.exists(_.isEmpty)
        val body2 = if (hasEmptyStatement) Some(defaultStatement) else body
        SwitchBlock(expr, body2)
      case p: PsiPackageStatement => PackageStatement(convertPsiToIntermediate(p.getPackageReference, externalProperties))
      case f: PsiForeachStatementBase =>
        val tp = Option(f.getIteratedValue).flatMap((e: PsiExpression) => Option(e.getType))
        val isJavaCollection = if (tp.isEmpty) true else !tp.get.isInstanceOf[PsiArrayType]

        val iteratedValue = Option(f.getIteratedValue).map(convertPsiToIntermediate(_, externalProperties))
        val body = Option(f.getBody).map(convertPsiToIntermediate(_, externalProperties))
        val nameIdentifier = f match {
          case statement: PsiForeachStatement => statement.getIterationParameter.getNameIdentifier
          case patternStatement: PsiForeachPatternStatement =>
            val pattern = patternStatement.getIterationPattern
            val variable = JavaPsiPatternUtil.getPatternVariable(pattern)
            if (variable != null) variable.getNameIdentifier else null
          case _ => null
        }
        val name = convertPsiToIntermediate(nameIdentifier, externalProperties)
        ForeachStatement(name, iteratedValue, body, isJavaCollection)
      case r: PsiReferenceExpression =>
        val args = Option(r.getParameterList).map(convertPsiToIntermediate(_, externalProperties))

        val refName: Option[String] = {
          val nameWithPrefix: String = if (conversionContext.textMode && r.getQualifier == null) r.resolve() match {
            case clazz: PsiClass => ScalaPsiUtil.nameWithPrefixIfNeeded(clazz)
            case _ => r.getReferenceName
          } else r.getReferenceName

          val name: String = if (externalProperties.isInstanceOf[WithReferenceExpression]) {
            fieldParameterMap.getOrElse(r.getReferenceName, nameWithPrefix)
          } else {
            nameWithPrefix
          }
          Option(name)
        }

        var iResult = JavaCodeReferenceStatement(None, args, refName)
        if (r.getQualifierExpression != null) {
          val t = Option(r.getQualifierExpression).map(convertPsiToIntermediate(_, externalProperties))
          iResult = JavaCodeReferenceStatement(t, args, refName)
        } else {
          r.resolve() match {
            case f: PsiMember
              if f.hasModifierProperty("static") =>
              val clazz = f.containingClass
              if (clazz != null && context.value.contains((false, clazz.qualifiedName))) {
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
        JavaCodeReferenceStatement(qualifier, args, Option(p.getReferenceName))
      case be: PsiBinaryExpression =>
        def isOk: Boolean = {
          if (be.getLOperand.getType.isInstanceOf[PsiPrimitiveType]) return false
          be.getROperand match {
            case l: PsiLiteralExpression if l.textMatches("null") => return false
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
          convertPsiToIntermediate(c.getOperand, externalProperties),
          convertTypePsiToIntermediate(c.getCastType),
          isPrimitive = c.getCastType.getType.isInstanceOf[PsiPrimitiveType] && c.getOperand.getType.isInstanceOf[PsiPrimitiveType]
        )
      case a: PsiArrayAccessExpression =>
        ArrayAccess(
          convertPsiToIntermediate(a.getArrayExpression, externalProperties),
          convertPsiToIntermediate(a.getIndexExpression, externalProperties))
      case a: PsiArrayInitializerExpression =>
        ArrayInitializer(a.getInitializers.map(convertPsiToIntermediate(_, externalProperties)).toIndexedSeq)
      case c: PsiClassObjectAccessExpression => ClassObjectAccess(convertPsiToIntermediate(c.getOperand, externalProperties))
      case typeTest: PsiTypeTestPattern =>
        //TODO support Java pattern matching truly, see SCL-21510
        LiteralExpression(typeTest.getText)
      case i: PsiInstanceOfExpression =>
        val checkType = i.getCheckType match {
          case null =>
            //type can be null since Java 15/16 when there is a variable: `obj instanceof String str`
            //NOTE: this is a fast workaround just to avoid NPE (see SCL-21509)
            //TODO support Java pattern matching truly, see SCL-21510
            i.getPattern match {
              case typeTest: PsiTypeTestPattern => typeTest.getCheckType
              case _ => null
            }
          case t => t
        }
        val checkTypeNode = if (checkType != null) convertTypePsiToIntermediate(checkType) else EmptyTypeNode()
        InstanceOfConstruction(
          convertPsiToIntermediate(i.getOperand, externalProperties),
          checkTypeNode
        )
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
            val receiver = Option(m.getMethodExpression.getQualifierExpression).map(convertPsiToIntermediate(_, externalProperties)).getOrElse(LiteralExpression("this"))
            MethodCallExpression.build(
              receiver,
              " == ",
              ExpressionList(Seq(convertPsiToIntermediate(m.getArgumentList.getExpressions.apply(0), externalProperties)))
            )
          case _ =>
            MethodCallExpression(
              convertPsiToIntermediate(m.getMethodExpression, externalProperties),
              convertToExpressionList(m.getArgumentList, externalProperties),
              (m.getType == PsiTypeConstants.Void) && m.getArgumentList.getExpressions.isEmpty
            )
        }
      case t: PsiThisExpression =>
        ThisExpression(Option(t.getQualifier).map(convertPsiToIntermediate(_, externalProperties)))
      case s: PsiSuperExpression =>
        SuperExpression(Option(s.getQualifier).map(convertPsiToIntermediate(_, externalProperties)))
      case e: PsiExpressionList =>
        convertToExpressionList(e, externalProperties)
      case lambda: PsiLambdaExpression =>
        FunctionalExpression(
          convertToParameterListConstruction(lambda.getParameterList),
          convertPsiToIntermediate(lambda.getBody, externalProperties)
        )
      case l: PsiLocalVariable =>
        val parent = Option(PsiTreeUtil.getParentOfType(l, classOf[PsiCodeBlock], classOf[PsiBlockStatement]))
        val needVar = if (parent.isEmpty) false else isVar(l, parent)
        val initializer = Option(l.getInitializer).map(convertPsiToIntermediate(_, externalProperties))
        val name = convertToIdentifier(l.getNameIdentifier)
        LocalVariable(
          handleModifierList(l),
          name,
          convertTypePsiToIntermediate(l.getType, l.getTypeElement, l.getProject),
          needVar,
          initializer
        )
      case enumConstant: PsiEnumConstant =>
        EnumConstruction(convertToIdentifier(enumConstant.getNameIdentifier))
      case f: PsiField =>
        val modifiers = handleModifierList(f)
        val needVar = isVar(f, Option(f.getContainingClass))
        val initializer = Option(f.getInitializer).map(convertPsiToIntermediate(_, externalProperties))
        val name = convertToIdentifier(f.getNameIdentifier)
        FieldConstruction(
          modifiers,
          name,
          convertTypePsiToIntermediate(f.getType, f.getTypeElement, f.getProject),
          needVar,
          initializer
        )
      case p: PsiParameterList =>
        convertToParameterListConstruction(p)
      case m: PsiMethod =>
        def body: Option[IntermediateNode] = {
          if (m.isConstructor) {
            getFirstStatement(m).map(_.getExpression).flatMap {
              case mc: PsiMethodCallExpression if mc.getMethodExpression.getQualifiedName == "this" =>
                Some(convertPsiToIntermediate(m.getBody, externalProperties))
              case _ =>
                getStatements(m).map(statements => {
                  val statementsUpdated = LiteralExpression("this()") +: statements.map(convertPsiToIntermediate(_, externalProperties)).toIndexedSeq
                  BlockConstruction(statementsUpdated)
                })
            }
          } else {
            Option(m.getBody).map(convertPsiToIntermediate(_, externalProperties))
          }
        }

        def convertMethodReturnType: Option[TypeNode] =
          if (m.getReturnType != PsiTypeConstants.Void || ScalaCodeStyleSettings.getInstance(m.getProject).ENFORCE_FUNCTIONAL_SYNTAX_FOR_UNIT)
            Some(convertTypePsiToIntermediate(m.getReturnTypeElement))
          else None

        if (m.isConstructor) {
          ConstructorSimply(
            handleModifierList(m),
            m.getTypeParameters.map(convertToTypeParameterConstruction(_, externalProperties)).toIndexedSeq,
            m.parameters.map(convertToParameterConstruction),
            body
          )
        } else {
          val name = convertToIdentifier(m.getNameIdentifier)
          MethodConstruction(
            handleModifierList(m),
            name,
            m.getTypeParameters.map(convertToTypeParameterConstruction(_, externalProperties)).toIndexedSeq,
            m.parameters.map(convertToParameterConstruction),
            body,
            convertMethodReturnType
          )
        }
      case c: PsiClass =>
        createClass(c, externalProperties)
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
        AnnotationConstruction(inAnnotation, attrResult.toSeq, name)
      case p: PsiParameter =>
        convertToParameterConstruction(p)

      case n: PsiNewExpression =>
        val anonymousClass = n.getAnonymousClass
        if (anonymousClass != null) {
          val node = convertPsiToIntermediate(anonymousClass, externalProperties)
          node match {
            case ac: AnonymousClass =>
              return AnonymousClassExpression(ac)
            case other =>
              return other
          }
        }

        val iType = convertTypePsiToIntermediate(n.getType, n.getClassReference, n.getProject)
        val withArrayInitializer = n.getArrayInitializer != null
        val argList: Seq[IntermediateNode] =
          if (n.getArrayInitializer != null)
            n.getArrayInitializer.getInitializers.map(convertPsiToIntermediate(_, externalProperties)).toIndexedSeq
          else if (n.getArrayDimensions.nonEmpty)
            n.getArrayDimensions.map(convertPsiToIntermediate(_, externalProperties)).toIndexedSeq
          else if (n.getArgumentList != null) {
            if (n.getArgumentList.getExpressions.isEmpty)
              n.getParent match {
                case r: PsiJavaCodeReferenceElement if n == r.getQualifier =>
                  Seq(LiteralExpression("()"))
                case _ => null
              }
            else
              Seq(convertPsiToIntermediate(n.getArgumentList, externalProperties))
          }
          else null

        NewExpression(iType, argList, withArrayInitializer)
      case t: PsiTryStatement =>
        val resourcesVariables = mutable.ArrayBuffer[(String, IntermediateNode)]()
        Option(t.getResourceList).foreach { resourceList =>
          val it = resourceList.iterator
          while (it.hasNext) {
            val next = it.next()
            next match {
              case variable: PsiResourceVariable =>
                resourcesVariables += ((variable.getName, convertPsiToIntermediate(variable, externalProperties)))
              case _ =>
            }
          }
        }
        val tryBlock = Option(t.getTryBlock).map(convertToBlockConstruction(_, externalProperties))
        val catches: Seq[(ParameterConstruction, IntermediateNode)] = t.getCatchSections.map { (cb: PsiCatchSection) =>
          (convertToParameterConstruction(cb.getParameter),
            convertPsiToIntermediate(cb.getCatchBlock, externalProperties))
        }.toIndexedSeq
        val finallyBlockStatements = Option(t.getFinallyBlock).map(_.getStatements.map(convertPsiToIntermediate(_, externalProperties)).toSeq)
        TryCatchStatement(resourcesVariables.toSeq, tryBlock, catches, finallyBlockStatements, ScalaPsiUtil.functionArrow(t.getProject))
      case p: PsiPrefixExpression =>
        PrefixExpression(convertPsiToIntermediate(p.getOperand, externalProperties), p.getOperationSign.getText, ExpressionUtils.isVoidContext(p))
      case p: PsiPostfixExpression =>
        PostfixExpression(convertPsiToIntermediate(p.getOperand, externalProperties), p.getOperationSign.getText, ExpressionUtils.isVoidContext(p))
      case p: PsiPolyadicExpression =>
        val tokenValue = if (p.getOperands.nonEmpty) {
          p.getTokenBeforeOperand(p.getOperands.apply(1)).getText
        } else ""
        PolyadicExpression(p.getOperands.map(convertPsiToIntermediate(_, externalProperties)).toIndexedSeq, tokenValue)
      case r: PsiReferenceParameterList => TypeParameters(r.getTypeParameterElements.map(convertPsiToIntermediate(_, externalProperties)).toIndexedSeq)
      case b: PsiBreakStatement =>
        if (isBreakRemovable(b)) EmptyConstruction()
        else if (b.getLabelIdentifier != null)
          NotSupported(None, "break " + b.getLabelIdentifier.getText + "// todo: label break is not supported")
        else NotSupported(None, "break //todo: break is not supported")
      case y: PsiYieldStatement =>
        if (isYieldRemovable(y)) convertPsiToIntermediate(y.getExpression, externalProperties)
        else NotSupported(None, "`yield` " + Option(y.getExpression).map(_.getText).mkString + "// todo: Java's yield is not supported")
      case c: PsiContinueStatement =>
        if (c.getLabelIdentifier != null)
          NotSupported(None, "continue " + c.getLabelIdentifier.getText + " //todo: continue is not supported")
        else NotSupported(None, "continue //todo: continue is not supported")
      case s: PsiLabeledStatement =>
        val statements = Option(s.getStatement).map(convertPsiToIntermediate(_, externalProperties))
        NotSupported(statements, s.getLabelIdentifier.getText + " //todo: labels are not supported")
      case _: PsiEmptyStatement => EmptyConstruction()
      case _: PsiErrorElement => EmptyConstruction()
      case c: PsiComment if conversionContext.usedComments.contains(c) =>
        EmptyConstruction()
      case e =>
        LiteralExpression(e.getText)
    }

    result.setComments(comments)
    result
  }

  private def convertToBlockConstruction(block: PsiCodeBlock, externalProperties: ExternalProperties)
                                        (implicit conversionContext: ConversionContext): BlockConstruction = {
    val statements = block.getStatements.toIndexedSeq.map(convertPsiToIntermediate(_, externalProperties))
    BlockConstruction(statements)
  }

  private def convertToParameterListConstruction(p: PsiParameterList)
                                                (implicit conversionContext: ConversionContext): ParameterListConstruction =
    ParameterListConstruction(p.getParameters.map(convertToParameterConstruction).toIndexedSeq)

  private def convertToExpressionList(e: PsiExpressionList, externalProperties: ExternalProperties)
                                     (implicit conversionContext: ConversionContext): ExpressionList =
    ExpressionList(e.getExpressions.map(convertPsiToIntermediate(_, externalProperties)).toIndexedSeq)

  private def convertToIdentifier(@Nullable identifier: PsiIdentifier): NameIdentifier = {
    val text = if (identifier == null) ""  else identifier.getText
    NameIdentifier(text)
  }

  private def convertToTypeParameterConstruction(t: PsiTypeParameter, externalProperties: ExternalProperties)
                                                (implicit conversionContext: ConversionContext): TypeParameterConstruction =
    TypeParameterConstruction(
      convertToIdentifier(t.getNameIdentifier),
      t.getExtendsList.getReferenceElements.map(convertPsiToIntermediate(_, externalProperties)).toIndexedSeq
    )

  private def convertToParameterConstruction(
    p: PsiParameter,
  )(implicit conversionContext: ConversionContext): ParameterConstruction = {
    val modifiers = handleModifierList(p)
    val name = convertToIdentifier(p.getNameIdentifier)

    val `type` = convertTypePsiToIntermediate(p.getType, p.getTypeElement, p.getProject)
    val (typ, isArray) = if (p.isVarArgs)
      p.getType match {
        case at: PsiArrayType =>
          val typ = convertTypePsiToIntermediate(at.getComponentType, p.getTypeElement.getInnermostComponentReferenceElement, p.getProject)
          (typ, true)
        case _ =>
          (`type`, false)
      }
    else
      (`type`, false)

    ParameterConstruction(modifiers, name, typ, None, isArray)
  }

  private def handleAssociations(
    element: PsiElement,
    result: IntermediateNode
  )(implicit conversionContext: ConversionContext): Unit = {
    // TODO: eliminate amount of call
    for {
      target <- Option(element)
      range = target.getTextRange

      reference <- conversionContext.references.find { ref =>
        ref.startOffset == range.getStartOffset &&
          ref.endOffset == range.getEndOffset
      }
    } {
      conversionContext.associations += AssociationHelper(result, DependencyPath(reference.qClassName, Option(reference.staticMemberName)))
    }

    val associationMap = result match {
      case parametrizedConstruction: ParametrizedConstruction => parametrizedConstruction.associationMap
      case arrayConstruction: ArrayConstruction => arrayConstruction.associationMap
      case _ => Seq.empty
    }

    conversionContext.associations ++= associationMap.collect {
      case (node, Some(entity)) => AssociationHelper(node, DependencyPath(entity))
    }
  }

  private val fieldParameterMap = mutable.HashMap.empty[String, String]

  import ClassConstruction.ClassType._

  private def createClass(
    inClass: PsiClass,
    externalProperties: ExternalProperties
  )(implicit conversionContext: ConversionContext): IntermediateNode = {
    val context = this.context.value

    def extendList: Seq[(PsiClassType, PsiJavaCodeReferenceElement)] = {
      val types = Seq.newBuilder[(PsiClassType, PsiJavaCodeReferenceElement)]
      if (inClass.getExtendsList != null) types ++= inClass.getExtendsList.getReferencedTypes.zip(inClass.getExtendsList.getReferenceElements)
      if (inClass.getImplementsList != null) types ++= inClass.getImplementsList.getReferencedTypes.zip(inClass.getImplementsList.getReferenceElements)
      types.result()
    }

    def collectClassObjectMembers(): (Seq[PsiMember], Seq[PsiMember]) = {
      val forClassBuilder = ArraySeq.newBuilder[PsiMember]
      val forObjectBuilder = ArraySeq.newBuilder[PsiMember]
      for (method <- inClass.getMethods if PsiTreeUtil.isAncestor(inClass, method, true)) {
        if (method.hasModifierProperty("static") || inClass.isEnum) forObjectBuilder += method else forClassBuilder += method
      }

      val serialVersionUID = serialVersion(inClass)
      for (field <- inClass.getFields if !serialVersionUID.contains(field)) {
        if (field.hasModifierProperty("static") || inClass.isEnum) forObjectBuilder += field else forClassBuilder += field
      }

      for (clazz <- inClass.getInnerClasses) {
        if (clazz.hasModifierProperty("static") || inClass.isEnum) forObjectBuilder += clazz else forClassBuilder += clazz
      }

      val forClass = forClassBuilder.result().sortBy(_.getTextOffset)
      val forObject = forObjectBuilder.result().sortBy(_.getTextOffset)
      (forClass, forObject)
    }

    val name = convertToIdentifier(inClass.getNameIdentifier)

    def handleObject(objectMembers: Seq[PsiMember]): IntermediateNode = {
      def handleAsEnum(modifiers: ModifiersConstruction): IntermediateNode = {
        Enum(name, modifiers, objectMembers.map(m => convertPsiToIntermediate(m, externalProperties)))
      }

      def handleAsObject(modifiers: ModifiersConstruction): IntermediateNode = {
        val membersOut = objectMembers.filter(!_.isInstanceOf[PsiEnumConstant]).map(convertPsiToIntermediate(_, externalProperties))
        val initializers = inClass.getInitializers.map((x: PsiClassInitializer) => convertPsiToIntermediate(x.getBody, externalProperties))
        val primaryConstructor = None
        val typeParams = None
        val companionObject = EmptyConstruction()
        ClassConstruction(
          name,
          primaryConstructor,
          membersOut,
          modifiers,
          typeParams,
          Some(initializers.toIndexedSeq),
          OBJECT,
          companionObject,
          None
        )
      }

      if (objectMembers.nonEmpty && !inClass.isInstanceOf[PsiAnonymousClass]) {
        context.push((true, inClass.qualifiedName))
        try {
          val modifiers = handleModifierList(inClass)
          val updatedModifiers = modifiers.without(ModifierType.ABSTRACT)
          if (inClass.isEnum) handleAsEnum(updatedModifiers) else handleAsObject(updatedModifiers)
        } finally {
          context.pop()
        }
      } else {
        EmptyConstruction()
      }
    }

    def couldFindInstancesForClass: Boolean = {
      def isParentValid(ref: PsiReference): Boolean =
        Option(ref.getElement).flatMap(element => Option(PsiTreeUtil.getParentOfType(element, classOf[PsiNewExpression], classOf[ScConstructorInvocation]))).exists {
          case n: PsiNewExpression if Option(n.getClassReference).contains(ref) => true
          case e: ScConstructorInvocation if e.reference.contains(ref) => true
          case _ => false
        }

      def withInstances = {
        ReferencesSearch
          .search(inClass, GlobalSearchScope.projectScope(inClass.getProject))
          .findAll()
          .asScala
          .exists(isParentValid)
      }

      if (conversionContext.textMode) {
        val p = Pattern.compile("new\\s+" + inClass.getName)
        p.matcher(inClass.getContainingFile.getText).find()
      }
      else withInstances
    }

    def handleAsClass(classMembers: Seq[PsiMember], objectMembers: Seq[PsiMember],
                      companionObject: IntermediateNode, extendList: Seq[(PsiClassType, PsiJavaCodeReferenceElement)]): IntermediateNode = {

      def handleAnonymousClass(clazz: PsiAnonymousClass): IntermediateNode = {
        val tp = convertTypePsiToIntermediate(clazz.getBaseClassType, clazz.getBaseClassReference, clazz.getProject)
        val argList = convertPsiToIntermediate(clazz.getArgumentList, externalProperties)
        val classMembersNodes = classMembers.map(convertPsiToIntermediate(_, externalProperties))
        val objectMembersNodes = objectMembers.map { m =>
          val node = convertPsiToIntermediate(m, externalProperties)
          //NOTE: for non-anonymous classes we would move static members to the companion object
          // However in Scala anonymous classes can't have companion object so we can't move them there
          node.comments.beforeComments.append(LiteralExpression("//TODO: 'static' modifier is not supported\n"))
          node
        }
        val members = classMembersNodes ++ objectMembersNodes
        AnonymousClass(
          tp, argList,
          members,
          extendList.map(el => convertTypePsiToIntermediate(el._1, el._2, clazz.getProject))
        )
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

        val constructorsCallMap = buildConstructorTargetMap(inClass.getConstructors.sortBy(_.getTextOffset).toIndexedSeq)
        sort(constructorsCallMap)
      }

      def updateMembersAndConvert(dropMembers: Option[Seq[PsiMember]]): Seq[IntermediateNode] = {
        val sortedMembers = sortMembers()
        val updatedMembers = dropMembers.map(el => sortedMembers.filter(!el.contains(_))).getOrElse(sortedMembers)
        updatedMembers.map(convertPsiToIntermediate(_, externalProperties))
      }

      def convertExtendList(): Seq[IntermediateNode] =
        extendList.map { case (a, b) =>
          convertTypePsiToIntermediate(a, b, inClass.getProject)
        }

      val condition = classMembers.nonEmpty ||
        (objectMembers.isEmpty || inClass.is[PsiAnonymousClass]) ||
        extendList.nonEmpty ||
        couldFindInstancesForClass

      if (condition) {
        context.push((false, inClass.qualifiedName))
        try {
          inClass match {
            case clazz: PsiAnonymousClass => handleAnonymousClass(clazz)
            case _ =>
              val typeParams = inClass.getTypeParameters.map(convertToTypeParameterConstruction(_, externalProperties))
              val modifiers = handleModifierList(inClass)
              val dropMembersAndPrimaryConstructor = handlePrimaryConstructor(inClass.getConstructors.toIndexedSeq)
              val classType = if (inClass.isInterface) INTERFACE else CLASS
              val members = updateMembersAndConvert(dropMembersAndPrimaryConstructor.map(_._1))

              ClassConstruction(
                name,
                dropMembersAndPrimaryConstructor.map(_._2),
                members,
                modifiers,
                Some(typeParams.toSeq),
                None,
                classType,
                companionObject,
                Some(convertExtendList())
              )
          }
        } finally {
          context.pop()
        }
      } else {
        companionObject
      }
    }

    val (classMembers, objectMembers) = collectClassObjectMembers()
    val companionObject = handleObject(objectMembers)
    handleAsClass(classMembers, objectMembers, companionObject, extendList)
  }

  private def getFirstStatement(constructor: PsiMethod): Option[PsiExpressionStatement] = {
    val body = Option(constructor.getBody)
    val statements = body.map(_.getStatements)
    statements.flatMap(_.headOption).collect { case exp: PsiExpressionStatement => exp }
  }

  // build map of constructor and constructor that it call
  private def buildConstructorTargetMap(constructors: Seq[PsiMethod]): mutable.HashMap[PsiMethod, PsiMethod] = {
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
  private def handlePrimaryConstructor(
    constructors: Seq[PsiMethod]
  )(implicit conversionContext: ConversionContext): Option[(Seq[PsiMember], PrimaryConstructor)] = {

    val dropFieldsBuilder = List.newBuilder[PsiField]

    def createPrimaryConstructor(constructor: PsiMethod): PrimaryConstructor = {
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

      def getCorrespondedFieldInfo(param: PsiParameter): collection.Seq[(PsiField, PsiExpressionStatement)] = {
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

          field.zip(statement).foreach { case (f, s) =>
            if (s.getParent == constructor.getBody) {
              dropInfo += ((f, s))
              if (f.getName != param.getName) fieldParameterMap += ((param.getName, f.getName))
            }
          }
        }

        dropInfo
      }

      def createConstructor: PrimaryConstructor = {
        val params = constructor.parameters
        val updatedParams = mutable.ArrayBuffer[ParameterConstruction]()
        val dropStatements = mutable.ArrayBuffer[PsiExpressionStatement]()

        for (param <- params) {
          val fieldInfo: collection.Seq[(PsiField, PsiExpressionStatement)] = getCorrespondedFieldInfo(param)
          val updatedField = if (fieldInfo.isEmpty) {
            convertPsiToIntermediate(param, null).asInstanceOf[ParameterConstruction]
          } else {
            fieldInfo.foreach {
              case (field, statement) =>
                dropFieldsBuilder += field
                dropStatements += statement
            }
            val fieldInfoFirst = fieldInfo.head
            val modifiers = handleModifierList(fieldInfoFirst._1)
              .without(ModifierType.FINAL) // final will be expressed in `val`, so we don't need еру modifier
            val fieldConverted = convertPsiToIntermediate(fieldInfoFirst._1, WithReferenceExpression(true)).asInstanceOf[FieldConstruction]
            val param = ParameterConstruction(
              modifiers,
              fieldConverted.name,
              fieldConverted.ftype,
              Some(fieldConverted.isVar),
              isArray = false
            )
            param.setComments(fieldConverted.comments)
            param
          }
          updatedParams += updatedField
        }

        val superCall = getSuperCall(dropStatements)

        val statementsOpt = getStatements(constructor)
        statementsOpt.map { statements =>
          val constructorStatements = statements
            .filter(notContains(_, dropStatements.toSeq))
            .map(convertPsiToIntermediate(_, WithReferenceExpression(true)))

          PrimaryConstructor(
            updatedParams.toSeq,
            superCall,
            Option(BlockConstruction(constructorStatements)),
            handleModifierList(constructor)
          )
        }.orNull
      }
      val primaryConstructor = createConstructor
      primaryConstructor
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

    implicit val usedComments: UsedComments = conversionContext.usedComments

    def getDropCommentsInside(dropMembers: Seq[PsiMember]): Seq[LiteralExpression] = {
      val commentsInside = dropMembers.flatMap(CommentsCollector.getAllInsideComments)
      val commentsConverted = commentsInside.map(CommentsCollector.convertComment)
      commentsConverted
    }

    val droppedMembersAndPrimaryConstructor: Option[PsiMethod] = constructors.length match {
      case 0 => None
      case 1 => Some(constructors.head)
      case _ => Option(GetComplexPrimaryConstructor())
    }
    droppedMembersAndPrimaryConstructor match {
      case Some(pc) =>
        val updatedConstructor = createPrimaryConstructor(pc)

        val dropMembers = pc :: dropFieldsBuilder.result()

        val commentsBefore = dropMembers.flatMap(CommentsCollector.getAllBeforeComments(_, ignoreCommentsUsedInParent = false))
        updatedConstructor.comments.afterComments ++= commentsBefore

        val commentsInside = getDropCommentsInside(dropMembers)
        val abandonedInsideCommentsTarget = updatedConstructor.body.getOrElse(updatedConstructor)
        abandonedInsideCommentsTarget.comments.afterComments ++= commentsInside

        Some((dropMembers, updatedConstructor))
      case None =>
        None
    }
  }

  private val SIMPLE_MODIFIERS_MAP: Map[String, ModifierType.Value] = Map(
    (PsiModifier.VOLATILE, ModifierType.VOLATILE),
    (PsiModifier.PRIVATE, ModifierType.PRIVATE),
    (PsiModifier.PROTECTED, ModifierType.PROTECTED),
    (PsiModifier.TRANSIENT, ModifierType.TRANSIENT),
    (PsiModifier.NATIVE, ModifierType.NATIVE)
  )

  private def handleModifierList(
    owner: PsiModifierListOwner
  )(implicit conversionContext: ConversionContext): ModifiersConstruction = {

    def handleAnnotations: Seq[AnnotationConstruction] = owner.getModifierList match {
      case null => Seq.empty
      case list =>
        for {
          annotation <- list.getAnnotations.toIndexedSeq
          if (annotation.getQualifiedName match {
            case null |
                 "java.lang.Override" |
                 "org.jetbrains.annotations.Nullable" |
                 "org.jetbrains.annotations.NotNull" |
                 "org.jetbrains.annotations.NonNls" => false
            case _ => true
          })
        } yield convertPsiToIntermediate(annotation, null).asInstanceOf[AnnotationConstruction]
    }

    def handleModifiers: Seq[Modifier] = {
      val context = this.context.value
      val modifiersBuilder = ArraySeq.newBuilder[Modifier]

      val simpleList = SIMPLE_MODIFIERS_MAP.filter {
        case (psiType, _) => owner.hasModifierProperty(psiType)
      }.values

      modifiersBuilder ++= simpleList.map(SimpleModifier)

      owner match {
        case method: PsiMethod =>
          val references = method.getThrowsList.getReferenceElements
          for (ref <- references) {
            modifiersBuilder += ModifierWithExpression(ModifierType.THROW, convertPsiToIntermediate(ref, null))
          }

          if (method.findSuperMethods.exists(!_.hasModifierProperty("abstract")
            || ScalaProjectSettings.getInstance(method.getProject).isAddOverrideToImplementInConverter)) {
            modifiersBuilder += SimpleModifier(ModifierType.OVERRIDE)
          }

        case c: PsiClass =>
          serialVersion(c) match {
            case Some(f) =>
              modifiersBuilder += ModifierWithExpression(ModifierType.SerialVersionUID, convertPsiToIntermediate(f.getInitializer, null))
            case _ =>
          }

          if ((!c.isInterface) && c.hasModifierProperty(PsiModifier.ABSTRACT))
            modifiersBuilder += SimpleModifier(ModifierType.ABSTRACT)

        case _ =>
      }

      if (!owner.hasModifierProperty(PsiModifier.PUBLIC) &&
        !owner.hasModifierProperty(PsiModifier.PRIVATE) &&
        !owner.hasModifierProperty(PsiModifier.PROTECTED) &&
        owner.getParent != null && owner.getParent.isInstanceOf[PsiClass]) {
        val packageName: String = owner.getContainingFile.asInstanceOf[PsiClassOwner].getPackageName
        if (packageName != "")
          modifiersBuilder += ModifierWithExpression(
            ModifierType.PRIVATE,
            LiteralExpression(packageName.substring(packageName.lastIndexOf(".") + 1))
          )
      }

      if (owner.hasModifierProperty(PsiModifier.FINAL) && !context.empty() && !context.peek()._1) {
        owner match {
          case _: PsiLocalVariable =>
          case _: PsiParameter =>
          case _ =>
            modifiersBuilder += SimpleModifier(ModifierType.FINAL) //only to classes, not objects
        }
      }

      modifiersBuilder.result()
    }

    val modifiers = ModifiersConstruction(handleAnnotations, handleModifiers)
    owner.getModifierList.toOption.foreach { modList =>
      val comments = CommentsCollector.allCommentsForElement(modList)(conversionContext.usedComments)
      modifiers.setComments(comments)
    }
    modifiers
  }

  import visitors.PrintWithComments

  def convertPsisToText(
    elements: Array[PsiElement],
    dropElements: mutable.Set[PsiElement] = new mutable.HashSet[PsiElement](),
    textMode: Boolean = false
  ): String = {
    //share same `UsedComments` mutable instance between all elements
    //otherwise they might print same comments - for one elements it will be "afterComment" for another it will be "beforeComment"
    val usedComments: UsedComments = new UsedComments(dropElements.filterByType[PsiComment])
    val children = elements.toSeq.map { part =>
      val context = new ConversionContext(textMode, mutable.ListBuffer(), Nil, dropElements, usedComments)
      convertPsiToIntermediate(part, null)(context)
    }
    val resultNode = MainConstruction(children)
    val text = PrintWithComments.print(resultNode)
    text
  }

  def convertPsiToText(element: PsiElement): String = {
    val resultNode = convertPsiToIntermediatePublic(element, null)(textMode = true)
    val text = PrintWithComments.print(resultNode)
    text
  }

  private def handleImport(psiImport: PsiImportStatementBase): IntermediateNode = {
    Option(psiImport.getImportReference).map(_.getQualifiedName) match {
      case Some(qName) if ScalaCodeStyleSettings.getInstance(psiImport.getProject).hasImportWithPrefix(qName) =>
        ImportStatement(LiteralExpression(qName.split('.').init.mkString(".")), psiImport.isOnDemand)
      case Some(name) =>
        ImportStatement(LiteralExpression(name), psiImport.isOnDemand)
      case _ => EmptyConstruction()
    }
  }

  private def getStatements(m: PsiMethod): Option[Seq[PsiStatement]] =
    Option(m.getBody).map(_.getStatements.toIndexedSeq)

  private def serialVersion(c: PsiClass): Option[PsiField] = {
    val serialField = c.findFieldByName("serialVersionUID", false)
    if (serialField != null && serialField.getType.isAssignableFrom(PsiTypeConstants.Long) &&
      serialField.hasModifierProperty("static") && serialField.hasModifierProperty("final") &&
      serialField.hasInitializer) {
      Some(serialField)
    } else None
  }


  @scala.annotation.tailrec
  private def isYieldRemovable(statement: PsiStatement): Boolean = {
    val noNextStatement = PsiTreeUtil.getNextSiblingOfType(statement, classOf[PsiStatement]) == null
    statement.getParent match {
      case _: PsiSwitchLabeledRuleStatement => noNextStatement
      case s: PsiIfStatement => isYieldRemovable(s)
      case s: PsiLabeledStatement => isYieldRemovable(s)
      case b: PsiCodeBlock if noNextStatement =>
        b.getParent match {
          case bs: PsiBlockStatement => isYieldRemovable(bs)
          case _: PsiSwitchExpression => true
          case _ => false
        }
      case _ => false
    }
  }

  private def isBreakRemovable(statement: PsiBreakStatement): Boolean = {
    statement.findExitedStatement() match {
      case sw: PsiSwitchStatement => isBreakRemovable(sw, statement)
      case _ => false
    }
  }

  @scala.annotation.tailrec
  private def isBreakRemovable(switchStatement: PsiSwitchStatement, statement: PsiStatement): Boolean = {
    statement.getParent match {
      case _: PsiSwitchLabeledRuleStatement => true
      case s: PsiIfStatement => isBreakRemovable(switchStatement, s)
      case s: PsiLabeledStatement => isBreakRemovable(switchStatement, s)
      case b: PsiCodeBlock if PsiTreeUtil.getNextSiblingOfType(statement, classOf[PsiStatement]) == null =>
        b.getParent match {
          case bs: PsiBlockStatement => isBreakRemovable(switchStatement, bs)
          case p => p eq switchStatement
        }
      case _ =>
        PsiTreeUtil.getNextSiblingOfType(statement, classOf[PsiStatement]) match {
          case ls: PsiSwitchLabelStatement =>
            (ls.getEnclosingSwitchBlock eq switchStatement) && !ControlFlowUtils.statementMayCompleteNormally(statement)
          case bs: PsiBreakStatement => bs.findExitedStatement eq switchStatement
          case _ => false
        }
    }
  }
}
