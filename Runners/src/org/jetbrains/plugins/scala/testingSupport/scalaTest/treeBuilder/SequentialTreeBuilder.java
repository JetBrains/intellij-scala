package org.jetbrains.plugins.scala.testingSupport.scalaTest.treeBuilder;

import org.scalatest.events.Ordinal;
import org.scalatest.events.RunStarting;
import org.scalatest.events.SuiteCompleted;
import org.scalatest.events.SuiteStarting;

import java.util.Stack;

import static org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil.escapeString;

/**
 * @author Roman.Shein
 * @since 11.02.2015.
 */
public class SequentialTreeBuilder implements TreeBuilder {

  private final Stack<Integer> idStack = new Stack<Integer>();
  private final Stack<String> waitingScopeMessagesQueue = new Stack<String>();
  //ID is static because scalaTest v1.9.2 creates multiple reporters when running in package
  private static int id = 0;
  private int getCurrentId() {return idStack.peek();}

  public void openScope(String message, Ordinal ordinal, String suiteId, boolean isTestStarted) {
    int parentId = idStack.peek();
    idStack.push(++id);
    waitingScopeMessagesQueue.push("\n##teamcity[" + message + " nodeId='" + getCurrentId() + "' parentNodeId='" + parentId + "']");
    if (isTestStarted) onTestStarted();
  }

  public void openSuite(String message, SuiteStarting suiteStarting) {
    int parentId = idStack.peek();
    idStack.push(++id);
    waitingScopeMessagesQueue.push("\n##teamcity[" + message + " nodeId='" + getCurrentId() + "' parentNodeId='" + parentId + "']");
  }

  private void onTestStarted() {
    for (String openScopeMessage : waitingScopeMessagesQueue) {
      System.out.println(openScopeMessage);
    }
    waitingScopeMessagesQueue.clear();
  }

  public void closePendingScope(String scopePendingMessage, Ordinal ordinal, String suiteId) {
    if (waitingScopeMessagesQueue.isEmpty()) {
      //print three messages from ScopePending event processing
      System.out.println("\n##teamcity[testIgnored name='(Scope Pending)' message='" +
          escapeString("Scope Pending") + "' nodeId='" + getCurrentId() + "']");
      System.out.println("\n##teamcity[testIgnored name='" + escapeString(scopePendingMessage) + "' message='" +
          escapeString("Scope Pending") + "' nodeId='" + getCurrentId() + "']");
      System.out.println("\n##teamcity[testSuiteFinished name='" + escapeString(scopePendingMessage) + "' nodeId='" + getCurrentId() + "']");
    } else {
      waitingScopeMessagesQueue.pop();
    }
    idStack.pop();
  }

  public void closeScope(String message, Ordinal ordinal, String suiteId, boolean isTestFinished) {
    if (waitingScopeMessagesQueue.isEmpty()) {
      //there are no open empty scopes, so scope currently being closed must be not empty, print the actual message
      System.out.println("\n##teamcity[" + message +  "nodeId='" + getCurrentId() + "']");
    } else {
      waitingScopeMessagesQueue.pop();
    }
    idStack.pop();
  }

  public void closeSuite(String message, SuiteCompleted suiteCompleted) {
    if (waitingScopeMessagesQueue.isEmpty()) {
      //there are no open empty scopes, so scope currently being closed must be not empty, print the actual message
      System.out.println("\n##teamcity[" + message +  "nodeId='" + getCurrentId() + "']");
    } else {
      waitingScopeMessagesQueue.pop();
    }
    idStack.pop();
  }

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
}
