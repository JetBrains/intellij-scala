package org.jetbrains.plugins.scala.externalLibraries.estaticoNewtype

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.SyntheticMembersInjector
import org.jetbrains.plugins.scala.lang.psi.types.api.ParameterizedType

class NewTypeInjector extends SyntheticMembersInjector {

  import NewTypeInjector._

  override def injectFunctions(source: ScTypeDefinition): Seq[String] = {
    val companionClass = source match {
      case obj: ScObject => obj.fakeCompanionClassOrCompanionClass
      case _ => null
    }

    companionClass match {
      case clazz: ScClass if isNewType(clazz) =>
        mkCompanionMembers(clazz)
      case _ => Nil
    }
  }
}

object NewTypeInjector {

  private def isNewType(clazz: ScClass): Boolean = {
    clazz.findAnnotationNoAliases("_root_.io.estatico.newtype.macros.newtype") != null ||
      clazz.findAnnotationNoAliases("_root_.io.estatico.newtype.macros.newsubtype") != null
  }

  private def mkCompanionMembers(clazz: ScClass): Seq[String] = clazz.parameters.headOption.flatMap(_.`type`().toOption).map { reprType =>
    val (reprName, hasDifferentShape) = reprType match {
      case ParameterizedType(des, targs) => des.canonicalText -> (targs.map(_.canonicalText) != clazz.typeParameters.map(_.name))
      case t => t.canonicalText -> clazz.typeParameters.nonEmpty
    }

    val fullReprName = reprType.canonicalText
    val typeParams = clazz.typeParameters.map(_.typeParameterText)

    val isHigherKinded = typeParams.nonEmpty
    val declareTypeParams = if (isHigherKinded) typeParams.mkString(",", ",", "") else ""
    val appliedTypeParams = if (isHigherKinded) typeParams.map(_.replaceAll("\\[.*\\]", "")).mkString("[", ",", "]") else ""
    val shape = if (isHigherKinded) typeParams.map(_.replaceAll("\\w+", "_")).mkString("[", ",", "]") else ""
    val className = clazz.name

    val deriving = List(s"def deriving[TC[_]$declareTypeParams](implicit ev: TC[$fullReprName]): TC[$className$appliedTypeParams] = ???")
    val derivingK = if (isHigherKinded && !hasDifferentShape) List(s"def derivingK[TC[_$shape]](implicit ev: TC[$reprName]): TC[$className] = ???") else Nil

    val coercibles = List(
      s"implicit def unsafeWrap$appliedTypeParams: _root_.io.estatico.newtype.Coercible[$fullReprName, $className$appliedTypeParams] = ???",
      s"implicit def unsafeUnwrap$appliedTypeParams: _root_.io.estatico.newtype.Coercible[$className$appliedTypeParams, $fullReprName] = ???",
      s"implicit def unsafeWrapM[M[_]$declareTypeParams]: _root_.io.estatico.newtype.Coercible[M[$fullReprName], M[$className$appliedTypeParams]] = ???",
      s"implicit def unsafeUnwrapM[M[_]$declareTypeParams]: _root_.io.estatico.newtype.Coercible[M[$className$appliedTypeParams], M[$fullReprName]] = ???",
      s"implicit def cannotWrapArrayAmbiguous1$appliedTypeParams: _root_.io.estatico.newtype.Coercible[_root_.scala.Array[$fullReprName], _root_.scala.Array[$className$appliedTypeParams]] = ???",
      s"implicit def cannotWrapArrayAmbiguous2$appliedTypeParams: _root_.io.estatico.newtype.Coercible[_root_.scala.Array[$fullReprName], _root_.scala.Array[$className$appliedTypeParams]] = ???",
      s"implicit def cannotUnwrapArrayAmbiguous1$appliedTypeParams: _root_.io.estatico.newtype.Coercible[_root_.scala.Array[$className$appliedTypeParams], _root_.scala.Array[$fullReprName]] = ???",
      s"implicit def cannotUnwrapArrayAmbiguous2$appliedTypeParams: _root_.io.estatico.newtype.Coercible[_root_.scala.Array[$className$appliedTypeParams], _root_.scala.Array[$fullReprName]] = ???"
    )
    val coerciblesK = if (isHigherKinded && !hasDifferentShape) List(
      s"implicit def unsafeWrapK[T[_$appliedTypeParams]]: _root_.io.estatico.newtype.Coercible[T[$reprName], T[$className]] = ???",
      s"implicit def unsafeUnwrapK[T[_$appliedTypeParams]]: _root_.io.estatico.newtype.Coercible[T[$className], T[$reprName]] = ???",
    ) else Nil

    deriving ++ derivingK ++ coercibles ++ coerciblesK
  }.getOrElse(Nil)
}
