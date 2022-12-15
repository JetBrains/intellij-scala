package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.caches.ModTracker
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameterType
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.psi.types.{ScParameterizedType, ScType}
import org.jetbrains.plugins.scala.macroAnnotations.CachedInUserData

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

private[declarationRedundancy] object TypeDefEscaping {

  private def destructureParameterizedTypes(m: ScMember, t: Seq[ScType]): ListBuffer[ScType] = {

    val res = ListBuffer[ScType]()

    t.foreach {

      case parameterizedType: ScParameterizedType =>

        res.addOne(parameterizedType.designator)

        res.addAll(destructureParameterizedTypes(m, parameterizedType.typeArguments))

      case t => res.addOne(t)
    }

    res
  }

  /**
   * Since we're only interested in whether a type escapes from its defining
   * scope, we can safely filter out any scraped types that are defined in
   * another file, including Scala Standard Library definitions.
   */
  private def isScTypeDefinedInFile(scType: ScType, file: PsiFile): Boolean =
    scType.extractClass.forall(_.getContainingFile == file)

  private def isPrivate(member: ScMember): Boolean =
    member.getModifierList.accessModifier.exists(_.isUnqualifiedPrivateOrThis)

  private def getTypeParameterTypes(owner: ScTypeParametersOwner): Seq[ScType] =
    owner.typeParameters.flatMap { typeParam =>
      typeParam.viewTypeElement ++
        typeParam.upperTypeElement ++
        typeParam.lowerTypeElement ++
        typeParam.contextBoundTypeElement
    }.flatMap(_.`type`().toSeq)

  @CachedInUserData(typeDef, ModTracker.anyScalaPsiChange)
  def getEscapeInfosOfTypeDefMembers(typeDef: ScTypeDefinition): mutable.Map[ScMember, ListBuffer[ScType]] = {

    val typeDefFile = typeDef.getContainingFile

    val res = new mutable.HashMap[ScMember, ListBuffer[ScType]]()

    def addResults(m: ScMember, types: Seq[ScType]): Unit = {

      val destructuredAndFiltered =
        destructureParameterizedTypes(m, types)
          .filter(t => isScTypeDefinedInFile(t, typeDefFile))
          .filterNot(_.is[TypeParameterType])

      res.getOrElseUpdate(m, ListBuffer.empty).addAll(destructuredAndFiltered)
    }

    typeDef.members.foreach { member =>

      ProgressManager.checkCanceled()

      member match {

        case typeDef: ScTypeDefinition if !isPrivate(typeDef) =>

          val typeDefEscapeInfo = typeDef.`type`().toSeq ++ getTypeParameterTypes(typeDef)
          addResults(typeDef, typeDefEscapeInfo)

          val typeDefAndCompanion = typeDef +: typeDef.baseCompanion.toSeq

          res.addAll(typeDefAndCompanion.flatMap(getEscapeInfosOfTypeDefMembers))

        case typeAlias: ScTypeAliasDefinition if !isPrivate(typeAlias) =>
          addResults(typeAlias, (getTypeParameterTypes(typeAlias) ++ typeAlias.aliasedType.toSeq))

        case primaryConstructor: ScPrimaryConstructor =>

          def isPrivateOrNonMember(p: ScClassParameter): Boolean =
            isPrivate(p) || !p.isClassMember

          val parametersThroughWhichTypeDefsCanEscape = if (isPrivate(primaryConstructor)) {
            primaryConstructor.parameters.filterNot(isPrivateOrNonMember)
          } else {
            primaryConstructor.parameters.filterNot(p => isPrivateOrNonMember(p) && p.getDefaultExpression.isEmpty)
          }

          parametersThroughWhichTypeDefsCanEscape.foreach(p => addResults(p, p.`type`().toSeq))

        case function: ScFunction if !isPrivate(function) =>

          val returnAndParameterTypes: Seq[ScType] = function.`type`().toSeq

          addResults(function, returnAndParameterTypes ++ getTypeParameterTypes(function))

        case typeable: Typeable if !isPrivate(typeable) =>
          addResults(typeable, typeable.`type`().toSeq)

        case _ => ()
      }
    }

    res
  }
}
