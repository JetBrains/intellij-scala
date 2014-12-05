package org.jetbrains.plugins.scala.debugger

import com.intellij.debugger.engine.SyntheticTypeComponentProvider
import com.sun.jdi.{Method, ReferenceType, TypeComponent}
import org.jetbrains.plugins.scala.decompiler.{DecompilerUtil, ConstantPoolParser}

/**
 * Nikolay.Tropin
 * 2014-12-03
 */
class ScalaSyntheticProvider extends SyntheticTypeComponentProvider {
  override def isSynthetic(typeComponent: TypeComponent): Boolean = {
    typeComponent match {
      case m: Method if m.isConstructor && isAnonFun(m.declaringType()) => true
      case m: Method if isSpecialization(m) => true
      case m: Method if isTraitForwarder(m) => true
      case m: Method if isDefaultArg(m) => true
      case _ => false
    }
  }

  private def isAnonFun(refType: ReferenceType): Boolean = {
    short(refType.name).contains("$anonfun")
  }

  private def isSpecialization(method: Method): Boolean = {
    method.name.contains("$mc") && method.name.endsWith("$sp")
  }

  private def short(name: String) = {
    name.substring(name.lastIndexOf('.') + 1)
  }

  private def isDefaultArg(m: Method): Boolean = {
    m.name.contains("$default$")
  }

  private def isTraitForwarder(m: Method): Boolean = {
    try {
      doesInvokeStatic(m) match {
        case None => false
        case Some(index) =>
          val clazz = m.declaringType()
          val cpCount = clazz.constantPoolCount()
          val constPoolParser = new ConstantPoolParser(cpCount, clazz.constantPool())
          val methodInfo = constPoolParser.readMethodInfo(index)
          if (methodInfo.className.endsWith("$class") && methodInfo.methodName == m.name) true
          else false
      }
    }
    catch {
      case e: Exception => false
    }
  }

  private def doesInvokeStatic(m: Method): Option[Int] = {
    val bytecodes = m.bytecodes()
    var i = 0
    while (i < bytecodes.length) {
      val instr = bytecodes(i)
      if (DecompilerUtil.loadWithIndexInstructions.contains(instr)) i += 2
      else if (DecompilerUtil.loadWithoutIndexInstructions.contains(instr)) i += 1
      else if (instr == DecompilerUtil.invokeStaticInstruction) {
        val index = (bytecodes(i + 1) & 255) << 8 | bytecodes(i + 2) & 255
        val nextInstr = bytecodes(i + 3)
        if (DecompilerUtil.returnInstructions.contains(nextInstr)) return Some(index)
        else return None
      }
      else return None
    }
    None
  }
}