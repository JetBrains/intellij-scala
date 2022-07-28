package org.jetbrains.plugins.scala.macroAnnotations

import org.jetbrains.plugins.scala.macroAnnotations.ValueWrapper.ValueWrapper

import scala.annotation.{StaticAnnotation, tailrec}
import scala.collection.mutable.ArrayBuffer
import scala.language.experimental.macros
import scala.reflect.macros.whitebox

/**
  * If you annotate a function with @CachedWithoutModificationCount annotation, the compiler will generate code to cache it.
  *
  * If an annotated function has parameters, one field will be generated (a HashMap).
  * If an annotated function has no parameters, two fields will be generated: result and modCount
  *
  * NOTE !IMPORTANT!: function annotated with @Cached must be on top-most level because generated code generates fields
  * right outside the cached function and if this function is inner it won't work.
  */
class CachedWithoutModificationCount(valueWrapper: ValueWrapper,
                                     cleanupScheduler: Any,
                                     tracked: Any*) extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro CachedWithoutModificationCount.cachedWithoutModificationCountImpl
}

object CachedWithoutModificationCount {
  def cachedWithoutModificationCountImpl(c: whitebox.Context)(annottees: c.Tree*): c.Expr[Any] = {
    import CachedMacroUtil._
    import c.universe._
    implicit val x: c.type = c

    def parameters: (ValueWrapper, Tree, Seq[Tree]) = {
      @tailrec
      def valueWrapperParam(valueWrapper: Tree): ValueWrapper = valueWrapper match {
        case q"valueWrapper = $v" => valueWrapperParam(v)
        case q"ValueWrapper.$v" => ValueWrapper.withName(v.toString)
        case q"$v" => ValueWrapper.withName(v.toString)
      }

      c.prefix.tree match {
        case q"new CachedWithoutModificationCount(..$params)" if params.nonEmpty =>
          val valueWrapper = valueWrapperParam(params.head)
          val cleanupScheduler = params(1)
          (valueWrapper, q"$cleanupScheduler.asInstanceOf[$cleanupSchedulerTypeFqn]", params.drop(2))
        case _ => abort(MacrosBundle.message("macros.cached.wrong.parameters"))
      }
    }

    //annotation parameters
    val (valueWrapper, cleanupScheduler, trackedExprs) = parameters

    annottees.toList match {
      case DefDef(mods, termName, tpParams, paramss, retTp, rhs) :: Nil =>
        preventCacheModeParameter(c)(paramss)
        if (retTp.isEmpty) {
          abort(MacrosBundle.message("macros.cached.specify.return.type"))
        }
        //generated names
        val name = qualifiedTernName(termName.toString)
        val cacheName = withClassName(termName)
        val cacheVarName = TermName(name)
        val mapName = generateTermName(name, "map")
        val computedValue = generateTermName(name, "computedValue")
        val tracerName = generateTermName(name, "$tracer")

        val keyId = stringLiteral(name + "$cacheKey")

        //DefDef parameters
        val flatParams = paramss.flatten
        val paramNames = flatParams.map(_.name)
        val hasParameters: Boolean = flatParams.nonEmpty

        val wrappedRetTp: Tree = valueWrapper match {
          case ValueWrapper.None => retTp
          case ValueWrapper.WeakReference => tq"_root_.java.lang.ref.WeakReference[$retTp]"
          case ValueWrapper.SoftReference => tq"_root_.java.lang.ref.SoftReference[$retTp]"
          case ValueWrapper.SofterReference => tq"_root_.com.intellij.util.SofterReference[$retTp]"
        }

        val fields = if (hasParameters) {
          q"""
            private val $mapName = new java.util.concurrent.ConcurrentHashMap[(..${flatParams.map(_.tpt)}), $wrappedRetTp]()
            $cleanupScheduler.subscribe(() => $mapName.clear())
          """
        } else {
          q"""
            new _root_.scala.volatile()
            private var $cacheVarName: $wrappedRetTp = null.asInstanceOf[$wrappedRetTp]
            $cleanupScheduler.subscribe(() => $cacheVarName = null)
          """
        }

        def getValuesFromMap: c.universe.Tree =
          q"""
            var $cacheVarName = {
              val fromMap = $mapName.get(..$paramNames)
              if (fromMap != null) fromMap
              else null.asInstanceOf[$wrappedRetTp]
            }
            """

        def storeValue: c.universe.Tree = {
          val wrappedResult = valueWrapper match {
            case ValueWrapper.None => q"$computedValue"
            case ValueWrapper.WeakReference => q"new _root_.java.lang.ref.WeakReference($computedValue)"
            case ValueWrapper.SoftReference => q"new _root_.java.lang.ref.SoftReference($computedValue)"
            case ValueWrapper.SofterReference => q"new _root_.com.intellij.util.SofterReference($computedValue)"
          }

          if (hasParameters) q"$mapName.put((..$paramNames), $wrappedResult)"
          else q"$cacheVarName = $wrappedResult"
        }

        val getFromCache =
          if (valueWrapper == ValueWrapper.None) q"$cacheVarName"
          else {
            q"""
                if ($cacheVarName == null) null
                else $cacheVarName.get()
              """
          }

        val functionContents =
          q"""
            ..${if (hasParameters) getValuesFromMap else EmptyTree}

            val $tracerName = ${internalTracerInstance(c)(keyId, cacheName, trackedExprs)}
            $tracerName.invocation()

            val resultFromCache = $getFromCache
            if (resultFromCache == null) {

              $tracerName.calculationStart()

              val $computedValue = try {
                $rhs
              } finally {
                $tracerName.calculationEnd()
              }

              assert($computedValue != null, "Cached function should never return null")

              $storeValue
              $computedValue
            }
            else resultFromCache
          """

        val updatedDef = DefDef(mods, termName, tpParams, paramss, retTp, functionContents)
        val res =
          q"""
          ..$fields
          $updatedDef
          """
        debug(res)
        c.Expr(res)
      case _ => abort(MacrosBundle.message("macros.cached.only.annotate.one.function"))
    }
  }
}

object ValueWrapper extends Enumeration {
  type ValueWrapper = Value
  val None: ValueWrapper = Value("None")
  val SoftReference: ValueWrapper = Value("SoftReference")
  val WeakReference: ValueWrapper = Value("WeakReference")
  val SofterReference: ValueWrapper = Value("SofterReference")
}