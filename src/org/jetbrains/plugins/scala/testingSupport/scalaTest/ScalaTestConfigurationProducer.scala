package org.jetbrains.plugins.scala.testingSupport.scalaTest

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.testingSupport.RuntimeConfigurationProducerAdapter
import org.jetbrains.plugins.scala.ScalaBundle
import com.intellij.execution._
import com.intellij.psi.util.PsiTreeUtil
import configurations.RunConfiguration
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.plugins.scala.testingSupport.scalaTest.ScalaTestRunConfigurationForm.TestKind
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScInfixExpr, MethodInvocation, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScValue, ScFunctionDefinition, ScFunction}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScClass, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor

/**
 * User: Alexander Podkhalyuzin
 * Date: 08.05.2009
 */
class ScalaTestConfigurationProducer extends {
  val confType = new ScalaTestConfigurationType
  val confFactory = confType.confFactory
} with RuntimeConfigurationProducerAdapter(confType) {
  private var myPsiElement: PsiElement = null
  def getSourceElement: PsiElement = myPsiElement

  protected def createConfigurationByElement(location: Location[_ <: PsiElement],
                                                       context: ConfigurationContext): RunnerAndConfigurationSettingsImpl = {
    if (context.getModule == null) return null
    val scope: GlobalSearchScope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(context.getModule, true)
    if (ScalaPsiManager.instance(context.getProject).getCachedClass(scope, "org.scalatest.Suite") == null) return null
    myPsiElement = location.getPsiElement
    createConfigurationByLocation(location).asInstanceOf[RunnerAndConfigurationSettingsImpl]
  }

  protected override def findExistingByElement(location: Location[_ <: PsiElement],
                                               existingConfigurations: Array[RunnerAndConfigurationSettings],
                                               context: ConfigurationContext): RunnerAndConfigurationSettings = {
    existingConfigurations.find(c => isConfigurationByLocation(c.getConfiguration, location)).getOrElse(null)
  }

  private def isInheritor(clazz: ScTypeDefinition, fqn: String): Boolean = {
    val suiteClazz = ScalaPsiManager.instance(clazz.getProject).getCachedClass(clazz.getResolveScope, fqn)
    if (suiteClazz == null) return false
    ScalaPsiUtil.cachedDeepIsInheritor(clazz, suiteClazz)
  }
  
  private def getLocationClassAndTest(location: Location[_ <: PsiElement]): (String, String) = {
    val element = location.getPsiElement
    var clazz: ScTypeDefinition = PsiTreeUtil.getParentOfType(element, classOf[ScTypeDefinition], false)
    val tb = clazz.extendsBlock.templateBody.getOrElse(null)
    if (clazz == null) return null
    while (PsiTreeUtil.getParentOfType(clazz, classOf[ScTypeDefinition], true) != null) {
      clazz = PsiTreeUtil.getParentOfType(clazz, classOf[ScTypeDefinition], true)
    }
    if (!clazz.isInstanceOf[ScClass]) return (null, null)
    if (clazz.hasModifierProperty("abstract")) return (null, null)
    if (!isInheritor(clazz, "org.scalatest.Suite")) return (null, null)
    val testClassPath = clazz.getQualifiedName
    
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
              case fun: ScMember => fun.getContainingClass
              case p: ScBindingPattern =>
                p.nameContext match {
                  case v: ScMember => v.getContainingClass
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
                  if (clauses.length > 0) {
                    val params = clauses(0).parameters
                    if (params.length > 0) {
                      import org.jetbrains.plugins.scala.lang.psi.types.Unit
                      params(0).getType(TypingContext.empty) match {
                        case Success(Unit, _) => failedToCheck = false
                        case Success(tp, _) =>
                          ScType.extractClass(tp) match {
                            case Some(clazz) if clazz.getQualifiedName == "java.lang.String" =>
                              call.argumentExpressions.apply(0) match {
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
            if (containingClass != null && fqns.find(_ == containingClass.getQualifiedName) != None) {
              if (!failedToCheck) {
                val res = inv(call)
                if (res.isDefined) return SuccessResult(call, res.get, middleName)
                else return WrongResult
              } else return WrongResult
            }
          }
        case _call: MethodInvocation =>
          checkCallGeneral(_call, namesSet, inv, false, checkFirstArgIsUnitOrString) match {
            case res: SuccessResult => return res.copy(invocation = call)
            case WrongResult => return WrongResult
            case _ =>
          }
        case _ =>
      }
      if (!recursive) return NotFoundResult
      checkCallGeneral(PsiTreeUtil.getParentOfType(call, classOf[MethodInvocation], true), namesSet, inv, true,
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
        val literal = call.argumentExpressions.apply(0)
        endupWithLitral(literal)
      }
      checkCallGeneral(call, namesSet, inv, true, false)
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
      checkCallGeneral(call, namesSet, inv, true, checkFirstArgIsUnitOrString)
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
                  val clazz = fun.getContainingClass
                  if (clazz != null && fqn.contains(clazz.getQualifiedName)) {
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
      checkCallGeneral(call, namesSet, if (testNameIsAlwaysEmpty) _ => Some("") else inv, true, checkFirstArgIsUnitOrString)
    }
    
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
              testName = featureName + " " + testName
            case WrongResult => return None
            case _ =>
          }
          return Some(testName)
        case _ =>
      }
      None
    }

    def checkSpec(fqn: String): Option[String] = {
      if (!isInheritor(clazz, fqn)) return None
      checkCall(PsiTreeUtil.getParentOfType(element, classOf[MethodInvocation], false),
        Map("it" -> fqn, "ignore" -> fqn)) match {
        case SuccessResult(_call, _testName, _) =>
          var testName = _testName
          var call = _call
          while (call != null) {
            checkCall(PsiTreeUtil.getParentOfType(call, classOf[MethodInvocation], true),
              Map("describe" -> fqn)) match {
              case SuccessResult(_call, featureName, _) =>
                testName = featureName + " " + testName
                call = _call
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
                case SuccessResult(_call, _testName, _) =>
                  call = _call
                  testName = _testName + " " + testName
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
              checkInfix(PsiTreeUtil.getParentOfType(call, classOf[MethodInvocation], true),
                Map("when" -> wfqn, "that" -> ifqn, "should" -> shouldFqn, "must" -> mustFqn, "can" -> canFqn), true) match {
                case SuccessResult(_call, _testName, refName) =>
                  call = _call
                  testName = _testName + " " + refName + " " + testName
                case WrongResult => return None
                case _ => call = null
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
        val literal = call.argumentExpressions.apply(0)
        endupWithLitral(literal)
      }
      
      val visitor = new ScalaRecursiveElementVisitor {
        override def visitReferenceExpression(ref: ScReferenceExpression) {
          ref.refName match {
            case "should" =>
              ref.resolve() match {
                case fun: ScFunction if fun.getContainingClass != null &&
                  fun.getContainingClass.getQualifiedName == shouldFqn =>
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
                case fun: ScFunction if fun.getContainingClass != null &&
                  fun.getContainingClass.getQualifiedName == mustFqn =>
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
                case fun: ScFunction if fun.getContainingClass != null &&
                  fun.getContainingClass.getQualifiedName == canFqn =>
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
                case fun: ScFunction if fun.getContainingClass != null &&
                  fun.getContainingClass.getQualifiedName == "org.scalatest.FlatSpec.BehaviorWord" =>
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
            case ref: ScReferenceExpression if ref.refName == "it" || ref.refName == "ignore" =>
              endupWithIt(ref)
            case _ =>
              endupWithLitral(i.getBaseExpr)
          }
        case call: MethodInvocation =>
          call.getInvokedExpr match {
            case ref: ScReferenceExpression =>
              ref.qualifier match {
                case Some(ref: ScReferenceExpression) if ref.refName == "it" || ref.refName == "ignore" =>
                  endupWithIt(ref)
                case Some(qual) => endupWithLitral(qual)
                case _ => None
              }
            case _ => None
          }
      }
      checkCallGeneral(call, namesSet, inv, false, checkFirstArgIsUnitOrString)
    }

    def checkFlatSpec(fqn: String): Option[String] = {
      if (!isInheritor(clazz, fqn)) return None
      val itFqn = fqn + ".ItWord"
      val itVFqn = fqn + ".ItVerbString"
      val itVTFqn = fqn + ".ItVerbStringTaggedAs"
      val igVTFqn = fqn + ".IgnoreVerbStringTaggedAs"
      val igVFqn = fqn + ".IgnoreVerbString"
      val igFqn = fqn + ".IgnoreWord"
      val inFqn = fqn + ".InAndIgnoreMethods"
      val inTFqn = fqn + ".InAndIgnoreMethodsAfterTaggedAs"
      val resFqn = "org.scalatest.verb.ResultOfStringPassedToVerb"
      checkInfixTagged(PsiTreeUtil.getParentOfType(element, classOf[MethodInvocation], false),
        Map("in" -> Set(itVTFqn, itVFqn, igVFqn, igVTFqn, inFqn, inTFqn), 
          "is" -> Set(itVTFqn, itVFqn, igVFqn, igVTFqn, resFqn), "ignore" -> Set(itVFqn, itVTFqn, inFqn, inTFqn)),
          Set(itVFqn, igVFqn, resFqn), testNameIsAlwaysEmpty = true) match {
        case SuccessResult(_call, _testName, _) =>
          var testName = _testName
          var call = _call
          while (call != null) {
            val base = call match {
              case i: ScInfixExpr =>
                i.getBaseExpr
              case m: MethodInvocation => m.getInvokedExpr match {
                case ref: ScReferenceExpression => ref.qualifier.getOrElse(null)
                case _ => null
              }
            }
            base match {
              case null => call = null
              case m: MethodInvocation =>
                checkInfixWithIt(m, Map("should" -> Set(shouldFqn, itFqn, igFqn), "must" -> Set(mustFqn, itFqn, igFqn),
                    "can" -> Set(canFqn, itFqn, igFqn)), true) match {
                  case SuccessResult(_call, _testName, middleName) =>
                    call = _call
                    testName = _testName + " " + middleName + (if (testName.isEmpty) "" else " ") + testName
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
        if (fun.getParent.isInstanceOf[ScTemplateBody] && fun.getContainingClass == clazz) {
          if (fun.getName.startsWith("test")) {
            return Some(fun.getName)
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
        if (fun.getParent.isInstanceOf[ScTemplateBody] && fun.getContainingClass == clazz) {
          if (fun.hasAnnotation(annot) != None) {
            return Some(fun.getName)
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


    (testClassPath, checkFunSuite("org.scalatest.FunSuite") ++
      checkFunSuite("org.scalatest.fixture.FixtureFunSuite") ++
      checkFunSuite("org.scalatest.fixture.MultipleFixtureFunSuite") ++
      checkFeatureSpec("org.scalatest.FeatureSpec") ++
      checkFeatureSpec("org.scalatest.fixture.FixtureFeatureSpec") ++
      checkFeatureSpec("org.scalatest.fixture.MultipleFixtureFeatureSpec") ++
      checkFreeSpec("org.scalatest.FreeSpec") ++
      checkFreeSpec("org.scalatest.fixture.FixtureFreeSpec") ++
      checkFreeSpec("org.scalatest.fixture.MultipleFixtureFreeSpec") ++
      checkJUnit3Suite("org.scalatest.junit.JUnit3Suite") ++
      checkJUnitSuite("org.scalatest.junit.JUnitSuite") ++
      checkPropSpec("org.scalatest.PropSpec") ++
      checkPropSpec("org.scalatest.fixture.FixturePropSpec") ++
      checkPropSpec("org.scalatest.fixture.MultipleFixturePropSpec") ++
      checkSpec("org.scalatest.Spec") ++
      checkSpec("org.scalatest.fixture.FixtureSpec") ++
      checkSpec("org.scalatest.fixture.MultipleFixtureSpec") ++
      checkTestNGSuite("org.scalatest.testng.TestNGSuite") ++
      checkFlatSpec("org.scalatest.FlatSpec") ++
      checkFlatSpec("org.scalatest.fixture.FixtureFlatSpec") ++
      checkFlatSpec("org.scalatest.fixture.MultipleFixtureFlatSpec") ++
      checkWordSpec("org.scalatest.WordSpec") ++
      checkWordSpec("org.scalatest.fixture.FixtureWordSpec") ++
      checkWordSpec("org.scalatest.fixture.MultipleFixtureWordSpec")
      getOrElse null)
  }

  private def createConfigurationByLocation(location: Location[_ <: PsiElement]): RunnerAndConfigurationSettings = {
    val element = location.getPsiElement
    if (element == null) return null
    if (element.isInstanceOf[PsiPackage] || element.isInstanceOf[PsiDirectory]) {
      val pack: PsiPackage = element match {
        case dir: PsiDirectory => JavaDirectoryService.getInstance.getPackage(dir)
        case pack: PsiPackage => pack
      }
      if (pack == null) return null
      val displayName = ScalaBundle.message("test.in.scope.scalatest.presentable.text", pack.getQualifiedName)
      val settings = RunManager.getInstance(location.getProject).createRunConfiguration(displayName, confFactory)
      val configuration = settings.getConfiguration.asInstanceOf[ScalaTestRunConfiguration]
      configuration.setTestPackagePath(pack.getQualifiedName)
      configuration.setTestKind(ScalaTestRunConfigurationForm.TestKind.ALL_IN_PACKAGE)
      configuration.setGeneratedName(displayName)
      JavaRunConfigurationExtensionManager.getInstance.extendCreatedConfiguration(configuration, location)
      return settings
    }
    val (testClassPath, testClassName) = getLocationClassAndTest(location)
    if (testClassPath == null) return null
    val settings = RunManager.getInstance(location.getProject).
      createRunConfiguration(StringUtil.getShortName(testClassPath) +
      (if (testClassName != null) "." + testClassName else ""), confFactory)
    val runConfiguration = settings.getConfiguration.asInstanceOf[ScalaTestRunConfiguration]
    runConfiguration.setTestClassPath(testClassPath)
    if (testClassName != null) runConfiguration.setTestName(testClassName)
    val kind = if (testClassName == null) TestKind.CLASS else TestKind.TEST_NAME
    runConfiguration.setTestKind(kind)
    try {
      val module = ScalaPsiUtil.getModule(element)
      if (module != null) {
        runConfiguration.setModule(module)
      }
    }
    catch {
      case e =>
    }
    JavaRunConfigurationExtensionManager.getInstance.extendCreatedConfiguration(runConfiguration, location)
    settings
  }

  private def isConfigurationByLocation(configuration: RunConfiguration, location: Location[_ <: PsiElement]): Boolean = {
    val element = location.getPsiElement
    if (element == null) return false
    if (element.isInstanceOf[PsiPackage] || element.isInstanceOf[PsiDirectory]) {
      val pack: PsiPackage = element match {
        case dir: PsiDirectory => JavaDirectoryService.getInstance.getPackage(dir)
        case pack: PsiPackage => pack
      }
      if (pack == null) return false
      configuration match {
        case configuration: ScalaTestRunConfiguration => {
          return configuration.getTestKind() == ScalaTestRunConfigurationForm.TestKind.ALL_IN_PACKAGE &&
            configuration.getTestPackagePath == pack.getQualifiedName
        }
        case _ => return false
      }
    }
    val (testClassPath, testClassName) = getLocationClassAndTest(location)
    if (testClassPath == null) return false
    configuration match {
      case configuration: ScalaTestRunConfiguration if configuration.getTestKind() == TestKind.CLASS &&
        testClassName == null =>
        testClassPath == configuration.getTestClassPath
      case configuration: ScalaTestRunConfiguration if configuration.getTestKind() == TestKind.TEST_NAME =>
        testClassPath == configuration.getTestClassPath && testClassName != null &&
          testClassName == configuration.getTestName()
      case _ => false
    }
  }

}