package org.jetbrains.plugins.scala.compiler.highlighting.services.core

import com.intellij.openapi.vfs.VirtualFile

/**
 * What the compiler told us about one compilation it reported errors for.
 *
 * The targets are resolved by the caller, because that needs a read action and the state is deliberately free
 * of the platform.
 *
 * @param covered          the files the compiler reached before giving up.
 * @param requestedTargets the targets the compilation was asked to build.
 * @param erroredTargets   the targets of the files the compiler reported an error against.
 * @param coveredTargets   the targets of `covered`.
 */
final case class FailedCompilation(covered: Set[VirtualFile],
                                   requestedTargets: Set[ModuleKey],
                                   erroredTargets: Set[ModuleKey],
                                   coveredTargets: Set[ModuleKey])
