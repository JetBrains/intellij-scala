package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}

class MetaSupportInjector extends SyntheticMembersInjector {
  /**
    * This method allows to add custom functions to any class, object or trait.
    * This includes synthetic companion object.
    *
    * Context for this method will be class. So inner types and imports of this class
    * will not be available. But you can use anything outside of it.
    *
    * Injected method will not participate in class overriding hierarchy unless this method
    * is marked with override modifier. Use it carefully, only when this behaviour is intended.
    *
    * @param source class to inject functions
    * @return sequence of functions text
    */
  override def injectFunctions(source: ScTypeDefinition): Seq[String] = {
    source match {
      case po: ScObjectImpl if po.isPackageObject && po.qualifiedName == "scala.meta" =>
        Seq("def meta(f: =>scala.meta.Tree): scala.meta.Tree = ???")
      case _ => Seq.empty
    }
  }

  /**
    * Use this method to mark class or trait, that it requires companion object.
    * Note that object as source is not possible.
    *
    * @param source class or trait
    * @return if this source requires companion object
    */
  override def needsCompanionObject(source: ScTypeDefinition): Boolean = {
    ScalaPsiUtil.getMetaCompanionObject(source).isDefined
  }

  override def injectMembers(source: ScTypeDefinition): Seq[String] = {
    source match {
      case obj: ScObject => obj.fakeCompanionClassOrCompanionClass match {
        case ah: ScAnnotationsHolder =>
          ScalaPsiUtil.getMetaCompanionObject(ah).map(_.members.map(_.getText)).getOrElse(Seq.empty)
        case _ => Seq.empty
      }
      case _ => Seq.empty
    }
  }
}
