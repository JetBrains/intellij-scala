package org.jetbrains.plugins.scala.lang.macros.evaluator.impl

import org.jetbrains.plugins.scala.lang.macros.evaluator.{MacroContext, MacroImpl, ScalaMacroTypeable}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAliasDeclaration}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.{ScCompoundType, ScParameterizedType, ScType, TypeAliasSignature}

/**
 * Generates type signatures that serialize _named_ case class fields
 *
 * example:
 * case class Foo(age: Int, name: String) ->
 * -> DefaultSymbolicLabelling.Aux[Main.this.Foo, (Symbol @@ String("age")) :: (Symbol @@ String("name")) :: shapeless.HNil])
 *
 * note: since we don't support literal types yet, field names are encoded as compound types with a type alias
 */
object ShapelessDefaultSymbolicLabelling extends ScalaMacroTypeable with ShapelessUtils {

  override val boundMacro: Seq[MacroImpl] = MacroImpl("mkDefaultSymbolicLabelling", "shapeless.DefaultSymbolicLabelling") :: Nil

  override def checkMacro(macros: ScFunction, context: MacroContext): Option[ScType] = {
    if (context.expectedType.isEmpty) return None

    typesInScope(context).map { foundTypes =>

      import foundTypes._

      val targetClass = extractTargetType(context)
      val fields      = extractFields(targetClass)

      val fieldTypes = fields.map {
        case (pName, pType) => mkParamTp(pName, pType, foundTypes)(context)
      }

      ScParameterizedType(aux, Seq(targetClass, hListType(fieldTypes, hNil, hCons)))
    }
  }

  private def mkParamTp(paramName: String, tp: ScType, typesInScope: TypesInScope)
                       (implicit macroContext: MacroContext): ScType = {
    import tp.projectContext
    import typesInScope._

    val taggedSymbol = ScCompoundType(Seq(symbol, ScParameterizedType(tagged, Seq(nameTypeAliasCompound(paramName)))))
    ScCompoundType(Seq(tp, ScParameterizedType(keyTag, Seq(taggedSymbol, tp))))
  }

  // { type name }
  private def nameTypeAliasCompound(name: String)(implicit macroContext: MacroContext): ScType = {
    val declaration =
      ScalaPsiElementFactory.createDeclarationFromText(s"type $name", macroContext.place, null)
        .asInstanceOf[ScTypeAliasDeclaration]

    ScCompoundType(Seq.empty, typesMap = Map(name -> TypeAliasSignature(declaration)))(macroContext.place)
  }

  private def typesInScope(implicit context: MacroContext): Option[TypesInScope] = {
    for {
      hNil   <- findClassType(fqHNil)
      hCons  <- findClassType(fqColonColon)
      symbol <- findClassType("scala.Symbol")
      keyTag <- findClassType(fqKeyTag)
      tagged <- findClassType(fqTagged)
      aux    <- findAliasProjectionType(fqDefSymLab, "Aux")
    } yield {
      TypesInScope(hNil, hCons, symbol, keyTag, tagged, aux)
    }
  }

  private case class TypesInScope(
    hNil  : ScType,
    hCons : ScType,
    symbol: ScType,
    keyTag: ScType,
    tagged: ScType,
    aux   : ScType
  )
}
