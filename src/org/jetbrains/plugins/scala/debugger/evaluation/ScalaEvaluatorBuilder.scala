package org.jetbrains.plugins.scala.debugger.evaluation

import com.intellij.debugger.SourcePosition
import evaluator._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import com.intellij.debugger.engine.evaluation._
import com.intellij.psi._
import expression._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameterClause, ScParameter, ScClassParameter}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScVariable, ScValue}
import reflect.NameTransformer
import util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import collection.mutable.{HashSet, ArrayBuffer}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import com.intellij.debugger.engine.JVMNameUtil
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScEarlyDefinitions}
import org.jetbrains.plugins.scala.lang.psi.api.{ScPackage, ScalaRecursiveElementVisitor, ScalaElementVisitor}

/**
 * User: Alefas
 * Date: 11.10.11
 */

object ScalaEvaluatorBuilder extends EvaluatorBuilder {
  //todo: possibily will be removed
  def build(text: TextWithImports, contextElement: PsiElement, position: SourcePosition): ExpressionEvaluator = null

  def build(codeFragment: PsiElement, position: SourcePosition): ExpressionEvaluator = {
    new Builder(position).buildElement(codeFragment)
  }
  
  private class Builder(position: SourcePosition) extends ScalaElementVisitor {
    private var myResult: Evaluator = null
    private var myCurrentFragmentEvaluator: CodeFragmentEvaluator = null
    private var myContextClass: PsiElement = null

    private def getContextClass: PsiElement = myContextClass

    private def isGenerateClass(elem: PsiElement): Boolean = {
      elem match {
        case clazz: PsiClass => true
        case f: ScFunctionExpr => true
        case e: ScExpression if e.getText.indexOf('_') != -1 &&
          ScUnderScoreSectionUtil.underscores(e).length > 0 => true
        case _ => false
      }
    }
    
    private def getContainingClass(elem: PsiElement): PsiElement = {
      var element = elem.getParent
      while (element != null && !isGenerateClass(element)) element = element.getParent
      if (element == null) getContextClass else element
    }

    private def getContextClass(elem: PsiElement): PsiElement = {
      var element = elem.getContext
      while (element != null && !isGenerateClass(element)) element = element.getContext
      element
    }

    override def visitFile(file: PsiFile) {
      if (!file.isInstanceOf[ScalaCodeFragment]) return
      val oldCurrentFragmentEvaluator = myCurrentFragmentEvaluator
      myCurrentFragmentEvaluator = new CodeFragmentEvaluator(oldCurrentFragmentEvaluator)
      val evaluators = new ArrayBuffer[Evaluator]()
      var child = file.getFirstChild
      while (child != null) {
        child.accept(this)
        if (myResult != null) {
          evaluators += myResult
        }
        myResult = null
        child = child.getNextSibling
      }
      if (evaluators.length > 0) {
        myCurrentFragmentEvaluator.setStatements(evaluators.toArray)
        myResult = myCurrentFragmentEvaluator
      }
      myCurrentFragmentEvaluator = oldCurrentFragmentEvaluator
    }

    private def localParams(fun: ScFunction, context: PsiElement): Seq[PsiElement] = {
      val buf = new HashSet[PsiElement]
      fun.accept(new ScalaRecursiveElementVisitor {
        override def visitReference(ref: ScReferenceElement) {
          val elem = ref.resolve()
          if (elem != null) {
            var element = elem
            while (element.getContext != null) {
              element = element.getContext
              if (element == fun) return
              else if (element == context) {
                buf += elem
                return
              }
            }
          }
        }
      })
      buf.toSeq.filter(isLocalV(_)).sortBy(e => (e.isInstanceOf[ScObject], e.getTextRange.getStartOffset))
    }

    private def paramCount(fun: ScFunction, context: PsiElement, elem: PsiElement): Int = {
      var index = localParams(fun, context).indexOf(elem)
      index = index
      if (index < 0) index = 0
      fun.effectiveParameterClauses.foldLeft(0) {
        case (i: Int, clause: ScParameterClause) => 
          i + clause.parameters.length
      } + index
    }

    private def isLocalV(resolve: PsiElement): Boolean = {
      resolve match {
        case _: PsiLocalVariable => true
        case _: ScClassParameter => false
        case _: PsiParameter => true
        case b: ScBindingPattern =>
          ScalaPsiUtil.nameContext(b) match {
            case v: ScValue =>
              !v.getContext.isInstanceOf[ScTemplateBody] && !v.getContext.isInstanceOf[ScEarlyDefinitions]
            case v: ScVariable =>
              !v.getContext.isInstanceOf[ScTemplateBody] && !v.getContext.isInstanceOf[ScEarlyDefinitions]
          }
        case o: ScObject =>
          !o.getContext.isInstanceOf[ScTemplateBody] && ScalaPsiUtil.getContextOfType(o, true, classOf[PsiClass]) != null
        case _ => false
      }
    }

    override def visitPostfixExpression(p: ScPostfixExpr) {
      val qualifier = Some(p.operand)
      val resolve = p.operation.resolve()
      visitReferenceNoParameters(qualifier, resolve, p.operation)
    }

    override def visitReferenceExpression(ref: ScReferenceExpression) {
      val qualifier: Option[ScExpression] = ref.qualifier
      val resolve: PsiElement = ref.resolve()
      visitReferenceNoParameters(qualifier, resolve, ref)
    }

    override def visitThisReference(t: ScThisReference) {
      def defaults() {
        var contextClass = getContextClass
        var iterations = 0
        while (contextClass != null && !contextClass.isInstanceOf[PsiClass]) {
          contextClass = getContextClass(contextClass)
          iterations += 1
        }
        if (contextClass == null) myResult = new ScalaThisEvaluator()
        else myResult = new ScalaThisEvaluator(iterations)
      }
      t.reference match {
        case Some(ref) if ref.resolve() != null && ref.resolve().isInstanceOf[PsiClass] =>
          val clazz = ref.resolve().asInstanceOf[PsiClass]
          var contextClass = getContextClass
          var iterations = 0
          while (contextClass != null && contextClass != clazz) {
            contextClass = getContextClass(contextClass)
            iterations += 1
          }
          if (contextClass == null) myResult = new ScalaThisEvaluator()
          else myResult = new ScalaThisEvaluator(iterations)
        case Some(ref) =>
          val refName = ref.refName
          var contextClass = getContextClass
          var iterations = 0
          while (contextClass != null && (!contextClass.isInstanceOf[PsiClass] ||
            contextClass.asInstanceOf[PsiClass].getName == null ||
            contextClass.asInstanceOf[PsiClass].getName == refName)) {
            contextClass = getContextClass(contextClass)
            iterations += 1
          }
          if (contextClass == null) defaults()
          else myResult = new ScalaThisEvaluator(iterations)
        case _ => defaults()
      }
    }

    private def isStable(o: ScObject): Boolean = {
      val context = ScalaPsiUtil.getContextOfType(o, true, classOf[PsiClass])
      if (context == null) return true
      context match {
        case o: ScObject => isStable(o)
        case _ => false
      }
    }

    private def stableObjectEvaluator(qual: String): ScalaFieldEvaluator = {
      val jvm = JVMNameUtil.getJVMRawText(qual)
      new ScalaFieldEvaluator(new TypeEvaluator(jvm), ref => ref.name() == qual, "MODULE$")
    }

    private def stableObjectEvaluator(obj: ScObject): Evaluator = {
      val qual = obj.getQualifiedNameForDebugger.split('.').map(NameTransformer.decode(_)).mkString(".") + "$"
      stableObjectEvaluator(qual)
    }

    private def thisEvaluator(elem: PsiElement): Evaluator = {
      //this reference
      val containingClass = getContextClass(elem)
      containingClass match {
        case o: ScObject if isStable(o) =>
          return stableObjectEvaluator(o)
        case _ =>
      }
      var iterationCount = 0
      var outerClass = getContextClass
      while (outerClass != null && outerClass != containingClass) {
        iterationCount += 1
        outerClass = getContextClass(outerClass)
      }
      new ScalaThisEvaluator(iterationCount)
    }

    private def visitReferenceNoParameters(qualifier: Option[ScExpression],
                                           resolve: PsiElement,
                                           ref: ScReferenceExpression) {
      val isLocalValue = isLocalV(resolve)

      def isLocalFunction(fun: ScFunction): Boolean = {
        !fun.getContext.isInstanceOf[ScTemplateBody]
      }

      def isInsideLocalFunction(elem: PsiElement): Option[ScFunction] = {
        var element = elem
        while (element != null) {
          element match {
            case fun: ScFunction if isLocalFunction(fun) && fun.parameters.find(elem == _) == None => return Some(fun)
            case _ => element = element.getContext
          }
        }
        None
      }

      def calcLocal: Boolean = {
        val labeledValue = resolve.getUserData(CodeFragmentFactoryContextWrapper.LABEL_VARIABLE_VALUE_KEY)
        if (labeledValue != null) {
          myResult = new IdentityEvaluator(labeledValue)
          return true
        }


        val isObject = resolve.isInstanceOf[ScObject]

        val namedElement = resolve.asInstanceOf[PsiNamedElement]
        val name = NameTransformer.decode(namedElement.getName) + (if (isObject) "$module" else "")
        val containingClass = getContainingClass(namedElement)

        if (getContextClass == null || getContextClass == containingClass) {
          val evaluator = new ScalaLocalVariableEvaluator(name)
          namedElement match {
            case param: ScParameter =>
              val clause = param.getContext.asInstanceOf[ScParameterClause]
              evaluator.setParameterIndex(clause.parameters.indexOf(param))
            case _ =>
          }
          myResult = evaluator
          if (isObject) {
            myResult = new ScalaFieldEvaluator(myResult, ref => true, "elem") //get from VolatileObjectReference
          }
          return true
        }

        var iterationCount = 0
        var positionClass = getContextClass
        var outerClass = getContextClass(getContextClass)
        while (outerClass != null && outerClass != containingClass) {
          iterationCount += 1
          outerClass = getContextClass(outerClass)
          positionClass = getContextClass(positionClass)
        }
        if (outerClass != null) {
          val evaluator = new ScalaThisEvaluator(iterationCount)
          val filter = ScalaFieldEvaluator.getFilter(positionClass)
          myResult = new ScalaFieldEvaluator(evaluator, filter, name)
          if (isObject) {
            //todo: calss name() method to initialize this field?
            myResult = new ScalaFieldEvaluator(myResult, ref => true, "elem") //get from VolatileObjectReference
          }
          return true
        }
        throw EvaluateExceptionUtil.createEvaluateException("Cannot load local variable from anonymous class")
      }

      if (isLocalValue && isInsideLocalFunction(ref) == None) {
        calcLocal
        return
      } else if (isLocalValue) {
        val fun = isInsideLocalFunction(ref).get
        val contextClass = getContextClass(fun)
        if (PsiTreeUtil.isContextAncestor(contextClass, resolve, true)) {
          val pCount = paramCount(fun, contextClass, resolve)
          val context = getContextClass
          if (context != contextClass) {
            calcLocal
            return
          } else {
            val name = NameTransformer.decode(resolve.asInstanceOf[PsiNamedElement].getName)
            val evaluator = new ScalaLocalVariableEvaluator(name, true)
            //it's simple, let's take parameter
            evaluator.setParameterIndex(pCount)
            myResult = evaluator
            return
          }
        } else {
          calcLocal
          return
        }
      } else if (resolve.isInstanceOf[ScFunction] && !resolve.getContext.isInstanceOf[ScTemplateBody]) {
        //local method
        val fun = resolve.asInstanceOf[ScFunction]
        val name = NameTransformer.decode(fun.name)
        val containingClass = getContainingClass(fun)
        if (getContextClass == null) {
          throw EvaluateExceptionUtil.createEvaluateException("Cannot evaluate local method")
        }
        var iterationCount = 0
        var outerClass = getContextClass
        while (outerClass != null && outerClass != containingClass) {
          iterationCount += 1
          outerClass = getContextClass(outerClass)
        }
        if (outerClass != null) {
          val evaluator = new ScalaThisEvaluator(iterationCount)
          if (fun.effectiveParameterClauses.lastOption.map(_.isImplicit).getOrElse(false)) {
            //todo: find and pass implicit parameters
            throw EvaluateExceptionUtil.createEvaluateException("passing implicit parameters is not supported")
          }
          val args = localParams(fun, getContextClass(fun))
          val evaluators = args.map(arg => {
            val name = arg.asInstanceOf[PsiNamedElement].getName
            val ref = ScalaPsiElementFactory.createExpressionWithContextFromText(name, position.getElementAt,
              position.getElementAt).asInstanceOf[ScReferenceExpression]
            val builder = new Builder(position)
            builder.visitReferenceExpression(ref)
            var res = builder.myResult
            if (arg.isInstanceOf[ScObject]) {
              val qual = "scala.runtime.VolatileObjectRef"
              val typeEvaluator = new TypeEvaluator(JVMNameUtil.getJVMRawText(qual))
              res = new ScalaNewClassInstanceEvaluator(typeEvaluator,
                JVMNameUtil.getJVMRawText("(Ljava/lang/Object;)V"), Array(res))
            }
            res
          })
          val methodEvaluator = new ScalaMethodEvaluator(
            evaluator, name, null /* todo? */, evaluators, true
          )
          myResult = methodEvaluator
          return
        }
        throw EvaluateExceptionUtil.createEvaluateException("Cannot evaluate local method")
      } else if (resolve.isInstanceOf[ScObject]) {
        val obj = resolve.asInstanceOf[ScObject]
        //here we have few possibilities
        //1. top level object
        if (isStable(obj)) {
          myResult = stableObjectEvaluator(obj)
          return
        }
        //2. object on reference
        //3. object on implicit reference
        qualifier match {
          case Some(qual) =>
            ref.bind() match {
              case Some(r: ScalaResolveResult) =>
                r.implicitFunction match {
                  case Some(fun) =>
                    //todo:
                    throw EvaluateExceptionUtil.createEvaluateException("Implicit function conversions is not supported")
                  case _ =>
                    qual.accept(this)
                    val name = NameTransformer.decode(obj.getName)
                    myResult = new ScalaMethodEvaluator(myResult, name, null /* todo? */, Seq.empty, false)
                    return
                }
              case _ => //resolve not null => shouldn't be
            }
          case None => 
            ref.bind() match {
              case Some(r: ScalaResolveResult) =>
                if (!r.importsUsed.isEmpty) {
                  //todo:
                  throw EvaluateExceptionUtil.createEvaluateException("Evaluation of imported objects is not supported")
                } else {
                  val evaluator = thisEvaluator(obj)
                  val name = NameTransformer.decode(obj.getName)
                  myResult = new ScalaMethodEvaluator(evaluator, name, null /* todo? */, Seq.empty, false)
                  return
                }
              case _ => //resolve not null => shouldn't be
            }
        }
        throw EvaluateExceptionUtil.createEvaluateException("Cannot evaluate object")
      } else if (resolve.isInstanceOf[ScFunction]) {
        val fun = resolve.asInstanceOf[ScFunction]
        if (fun.effectiveParameterClauses.lastOption.map(_.isImplicit).getOrElse(false)) {
          //todo: find and pass implicit parameters
          throw EvaluateExceptionUtil.createEvaluateException("passing implicit parameters is not supported")
        }

        qualifier match {
          case Some(qual) =>
            ref.bind() match {
              case Some(r: ScalaResolveResult) =>
                r.implicitFunction match {
                  case Some(fun) =>
                    //todo:
                    throw EvaluateExceptionUtil.createEvaluateException("Implicit function conversions is not supported")
                  case _ =>
                    qual.accept(this)
                    val name = NameTransformer.decode(fun.name)
                    myResult = new ScalaMethodEvaluator(myResult, name, null /* todo? */, Seq.empty, false)
                    return
                }
              case _ => //resolve not null => shouldn't be
            }
          case None =>
            ref.bind() match {
              case Some(r: ScalaResolveResult) =>
                if (!r.importsUsed.isEmpty) {
                  //todo:
                  throw EvaluateExceptionUtil.createEvaluateException("Evaluation of imported functions is not supported")
                } else {
                  val evaluator = thisEvaluator(fun)
                  val name = NameTransformer.decode(fun.getName)
                  myResult = new ScalaMethodEvaluator(evaluator, name, null /* todo? */, Seq.empty, false)
                  return
                }
              case _ => //resolve not null => shouldn't be
            }
        }
        throw EvaluateExceptionUtil.createEvaluateException("Cannot evaluate method")
      } else if (resolve.isInstanceOf[PsiMethod]) {
        val method = resolve.asInstanceOf[PsiMethod]
        qualifier match {
          case Some(qual) =>
            if (method.hasModifierProperty("static")) {
              val eval = 
                new TypeEvaluator(JVMNameUtil.getContextClassJVMQualifiedName(SourcePosition.createFromElement(method)))
              val name = method.getName
              myResult = new ScalaMethodEvaluator(eval, name, null /* todo? */, Seq.empty, false)
              return
            } else {
              ref.bind() match {
                case Some(r: ScalaResolveResult) =>
                  r.implicitFunction match {
                    case Some(fun) =>
                      //todo:
                      throw EvaluateExceptionUtil.createEvaluateException("Implicit function conversions is not supported")
                    case _ =>
                      qual.accept(this)
                      val name = method.getName
                      myResult = new ScalaMethodEvaluator(myResult, name, null /* todo? */, Seq.empty, false)
                      return
                  }
                case _ => //resolve not null => shouldn't be
              }
            }
          case None =>
            ref.bind() match {
              case Some(r: ScalaResolveResult) =>
                if (!r.importsUsed.isEmpty) {
                  //todo:
                  throw EvaluateExceptionUtil.createEvaluateException("Evaluation of imported functions is not supported")
                } else {
                  val evaluator = thisEvaluator(method)
                  val name = method.getName
                  myResult = new ScalaMethodEvaluator(evaluator, name, null /* todo? */, Seq.empty, false)
                  return
                }
              case _ => //resolve not null => shouldn't be
            }
        }
        throw EvaluateExceptionUtil.createEvaluateException("Cannot evaluate method")
      } else if (resolve.isInstanceOf[ScClassParameter] || resolve.isInstanceOf[ScBindingPattern]) {
        //this is scala "field"
        val named = resolve.asInstanceOf[ScNamedElement]
        qualifier match {
          case Some(qual) =>
            ref.bind() match {
              case Some(r: ScalaResolveResult) =>
                r.implicitFunction match {
                  case Some(fun) =>
                    //todo:
                    throw EvaluateExceptionUtil.createEvaluateException("Implicit function conversions is not supported")
                  case _ =>
                    qual.accept(this)
                    val name = NameTransformer.decode(named.name)
                    myResult = new ScalaMethodEvaluator(myResult, name, null /* todo */, Seq.empty, false)
                    return
                }
              case None =>
            }
          case None =>
            ref.bind() match {
              case Some(r: ScalaResolveResult) =>
                if (!r.importsUsed.isEmpty) {
                  //todo:
                  throw EvaluateExceptionUtil.createEvaluateException("Evaluation of imported fields is not supported")
                } else {
                  val evaluator = thisEvaluator(named)
                  val name = NameTransformer.decode(named.getName)
                  myResult = new ScalaMethodEvaluator(evaluator, name, null/* todo */, Seq.empty, false)
                  return
                }
              case None =>
            }
        }

        throw EvaluateExceptionUtil.createEvaluateException("Cannot evaluate value")
      } else if (resolve.isInstanceOf[PsiField]) {
        val field = resolve.asInstanceOf[PsiField]
        qualifier match {
          case Some(qual) =>
            if (field.hasModifierProperty("static")) {
              val eval =
                new TypeEvaluator(JVMNameUtil.getContextClassJVMQualifiedName(SourcePosition.createFromElement(field)))
              val name = field.getName
              myResult = new ScalaFieldEvaluator(eval, ref => true,name)
              return
            } else {
              ref.bind() match {
                case Some(r: ScalaResolveResult) =>
                  r.implicitFunction match {
                    case Some(fun) =>
                      //todo:
                      throw EvaluateExceptionUtil.createEvaluateException("Implicit function conversions is not supported")
                    case _ =>
                      qual.accept(this)
                      val name = field.getName
                      myResult = new ScalaFieldEvaluator(myResult,
                        ScalaFieldEvaluator.getFilter(getContainingClass(field)), name)
                      return
                  }
                case _ => //resolve not null => shouldn't be
              }
            }
          case None =>
            ref.bind() match {
              case Some(r: ScalaResolveResult) =>
                if (!r.importsUsed.isEmpty) {
                  //todo:
                  throw EvaluateExceptionUtil.createEvaluateException("Evaluation of imported fileds is not supported")
                } else {
                  val evaluator = thisEvaluator(field)
                  val name = field.getName
                  myResult = new ScalaFieldEvaluator(evaluator,
                    ScalaFieldEvaluator.getFilter(getContainingClass(field)), name)
                  return
                }
              case _ => //resolve not null => shouldn't be
            }
        }
      } else if (resolve.isInstanceOf[ScPackage]) {
        val pack = resolve.asInstanceOf[ScPackage]
        //let's try to find package object:
        val qual = (pack.getQualifiedName + ".package$").split('.').map(NameTransformer.decode(_)).mkString(".")
        myResult = stableObjectEvaluator(qual)
      } else {
        //unresolved symbol => try to resolve it dynamically
        val name = NameTransformer.decode(ref.refName)
        qualifier match {
          case Some(qual) =>
            qual.accept(this)
            if (myResult == null) {
              throw EvaluateExceptionUtil.createEvaluateException("Cannot evaluate unresolved reference expression")
            }
            myResult = new ScalaFieldEvaluator(myResult, ref => true, name) //todo: look for method?
            return
          case None =>
            myResult = new ScalaLocalVariableEvaluator(name, false) //todo: look for method?
            return
        }
      }
    }

    def buildElement(element: PsiElement): ExpressionEvaluator = {
      assert(element.isValid)
      myContextClass = getContextClass(element)
      try {
        element.accept(this)
      } catch {
        case e: EvaluateRuntimeException => throw e.getCause
      }
      if (myResult == null) {
        throw EvaluateExceptionUtil.createEvaluateException("Invalid evaluation expression")
      }
      new ExpressionEvaluatorImpl(myResult)
    }
  }
}