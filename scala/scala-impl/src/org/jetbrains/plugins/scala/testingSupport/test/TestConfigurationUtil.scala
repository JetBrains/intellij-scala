package org.jetbrains.plugins.scala
package testingSupport.test

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions.{OptionExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScInfixExpr, ScMethodCall, ScParenthesisedExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.testingSupport.test.testdata.AllInPackageTestData

object TestConfigurationUtil {

  def isPackageConfiguration(element: PsiElement, configuration: AbstractTestRunConfiguration): Boolean = {
    val pack: PsiPackage = element match {
      case dir: PsiDirectory => JavaDirectoryService.getInstance.getPackage(dir)
      case pack: PsiPackage  => pack
      case _                 => null
    }
    pack != null && isPackageConfiguration(pack, configuration)
  }

  private def isPackageConfiguration(pack: PsiPackage, configuration: AbstractTestRunConfiguration): Boolean =
    configuration.testConfigurationData match {
      case data: AllInPackageTestData => data.testPackagePath == pack.getQualifiedName
      case _                          => false
    }

  def isInheritor(clazz: ScTemplateDefinition, fqn: String): Boolean = {
    val suiteClazz = ScalaPsiManager.instance(clazz.getProject).getCachedClass(clazz.resolveScope, fqn)
    suiteClazz.fold(false)(ScalaPsiUtil.isInheritorDeep(clazz, _))
  }

  def isInheritor(clazz: ScTemplateDefinition, fqn: String, otherFqns: String*): Boolean =
    isInheritor(clazz, fqn) || otherFqns.exists(isInheritor(clazz, _))

  def isInheritor(clazz: ScTemplateDefinition, fqns: IterableOnce[String]): Boolean =
    fqns.iterator.exists(isInheritor(clazz, _))

  //noinspection TypeAnnotation
  private object StringMethodNames {
    val ToLowerCase = "toLowerCase"
    val ToUpperCase = "toUpperCase"
    val Trim = "trim"
    val ToString = "toString"
    val Intern = "intern"
    val Capitalize = "capitalize"
    val StripMargin = "stripMargin"
    val StripSuffix = "stripSuffix"
    val StripPrefix = "stripPrefix"
    val Concat = "concat"
    val Substring = "substring"
    val Replace = "replace"

    val NoArgMethods = Seq(ToLowerCase, Trim, ToString, StripMargin, Intern, Capitalize)
    val OneArgMethods = Seq(StripSuffix, StripPrefix, Substring, StripMargin)
    val TwoArgMethods = Seq(Replace, Substring)
  }

  private def getStaticTestNameElement(element: PsiElement, allowSymbolLiterals: Boolean): Option[Any] = {
    import StringMethodNames._

    def processNoArgMethod(refExpr: ScReferenceExpression): Option[String] = {
      val methodName = refExpr.refName
      if (methodName == ToString) {
        //special handling for now, since only toString is allowed on integers
        refExpr.smartQualifier
          .map(getStaticTestNameElement(_, allowSymbolLiterals))
          .flatMap {
            case Some(string: String) => Some(string)
            case Some(number: Number) => Some(number.toString)
            case _ => None
          }
      } else {
        refExpr.smartQualifier
          .flatMap(getStaticTestNameRaw(_, allowSymbolLiterals))
          .flatMap { (name: String) =>
            methodName match {
              case ToLowerCase => Some(name.toLowerCase)
              case ToUpperCase => Some(name.toUpperCase)
              case Trim => Some(name.trim)
              case Intern => Some(name.intern)
              case StripMargin => Some(name.stripMargin)
              case Capitalize => Some(name.capitalize)
              case ToString => Some(name)
              case _ => None
            }
          }
      }
    }

    def processOneArgMethod(refExpr: ScReferenceExpression, methodCall: ScMethodCall): Option[String] = {
      def helper(anyExpr: Any, arg: Any): Option[String] = (anyExpr, refExpr.refName, arg) match {
        case (expr: String, StripSuffix, string: String) => Some(expr.stripSuffix(string))
        case (expr: String, StripPrefix, string: String) => Some(expr.stripPrefix(string))
        case (expr: String, StripMargin, char: Char) => Some(expr.stripMargin(char))
        case (expr: String, Concat, string: String) => Some(expr.concat(string))
        case (expr: String, Substring, integer: Int) => Some(expr.substring(integer))
        case _ => None
      }

      for {
        arg <- getStaticTestNameElement(methodCall.argumentExpressions.head, allowSymbolLiterals)
        qualifier <- refExpr.smartQualifier
        name <- getStaticTestNameElement(qualifier, allowSymbolLiterals)
        result <- helper(name, arg)
      } yield result
    }

    def processTwoArgMethod(refExpr: ScReferenceExpression, methodCall: ScMethodCall): Option[String] = {
      def helper(anyExpr: Any, arg1: Any, arg2: Any): Option[String] = (anyExpr, refExpr.refName, arg1, arg2) match {
        case (expr: String, Replace, s1: String, s2: String) => Some(expr.replace(s1, s2))
        case (expr: String, Substring, begin: Int, end: Int) => Some(expr.substring(begin, end))
        case _ => None
      }

      for {
        arg1 <- getStaticTestNameElement(methodCall.argumentExpressions.head, allowSymbolLiterals)
        arg2 <- getStaticTestNameElement(methodCall.argumentExpressions(1), allowSymbolLiterals)
        qualifier <- refExpr.smartQualifier
        name <- getStaticTestNameElement(qualifier, allowSymbolLiterals)
        result <- helper(name, arg1, arg2)
      } yield result
    }

    element match {
      case literal: ScLiteral =>
        literal.getValue match {
          case text: String =>
            val result = literal.getContainingFile.getVirtualFile.getDetectedLineSeparator match {
              case newSeparator if newSeparator != null && literal.isMultiLineString =>
                StringUtil.convertLineSeparators(text, newSeparator)
              case _ => text
            }
            Some(result)
          case symbol: Symbol if allowSymbolLiterals => Some(symbol.name)
          case number: Number => Some(number)
          case character: Character => Some(character)
          case _ => None
        }
      case p: ScParenthesisedExpr =>
        p.innerElement.flatMap(getStaticTestNameRaw(_, allowSymbolLiterals))
      case infixExpr: ScInfixExpr =>
        infixExpr.getInvokedExpr match {
          case refExpr: ScReferenceExpression if refExpr.refName == "+" =>
            for {
              left <- getStaticTestNameElement(infixExpr.left, allowSymbolLiterals)
              right <- getStaticTestNameElement(infixExpr.right, allowSymbolLiterals)
            } yield left.toString + right.toString
          case _ => None
        }
      case methodCall: ScMethodCall =>
        methodCall.getInvokedExpr match {
          case refExpr: ScReferenceExpression =>
            val argsCount = methodCall.argumentExpressions.size
            val name = refExpr.refName
            if(NoArgMethods.contains(name) && argsCount == 0) {
              processNoArgMethod(refExpr)
            } else if(OneArgMethods.contains(name) && argsCount == 1) {
              processOneArgMethod(refExpr, methodCall)
            } else if(TwoArgMethods.contains(name) && argsCount == 2) {
              processTwoArgMethod(refExpr, methodCall)
            } else {
              None
            }
          case _ => None
        }
      case refExpr: ScReferenceExpression if refExpr.textMatches("+") =>
        getStaticTestNameRaw(refExpr.getParent, allowSymbolLiterals)
      case refExpr: ScReferenceExpression if NoArgMethods.contains(refExpr.refName) =>
        processNoArgMethod(refExpr)
      case refExpr: ScReferenceExpression =>
        refExpr.bind().map(_.getActualElement) match {
          case Some(refPattern: ScReferencePattern) =>
            ScalaPsiUtil.nameContext(refPattern) match {
              case patternDef: ScPatternDefinition =>
                patternDef.expr.flatMap(getStaticTestNameRaw(_, allowSymbolLiterals))
              case _ => None
            }
          case _ => None
        }
      case _ => None
    }
  }

  def getStaticTestName(element: PsiElement, allowSymbolLiterals: Boolean = false): Option[String] =
    getStaticTestNameRaw(element, allowSymbolLiterals)
      .map(escapeTestName(_).trim)

  private def getStaticTestNameRaw(element: PsiElement,
                                   allowSymbolLiterals: Boolean): Option[String] =
    getStaticTestNameElement(element, allowSymbolLiterals).filterByType[String]

  private def escapeTestName(testName: String): String = {
    testName
      .replace("\\", "\\\\")
      .replace("\n", "\\n")
      // we DO need to escape \r because on Windows e.g. scalatest also use \r in test names in case ofm multiline strings
      .replace("\r", "\\r")
  }
}
