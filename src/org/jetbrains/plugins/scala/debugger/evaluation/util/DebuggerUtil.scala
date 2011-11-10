package org.jetbrains.plugins.scala.debugger.evaluation.util

import com.intellij.debugger.jdi.VirtualMachineProxyImpl
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil
import com.intellij.debugger.{DebuggerBundle, SourcePosition}
import com.intellij.openapi.application.ApplicationManager
import com.sun.jdi.Value
import com.intellij.debugger.engine.{DebugProcessImpl, JVMNameUtil, JVMName}
import com.intellij.openapi.util.Computable
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTrait, ScTypeDefinition}
import com.intellij.psi.{PsiElement, PsiClass}
import com.intellij.lang.ASTNode
import collection.mutable.{ArrayBuffer, HashSet}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.types.{ScTypeParameterType, ScSubstitutor, ScType}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext

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
      case f@ScFunctionType(returnType, params) =>
        f.resolveFunctionTrait match {
          case Some(p) => getJVMQualifiedName(p)
          case None =>
            JVMNameUtil.getJVMRawText("scala.Function" + params.length)
        }
      case t@ScTupleType(components) =>
        t.resolveTupleTrait match {
          case Some(t) => getJVMQualifiedName(t)
          case None => JVMNameUtil.getJVMRawText("scala.Tuple" + components.length)
        }
      case JavaArrayType(arg) =>
        val buff = new JVMNameBuffer()
        buff.append(getJVMQualifiedName(arg))
        buff.append("[]")
        buff.toName
      case ScParameterizedType(arr, Seq(arg)) if ScType.extractClass(arr).map(_.getQualifiedName == "scala.Array").
        getOrElse(false) =>
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
  
  def getJVMStringForType(tp: ScType): String = {
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
      case Unit => "V"
      case JavaArrayType(arg) => "[" + getJVMStringForType(arg)
      case ScParameterizedType(ScDesignatorType(clazz: PsiClass), Seq(arg)) 
        if clazz.getQualifiedName == "scala.Array" => "[" + getJVMStringForType(arg)
      case _ =>
        ScType.extractClass(tp) match {
          case Some(obj: ScObject) => "L" + obj.getQualifiedNameForDebugger.replace('.', '/') + "$;"
          case Some(obj: ScTypeDefinition) => "L" + obj.getQualifiedNameForDebugger.replace('.', '/') + ";"
          case Some(clazz) => "L" + clazz.getQualifiedName.replace('.', '/') + ";"
          case _ => "Ljava/lang/Object;"
        }
    }
  }

  def getFunctionJVMSignature(function: ScFunction): JVMName = {
    val subst = function.typeParameters.foldLeft(ScSubstitutor.empty) {
      (subst, tp) => subst.bindT((tp.getName, ScalaPsiUtil.getPsiElementId(tp)), tp.upperBound.getOrAny)
    }
    val sign = function.effectiveParameterClauses.flatMap(_.parameters).map(param => 
      getJVMStringForType(subst.subst(param.getType(TypingContext.empty).getOrAny))).mkString("(", ",", ")") +
      getJVMStringForType(subst.subst(function.returnType.getOrAny))
    JVMNameUtil.getJVMRawText(sign)
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

  def getSourcePositions(elem: PsiElement, lines: HashSet[SourcePosition] = new HashSet[SourcePosition]): Set[SourcePosition] = {
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
}