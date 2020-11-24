package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import com.intellij.psi.{PsiClass, PsiMember, PsiMethod, PsiNamedElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.{PropertyMethods, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.api.PropertyMethods.{DefinitionRole, EQ, SETTER, isApplicable, methodName}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScEnumCase, ScFunction, ScTypeAlias, ScValue, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScEnum, ScMember, ScObject, ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.{PhysicalMethodSignature, ScCompoundType, Signature, TermSignature, TypeSignature}
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.annotation.tailrec

trait SignatureProcessor[T <: Signature] {
  type Sink = SignatureSink[T]

  def shouldSkip(t: T): Boolean

  def process(t: T, sink: Sink): Unit = {
    if (!shouldSkip(t))
      sink.put(t)
  }

  def processJava(clazz: PsiClass, subst: ScSubstitutor, processor: Sink): Unit

  def processScala(template: ScTemplateDefinition, subst: ScSubstitutor, sink: Sink): Unit

  def processRefinement(cp: ScCompoundType, sink: Sink): Unit

  //noinspection ScalaWrongMethodsUsage
  protected def isStaticJava(m: PsiMember): Boolean = !m.isInstanceOf[ScalaPsiElement] && m.hasModifierProperty("static")

  @tailrec
  final def processAll(clazz: PsiClass, substitutor: ScSubstitutor, sink: Sink): Unit = clazz match {
    case null                                            => ()
    case cls: ScClass if cls.originalEnumElement != null => processAll(cls.originalEnumElement, substitutor, sink)
    case syn: ScSyntheticClass                           => processAll(realClass(syn), substitutor, sink)
    case td: ScTemplateDefinition                        => processScala(td, substitutor, sink)
    case _                                               => processJava(clazz, substitutor, sink)
  }

  private def realClass(syn: ScSyntheticClass): ScTemplateDefinition =
    syn.elementScope.getCachedClass(syn.getQualifiedName)
      .filterByType[ScTemplateDefinition].orNull
}

object TypesCollector extends SignatureProcessor[TypeSignature] {
  override def shouldSkip(t: TypeSignature): Boolean = t.namedElement match {
    case _: ScObject => true
    case _: ScTypeDefinition | _: ScTypeAlias => false
    case c: PsiClass => isStaticJava(c)
    case _ => true
  }


  override def processJava(clazz: PsiClass, subst: ScSubstitutor, sink: Sink): Unit = {
    for (inner <- clazz.getInnerClasses) {
      process(TypeSignature(inner, subst), sink)
    }
  }

  override def processScala(template: ScTemplateDefinition, subst: ScSubstitutor, sink: Sink): Unit = {
    for (member <- template.membersWithSynthetic.filterByType[ScNamedElement]) {
      member match {
        case e: ScEnum => e.syntheticClass.foreach(cls => process(TypeSignature(cls, subst), sink))
        case _         => ()
      }
      process(TypeSignature(member, subst), sink)
    }
  }

  override def processRefinement(cp: ScCompoundType, sink: Sink): Unit = {
    for ((_, aliasSig) <- cp.typesMap) {
      process(TypeSignature(aliasSig.typeAlias, aliasSig.substitutor), sink)
    }
  }
}

abstract class TermsCollector extends SignatureProcessor[TermSignature] {

  protected def relevantMembers(td: ScTemplateDefinition): Seq[ScMember]

  override def shouldSkip(t: TermSignature): Boolean = t.namedElement match {
    case f: ScFunction => f.isConstructor
    case m: PsiMethod  => m.isConstructor || isStaticJava(m)
    case m: PsiMember  => isStaticJava(m)
    case _             => false
  }

  override def processJava(clazz: PsiClass, subst: ScSubstitutor, sink: Sink): Unit = {
    for (method <- clazz.getMethods) {
      val phys = new PhysicalMethodSignature(method, subst)
      process(phys, sink)
    }

    for (field <- clazz.getFields) {
      val sig = TermSignature.withoutParams(field.getName, subst, field)
      process(sig, sink)
    }
  }

  override def processScala(template: ScTemplateDefinition, subst: ScSubstitutor, sink: Sink): Unit = {
    implicit val ctx: ProjectContext = template

    def addSignature(s: TermSignature): Unit = {
      process(s, sink)
    }

    if (template.qualifiedName == "scala.AnyVal") {
      addAnyValObjectMethods(template, addSignature)
    }

    for (member <- relevantMembers(template)) {
      member match {
        case v: ScValueOrVariable =>
          v.declaredElements
            .foreach(addPropertySignatures(_, subst, addSignature))
        case constr: ScPrimaryConstructor =>
          constr.parameters
            .foreach(addPropertySignatures(_, subst, addSignature))
        case f: ScFunction =>
          addSignature(new PhysicalMethodSignature(f, subst))
        case o: ScObject =>
          addSignature(TermSignature(o, subst))
        case c: ScTypeDefinition =>
          syntheticSignaturesFromInnerClass(c, subst)
            .foreach(addSignature)
        case _ =>
      }
    }
  }

  override def processRefinement(cp: ScCompoundType, sink: Sink): Unit = {
    for ((sign, _) <- cp.signatureMap) {
      process(sign, sink)
    }
  }

  private def addAnyValObjectMethods(template: ScTemplateDefinition, addSignature: TermSignature => Unit): Unit = {
    //some methods of java.lang.Object are available for value classes
    val javaObject = ScalaPsiManager.instance(template.projectContext)
      .getCachedClass(template.resolveScope, "java.lang.Object")

    for (obj <- javaObject; method <- obj.getMethods) {
      method.getName match {
        case "equals" | "hashCode" | "toString" =>
          addSignature(new PhysicalMethodSignature(method, ScSubstitutor.empty))
        case _ =>
      }
    }
  }

  /**
   * @param named is class parameter, or part of ScValue or ScVariable
   * */
  private def addPropertySignatures(named: ScTypedDefinition, subst: ScSubstitutor, addSignature: TermSignature => Unit): Unit = {
    PropertyMethods.allRoles
      .filter(isApplicable(_, named, noResolve = true))
      .map(signature(named, subst, _))
      .foreach(addSignature)
  }

  private def signature(named: ScTypedDefinition, subst: ScSubstitutor, role: DefinitionRole): TermSignature = role match {
    case SETTER | EQ => TermSignature.setter(methodName(named.name, role), named, subst)
    case _           => TermSignature.withoutParams(methodName(named.name, role), subst, named)
  }

  private def syntheticSignaturesFromInnerClass(td: ScTypeDefinition, subst: ScSubstitutor): Seq[TermSignature] = {
    val companionSig = td.fakeCompanionModule.map(TermSignature(_, subst))

    val implicitClassFun = td match {
      case c: ScClass if c.hasModifierProperty("implicit") =>
        c.getSyntheticImplicitMethod.map(new PhysicalMethodSignature(_, subst))
      case _ => None
    }

    companionSig.toList ::: implicitClassFun.toList
  }
}

object TermsCollector extends TermsCollector {
  override def relevantMembers(td: ScTemplateDefinition): Seq[ScMember] =
    td.membersWithSynthetic
}

object StableTermsCollector extends TermsCollector {
  override def relevantMembers(td: ScTemplateDefinition): Seq[ScMember] = {
    (td.members ++ td.syntheticMembers ++ td.syntheticTypeDefinitions)
      .filter(mayContainStable)
  }

  override def shouldSkip(t: TermSignature): Boolean = !isStable(t.namedElement) || super.shouldSkip(t)

  private def isStable(named: PsiNamedElement): Boolean = named match {
    case _: ScObject => true
    case t: ScTypedDefinition => t.isStable
    case _ => false
  }

  private def mayContainStable(m: ScMember): Boolean = m match {
    case _: ScTypeDefinition | _: ScValue | _: ScPrimaryConstructor => true
    case _ => false
  }
}