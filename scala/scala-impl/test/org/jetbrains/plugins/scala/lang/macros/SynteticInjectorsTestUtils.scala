package org.jetbrains.plugins.scala.lang.macros

import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.resolve.MethodTypeProvider._
import org.junit.Assert._

object SynteticInjectorsTestUtils {
  private val log = Logger.getInstance(getClass)

  sealed trait TDefKind
  object TDefKind {
    case object Object extends TDefKind
    case object Trait  extends TDefKind
    case object Class  extends TDefKind
  }

  sealed trait SyntheticElement {
    def validate(e: ScalaPsiElement, strict: Boolean): Unit
  }

  protected final case class SyntheticTypeDef(
    sig:       String,
    kind:      TDefKind,
    functions: Seq[SyntheticMethod]  = Seq.empty,
    inners:    Seq[SyntheticTypeDef] = Seq.empty
  ) extends SyntheticElement {
    def `with`(member: SyntheticElement): SyntheticTypeDef = member match {
      case m: SyntheticMethod     => copy(functions = functions :+ m)
      case tdef: SyntheticTypeDef => copy(inners = inners :+ tdef)
    }

    def apply(inners: SyntheticElement*): SyntheticTypeDef =
      inners.foldLeft(this)(_ `with` _)

    def validateInner(
      target: ScTypeDefinition,
      strict: Boolean
    ): Unit = {
      val targetFunctions = target.functions ++ target.syntheticMethods
      val targetInners    = target.allInnerTypeDefinitions

      if (strict && functions.size < targetFunctions.size) {
        log.error(s"${targetFunctions.size - functions.size} extra function definition(s) ins ${target.getText}.")
      } else
        functions.foreach { f =>
          val funExists = targetFunctions.exists(_.toSynthetic == f)
          assertTrue(s"Missing function definition $f in ${target.getText}", funExists)
        }

      if (strict && inners.size < targetInners.size) {
        log.error(s"${targetInners.size - inners.size} extra type definition(s) ins ${target.getText}.")
      } else
        inners.foreach { inner =>
          val maybeInner = targetInners.find(_.sig == inner.sig)
          assertTrue(s"Missing type definition $inner in ${target.getText}", maybeInner.isDefined)
          maybeInner.foreach(inner.validate(_, strict))
        }
    }

    private def checkSig(tdef: ScTypeDefinition): Unit =
      assertEquals("Type definition signature doesn't match expected", sig, tdef.sig)

    override def validate(e: ScalaPsiElement, strict: Boolean): Unit = (e, kind) match {
      case (o: ScObject, TDefKind.Object) => checkSig(o); validateInner(o, strict)
      case (c: ScClass, TDefKind.Class)   => checkSig(c); validateInner(c, strict)
      case (t: ScTrait, TDefKind.Trait)   => checkSig(t); validateInner(t, strict)
      case _                              => throw new IllegalArgumentException
    }
  }

  final case class SyntheticMethod(
    name:       String,
    tpeSig:     String,
    isImplicit: Boolean
  ) extends SyntheticElement {
    override def validate(e: ScalaPsiElement, strict: Boolean): Unit = e match {
      case f: ScFunction => assertEquals(s"Failed to validate function $f against $this", this, f.toSynthetic)
      case _             => throw new IllegalArgumentException
    }
  }

  def `object`(sig: String): SyntheticTypeDef =
    SyntheticTypeDef(sig, TDefKind.Object)

  def `class`(sig: String): SyntheticTypeDef =
    SyntheticTypeDef(sig, TDefKind.Class)

  def `trait`(sig: String): SyntheticTypeDef =
    SyntheticTypeDef(sig, TDefKind.Trait)

  def `def`(name: String, tpe: String): SyntheticMethod =
    SyntheticMethod(name, tpe, isImplicit = false)

  def `implicit`(name: String, tpe: String): SyntheticMethod =
    SyntheticMethod(name, tpe, isImplicit = true)

  implicit class SyntheticChecker(private val target: ScalaPsiElement) extends AnyVal {
    def mustBeLike(synthetic:    SyntheticElement): Unit = synthetic.validate(target, strict = false)
    def mustBeExactly(synthetic: SyntheticElement): Unit = synthetic.validate(target, strict = true)
  }

  implicit class ScTypeDefSig(private val tdef: ScTypeDefinition) extends AnyVal {
    def sig: String = {
      val supers = tdef.extendsBlock.templateParents.fold("")(" extends " + _.getText)
      val name = tdef.name
      val tparams = tdef.typeParametersClause.fold("")(_.getText)
      s"$name$tparams$supers"
    }
  }

  implicit class ScFunctionToSynthetic(private val f: ScFunction) extends AnyVal {
    def toSynthetic: SyntheticMethod =
      SyntheticMethod(f.name, f.polymorphicType().canonicalText, f.hasModifierPropertyScala("implicit"))
  }
}
