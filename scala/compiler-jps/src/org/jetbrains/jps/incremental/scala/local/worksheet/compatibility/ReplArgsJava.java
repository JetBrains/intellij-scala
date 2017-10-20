package org.jetbrains.jps.incremental.scala.local.worksheet.compatibility;

/**
 * User: Dmitry.Naydanov
 * Date: 13.03.17.
 */
public class ReplArgsJava {
  private final String sessionId;
  private final String codeChunk;

  public ReplArgsJava(String sessionId, String codeChunk) {
    this.sessionId = sessionId;
    this.codeChunk = codeChunk;
  }

  public String getCodeChunk() {
    return codeChunk;
  }

  public String getSessionId() {
    return sessionId;
  }
}
