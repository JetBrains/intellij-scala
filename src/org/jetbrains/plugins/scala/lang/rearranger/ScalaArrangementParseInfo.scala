package org.jetbrains.plugins.scala
package lang.rearranger

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

import scala.collection.immutable.HashSet
import scala.collection.{immutable, mutable}

/**
 * @author Roman.Shein
 * Date: 09.07.13
 */
class ScalaArrangementParseInfo {

  /**
   * All entries created from PSI tree by the moment.
   */
  private val myEntries = mutable.Buffer[ScalaArrangementEntry]()
  private val methodToEntry = mutable.HashMap[ScFunction, ScalaArrangementEntry]()
  /**
   * Anchors for method dependencies. Any method may only have one root it is connected to. When rearranging with some
   * settings entries may be moved closer to roots.
   */
  private val dependencyRoots = mutable.Buffer[ScalaArrangementDependency]()
  /**
   * Maps dependency root to set of its dependent methods.
   */
  private val methodDependencies = mutable.HashMap[ScFunction, immutable.HashSet[ScFunction]]()
  private val currentMethodDependencyRoots = mutable.HashSet[ScFunction]()
  private val currentDependentMethods = mutable.HashSet[ScFunction]()
  private var rebuildMethodDependencies = true
  private val javaPropertiesData = mutable.HashMap[(String/*property name*/, PsiElement/*PSI parent*/), ScalaPropertyInfo]()
  private val scalaPropertiesData = mutable.HashMap[(String/*property name*/, PsiElement/*PSI parent*/), ScalaPropertyInfo]()

  def onMethodEntryCreated(method: ScFunction, entry: ScalaArrangementEntry) = methodToEntry += ((method, entry))

  def addEntry(entry: ScalaArrangementEntry) = myEntries += entry

  def entries: immutable.List[ScalaArrangementEntry] = myEntries.toList

  def javaProperties = javaPropertiesData.values
  def scalaProperties = scalaPropertiesData.values

  def registerDependency(caller: ScFunction, callee: ScFunction) {
    currentMethodDependencyRoots -= callee
    if (!currentDependentMethods.contains(caller)) {
      currentMethodDependencyRoots += caller
    }
    currentDependentMethods += callee
    var callerDependent = if (!methodDependencies.contains(caller)) {
      immutable.HashSet[ScFunction]()
    } else {
      methodDependencies(caller)
    }
    if (!callerDependent.contains(callee)) {
      callerDependent = callerDependent + callee
    }
    methodDependencies += ((caller, callerDependent))
    rebuildMethodDependencies = true
  }

  def getMethodDependencyRoots = {
    if (rebuildMethodDependencies) {
      dependencyRoots.clear()
      val cache = new mutable.HashMap[ScFunction, ScalaArrangementDependency]
      for (method <- currentMethodDependencyRoots) {
        val info = buildMethodDependencyInfo(method, cache)
        if (info.isDefined) dependencyRoots += info.get
      }
      rebuildMethodDependencies = false
    }
    dependencyRoots
  }

  private def buildMethodDependencyInfo(
    method: ScFunction,
    cache: mutable.HashMap[ScFunction, ScalaArrangementDependency]
  ): Option[ScalaArrangementDependency] = {
    val entry: ScalaArrangementEntry = methodToEntry(method)
    val result: ScalaArrangementDependency = new ScalaArrangementDependency(entry)
    var toProcess: List[(ScFunction, ScalaArrangementDependency)] = List[(ScFunction, ScalaArrangementDependency)]()
    toProcess = (method, result)::toProcess
    var usedMethods = HashSet[ScFunction]()
    while (!toProcess.isEmpty) {
      val (depenenceSource, dependency) = toProcess.head
      toProcess = toProcess.tail
      methodDependencies.get(depenenceSource) match {
        case Some(dependencies) =>
          usedMethods += depenenceSource
          for (dependentMethod <- dependencies.toList) {
            if (usedMethods.contains(dependentMethod)) {
              return None
            }
            val dependentEntry = methodToEntry(dependentMethod)
            if (dependentEntry != null) {
              val dependentMethodInfo = if (cache.contains(dependentMethod)) {
                cache(dependentMethod)
              } else {
                new ScalaArrangementDependency(dependentEntry)
              }
              cache.put(dependentMethod, dependentMethodInfo)
              dependency.addDependentMethodInfo(dependentMethodInfo)
              toProcess = (dependentMethod, dependentMethodInfo) :: toProcess
            }
          }
        case None =>
      }
    }
    Some(result)
  }

  def registerJavaGetter(key: (String, PsiElement), getter: ScFunction, entry: ScalaArrangementEntry) {
    javaPropertiesData.get(key) match {
      case Some(existingData) => javaPropertiesData += (key -> new ScalaPropertyInfo(entry, existingData.setter))
      case None               => javaPropertiesData += (key -> new ScalaPropertyInfo(entry, null))
    }
  }

  def registerJavaSetter(key: (String, PsiElement), setter: ScFunction, entry: ScalaArrangementEntry) {
    javaPropertiesData.get(key) match {
      case Some(existingData) => javaPropertiesData += (key -> new ScalaPropertyInfo(existingData.getter, entry))
      case None               => javaPropertiesData += (key -> new ScalaPropertyInfo(null, entry))
    }
  }

  def registerScalaGetter(key: (String, PsiElement), getter: ScFunction, entry: ScalaArrangementEntry) {
    scalaPropertiesData.get(key) match {
      case Some(existingData) => scalaPropertiesData += (key -> new ScalaPropertyInfo(entry, existingData.setter))
      case None               => scalaPropertiesData += (key -> new ScalaPropertyInfo(entry, null))
    }
  }

  def registerScalaSetter(key: (String, PsiElement), setter: ScFunction, entry: ScalaArrangementEntry) {
    scalaPropertiesData.get(key) match {
      case Some(existingData) => scalaPropertiesData += (key -> new ScalaPropertyInfo(existingData.getter, entry))
      case None               => scalaPropertiesData += (key -> new ScalaPropertyInfo(null, entry))
    }
  }
}
