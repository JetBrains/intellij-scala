package org.jetbrains.plugins.scala.debugger.evaluation.util

import com.intellij.debugger.engine.{DebugProcess, DebugProcessImpl, JVMName, JVMNameUtil}
import com.intellij.debugger.jdi.VirtualMachineProxyImpl
import com.intellij.debugger.{DebuggerBundle, SourcePosition}
import com.intellij.lang.ASTNode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi._
import com.sun.jdi.{ReferenceType, ObjectReference, Value}
import org.jetbrains.plugins.scala.debugger.evaluation.{ScalaEvaluatorBuilderUtil, EvaluationException}
import org.jetbrains.plugins.scala.debugger.filters.ScalaDebuggerSettings
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScCaseClause}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScMethodLike, ScPrimaryConstructor, ScReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScNewTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScEarlyDefinitions, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.types.{ScSubstitutor, ScType}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
 * User: Alefas
 * Date: 19.10.11
 */
object DebuggerUtil {
  class JVMNameBuffer {
    def append(evaluator: JVMName) {
      buffer += evaluator
    }

    def append(name: Char) {
      append(Character.toString(name))
    }

    def append(text: String) {
      buffer += JVMNameUtil.getJVMRawText(text)
    }

    def toName: JVMName = {
      new JVMName {
        def getName(process: DebugProcessImpl): String = {
          if (myName == null) {
            var name: String = ""
            for (nameEvaluator <- buffer) {
              name += nameEvaluator.getName(process)
            }
            myName = name
          }
          myName
        }

        def getDisplayName(debugProcess: DebugProcessImpl): String = {
          if (myDisplayName == null) {
            var displayName: String = ""
            for (nameEvaluator <- buffer) {
              displayName += nameEvaluator.getDisplayName(debugProcess)
            }
            myDisplayName = displayName
          }
          myDisplayName
        }

        private var myName: String = null
        private var myDisplayName: String = null
      }
    }

    private var buffer = new ArrayBuffer[JVMName]
  }
  
  def getJVMQualifiedName(tp: ScType): JVMName = {
    import org.jetbrains.plugins.scala.lang.psi.types._
    tp match {
      case Any => JVMNameUtil.getJVMRawText("java.lang.Object")
      case Null => JVMNameUtil.getJVMRawText("scala.Null") //shouldn't be
      case AnyRef => JVMNameUtil.getJVMRawText("java.lang.Object") //shouldn't be
      case Nothing => JVMNameUtil.getJVMRawText("scala.Nothing") //shouldn't be
      case Singleton => JVMNameUtil.getJVMRawText("java.lang.Object")
      case AnyVal => JVMNameUtil.getJVMRawText("scala.AnyVal") //shouldn't be
      case Unit => JVMNameUtil.getJVMRawText("java.lang.Void")
      case Boolean => JVMNameUtil.getJVMRawText("java.lang.Boolean")
      case Char => JVMNameUtil.getJVMRawText("java.lang.Character")
      case Int => JVMNameUtil.getJVMRawText("java.lang.Int")
      case Long => JVMNameUtil.getJVMRawText("java.lang.Long")
      case Float => JVMNameUtil.getJVMRawText("java.lang.Float")
      case Double => JVMNameUtil.getJVMRawText("java.lang.Double")
      case Byte => JVMNameUtil.getJVMRawText("java.lang.Byte")
      case Short => JVMNameUtil.getJVMRawText("java.lang.Short")
      case JavaArrayType(arg) =>
        val buff = new JVMNameBuffer()
        buff.append(getJVMQualifiedName(arg))
        buff.append("[]")
        buff.toName
      case ScParameterizedType(arr, Seq(arg)) if ScType.extractClass(arr).exists(_.qualifiedName == "scala.Array") =>
        val buff = new JVMNameBuffer()
        buff.append(getJVMQualifiedName(arg))
        buff.append("[]")
        buff.toName
      case _ =>
        ScType.extractClass(tp) match {
          case Some(clazz) => getClassJVMName(clazz)
          case None => JVMNameUtil.getJVMRawText(ScType.canonicalText(tp))
        }
    }
  }
  
  def getJVMStringForType(tp: ScType, isParam: Boolean = true): String = {
    import org.jetbrains.plugins.scala.lang.psi.types._
    tp match {
      case AnyRef => "Ljava/lang/Object;"
      case Any => "Ljava/lang/Object;"
      case Singleton => "Ljava/lang/Object;"
      case Null => "Lscala/Null$;"
      case Nothing => "Lscala/Nothing$;"
      case Boolean => "Z"
      case Byte => "B"
      case Char => "C"
      case Short => "S"
      case Int => "I"
      case Long => "J"
      case Float => "F"
      case Double => "D"
      case Unit if isParam => "Lscala/runtime/BoxedUnit;"
      case Unit => "V"
      case JavaArrayType(arg) => "[" + getJVMStringForType(arg)
      case ScParameterizedType(ScDesignatorType(clazz: PsiClass), Seq(arg)) 
        if clazz.qualifiedName == "scala.Array" => "[" + getJVMStringForType(arg)
      case _ =>
        ScType.extractClass(tp) match {
          case Some(obj: ScObject) => "L" + obj.getQualifiedNameForDebugger.replace('.', '/') + "$;"
          case Some(obj: ScTypeDefinition) => "L" + obj.getQualifiedNameForDebugger.replace('.', '/') + ";"
          case Some(clazz) => "L" + clazz.qualifiedName.replace('.', '/') + ";"
          case _ => "Ljava/lang/Object;"
        }
    }
  }

  def getFunctionJVMSignature(function: ScMethodLike): JVMName = {
    val typeParams = function match {
      case fun: ScFunction if !fun.isConstructor => fun.typeParameters
      case _: ScFunction | _: ScPrimaryConstructor =>
        function.containingClass match {
          case td: ScTypeDefinition => td.typeParameters
          case _ => Seq.empty
        }
      case _ => Seq.empty
    }
    val subst = typeParams.foldLeft(ScSubstitutor.empty) {
      (subst, tp) => subst.bindT((tp.name, ScalaPsiUtil.getPsiElementId(tp)), tp.upperBound.getOrAny)
    }
    val localParameters = function match {
      case fun: ScFunctionDefinition if fun.isLocal => localParamsForFunDef(fun)
      case fun if fun.isConstructor =>
        fun.containingClass match {
          case c: ScClass => localParamsForConstructor(c)
          case _ => Seq.empty
        }

      case _ => Seq.empty
    }
    val parameters = function.effectiveParameterClauses.flatMap(_.effectiveParameters) ++ localParameters
    val paramTypes = parameters.map(parameterForJVMSignature(_, subst)).mkString("(", "", ")")
    val resultType = function match {
      case fun: ScFunction if !fun.isConstructor =>
        getJVMStringForType(subst.subst(fun.returnType.getOrAny), isParam = false)
      case _: ScFunction | _: ScPrimaryConstructor => "V"
    }
    JVMNameUtil.getJVMRawText(paramTypes + resultType)
  }

  def constructorSignature(named: PsiNamedElement): JVMName = {
    named match {
      case fun: ScFunction => DebuggerUtil.getFunctionJVMSignature(fun)
      case constr: ScPrimaryConstructor =>
        constr.containingClass match {
          case td: ScTypeDefinition if td.isTopLevel => DebuggerUtil.getFunctionJVMSignature(constr)
          case clazz => new JVMConstructorSignature(clazz)
        }
      case method: PsiMethod => JVMNameUtil.getJVMSignature(method)
      case clazz: ScClass if clazz.isTopLevel => clazz.constructor match {
        case Some(cnstr) => DebuggerUtil.getFunctionJVMSignature(cnstr)
        case _ => JVMNameUtil.getJVMRawText("()V")
      }
      case clazz: ScClass => new JVMConstructorSignature(clazz)
      case clazz: PsiClass => JVMNameUtil.getJVMRawText("()V")
      case _ => JVMNameUtil.getJVMRawText("()V")
    }
  }

  private def parameterForJVMSignature(param: ScTypedDefinition, subst: ScSubstitutor) = param match {
      case p: ScParameter if p.isRepeatedParameter => "Lscala/collection/Seq;"
      case _ => getJVMStringForType(subst.subst(param.getType(TypingContext.empty).getOrAny))
    }
  
  def createValue(vm: VirtualMachineProxyImpl, tp: ScType, b: Boolean): Value = {
    import org.jetbrains.plugins.scala.lang.psi.types._
    tp match {
      case Boolean => vm.mirrorOf(b)
      case Unit => vm.mirrorOf()
      case _ => null
    }
  }

  def createValue(vm: VirtualMachineProxyImpl, tp: ScType, b: Long): Value = {
    import org.jetbrains.plugins.scala.lang.psi.types._
    tp match {
      case Long => vm.mirrorOf(b)
      case Int => vm.mirrorOf(b.toInt)
      case Byte => vm.mirrorOf(b.toByte)
      case Short => vm.mirrorOf(b.toShort)
      case Char => vm.mirrorOf(b.toChar)
      case Float => vm.mirrorOf(b.toFloat)
      case Double => vm.mirrorOf(b.toDouble)
      case Unit => vm.mirrorOf()
      case _ => null
    }
  }

  def createValue(vm: VirtualMachineProxyImpl, tp: ScType, b: Char): Value = {
    import org.jetbrains.plugins.scala.lang.psi.types._
    tp match {
      case Long => vm.mirrorOf(b)
      case Int => vm.mirrorOf(b.toInt)
      case Byte => vm.mirrorOf(b.toByte)
      case Short => vm.mirrorOf(b.toShort)
      case Char => vm.mirrorOf(b.toChar)
      case Float => vm.mirrorOf(b.toFloat)
      case Double => vm.mirrorOf(b.toDouble)
      case Unit => vm.mirrorOf()
      case _ => null
    }
  }

  def createValue(vm: VirtualMachineProxyImpl, tp: ScType, b: Double): Value = {
    import org.jetbrains.plugins.scala.lang.psi.types._
    tp match {
      case Long => vm.mirrorOf(b)
      case Int => vm.mirrorOf(b.toInt)
      case Byte => vm.mirrorOf(b.toByte)
      case Short => vm.mirrorOf(b.toShort)
      case Char => vm.mirrorOf(b.toChar)
      case Float => vm.mirrorOf(b.toFloat)
      case Double => vm.mirrorOf(b.toDouble)
      case Unit => vm.mirrorOf()
      case _ => null
    }
  }

  def createValue(vm: VirtualMachineProxyImpl, tp: ScType, b: Float): Value = {
    import org.jetbrains.plugins.scala.lang.psi.types._
    tp match {
      case Long => vm.mirrorOf(b)
      case Int => vm.mirrorOf(b.toInt)
      case Byte => vm.mirrorOf(b.toByte)
      case Short => vm.mirrorOf(b.toShort)
      case Char => vm.mirrorOf(b.toChar)
      case Float => vm.mirrorOf(b.toFloat)
      case Double => vm.mirrorOf(b.toDouble)
      case Unit => vm.mirrorOf()
      case _ => null
    }
  }

  class JVMClassAt(sourcePosition: SourcePosition) extends JVMName {
    def getName(process: DebugProcessImpl): String = {
      jvmClassAtPosition(sourcePosition, process) match {
        case Some(refType) => refType.name
        case _ =>
          throw EvaluationException(DebuggerBundle.message("error.class.not.loaded", getDisplayName(process)))
      }
    }

    def getDisplayName(debugProcess: DebugProcessImpl): String = {
      ApplicationManager.getApplication.runReadAction(new Computable[String] {
        def compute: String = {
          JVMNameUtil.getSourcePositionClassDisplayName(debugProcess, sourcePosition)
        }
      })
    }
  }

  class JVMConstructorSignature(clazz: PsiClass) extends JVMName {
    val position = SourcePosition.createFromElement(clazz)

    override def getName(process: DebugProcessImpl): String = {
      jvmClassAtPosition(position, process) match {
        case Some(refType) => refType.methodsByName("<init>").get(0).signature()
        case None =>
          throw EvaluationException(DebuggerBundle.message("error.class.not.loaded", getDisplayName(process)))
      }
    }

    override def getDisplayName(debugProcess: DebugProcessImpl): String = getName(debugProcess)
  }

  def jvmClassAtPosition(sourcePosition: SourcePosition, debugProcess: DebugProcess): Option[ReferenceType] = {
    val allClasses = debugProcess.getPositionManager.getAllClasses(sourcePosition)
    if (!allClasses.isEmpty) Some(allClasses.get(0))
    else None
  }

  def withoutBackticks(name: String): String = {
    val backticked = """\$u0060(.+)\$u0060""".r
    name match {
      case null => null
      case backticked(id) => id
      case _ => name
    }
  }

  def getClassJVMName(clazz: PsiClass, withPostfix: Boolean = false): JVMName = {
    clazz match {
      case t: ScNewTemplateDefinition =>
        new JVMClassAt(SourcePosition.createFromElement(t))
      case t: ScTypeDefinition =>
        if (ScalaPsiUtil.isLocalClass(t)) {
          new JVMClassAt(SourcePosition.createFromElement(t))
        } else {
          val qual = t.getQualifiedNameForDebugger + (t match {
            case t: ScTrait if withPostfix => "$class"
            case o: ScObject if withPostfix => "$"
            case _ => ""
          })
          JVMNameUtil.getJVMRawText(qual)
        }
      case _ => JVMNameUtil.getJVMQualifiedName(clazz)
    }
  }

  def getSourcePositions(elem: PsiElement, lines: mutable.HashSet[SourcePosition] = new mutable.HashSet[SourcePosition]): Set[SourcePosition] = {
    val node = elem.getNode
    val children: Array[ASTNode] = if (node != null) node.getChildren(null) else Array.empty[ASTNode]
    if (children.isEmpty) {
      val position = SourcePosition.createFromElement(elem)
      if (lines.find(_.getLine == position.getLine) == None) {
        lines += position
      }
    }
    for (child <- children) {
      getSourcePositions(child.getPsi, lines)
    }
    lines.toSet
  }

  def unwrapScalaRuntimeObjectRef(evaluated: AnyRef): AnyRef = {
    unwrapRuntimeRef(evaluated, _ == "scala.runtime.ObjectRef")
  }

  def unwrapScalaRuntimeRef(value: AnyRef) = {
    unwrapRuntimeRef(value, isScalaRuntimeRef)
  }

  def isScalaRuntimeRef(typeFqn: String) = {
    typeFqn.startsWith("scala.runtime.") && typeFqn.endsWith("Ref")
  }

  object scalaRuntimeRefTo {
    def unapply(objRef: ObjectReference) = {
      val typeName = objRef.referenceType().name()
      if (isScalaRuntimeRef(typeName)) Some(unwrapScalaRuntimeRef(objRef))
      else None
    }
  }

  private def unwrapRuntimeRef(value: AnyRef, typeNameCondition: String => Boolean) = value match {
    case _ if !ScalaDebuggerSettings.getInstance().DONT_SHOW_RUNTIME_REFS => value
    case objRef: ObjectReference =>
      val refType = objRef.referenceType()
      if (typeNameCondition(refType.name)) {
        val elemField = refType.fieldByName("elem")
        if (elemField != null) objRef.getValue(elemField)
        else objRef
      }
      else objRef
    case _ => value
  }

  def localParamsForFunDef(fun: ScFunctionDefinition, visited: mutable.HashSet[PsiElement] = mutable.HashSet.empty): Seq[ScTypedDefinition] = {
    val container = ScalaEvaluatorBuilderUtil.getContextClass(fun)
    fun.body match { //to exclude references from default parameters
      case Some(b) => localParams(b, fun, container, visited)
      case _ => Seq.empty
    } 
  }

  def localParamsForConstructor(cl: ScClass, visited: mutable.HashSet[PsiElement] = mutable.HashSet.empty): Seq[ScTypedDefinition] = {
    val container = ScalaEvaluatorBuilderUtil.getContextClass(cl)
    val extendsBlock = cl.extendsBlock //to exclude references from default parameters
    localParams(extendsBlock, cl, container, visited)
  }

  def localParamsForDefaultParam(param: ScParameter, visited: mutable.HashSet[PsiElement] = mutable.HashSet.empty): Seq[ScTypedDefinition] = {
    val owner = param.owner
    val container = ScalaEvaluatorBuilderUtil.getContextClass {
      owner match {
        case pc: ScPrimaryConstructor => pc.containingClass
        case fun => fun
      }
    }
    param.getDefaultExpression match {
      case Some(expr) => localParams(expr, owner, container, visited)
      case None => Seq.empty
    }
  }


  def localParams(block: PsiElement, excludeContext: PsiElement, container: PsiElement,
                  visited: mutable.HashSet[PsiElement] = mutable.HashSet.empty): Seq[ScTypedDefinition] = {
    def atRightPlace(elem: PsiElement) = PsiTreeUtil.isContextAncestor(container, elem, false) &&
            !PsiTreeUtil.isContextAncestor(excludeContext, elem, false)

    val buf = new mutable.HashSet[ScTypedDefinition]
    block.accept(new ScalaRecursiveElementVisitor {
      override def visitReference(ref: ScReferenceElement) {
        if (ref.qualifier != None) {
          super.visitReference(ref)
          return
        }
        val elem = ref.resolve()
        elem match {
          case null =>
          case fun: ScFunctionDefinition if fun.isLocal && !visited.contains(fun) =>
            visited += fun
            buf ++= localParamsForFunDef(fun, visited).filter(atRightPlace)
          case fun: ScMethodLike if fun.isConstructor && !visited.contains(fun) =>
            fun.containingClass match {
              case c: ScClass if ScalaPsiUtil.isLocalClass(c) =>
                visited += c
                buf ++= localParamsForConstructor(c, visited).filter(atRightPlace)
              case _ =>
            }
          case td: ScTypedDefinition if isLocalV(td) && atRightPlace(td) =>
            buf += td
          case _ => super.visitReference(ref)
        }
      }
    })
    buf.toSeq.sortBy(e => (e.isInstanceOf[ScObject], e.getTextRange.getStartOffset))
  }

  def isLocalV(resolve: PsiElement): Boolean = {
    resolve match {
      case _: PsiLocalVariable => true
      case _: ScClassParameter => false
      case _: PsiParameter => true
      case b: ScBindingPattern =>
        ScalaPsiUtil.nameContext(b) match {
          case v @ (_: ScValue | _: ScVariable) =>
            !v.getContext.isInstanceOf[ScTemplateBody] && !v.getContext.isInstanceOf[ScEarlyDefinitions]
          case clause: ScCaseClause => true
          case _ => true //todo: for generator/enumerators
        }
      case o: ScObject =>
        !o.getContext.isInstanceOf[ScTemplateBody] && ScalaPsiUtil.getContextOfType(o, true, classOf[PsiClass]) != null
      case _ => false
    }
  }
}