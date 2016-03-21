package org.jetbrains.plugins.scala
package testingSupport.test.scalatest

import com.intellij.execution._
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScExpression, ScInfixExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScMember, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.ScTypeExt
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}
import org.jetbrains.plugins.scala.testingSupport.test.TestConfigurationUtil.isInheritor
import org.jetbrains.plugins.scala.testingSupport.test.TestRunConfigurationForm.TestKind
import org.jetbrains.plugins.scala.testingSupport.test.{TestConfigurationProducer, TestConfigurationUtil}

/**
 * User: Alexander Podkhalyuzin
 * Date: 08.05.2009
 */

class ScalaTestConfigurationProducer extends {
  val confType = new ScalaTestConfigurationType
  val confFactory = confType.confFactory
} with TestConfigurationProducer(confType) {

  override def suitePaths = List("org.scalatest.Suite")

  override def findExistingByElement(location: Location[_ <: PsiElement],
                                     existingConfigurations: Array[RunnerAndConfigurationSettings],
                                     context: ConfigurationContext): RunnerAndConfigurationSettings = {
    super.findExistingByElement(location, existingConfigurations, context)
  }

  override def createConfigurationByLocation(location: Location[_ <: PsiElement]): Option[(PsiElement, RunnerAndConfigurationSettings)] = {
    val element = location.getPsiElement
    if (element == null) return None

    if (element.isInstanceOf[PsiPackage] || element.isInstanceOf[PsiDirectory]) {
      val name = element match {
        case p: PsiPackage => p.getName
        case d: PsiDirectory => d.getName
      }
      return Some((element, TestConfigurationUtil.packageSettings(element, location, confFactory, ScalaBundle.message("test.in.scope.scalatest.presentable.text", name))))
    }

    val (testClass, testName) = getLocationClassAndTest(location)
    if (testClass == null) return None
    val testClassPath = testClass.qualifiedName
    val settings = RunManager.getInstance(location.getProject).
      createRunConfiguration(StringUtil.getShortName(testClassPath) +
      (if (testName != null) "." + testName else ""), confFactory)
    val runConfiguration = settings.getConfiguration.asInstanceOf[ScalaTestRunConfiguration]
    runConfiguration.setTestClassPath(testClassPath)
    runConfiguration.initWorkingDir()
    if (testName != null) runConfiguration.setTestName(testName)
    val kind = if (testName == null) TestKind.CLASS else TestKind.TEST_NAME
    runConfiguration.setTestKind(kind)
    try {
      val module = ScalaPsiUtil.getModule(element)
      if (module != null) {
        runConfiguration.setModule(module)
      }
    }
    catch {
      case e: Exception =>
    }
    JavaRunConfigurationExtensionManager.getInstance.extendCreatedConfiguration(runConfiguration, location)
    Some((testClass, settings))
  }

  override def isConfigurationByLocation(configuration: RunConfiguration, location: Location[_ <: PsiElement]): Boolean = {
    val element = location.getPsiElement
    if (element == null) return false
    if (element.isInstanceOf[PsiPackage] || element.isInstanceOf[PsiDirectory]) {
      if (!configuration.isInstanceOf[ScalaTestRunConfiguration]) return false
      return TestConfigurationUtil.isPackageConfiguration(element, configuration)
    }
    val (testClass, testName) = getLocationClassAndTest(location)
    if (testClass == null) return false
    val testClassPath = testClass.qualifiedName
    configuration match {
      case configuration: ScalaTestRunConfiguration if configuration.getTestKind == TestKind.CLASS &&
        testName == null =>
        testClassPath == configuration.getTestClassPath
      case configuration: ScalaTestRunConfiguration if configuration.getTestKind == TestKind.TEST_NAME =>
        testClassPath == configuration.getTestClassPath && testName != null &&
          testName == configuration.getTestName
      case _ => false
    }
  }

  def getLocationClassAndTest(location: Location[_ <: PsiElement]): (ScTypeDefinition, String) = {
    val element = location.getPsiElement
    var clazz: ScTypeDefinition = PsiTreeUtil.getParentOfType(element, classOf[ScTypeDefinition], false)
    if (clazz == null) return (null, null)
    val tb = clazz.extendsBlock.templateBody.orNull
    while (PsiTreeUtil.getParentOfType(clazz, classOf[ScTypeDefinition], true) != null) {
      clazz = PsiTreeUtil.getParentOfType(clazz, classOf[ScTypeDefinition], true)
    }
    if (!clazz.isInstanceOf[ScClass]) return (null, null)
    if (ScalaTestRunConfiguration.isInvalidSuite(clazz)) return (null, null)
    if (!suitePaths.exists(suitePath => isInheritor(clazz, suitePath))) return (null, null)

    sealed trait ReturnResult
    case class SuccessResult(invocation: MethodInvocation, testName: String, middleName: String) extends ReturnResult
    case object NotFoundResult extends ReturnResult
    case object WrongResult extends ReturnResult

    def checkCallGeneral(call: MethodInvocation, namesSet: Map[String, Set[String]],
                         inv: MethodInvocation => Option[String],
                         recursive: Boolean, checkFirstArgIsUnitOrString: Boolean): ReturnResult = {
      if (call == null) return NotFoundResult
      call.getInvokedExpr match {
        case ref: ScReferenceExpression if namesSet.isDefinedAt(ref.refName) =>
          var middleName = ref.refName
          val fqns = namesSet(ref.refName)
          val resolve = ref.resolve()
          if (resolve != null) {
            val containingClass = resolve match {
              case fun: ScMember => fun.containingClass
              case p: ScBindingPattern =>
                p.nameContext match {
                  case v: ScMember => v.containingClass
                  case _ => null
                }
              case _ => null
            }
            var failedToCheck = false
            if (checkFirstArgIsUnitOrString) {
              failedToCheck = true
              resolve match {
                case fun: ScFunction =>
                  val clauses = fun.paramClauses.clauses
                  if (clauses.nonEmpty) {
                    val params = clauses.head.parameters
                    if (params.nonEmpty) {
                      import org.jetbrains.plugins.scala.lang.psi.types.Unit
                      params.head.getType(TypingContext.empty) match {
                        case Success(Unit, _) => failedToCheck = false
                        case Success(tp, _) =>
                          tp.extractClass()(fun.typeSystem) match {
                            case Some(psiClass) if psiClass.qualifiedName == "java.lang.String" =>
                              call.argumentExpressions.head match {
                                case l: ScLiteral if l.isString =>
                                  failedToCheck = false
                                  middleName += " " + l.getValue.toString
                                case _ =>
                              }

                            case _ =>
                          }
                        case _ =>
                      }
                    }
                  }
              }
            }
            if (containingClass != null &&
                fqns.exists(fqn => fqn == containingClass.qualifiedName || isInheritor(containingClass, fqn))) {
              if (!failedToCheck) {
                val res = inv(call)
                if (res.isDefined) return SuccessResult(call, res.get, middleName)
                else return WrongResult
              } else return WrongResult
            }
          }
        case _call: MethodInvocation =>
          checkCallGeneral(_call, namesSet, inv, recursive = false, checkFirstArgIsUnitOrString) match {
            case res: SuccessResult => return res.copy(invocation = call)
            case WrongResult => return WrongResult
            case _ =>
          }
        case _ =>
      }
      if (!recursive) return NotFoundResult
      checkCallGeneral(PsiTreeUtil.getParentOfType(call, classOf[MethodInvocation], true), namesSet, inv, recursive = true,
        checkFirstArgIsUnitOrString)
    }

    def endupWithLitral(literal: ScExpression): Option[String] = {
      literal match {
        case l: ScLiteral if l.isString =>
          Some(l.getValue.asInstanceOf[String])
        case _ => None
      }
    }

    def checkCall(call: MethodInvocation, namesSet: Map[String, Set[String]]) = {
      val inv = (call: MethodInvocation) => {
        val literal = call.argumentExpressions.head
        endupWithLitral(literal)
      }
      checkCallGeneral(call, namesSet, inv, recursive = true, checkFirstArgIsUnitOrString = false)
    }

    def checkInfix(call: MethodInvocation, namesSet: Map[String, Set[String]],
                   checkFirstArgIsUnitOrString: Boolean = false) = {
      val inv: (MethodInvocation) => Option[String] = {
        case i: ScInfixExpr =>
          endupWithLitral(i.getBaseExpr)
        case call: MethodInvocation =>
          call.getInvokedExpr match {
            case ref: ScReferenceExpression =>
              ref.qualifier match {
                case Some(qual) => endupWithLitral(qual)
                case _ => None
              }
            case _ => None
          }
      }
      checkCallGeneral(call, namesSet, inv, recursive = true, checkFirstArgIsUnitOrString)
    }

    def checkInfixTagged(call: MethodInvocation, namesSet: Map[String, Set[String]], fqn: Set[String],
                         checkFirstArgIsUnitOrString: Boolean = false, testNameIsAlwaysEmpty: Boolean = false) = {
      val inv: (MethodInvocation) => Option[String] = m => {
        def checkTagged(m: MethodInvocation): Option[String] = {
          m.getInvokedExpr match {
            case ref: ScReferenceExpression if ref.refName == "taggedAs" =>
              val resolve = ref.resolve()
              resolve match {
                case fun: ScFunction =>
                  val clazz = fun.containingClass
                  if (clazz != null && fqn.contains(clazz.qualifiedName)) {
                    m match {
                      case i: ScInfixExpr => endupWithLitral(i.getBaseExpr)
                      case _ => m.getInvokedExpr match {
                        case ref: ScReferenceExpression => ref.qualifier match {
                          case Some(qual) => endupWithLitral(qual)
                          case None => None
                        }
                      }
                    }
                  } else None
                case _ => None
              }
            case _ => None
          }
        }
        m match {
          case i: ScInfixExpr =>
            i.getBaseExpr match {
              case m: MethodInvocation =>
                checkTagged(m)
              case base =>
                endupWithLitral(base)
            }
          case call: MethodInvocation =>
            call.getInvokedExpr match {
              case ref: ScReferenceExpression =>
                ref.qualifier match {
                  case Some(qual: MethodInvocation) => checkTagged(qual)
                  case Some(qual) => endupWithLitral(qual)
                  case _ => None
                }
              case _ => None
            }
        }
      }
      checkCallGeneral(call, namesSet, if (testNameIsAlwaysEmpty) _ => Some("") else inv, recursive = true, checkFirstArgIsUnitOrString)
    }

    import scala.language.implicitConversions

    implicit def s2set(s: String): Set[String] = Set(s) //todo: inline?

    def checkFunSuite(fqn: String): Option[String] = {
      if (!isInheritor(clazz, fqn)) return None
      checkCall(PsiTreeUtil.getParentOfType(element, classOf[MethodInvocation], false),
        Map("test" -> fqn, "ignore" -> fqn)) match {
        case SuccessResult(_, testName, _) => return Some(testName)
        case _ =>
      }
      None
    }

    def checkPropSpec(fqn: String): Option[String] = {
      if (!isInheritor(clazz, fqn)) return None
      checkCall(PsiTreeUtil.getParentOfType(element, classOf[MethodInvocation], false),
        Map("property" -> fqn, "ignore" -> fqn)) match {
        case SuccessResult(_, testName, _) => return Some(testName)
        case _ =>
      }
      None
    }

    def checkFeatureSpec(fqn: String): Option[String] = {
      if (!isInheritor(clazz, fqn)) return None
      checkCall(PsiTreeUtil.getParentOfType(element, classOf[MethodInvocation], false),
        Map("scenario" -> fqn, "ignore" -> fqn)) match {
        case SuccessResult(call, _testName, _) =>
          var testName = "Scenario: " + _testName
          checkCall(PsiTreeUtil.getParentOfType(call, classOf[MethodInvocation], true),
            Map("feature" -> fqn)) match {
            case SuccessResult(_, featureName, _) =>
              //check with Informing is used to distinguish scalatest 2.0 from scalatest 1.9.2
              testName = (if (isInheritor(clazz, "org.scalatest.Informing")) "Feature: " else "") +
                featureName + " " + testName
            case WrongResult => return None
            case _ =>
          }
          return Some(testName)
        case _ =>
      }
      None
    }

    def checkFunSpec(fqn: String): Option[String] = {
      if (!isInheritor(clazz, fqn)) return None
      checkCall(PsiTreeUtil.getParentOfType(element, classOf[MethodInvocation], false),
        Map("it" -> fqn, "ignore" -> fqn)) match {
        case SuccessResult(_call, _testName, _) =>
          var testName = _testName
          var call = _call
          while (call != null) {
            checkCall(PsiTreeUtil.getParentOfType(call, classOf[MethodInvocation], true),
              Map("describe" -> fqn)) match {
              case SuccessResult(invoc, featureName, _) =>
                testName = featureName + " " + testName
                call = invoc
              case WrongResult => return None
              case _ => call = null
            }
          }
          return Some(testName)
        case _ =>
      }
      None
    }

    def checkFreeSpec(fqn: String): Option[String] = {
      if (!isInheritor(clazz, fqn)) return None
      def checkFreeSpecInner(innerClassName: String): Option[String] = {
        val ifqn = fqn + innerClassName
        checkInfix(PsiTreeUtil.getParentOfType(element, classOf[MethodInvocation], false),
          Map("in" -> ifqn, "is" -> ifqn, "ignore" -> ifqn)) match {
          case SuccessResult(_call, _testName, _) =>
            var testName = _testName
            var call = _call
            while (call != null) {
              checkInfix(PsiTreeUtil.getParentOfType(call, classOf[MethodInvocation], true),
                Map("-" -> (fqn + ".FreeSpecStringWrapper"))) match {
                case SuccessResult(invoc, tName, _) =>
                  call = invoc
                  testName = tName + " " + testName
                case WrongResult => return None
                case _ => call = null
              }
            }
            Some(testName)
          case _ => None
        }
      }
      checkFreeSpecInner(".FreeSpecStringWrapper") match {
        case Some(name) => Some(name)
        case None => checkFreeSpecInner(".ResultOfTaggedAsInvocationOnString")
      }
    }

    val shouldFqn = "org.scalatest.verb.ShouldVerb.StringShouldWrapperForVerb"
    val mustFqn = "org.scalatest.verb.MustVerb.StringMustWrapperForVerb"
    val canFqn = "org.scalatest.verb.CanVerb.StringCanWrapperForVerb"
    val shouldFqn2 = "org.scalatest.words.ShouldVerb.StringShouldWrapperForVerb"
    val mustFqn2 = "org.scalatest.words.MustVerb.StringMustWrapperForVerb"
    val canFqn2 = "org.scalatest.words.CanVerb.StringCanWrapperForVerb"

    def checkWordSpec(fqn: String): Option[String] = {
      if (!isInheritor(clazz, fqn)) return None
      def checkWordSpecInner(innerClassName: String): Option[String] = {
        val ifqn = fqn + innerClassName
        val wfqn = fqn + ".WordSpecStringWrapper"
        checkInfixTagged(PsiTreeUtil.getParentOfType(element, classOf[MethodInvocation], false),
          Map("in" -> ifqn, "is" -> ifqn, "ignore" -> ifqn), wfqn) match {
          case SuccessResult(_call, _testName, _) =>
            var testName = _testName
            var call = _call
            while (call != null) {
              val checkInfixResult2 = checkInfix(PsiTreeUtil.getParentOfType(call, classOf[MethodInvocation], true),
                Map("when" -> wfqn, "that" -> ifqn, "should" -> shouldFqn2, "must" -> mustFqn2, "can" -> canFqn2), checkFirstArgIsUnitOrString = true)
              lazy val checkInfixResult = checkInfix(PsiTreeUtil.getParentOfType(call, classOf[MethodInvocation], true),
                Map("when" -> wfqn, "that" -> ifqn, "should" -> shouldFqn, "must" -> mustFqn, "can" -> canFqn), checkFirstArgIsUnitOrString = true)
              checkInfixResult2 match {
                case SuccessResult(invoc, tName, refName) =>
                  call = invoc
                  testName = tName + " " + refName + " " + testName
                case _ => (checkInfixResult, checkInfixResult) match {
                  case (_, SuccessResult(invoc, tName, refName)) =>
                    call = invoc
                    testName = tName + " " + refName + " " + testName
                  case (WrongResult, WrongResult) => return None
                  case _ => call = null
                }
              }
            }
            Some(testName)
          case _ => None
        }
      }
      checkWordSpecInner(".WordSpecStringWrapper") match {
        case Some(name) => Some(name)
        case None => checkWordSpecInner(".ResultOfTaggedAsInvocationOnString")
      }
    }

    def endupWithIt(it: ScReferenceExpression): Option[String] = {
      var elem: PsiElement = it
      var parent = it.getParent
      while (parent != null && (!parent.isInstanceOf[ScTemplateBody] || parent != tb)) {
        elem = parent
        parent = parent.getParent
      }
      var sibling = elem.getPrevSibling
      var result: Option[String] = null

      val infix: (MethodInvocation) => Option[String] = {
        case i: ScInfixExpr =>
          endupWithLitral(i.getBaseExpr)
        case call: MethodInvocation =>
          call.getInvokedExpr match {
            case ref: ScReferenceExpression =>
              ref.qualifier match {
                case Some(qual) => endupWithLitral(qual)
                case _ => None
              }
            case _ => None
          }
      }

      val call = (call: MethodInvocation) => {
        val literal = call.argumentExpressions.head
        endupWithLitral(literal)
      }

      val visitor = new ScalaRecursiveElementVisitor {
        override def visitReferenceExpression(ref: ScReferenceExpression) {
          ref.refName match {
            case "should" =>
              ref.resolve() match {
                case fun: ScFunction if fun.containingClass != null &&
                  fun.containingClass.qualifiedName == shouldFqn || fun.containingClass.qualifiedName == shouldFqn2 =>
                  if (result == null) {
                    ref.getParent match {
                      case m: MethodInvocation => result = infix(m)
                      case _ => result = None
                    }
                  }
                case _ =>
              }
            case "must" =>
              ref.resolve() match {
                case fun: ScFunction if fun.containingClass != null &&
                  fun.containingClass.qualifiedName == mustFqn || fun.containingClass.qualifiedName == mustFqn2 =>
                  if (result == null) {
                    ref.getParent match {
                      case m: MethodInvocation => result = infix(m)
                      case _ => result = None
                    }
                  }
                case _ =>
              }
            case "can" =>
              ref.resolve() match {
                case fun: ScFunction if fun.containingClass != null &&
                  fun.containingClass.qualifiedName == canFqn || fun.containingClass.qualifiedName == canFqn2 =>
                  if (result == null) {
                    ref.getParent match {
                      case m: MethodInvocation => result = infix(m)
                      case _ => result = None
                    }
                  }
                case _ =>
              }
            case "of" =>
              ref.resolve() match {
                case fun: ScFunction if fun.containingClass != null &&
                  fun.containingClass.qualifiedName == "org.scalatest.FlatSpec.BehaviorWord" =>
                  if (result == null) {
                    ref.getParent match {
                      case m: MethodInvocation => result = call(m)
                      case _ => result = None
                    }
                  }
                case _ =>
              }
            case _ =>
          }
        }
      }

      while (sibling != null && result == null) {
        sibling.accept(visitor)
        sibling = sibling.getPrevSibling
      }

      if (result == null) return None
      result
    }

    def checkInfixWithIt(call: MethodInvocation, namesSet: Map[String, Set[String]],
                         checkFirstArgIsUnitOrString: Boolean = false) = {
      val inv: (MethodInvocation) => Option[String] = {
        case i: ScInfixExpr =>
          i.getBaseExpr match {
            case ref: ScReferenceExpression if ref.refName == "it" || ref.refName == "ignore" || ref.refName == "they" =>
              endupWithIt(ref)
            case _ =>
              endupWithLitral(i.getBaseExpr)
          }
        case call: MethodInvocation =>
          call.getInvokedExpr match {
            case ref: ScReferenceExpression =>
              ref.qualifier match {
                case Some(ref: ScReferenceExpression) if ref.refName == "it" || ref.refName == "ignore" || ref.refName == "they" =>
                  endupWithIt(ref)
                case Some(qual) => endupWithLitral(qual)
                case _ => None
              }
            case _ => None
          }
      }
      checkCallGeneral(call, namesSet, inv, recursive = false, checkFirstArgIsUnitOrString)
    }

    def checkFlatSpec(fqn: String): Option[String] = {
      if (!isInheritor(clazz, fqn)) return None
      val itFqn = fqn + ".ItWord"
      val itVFqn = fqn + ".ItVerbString"
      val itVTFqn = fqn + ".ItVerbStringTaggedAs"
      val theyFqn = fqn + ".TheyWord"
      val theyVFqn = fqn + ".TheyVerbString"
      val theyVTFqn = fqn + ".TheyVerbStringTaggedAs"
      val igVTFqn = fqn + ".IgnoreVerbStringTaggedAs"
      val igVFqn = fqn + ".IgnoreVerbString"
      val igFqn = fqn + ".IgnoreWord"
      val inFqn = fqn + ".InAndIgnoreMethods"
      val inTFqn = fqn + ".InAndIgnoreMethodsAfterTaggedAs"
      val resFqn = "org.scalatest.verb.ResultOfStringPassedToVerb"
      val resFqn2 = "org.scalatest.words.ResultOfStringPassedToVerb"
      checkInfixTagged(PsiTreeUtil.getParentOfType(element, classOf[MethodInvocation], false),
        Map("in" -> Set(itVTFqn, itVFqn, igVFqn, igVTFqn, inFqn, inTFqn, theyVFqn, theyVTFqn),
          "is" -> Set(itVTFqn, itVFqn, igVFqn, igVTFqn, resFqn, resFqn2, theyVFqn, theyVTFqn), "ignore" -> Set(itVFqn, itVTFqn, inFqn, inTFqn, theyVFqn, theyVTFqn)),
        Set(itVFqn, igVFqn, resFqn, resFqn2, theyVFqn), testNameIsAlwaysEmpty = true) match {
        case SuccessResult(_call, _testName, _) =>
          var testName = _testName
          var call = _call
          while (call != null) {
            val base = call match {
              case i: ScInfixExpr =>
                i.getBaseExpr
              case m: MethodInvocation => m.getInvokedExpr match {
                case ref: ScReferenceExpression => ref.qualifier.orNull
                case _ => null
              }
            }
            base match {
              case null => call = null
              case m: MethodInvocation =>
                checkInfixWithIt(m, Map("should" -> Set(shouldFqn, shouldFqn2, itFqn, igFqn, theyFqn), "must" -> Set(mustFqn, mustFqn2, itFqn, igFqn, theyFqn),
                  "can" -> Set(canFqn, canFqn2, itFqn, igFqn, theyFqn)), checkFirstArgIsUnitOrString = true) match {
                  case SuccessResult(invoc, tName, middleName) =>
                    call = invoc
                    testName = tName + " " + middleName + (if (testName.isEmpty) "" else " ") + testName
                  case WrongResult => return None
                  case _ => call = null
                }
                call = m
              case _ => call = null
            }
          }
          Some(testName)
        case _ => None
      }
    }

    def checkJUnit3Suite(fqn: String): Option[String] = {
      if (!isInheritor(clazz, fqn)) return None
      var fun = PsiTreeUtil.getParentOfType(element, classOf[ScFunctionDefinition], false)
      while (fun != null) {
        if (fun.getParent.isInstanceOf[ScTemplateBody] && fun.containingClass == clazz) {
          if (fun.name.startsWith("test")) {
            return Some(fun.name)
          }
        }
        fun = PsiTreeUtil.getParentOfType(fun, classOf[ScFunctionDefinition], true)
      }
      None
    }

    def checkAnnotatedSuite(fqn: String, annot: String): Option[String] = {
      if (!isInheritor(clazz, fqn)) return None
      var fun = PsiTreeUtil.getParentOfType(element, classOf[ScFunctionDefinition], false)
      while (fun != null) {
        if (fun.getParent.isInstanceOf[ScTemplateBody] && fun.containingClass == clazz) {
          if (fun.hasAnnotation(annot).isDefined) {
            return Some(fun.name)
          }
        }
        fun = PsiTreeUtil.getParentOfType(fun, classOf[ScFunctionDefinition], true)
      }
      None
    }

    def checkJUnitSuite(fqn: String): Option[String] = {
      checkAnnotatedSuite(fqn, "org.junit.Test")
    }

    def checkTestNGSuite(fqn: String): Option[String] = {
      checkAnnotatedSuite(fqn, "org.testng.annotations.Test")
    }

    class OptionExtension(x: Option[String]) {
      def ++(s: => Option[String]): Option[String] = {
        if (x.isDefined) x
        else s
      }
    }
    implicit def o2e(x: Option[String]): OptionExtension = new OptionExtension(x)

    import ScalaTestUtil._
    val oldResult = (clazz,
      (getFunSuiteBases.toStream.map(checkFunSuite).find(_.isDefined).getOrElse(None) ++
              getFeatureSpecBases.toStream.map(checkFeatureSpec).find(_.isDefined).getOrElse(None) ++
              getFreeSpecBases.toStream.map(checkFreeSpec).find(_.isDefined).getOrElse(None) ++
              getJUnit3SuiteBases.toStream.map(checkJUnit3Suite).find(_.isDefined).getOrElse(None) ++
              getJUnitSuiteBases.toStream.map(checkJUnitSuite).find(_.isDefined).getOrElse(None) ++
              getPropSpecBases.toStream.map(checkPropSpec).find(_.isDefined).getOrElse(None) ++
              /**
                * //TODO: actually implement checkSpec for scalatest 2.0 Spec
                * checkSpec("org.scalatest.Spec") ++
                * checkSpec("org.scalatest.SpecLike") ++
                * checkSpec("org.scalatest.fixture.Spec") ++
                * checkSpec("org.scalatest.fixture.SpecLike") ++
                */
              //this is intended for scalatest versions < 2.0
              getFunSpecBasesPre2_0.toStream.map(checkFunSpec).find(_.isDefined).getOrElse(None) ++
              //this is intended for scalatest version 2.0
              getFunSpecBasesPost2_0.toStream.map(checkFunSpec).find(_.isDefined).getOrElse(None) ++
              //---
              getTestNGSuiteBases.toStream.map(checkTestNGSuite).find(_.isDefined).getOrElse(None) ++
              getFlatSpecBases.toStream.map(checkFlatSpec).find(_.isDefined).getOrElse(None) ++
              getWordSpecBases.toStream.map(checkWordSpec).find(_.isDefined).getOrElse(None)).orNull)

    val astTransformer = new ScalaTestAstTransformer()
    val selection = astTransformer.testSelection(location)

    if (selection != null) {
      if (selection.testNames.nonEmpty) (clazz, escapeAndConcatTestNames(selection.testNames().toList.map(_.trim)))
      else {
        val parent = location.getPsiElement.getParent
        if (parent != null) getLocationClassAndTest(new PsiLocation(location.getProject, parent))
        else null
      }
    }
    else oldResult
  }
}