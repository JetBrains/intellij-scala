package org.jetbrains.plugins.scala.debugger

import com.intellij.debugger.engine.SyntheticTypeComponentProvider
import com.intellij.util.containers.ContainerUtil
import com.sun.jdi._
import org.jetbrains.plugins.scala.debugger.ScalaPositionManager.isAnonfunType
import org.jetbrains.plugins.scala.debugger.TopLevelMembers.isSyntheticClassForTopLevelMembers
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil

import java.util.concurrent.ConcurrentMap
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.util.Try

class ScalaSyntheticProvider extends SyntheticTypeComponentProvider {
  override def isSynthetic(typeComponent: TypeComponent): Boolean = ScalaSyntheticProvider.isSynthetic(typeComponent)

  override def isNotSynthetic(typeComponent: TypeComponent): Boolean = typeComponent match {
    case m: Method => ScalaPositionManager.isIndyLambda(m)
    case _ => false
  }
}

object ScalaSyntheticProvider {
  private val cache: ConcurrentMap[TypeComponent, java.lang.Boolean] = ContainerUtil.createConcurrentWeakMap()

  def isSynthetic(typeComponent: TypeComponent): Boolean = {
    if (typeComponent == null) return true

    val fromCache = cache.get(typeComponent)
    if (fromCache != null) return fromCache

    val isScala = DebuggerUtil.isScala(typeComponent.declaringType(), default = false)
    if (!isScala) return false

    val result = typeComponent match {
      case _ if hasSpecialization(typeComponent) && !isMacroDefined(typeComponent) => true
      case m: Method if m.isBridge => true
      case m: Method if isSyntheticConstructor(m) => true
      case m: Method if isDefaultArg(m) => true
      case m: Method if isForwarder(m) => true
      case m: Method if m.name().endsWith("$adapted") => true
      case m: Method if ScalaPositionManager.isIndyLambda(m) => false
      case m: Method if isAccessorInDelayedInit(m) => true
      case f: Field if f.name().startsWith("bitmap$") => true
      case _ =>
        val machine: VirtualMachine = typeComponent.virtualMachine
        machine != null && machine.canGetSyntheticAttribute && typeComponent.isSynthetic
    }
    cache.putIfAbsent(typeComponent, result)
    result
  }

  def unspecializedName(s: String): Option[String] = """.*(?=\$mc\w+\$sp)""".r.findFirstIn(s)

  def hasSpecialization(tc: TypeComponent, refType: Option[ReferenceType] = None): Boolean = {
    val referenceType = refType.getOrElse(tc.declaringType())
    val name = tc.name()
    def checkName(cand: TypeComponent) = unspecializedName(cand.name()).contains(name)
    def checkSignature(cand: TypeComponent) = tc.signature() == cand.signature()

    tc match {
      case _: Method => referenceType.methods().asScala.exists(c => checkName(c) && checkSignature(c))
      case _: Field => referenceType.allFields().asScala.exists(checkName)
      case _ => false
    }
  }

  private def isSyntheticConstructor(m: Method): Boolean =
    (m.isConstructor || m.isStaticInitializer) &&
      (isAnonfunType(m.declaringType()) || isSyntheticClassForTopLevelMembers(m.declaringType()))

  private val defaultArgPattern = """\$default\$\d+""".r

  private def isDefaultArg(m: Method): Boolean = {
    val methodName = m.name()
    if (!methodName.contains("$default$")) false
    else {
      val lastDefault = defaultArgPattern.findAllMatchIn(methodName).toSeq.lastOption
      lastDefault.map(_.matched) match {
        case Some(s) if methodName.endsWith(s) =>
          val origMethodName = methodName.stripSuffix(s)
          val refType = m.declaringType
          !refType.methodsByName(origMethodName).isEmpty
        case _ => false
      }
    }
  }

  private def isForwarder(m: Method): Boolean = {
    //trait forwarders are usually generated with line number of the containing class
    def looksLikeForwarderLocation: Boolean = {
      val line = m.location().lineNumber()
      if (line < 0) return false

      val methods = m.declaringType().methods()
      val lines =
        methods
          .asScala
          .flatMap(m => Option(m.location()))
          .map(_.lineNumber())
          .filter(_ >= 0)

      lines.nonEmpty && line <= lines.min
    }

    Try(invokesSingleMethod(m) && (isTraitForwarder(m) || isSAMImplementation(m)) && looksLikeForwarderLocation)
      .getOrElse(false)
  }

  def isMacroDefined(typeComponent: TypeComponent): Boolean = {
    val typeName = typeComponent.declaringType().name()
    typeName.contains("$macro") || typeName.contains("$stateMachine$")
  }

  private def invokesSingleMethod(m: Method): Boolean = {
    import LocationLineManager._

    val bytecodes: Array[Byte] =
      try m.bytecodes()
      catch {case _: Throwable => return false}

    var staticFieldLoaded = false
    var i = 0
    while (i < bytecodes.length) {
      val instr = bytecodes(i)
      if (twoBytesLoadCodes.contains(instr)) i += 2
      else if (oneByteLoadCodes.contains(instr)) i += 1
      else if (instr == getStatic && !staticFieldLoaded) {
        i += 3
        staticFieldLoaded = true
      }
      else if (instr == invokeStatic || instr == invokeVirtual || instr == invokeSpecial) {
        val nextIdx = i + 3
        val nextInstr = bytecodes(nextIdx)
        return nextIdx == (bytecodes.length - 1) && returnCodes.contains(nextInstr)
      }
      else return false
    }
    false
  }

  private def isTraitForwarder(m: Method): Boolean = hasTraitWithImplementation(m) || isScala3TraitForwarder(m)

  private def isScala3TraitForwarder(m: Method): Boolean = m.declaringType() match {
    case it: InterfaceType =>
      m.isStatic && m.name.endsWith("$") && it.methods().asScala.exists(_.name() == m.name.stripSuffix("$"))
    case _ => false
  }

  private def hasTraitWithImplementation(m: Method): Boolean = {
    m.declaringType() match {
      case it: InterfaceType =>
        val implMethods = it.methodsByName(m.name + "$")
        if (implMethods.isEmpty) false
        else {
          val typeNames = m.argumentTypeNames().asScala
          val argCount = typeNames.size
          implMethods.asScala.exists { impl =>
            val implTypeNames = impl.argumentTypeNames().asScala
            val implArgCount = implTypeNames.size
            implArgCount == argCount + 1 && implTypeNames.tail == typeNames ||
              implArgCount == argCount && implTypeNames == typeNames
          }
        }
      case ct: ClassType =>
        val interfaces = ct.allInterfaces().asScala
        val vm = ct.virtualMachine()
        val allTraitImpls = vm.allClasses().asScala.filter(_.name().endsWith("$class"))
        for {
          interface <- interfaces
          traitImpl <- allTraitImpls
          if traitImpl.name().stripSuffix("$class") == interface.name() && !traitImpl.methodsByName(m.name).isEmpty
        } {
          return true
        }
        false
      case _ => false
    }
  }

  private def isSAMImplementation(m: Method): Boolean = {
    m.declaringType() match {
      case ct: ClassType =>
        ct.superclass() match {
          case sp: ReferenceType =>
            sp.methods().asScala.filter(_.isAbstract) match {
              case mutable.Buffer(sam) => sam.name == m.name
              case _ => false
            }
          case _ => false
        }
      case _ => false
    }
  }

  private def isAccessorInDelayedInit(m: Method): Boolean = {
    val simpleName = m.name.stripSuffix("_$eq")
    m.declaringType() match {
      case ct: ClassType if ct.fieldByName(simpleName) != null =>
        ct.allInterfaces().asScala.exists(_.name() == "scala.DelayedInit")
      case _ => false
    }
  }
}
