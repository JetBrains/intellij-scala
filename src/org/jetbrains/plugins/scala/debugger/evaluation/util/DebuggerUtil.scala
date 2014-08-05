package org.jetbrains.plugins.scala.debugger.evaluation.util

import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil
import com.intellij.debugger.engine.{DebugProcessImpl, JVMName, JVMNameUtil}
import com.intellij.debugger.jdi.VirtualMachineProxyImpl
import com.intellij.debugger.{DebuggerBundle, SourcePosition}
import com.intellij.lang.ASTNode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiElement, PsiLocalVariable, PsiParameter}
import com.sun.jdi.{ObjectReference, Value}
import org.jetbrains.plugins.scala.extensions.toPsiClassExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScCaseClause}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScMethodLike, ScPrimaryConstructor, ScReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition, ScTrait, ScTypeDefinition}
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
      case fun: ScFunctionDefinition if fun.isLocal =>
        localParams(fun, PsiTreeUtil.getParentOfType(fun, classOf[ScTemplateDefinition]))
      case _ => Seq.empty
    }
    val parameters = function.effectiveParameterClauses.flatMap(_.parameters) ++ localParameters
    val paramTypes = parameters.map(parameterForJVMSignature(_, subst)).mkString("(", "", ")")
    val resultType = function match {
      case fun: ScFunction if !fun.isConstructor =>
        getJVMStringForType(subst.subst(fun.returnType.getOrAny), isParam = false)
      case _: ScFunction | _: ScPrimaryConstructor => "V"
    }
    JVMNameUtil.getJVMRawText(paramTypes + resultType)
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
      val allClasses = process.getPositionManager.getAllClasses(mySourcePosition)
      if (!allClasses.isEmpty) {
        return allClasses.get(0).name
      }
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("error.class.not.loaded", getDisplayName(process)))
    }

    def getDisplayName(debugProcess: DebugProcessImpl): String = {
      ApplicationManager.getApplication.runReadAction(new Computable[String] {
        def compute: String = {
          JVMNameUtil.getSourcePositionClassDisplayName(debugProcess, mySourcePosition)
        }
      })
    }

    private final val mySourcePosition: SourcePosition = null
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

  def unwrapScalaRuntimeObjectRef(evaluated: AnyRef): AnyRef = evaluated match {
    case objRef: ObjectReference => {
      val refType = objRef.referenceType()
      if (refType.name == "scala.runtime.ObjectRef") {
        val elemField = refType.fieldByName("elem")
        if (elemField != null) objRef.getValue(elemField)
        else objRef
      }
      else objRef
    }
    case _ => evaluated
  }

  def localParams(fun: ScFunctionDefinition, context: PsiElement): Seq[ScTypedDefinition] = {
    val buf = new mutable.HashSet[PsiElement]
    val body = fun.body //to exclude references from default parameters
    body.foreach(_.accept(new ScalaRecursiveElementVisitor {
      override def visitReference(ref: ScReferenceElement) {
        if (ref.qualifier != None) {
          super.visitReference(ref)
          return
        }
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
        super.visitReference(ref)
      }
    }))
    buf.toSeq.collect {case td: ScTypedDefinition if isLocalV(td) => td}
            .sortBy(e => (e.isInstanceOf[ScObject], e.getTextRange.getStartOffset))
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