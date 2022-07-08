package org.jetbrains.plugins.scala.testingSupport.scalaTest.treeBuilder;

import org.scalatest.events.Ordinal;
import org.scalatest.events.RunStarting;
import org.scalatest.events.SuiteCompleted;
import org.scalatest.events.SuiteStarting;

import java.util.Stack;

import static org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil.escapeString;
import static org.jetbrains.plugins.scala.testingSupport.scalaTest.TeamcityReporter.reportMessage;

public class SequentialTreeBuilder implements TreeBuilder {

  private final Stack<Integer> idStack = new Stack<>();
  private final Stack<String> waitingScopeMessagesQueue = new Stack<>();
  //ID is static because scalaTest v1.9.2 creates multiple reporters when running in package
  private static int id = 0;
  private int getCurrentId() {return idStack.peek();}

  @Override
  public void initRun(RunStarting runStarting) {
    idStack.push(0);
  }

  /**
   * This is a hack used to maintain scalaTest 1.9.2 compatibility: it does not necessarily provide a RunStarting event.
   * @return whether the reporter has been initialized properly
   */
  public boolean isInitialized() {
    return !idStack.isEmpty();
  }

  @Override
  public void openScope(String message, Ordinal ordinal, String suiteId, boolean isTestStarted) {
    int parentId = idStack.peek();
    idStack.push(++id);
    waitingScopeMessagesQueue.push("##teamcity[" + message + " nodeId='" + getCurrentId() + "' parentNodeId='" + parentId + "']");
    if (isTestStarted)
      onTestStarted();
  }

  private void onTestStarted() {
    for (String openScopeMessage : waitingScopeMessagesQueue) {
      reportMessage(openScopeMessage);
    }
    waitingScopeMessagesQueue.clear();
  }


  @Override
  public void openSuite(String message, SuiteStarting suiteStarting) {
    int parentId = idStack.peek();
    idStack.push(++id);
    waitingScopeMessagesQueue.push("##teamcity[" + message + " nodeId='" + getCurrentId() + "' parentNodeId='" + parentId + "']");
  }

  @Override
  public void closePendingScope(String scopePendingMessage, Ordinal ordinal, String suiteId) {
    if (waitingScopeMessagesQueue.isEmpty()) {
      //print three messages from ScopePending event processing
      reportMessage("##teamcity[testIgnored name='(Scope Pending)' message='" + escapeString("Scope Pending") + "' nodeId='" + getCurrentId() + "']");
      reportMessage("##teamcity[testIgnored name='" + escapeString(scopePendingMessage) + "' message='" + escapeString("Scope Pending") + "' nodeId='" + getCurrentId() + "']");
      reportMessage("##teamcity[testSuiteFinished name='" + escapeString(scopePendingMessage) + "' nodeId='" + getCurrentId() + "']");
    } else {
      waitingScopeMessagesQueue.pop();
    }
    idStack.pop();
  }

  @Override
  public void closeScope(String message, Ordinal ordinal, String suiteId, boolean isTestFinished) {
    if (waitingScopeMessagesQueue.isEmpty()) {
      //there are no open empty scopes, so scope currently being closed must be not empty, print the actual message
      reportMessage("##teamcity[" + message +  "nodeId='" + getCurrentId() + "']");
    } else {
      waitingScopeMessagesQueue.pop();
    }
    idStack.pop();
  }

  @Override
  public void closeSuite(String message, SuiteCompleted suiteCompleted) {
    if (waitingScopeMessagesQueue.isEmpty()) {
      //there are no open empty scopes, so scope currently being closed must be not empty, print the actual message
      reportMessage("##teamcity[" + message +  "nodeId='" + getCurrentId() + "']");
    } else {
      waitingScopeMessagesQueue.pop();
    }
    idStack.pop();
  }
}
