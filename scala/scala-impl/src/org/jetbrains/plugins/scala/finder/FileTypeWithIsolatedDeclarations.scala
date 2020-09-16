package org.jetbrains.plugins.scala.finder

import com.intellij.openapi.fileTypes.FileType

/**
 * Marker trait which means that file type can't export any declarations outside the file scope.
 *
 * E.g. if we have two worksheets `ws1.sc` and `ws2.sc` in the same folder we would like them to be isolated,
 * which means that classes defined in `ws1.sc` can't be used in `ws2.sc` and vice versa.
 * Also this allows worksheets to contain declarations with same name (unlike ordinary `.scala` files).
 */
trait FileTypeWithIsolatedDeclarations extends FileType
