package org.jetbrains.bsp

import com.intellij.openapi.progress.ProcessCanceledException
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.jetbrains.annotations.Nls

import java.nio.file.Path

sealed trait BspError extends Exception
case class BspErrorMessage(@Nls message: String) extends Exception(message) with BspError
case class BspResponseError(@Nls message: String, error: ResponseError) extends Exception(message) with BspError
case class BspException(@Nls message: String, cause: Throwable) extends Exception(message, cause) with BspError
case class BspConnectionError(@Nls message: String, cause: Throwable = null) extends Exception(message, cause) with BspError
case class BspConnectionFileError(connectionFile: Path, cause: Throwable = null) extends Exception(BspBundle.message("unable.to.read.bsp.connection.file.at", connectionFile), cause) with BspError
case object BspTaskCancelled extends ProcessCanceledException with BspError
