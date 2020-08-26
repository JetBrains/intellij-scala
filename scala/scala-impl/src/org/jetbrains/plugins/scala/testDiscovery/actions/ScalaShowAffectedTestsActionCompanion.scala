package org.jetbrains.plugins.scala.testDiscovery.actions

import java.util.{List => JList}

import com.intellij.openapi.util.Couple
import com.intellij.psi.util.ClassUtil
import com.intellij.psi.{PsiElement, PsiMethod}
import com.intellij.rt.coverage.testDiscovery.instrumentation.TestDiscoveryInstrumentationUtils
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScMethodLike
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScPatternDefinition, ScValueOrVariable, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.ScType

import scala.jdk.CollectionConverters._

private[testDiscovery]
object ScalaShowAffectedTestsActionCompanion {

  /** We do not want to select methods that are defined in anonymous classes or inner classes that are not "inner"
    * in terms of java. For example in this code <br>
    * `class A { class B { def foo() { class C { def bar () { val x = 42 }}}}}`<br>
    * `foo` is best method for `val x = 42` but not `bar`<br>
    * This is so because we can't guess the exact JVM methods representation of
    * anonymous classes or inner classes outside another class
    *
    * @param element element to start search from
    * @return deepest parent method which parents are inner classes in terms of java
    */
  def findBestMethod(@NotNull element: PsiElement): ScMethodLike = {
    val allMethodsFromRoot = element.parentsInFile.toSeq.filterByType[ScMethodLike].reverse
    allMethodsFromRoot
      .takeWhile(m => m.containingClass match {
        case typedef: ScTypeDefinition =>
          typedef.isTopLevel || typedef.containingClass != null
        case _ =>
          false
      })
      .lastOption.orNull
  }

  def buildRawClassesAndMethods(members: Array[ScMember]): JList[Couple[String]] = {
    buildRawClassesAndMethodsScala(members).asJava
  }

  private def buildRawClassesAndMethodsScala(members: Array[ScMember]): Seq[Couple[String]] = {
    inReadAction {
      members.flatMap {
        case m: ScMethodLike => Option(getMethodKey(m)).toSeq
        case v: ScValueOrVariable => getValOrVarKeys(v)
        case _ => Seq()
      }
    }
  }

  def getMethodKey(method: ScMethodLike): Couple[String] = {
    (for {
      clazz <- (if (method.isValid) method.containingClass else null).nullSafe
      className <- DiscoveredTestsTreeModel.getClassName(clazz).nullSafe
      signature = methodSignature(method)
    } yield Couple.of(className, signature)).orNull
  }

  private def methodSignature(method: PsiMethod): String = {
    val tail = TestDiscoveryInstrumentationUtils.SEPARATOR + ClassUtil.getAsmMethodSignature(method)
    (if (method.isConstructor) "<init>" else method.getName) + tail
  }

  private def getValOrVarKeys(element: ScValueOrVariable): collection.Seq[Couple[String]] = {
    (for {
      clazz <- (if (element.isValid) element.getContainingClass else null).nullSafe
      className <- DiscoveredTestsTreeModel.getClassName(clazz).nullSafe
    } yield {
      val methodSignatures = element match {
        case v: ScVariableDefinition =>
          val bindings = v.bindings
          bindings.flatMap(getterSignature) ++ bindings.flatMap(setterSignature)
        case v: ScPatternDefinition =>
          v.bindings.flatMap(getterSignature)
        case _ =>
          Nil
      }
      methodSignatures.map(Couple.of(className, _))
    }).getOrElse(Nil)
  }

  @inline
  private def jvmType(t: ScType): String = DebuggerUtil.getJVMStringForType(t, isParam = false)

  private def getterSignature(v: ScBindingPattern): Option[String] = {
    import TestDiscoveryInstrumentationUtils.{SEPARATOR => Sep}

    if (v.isWildcard) {
      None
    } else {
      v.expectedType.map((t: ScType) => {
        s"${v.name}$Sep()${jvmType(t)}"
      })
    }
  }

  private def setterSignature(v: ScBindingPattern): Option[String] = {
    import TestDiscoveryInstrumentationUtils.{SEPARATOR => Sep}

    if (v.isWildcard) {
      None
    } else {
      v.expectedType.map((t: ScType) => {
        s"${v.name}_$$eq$Sep(${jvmType(t)})V"
      })
    }
  }
}
