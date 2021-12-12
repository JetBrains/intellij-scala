package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import com.intellij.psi._
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.PropertyMethods.{DefinitionRole, EQ, SETTER, isApplicable, methodName}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScExtension, ScFunction, ScFunctionDefinition, ScTypeAlias, ScValue, ScValueOrVariable, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{PropertyMethods, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.{PhysicalMethodSignature, ScCompoundType, Signature, TermSignature, TypeSignature}
import org.jetbrains.plugins.scala.lang.resolve.{ResolveTargets, ScalaResolveState, StdKinds}
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.annotation.tailrec

sealed trait SignatureProcessor[T <: Signature] {
  type Sink = SignatureSink[T]

  protected def shouldSkip(t: T): Boolean

  def process(t: T, sink: Sink): Unit = {
    if (!shouldSkip(t))
      sink.put(t)
  }

  def getExportClauseProcessor(subst: ScSubstitutor, sink: Sink, place: PsiElement): PsiScopeProcessor

  protected def processJava(clazz: PsiClass, subst: ScSubstitutor, processor: Sink): Unit

  protected def processScala(template: ScTemplateDefinition, subst: ScSubstitutor, sink: Sink): Unit

  def processRefinement(cp: ScCompoundType, sink: Sink): Unit

  //noinspection ScalaWrongMethodsUsage
  protected def isStaticJava(m: PsiMember): Boolean =
    !m.isInstanceOf[ScalaPsiElement] && m.hasModifierProperty("static")

  def processPsiClass(cls: PsiClass, subst: ScSubstitutor, sink: Sink): Unit =
    processAll(cls, subst, sink)

  private def addExportedSignatures(cls: PsiClass, subst: ScSubstitutor, sink: Sink): Unit = cls match {
    case tdef: ScTypeDefinition =>
      tdef.extendsBlock.templateBody.foreach { body =>
        body.processDeclarationsFromExports(
          getExportClauseProcessor(subst, sink, body),
          ScalaResolveState.empty,
          body,
          body
        )
      }
    case _ => ()
  }

  @tailrec
  protected final def processAll(clazz: PsiClass, substitutor: ScSubstitutor, sink: Sink): Unit = clazz match {
    case null                                           => ()
    case ScEnum.Original(enum)                          => processAll(enum, substitutor, sink)
    case ScGivenDefinition.DesugaredTypeDefinition(gvn) => processAll(gvn, substitutor, sink)
    case syn: ScSyntheticClass                          => processAll(realClass(syn), substitutor, sink)
    case td: ScTemplateDefinition =>
      processScala(td, substitutor, sink)
      addExportedSignatures(clazz, substitutor, sink)
    case _ => processJava(clazz, substitutor, sink)
  }

  private def realClass(syn: ScSyntheticClass): ScTemplateDefinition =
    syn.elementScope.getCachedClass(syn.getQualifiedName)
      .filterByType[ScTemplateDefinition].orNull
}

object TypesCollector extends SignatureProcessor[TypeSignature] {

  override protected def shouldSkip(t: TypeSignature): Boolean = t.namedElement match {
    case _: ScObject                          => true
    case _: ScTypeDefinition | _: ScTypeAlias => false
    case c: PsiClass                          => isStaticJava(c)
    case _                                    => true
  }

  override def getExportClauseProcessor(
    subst: ScSubstitutor,
    sink:  TypesCollector.Sink,
    place: PsiElement
  ): PsiScopeProcessor =
    new BaseProcessor(Set(ResolveTargets.CLASS))(place) {
      override protected def execute(
        namedElement: PsiNamedElement
      )(implicit
        state: ResolveState
      ): Boolean = {
        val accesible = isAccessible(namedElement, place)

        if (accesible) {
          process(TypeSignature(namedElement, subst), sink)
        }

        true
      }
    }

  override protected def processJava(clazz: PsiClass, subst: ScSubstitutor, sink: Sink): Unit = {
    for (inner <- clazz.getInnerClasses) {
      process(TypeSignature(inner, subst), sink)
    }
  }

  override protected def processScala(template: ScTemplateDefinition, subst: ScSubstitutor, sink: Sink): Unit = {
    for (member <- template.membersWithSynthetic.filterByType[ScNamedElement]) {
      member match {
        case e: ScEnum => e.syntheticClass.foreach(cls => process(TypeSignature(cls, subst), sink))
        case gvn: ScGivenDefinition =>
          gvn.desugaredDefinitions.collect {
            case tdef: ScTypeDefinition => process(TypeSignature(tdef, subst), sink)
          }
        case _ => ()
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

sealed abstract class TermsCollector extends SignatureProcessor[TermSignature] {

  protected def relevantMembers(td: ScTemplateDefinition): Seq[ScMember]

  override protected def shouldSkip(t: TermSignature): Boolean = t.namedElement match {
    case f: ScFunction => f.isConstructor
    case m: PsiMethod  => m.isConstructor || isStaticJava(m)
    case m: PsiMember  => isStaticJava(m)
    case _             => false
  }

  override protected def processJava(clazz: PsiClass, subst: ScSubstitutor, sink: Sink): Unit = {
    for (method <- clazz.getMethods) {
      val phys = new PhysicalMethodSignature(method, subst)
      process(phys, sink)
    }

    for (field <- clazz.getFields) {
      val sig = TermSignature.withoutParams(field.getName, subst, field)
      process(sig, sink)
    }
  }

  override def getExportClauseProcessor(
    subst: ScSubstitutor,
    sink:  Sink,
    place: PsiElement
  ): PsiScopeProcessor =
    new BaseProcessor(StdKinds.stableImportSelector)(place) {
      override protected def execute(
        namedElement: PsiNamedElement
      )(implicit
        state: ResolveState
      ): Boolean = {
        val accesible = isAccessible(namedElement, place)

        if (accesible) {
          val signatures = signaturesOf(namedElement.nameContext, subst)
          signatures.foreach(process(_, sink))
        }

        true
      }
    }

  private def signaturesOf(e: PsiElement, subst: ScSubstitutor): Seq[TermSignature] = e match {
    case v: ScValueOrVariable         => v.declaredElements.flatMap(propertySignatures(_, subst))
    case constr: ScPrimaryConstructor => constr.parameters.flatMap(propertySignatures(_, subst))
    case f: ScFunction                => Seq(new PhysicalMethodSignature(f, subst))
    case o: ScObject                  => Seq(TermSignature(o, subst))
    case c: ScTypeDefinition          => syntheticSignaturesFromInnerClass(c, subst)
    case ext: ScExtension =>
      ext
        .extensionMethods
        .map(m =>
          new PhysicalMethodSignature(m, subst, extensionTypeParameters = Option(ext.typeParameters))
        )
    case _ => Seq.empty
  }

  override protected def processScala(template: ScTemplateDefinition, subst: ScSubstitutor, sink: Sink): Unit = {
    implicit val ctx: ProjectContext = template

    def addSignature(s: TermSignature): Unit = {
      process(s, sink)
    }

    if (template.qualifiedName == "scala.AnyVal") {
      addAnyValObjectMethods(template, addSignature)
    }

    for (member <- relevantMembers(template)) {
      val sigs = signaturesOf(member, subst)
      sigs.foreach(addSignature)
    }
  }

  override def processRefinement(cp: ScCompoundType, sink: Sink): Unit = {
    for ((sign, _) <- cp.signatureMap) {
      process(sign, sink)
    }
  }

  override def processPsiClass(cls: PsiClass, subst: ScSubstitutor, sink: Sink): Unit =
    MixinNodes.withSignaturesFor(cls, sink.asInstanceOf[MixinNodes.Map[TermSignature]])(
      processAll(cls, subst, sink)
    )


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
  private def propertySignatures(
    named:        ScTypedDefinition,
    subst:        ScSubstitutor,
  ): Seq[TermSignature] =
    PropertyMethods.allRoles
      .filter(isApplicable(_, named, noResolve = true))
      .map(signature(named, subst, _))

  private def signature(named: ScTypedDefinition, subst: ScSubstitutor, role: DefinitionRole): TermSignature = role match {
    case SETTER | EQ => TermSignature.setter(methodName(named.name, role), named, subst)
    case _           => TermSignature.withoutParams(methodName(named.name, role), subst, named)
  }

  private def syntheticSignaturesFromInnerClass(td: ScTypeDefinition, subst: ScSubstitutor): Seq[TermSignature] = {
    val companionSig = td.fakeCompanionModule.map(TermSignature(_, subst))

    val syntheticTerms = td match {
      case gvn: ScGivenDefinition => gvn.desugaredDefinitions.collect {
        case fn: ScFunctionDefinition => new PhysicalMethodSignature(fn, subst)
        case obj: ScObject            => TermSignature(obj, subst)
      }
      case c: ScClass if c.hasModifierProperty("implicit") =>
        c.getSyntheticImplicitMethod.toSeq.map(new PhysicalMethodSignature(_, subst))
      case _ => Seq.empty
    }

    companionSig.toList ::: syntheticTerms.toList
  }
}

object TermsCollector extends TermsCollector {
  override protected def relevantMembers(td: ScTemplateDefinition): Seq[ScMember] =
    td.membersWithSynthetic
}

object StableTermsCollector extends TermsCollector {
  override protected def relevantMembers(td: ScTemplateDefinition): Seq[ScMember] = {
    // syntheticMethods added for SCL-19477
    val members = td.members ++ td.syntheticMembers ++ td.syntheticMethods ++ td.syntheticTypeDefinitions
    val filtered = members.filter(mayContainStable)
    filtered
  }

  override protected def shouldSkip(t: TermSignature): Boolean = !isStable(t.namedElement) || super.shouldSkip(t)

  private def isStable(named: PsiNamedElement): Boolean = named match {
    case _: ScObject          => true
    case t: ScTypedDefinition => t.isStable
    case _                    => false
  }

  private def mayContainStable(m: ScMember): Boolean = m match {
    case _: ScTypeDefinition | _: ScValue | _: ScPrimaryConstructor => true
    case _: ScFunction | _: ScVariable                              => true // SCL-19477
    case _                                                          => false
  }
}