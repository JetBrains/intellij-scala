package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.stubs.StubElement
import org.jetbrains.plugins.scala.JavaArrayFactoryUtil.ScTypeDefinitionFactory
import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, cachedInUserData}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.TokenSets.TYPE_DEFINITIONS
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScEnumCase
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScCompanionOwner, ScEnum, ScObject, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaStubBasedElementImpl

trait ScCompanionOwnerImpl extends ScCompanionOwner {
  //Performance critical method
  //And it is REALLY SO!
  final override def baseCompanion: Option[ScTypeDefinition] = {
    val isObject = this match {
      // Enum cases always have injected companion objects
      case _: ScEnumCase                       => return None
      case _: ScObject                         => true
      case _: ScTrait | _: ScClass | _: ScEnum => false
      case _                                   => return None
    }

    val thisName: String = name

    val sameElementInContext = this.getSameElementInContext

    sameElementInContext match {
      case td: ScTypeDefinition if isCompanion(td) => return Some(td)
      case _ =>
    }

    def isCompanion(td: ScTypeDefinition): Boolean = td match {
      case td @ (_: ScClass | _: ScTrait | _: ScEnum) if isObject && td.name == thisName => true
      case o: ScObject if !isObject && thisName == o.name                                => true
      case _                                                                             => false
    }

    def findByStub(contextStub: StubElement[_]): Option[ScTypeDefinition] = {
      val siblings  = contextStub.getChildrenByType(TYPE_DEFINITIONS, ScTypeDefinitionFactory)
      siblings.find(isCompanion)
    }

    // A fallback for when no context exists (which should basically never be the case).
    // Finds the companion via a search of neighboring elements
    // Because this search is done for all type definitions within a context,
    // this search is quadratic in the number of type definitions.
    def findByAstViaDirectSearch: Option[ScTypeDefinition] = {
      var sibling: PsiElement = sameElementInContext

      while (sibling != null) {

        sibling = sibling.getNextSibling

        sibling match {
          case td: ScTypeDefinition if isCompanion(td) => return Some(td)
          case _ =>
        }
      }

      sibling = sameElementInContext
      while (sibling != null) {

        sibling = sibling.getPrevSibling

        sibling match {
          case td: ScTypeDefinition if isCompanion(td) => return Some(td)
          case _ =>
        }
      }

      None
    }

    // find the companion via a cached map of all TypeDefinitions under the context
    def findByAst: Option[ScTypeDefinition] = {
      val ctx = this.getContext
      if (ctx == null)
        return findByAstViaDirectSearch

      val (types, objects) = cachedInUserData("ScTypeDefinitionImpl.baseCompanion.findByAst", ctx, BlockModificationTracker(ctx)) {
        val types = Map.newBuilder[String, ScTypeDefinition]
        val objects = Map.newBuilder[String, ScObject]

        var current = ctx.getFirstChild
        while (current != null) {

          current match {
            case td: ScTypeDefinition if td.is[ScClass, ScTrait, ScEnum] => types += td.name -> td
            case o: ScObject => objects += o.name -> o
            case _ =>
          }

          current = current.getNextSibling
        }
        (types.result(), objects.result())
      }

      if (isObject) types.get(thisName)
      else objects.get(thisName)
    }

    val contextStub = getContext match {
      case stub: ScalaStubBasedElementImpl[_, _] => stub.getStub
      case file: PsiFileImpl => file.getStub
      case _ => null
    }

    if (contextStub != null) findByStub(contextStub)
    else findByAst
  }

  override def baseCompanionTypeDefinition: Option[ScTypeDefinition] = baseCompanion.collect {
    case td: ScTypeDefinition => td
  }
}
