package org.jetbrains.plugins.scala.testingSupport.test.scalatest

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiClassExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScExpression, ScInfixExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}
import org.jetbrains.plugins.scala.testingSupport.test.TestConfigurationUtil.isInheritor
import ScalaTestTestLocationsFinder.SuiteMethodNames

/**
 * TODO: now we have two different types set finders:
 *  1. defined in this class
 *  2. forked scala-test-finders (see most up-to-date forks: https://github.com/scalatest/scalatest-finders)
 *  Despite this class has "Old" it's not quite clear which is older
 *  because original scala-test-finders were abandoned in 2013 (!)
 *  Currently both finders work in some cases and do not in others.
 *  We should review all the finders, unify and leave some single finders.
 *  Aslso consider unifying with [[ScalaTestTestLocationsFinder]]
 */
class ScalaTestSingleTestLocationFinderOld(
  element: PsiElement,
  clazz: ScTypeDefinition,
  templateBody: ScTemplateBody
) {

  def testClassWithTestName: (ScTypeDefinition, String) = {

    import ScalaTestUtil._

    //noinspection ConvertibleToMethodValue
    val suitsWithFinders: Seq[(Seq[String], String => Option[String])] = Seq(
      (funSuiteBases, checkFunSuite _),
      (featureSpecBases, checkFeatureSpec _),
      (freeSpecBases, checkFreeSpec _),
      (JUnit3SuiteBases, checkJUnit3Suite _),
      (JUnitSuiteBases, checkJUnitSuite _),
      (propSpecBases, checkPropSpec _),
      /**
       * //TODO: actually implement checkSpec for scalatest 2.0 Spec
       * checkSpec("org.scalatest.Spec") ++
       * checkSpec("org.scalatest.SpecLike") ++
       * checkSpec("org.scalatest.fixture.Spec") ++
       * checkSpec("org.scalatest.fixture.SpecLike") ++
       */
      //this is intended for scalatest versions < 2.0
      (funSpecBasesPre2_0, checkFunSpec _),
      //this is intended for scalatest version 2.0
      (funSpecBasesPost2_0, checkFunSpec _),
      //---
      (testNGSuiteBases, checkTestNGSuite _),
      (flatSpecBases, checkFlatSpec _),
      (wordSpecBases, checkWordSpec _),
    )

    // use iterators, let the search be lazy
    val searchResults: Iterator[(String, String)] =
      for {
        (suites, findTestName) <- suitsWithFinders.iterator
        suite                  <- suites.iterator
        testName               <- findTestName(suite)
      } yield (suite, testName)

    val suiteWithTestName = searchResults.headOption
    val testName = suiteWithTestName.map(_._2)
    (clazz, testName.orNull)
  }

  private val shouldFqn  = "org.scalatest.verb.ShouldVerb.StringShouldWrapperForVerb"
  private val mustFqn    = "org.scalatest.verb.MustVerb.StringMustWrapperForVerb"
  private val canFqn     = "org.scalatest.verb.CanVerb.StringCanWrapperForVerb"
  private val shouldFqn2 = "org.scalatest.words.ShouldVerb.StringShouldWrapperForVerb"
  private val mustFqn2   = "org.scalatest.words.MustVerb.StringMustWrapperForVerb"
  private val canFqn2    = "org.scalatest.words.CanVerb.StringCanWrapperForVerb"

  private sealed trait ReturnResult
  private case class SuccessResult(invocation: MethodInvocation, testName: String, middleName: String) extends ReturnResult
  private case object NotFoundResult extends ReturnResult
  private case object WrongResult extends ReturnResult

  private def checkCallGeneral(
    call: MethodInvocation,
    namesSet: Map[String, Set[String]],
    inv: MethodInvocation => Option[String],
    recursive: Boolean,
    checkFirstArgIsUnitOrString: Boolean
  ): ReturnResult = {
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
                    params.head.`type`() match {
                      case Right(t) if t.isUnit => failedToCheck = false
                      case Right(tp) =>
                        tp.extractClass match {
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
            val result = if (!failedToCheck) {
              inv(call) match {
                case Some(invRes) => SuccessResult(call, invRes, middleName)
                case None => WrongResult
              }
            } else {
              WrongResult
            }
            return result
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
    if (recursive) {
      checkCallGeneral(
        PsiTreeUtil.getParentOfType(call, classOf[MethodInvocation], true),
        namesSet, inv, recursive = true,
        checkFirstArgIsUnitOrString
      )
    } else {
      NotFoundResult
    }
  }

  private def endUpWithLiteral(literal: ScExpression): Option[String] = {
    literal match {
      case literal: ScLiteral if literal.isString =>
        Some(literal.getValue.asInstanceOf[String])
      case _ => None
    }
  }

  private def checkCall(call: MethodInvocation, namesSet: Map[String, String])(implicit d: DummyImplicit): ReturnResult =
    checkCall(call, namesSet.view.mapValues(Set(_)).toMap)

  private def checkCall(call: MethodInvocation, namesSet: Map[String, Set[String]]): ReturnResult = {
    val inv: MethodInvocation => Option[String] = call => {
      call.argumentExpressions
        .headOption
        .flatMap(endUpWithLiteral)
    }
    checkCallGeneral(call, namesSet, inv, recursive = true, checkFirstArgIsUnitOrString = false)
  }

  private def checkInfix(call: MethodInvocation, namesSet: Map[String, Set[String]],
                         checkFirstArgIsUnitOrString: Boolean = false) = {
    val inv: MethodInvocation => Option[String] = {
      case i: ScInfixExpr =>
        endUpWithLiteral(i.getBaseExpr)
      case call: MethodInvocation =>
        call.getInvokedExpr match {
          case ref: ScReferenceExpression =>
            ref.qualifier match {
              case Some(qual) => endUpWithLiteral(qual)
              case _ => None
            }
          case _ => None
        }
    }
    checkCallGeneral(call, namesSet, inv, recursive = true, checkFirstArgIsUnitOrString)
  }

  private def checkInfixTagged(
    call: MethodInvocation,
    namesSet: Map[String, Set[String]],
    fqn: Set[String],
    checkFirstArgIsUnitOrString: Boolean = false,
    testNameIsAlwaysEmpty: Boolean = false
  ) = {
    val inv: MethodInvocation => Option[String] = m => {
      def checkTagged(m: MethodInvocation): Option[String] = {
        m.getInvokedExpr match {
          case ref: ScReferenceExpression if ref.refName == "taggedAs" =>
            val resolve = ref.resolve()
            resolve match {
              case fun: ScFunction =>
                val clazz = fun.containingClass
                if (clazz != null && fqn.contains(clazz.qualifiedName)) {
                  m match {
                    case i: ScInfixExpr => endUpWithLiteral(i.getBaseExpr)
                    case _ => m.getInvokedExpr match {
                      case ref: ScReferenceExpression => ref.qualifier match {
                        case Some(qual) => endUpWithLiteral(qual)
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
              endUpWithLiteral(base)
          }
        case call: MethodInvocation =>
          call.getInvokedExpr match {
            case ref: ScReferenceExpression =>
              ref.qualifier match {
                case Some(qual: MethodInvocation) => checkTagged(qual)
                case Some(qual) => endUpWithLiteral(qual)
                case _ => None
              }
            case _ => None
          }
      }
    }

    val invActual: MethodInvocation => Option[String] = if (testNameIsAlwaysEmpty) _ => Some("") else inv
    checkCallGeneral(call, namesSet, invActual, recursive = true, checkFirstArgIsUnitOrString)
  }

  import scala.language.implicitConversions

  implicit def s2set(s: String): Set[String] = Set(s) //todo: inline?

  private def checkFunSuite(fqn: String): Option[String] = {
    if (!isInheritor(clazz, fqn)) return None
    val result: ReturnResult = checkCall(
      PsiTreeUtil.getParentOfType(element, classOf[MethodInvocation], false),
      Map("test" -> fqn, "ignore" -> fqn)
    )
    result match {
      case SuccessResult(_, testName, _) => Some(testName)
      case _ => None
    }
  }

  private def checkPropSpec(fqn: String): Option[String] = {
    if (!isInheritor(clazz, fqn)) return None
    val result = checkCall(
      PsiTreeUtil.getParentOfType(element, classOf[MethodInvocation], false),
      Map("property" -> fqn, "ignore" -> fqn)
    )
    result match {
      case SuccessResult(_, testName, _) => Some(testName)
      case _ => None
    }
  }

  private def checkFeatureSpec(fqn: String): Option[String] = {
    if (!isInheritor(clazz, fqn)) return None
    val result = checkCall(
      PsiTreeUtil.getParentOfType(element, classOf[MethodInvocation], false),
      (SuiteMethodNames.FeatureSpecLeaves.map(_ -> fqn) ++ Seq("ignore" -> fqn)).toMap
    )
    result match {
      case SuccessResult(call, _testName, _) =>
        val testName = "Scenario: " + _testName
        val innerResult = checkCall(
          PsiTreeUtil.getParentOfType(call, classOf[MethodInvocation], true),
          SuiteMethodNames.FeatureSpecNodes.map(_ -> fqn).toMap
        )
        innerResult match {
          case SuccessResult(_, featureName, _) =>
            //check with Informing is used to distinguish scalatest 2.0 from scalatest 1.9.2
            val prefix = if (isInheritor(clazz, "org.scalatest.Informing")) "Feature: " else ""
            val testNameUpd = prefix + featureName + " " + testName
            Some(testNameUpd)
          case WrongResult =>
            None
          case _ =>
            Some(testName)
        }
      case _ =>
        None
    }
  }

  private def checkFunSpec(fqn: String): Option[String] = {
    if (!isInheritor(clazz, fqn)) return None
    val result = checkCall(
      PsiTreeUtil.getParentOfType(element, classOf[MethodInvocation], false),
      Map("it" -> fqn, "ignore" -> fqn)
    )
    result match {
      case SuccessResult(_call, _testName, _) =>
        var testName = _testName
        var call = _call
        while (call != null) {
          val innerResult = checkCall(
            PsiTreeUtil.getParentOfType(call, classOf[MethodInvocation], true),
            Map("describe" -> fqn)
          )
          innerResult match {
            case SuccessResult(inv, featureName, _) =>
              testName = featureName + " " + testName
              call = inv
            case WrongResult => return None
            case _ => call = null
          }
        }
        Some(testName)
      case _ =>
        None
    }
  }

  private def checkFreeSpec(fqn: String): Option[String] = {
    if (!isInheritor(clazz, fqn)) return None

    def checkFreeSpecInner(innerClassName: String): Option[String] = {
      val ifqn = fqn + innerClassName
      val result = checkInfix(
        PsiTreeUtil.getParentOfType(element, classOf[MethodInvocation], false),
        Map("in" -> ifqn, "is" -> ifqn, "ignore" -> ifqn)
      )
      result match {
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

    checkFreeSpecInner(".FreeSpecStringWrapper")
      .orElse(checkFreeSpecInner(".ResultOfTaggedAsInvocationOnString"))
  }

  private def checkWordSpec(fqn: String): Option[String] = {
    if (!isInheritor(clazz, fqn)) return None

    def checkWordSpecInner(innerClassName: String): Option[String] = {
      val ifqn = fqn + innerClassName
      val wfqn = fqn + ".WordSpecStringWrapper"
      val result = checkInfixTagged(
        PsiTreeUtil.getParentOfType(element, classOf[MethodInvocation], false),
        Map("in" -> ifqn, "is" -> ifqn, "ignore" -> ifqn),
        wfqn
      )
      result match {
        case SuccessResult(_call, _testName, _) =>
          var testName = _testName
          var call = _call
          while (call != null) {
            val checkInfixResult2 = checkInfix(
              PsiTreeUtil.getParentOfType(call, classOf[MethodInvocation], true),
              Map("when" -> wfqn, "that" -> ifqn, "should" -> shouldFqn2, "must" -> mustFqn2, "can" -> canFqn2),
              checkFirstArgIsUnitOrString = true
            )
            lazy val checkInfixResult = checkInfix(
              PsiTreeUtil.getParentOfType(call, classOf[MethodInvocation], true),
              Map("when" -> wfqn, "that" -> ifqn, "should" -> shouldFqn, "must" -> mustFqn, "can" -> canFqn),
              checkFirstArgIsUnitOrString = true
            )
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

    checkWordSpecInner(".WordSpecStringWrapper")
      .orElse(checkWordSpecInner(".ResultOfTaggedAsInvocationOnString"))
  }

  private def endUpWithIt(it: ScReferenceExpression): Option[String] = {
    var elem: PsiElement = it
    var parent = it.getParent
    while (parent != null && (!parent.isInstanceOf[ScTemplateBody] || parent != templateBody)) {
      elem = parent
      parent = parent.getParent
    }
    var sibling = elem.getPrevSiblingNotWhitespaceComment
    var result: Option[String] = null

    val infix: MethodInvocation => Option[String] = {
      case i: ScInfixExpr =>
        endUpWithLiteral(i.getBaseExpr)
      case call: MethodInvocation =>
        call.getInvokedExpr match {
          case ref: ScReferenceExpression =>
            ref.qualifier match {
              case Some(qual) => endUpWithLiteral(qual)
              case _ => None
            }
          case _ => None
        }
    }

    val call: MethodInvocation => Option[String] = call => {
      val literal = call.argumentExpressions.head
      endUpWithLiteral(literal)
    }

    val visitor: ScalaRecursiveElementVisitor = new ScalaRecursiveElementVisitor {
      private val wordToFqns = Map(
        "should" -> (infix, Seq(shouldFqn, shouldFqn2)),
        "must" -> (infix, Seq(mustFqn, mustFqn2)),
        "can" -> (infix, Seq(canFqn, canFqn2)),
        "of" -> (call, Seq("org.scalatest.FlatSpec.BehaviorWord"))
      )

      override def visitReferenceExpression(ref: ScReferenceExpression): Unit =
        wordToFqns.get(ref.refName) match {
          case Some((inv, fqns)) =>
            ref.resolve() match {
              case fun: ScFunction if fun.containingClass != null && fqns.contains(fun.containingClass.qualifiedName) =>
                if (result == null) {
                  ref.getParent match {
                    case m: MethodInvocation => result = inv(m)
                    case _ => result = None
                  }
                }
              case _ =>
            }
          case _ =>
        }
    }

    while (sibling != null && result == null) {
      sibling.accept(visitor)
      sibling = sibling.getPrevSiblingNotWhitespaceComment
    }

    // if test starts with 'it' in the beginning of some TestSuite without any `behaviour of` specification
    // then the test have test class name as a scope
    if (result == null) Some("")
    else result
  }

  private def checkInfixWithIt(
    call: MethodInvocation,
    namesSet: Map[String, Set[String]],
    checkFirstArgIsUnitOrString: Boolean = false
  ) = {
    val inv: MethodInvocation => Option[String] = {
      case i: ScInfixExpr =>
        i.getBaseExpr match {
          case ref: ScReferenceExpression if ref.refName == "it" || ref.refName == "ignore" || ref.refName == "they" =>
            endUpWithIt(ref)
          case _ =>
            endUpWithLiteral(i.getBaseExpr)
        }
      case call: MethodInvocation =>
        call.getInvokedExpr match {
          case ref: ScReferenceExpression =>
            ref.qualifier match {
              case Some(ref: ScReferenceExpression) if ref.refName == "it" || ref.refName == "ignore" || ref.refName == "they" =>
                endUpWithIt(ref)
              case Some(qual) => endUpWithLiteral(qual)
              case _ => None
            }
          case _ => None
        }
    }
    checkCallGeneral(call, namesSet, inv, recursive = false, checkFirstArgIsUnitOrString)
  }

  private def checkFlatSpec(fqn: String): Option[String] = {
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

    val result = checkInfixTagged(
      PsiTreeUtil.getParentOfType(element, classOf[MethodInvocation], false),
      Map(
        "in" -> Set(itVTFqn, itVFqn, igVFqn, igVTFqn, inFqn, inTFqn, theyVFqn, theyVTFqn),
        "is" -> Set(itVTFqn, itVFqn, igVFqn, igVTFqn, resFqn, resFqn2, theyVFqn, theyVTFqn),
        "ignore" -> Set(itVFqn, itVTFqn, inFqn, inTFqn, theyVFqn, theyVTFqn)
      ),
      Set(itVFqn, igVFqn, resFqn, resFqn2, theyVFqn),
      testNameIsAlwaysEmpty = true
    )
    result match {
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
            case invocation: MethodInvocation =>
              val innerResult = checkInfixWithIt(
                invocation,
                Map(
                  "should" -> Set(shouldFqn, shouldFqn2, itFqn, igFqn, theyFqn),
                  "must" -> Set(mustFqn, mustFqn2, itFqn, igFqn, theyFqn),
                  "can" -> Set(canFqn, canFqn2, itFqn, igFqn, theyFqn)
                ),
                checkFirstArgIsUnitOrString = true
              )
              innerResult match {
                case SuccessResult(invoc, tName, middleName) =>
                  call = invoc
                  testName = (if(tName.isEmpty) "" else tName + " ") + middleName + (if (testName.isEmpty) "" else " ") + testName
                case WrongResult =>
                  return None
                case _ => call = null
              }
              call = invocation
            case _ => call = null
          }
        }
        Some(testName)
      case _ =>
        None
    }
  }

  private def checkJUnit3Suite(fqn: String): Option[String] = {
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

  private def checkAnnotatedSuite(fqn: String, annot: String): Option[String] = {
    if (!isInheritor(clazz, fqn)) return None
    var fun = PsiTreeUtil.getParentOfType(element, classOf[ScFunctionDefinition], false)
    while (fun != null) {
      if (fun.getParent.isInstanceOf[ScTemplateBody] && fun.containingClass == clazz) {
        if (fun.hasAnnotation(annot)) {
          return Some(fun.name)
        }
      }
      fun = PsiTreeUtil.getParentOfType(fun, classOf[ScFunctionDefinition], true)
    }
    None
  }

  private def checkJUnitSuite(fqn: String): Option[String] =
    checkAnnotatedSuite(fqn, "org.junit.Test")

  private def checkTestNGSuite(fqn: String): Option[String] =
    checkAnnotatedSuite(fqn, "org.testng.annotations.Test")
}