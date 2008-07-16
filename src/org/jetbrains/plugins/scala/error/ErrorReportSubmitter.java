/*
 * Copyright 2000-2007 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.scala.error;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.SubmittedReportInfo;
import org.apache.xmlrpc.XmlRpcClient;
import org.apache.xmlrpc.XmlRpcException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.ScalaBundle;

import java.awt.*;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Date;
import java.util.Hashtable;
import java.util.Vector;
import java.util.jar.Manifest;

public class ErrorReportSubmitter extends com.intellij.openapi.diagnostic.ErrorReportSubmitter {
  @NonNls
  private static final String LOGIN = "scala_reporter";
  @NonNls
  private static final String PASSWORD = "scala";

  @NonNls
  private static final String PLUGIN_REPORTS = "Plugin Reports";

  @NonNls
  private static final String JIRA_LOGOUT_COMMAND = "jira1.logout";
  @NonNls
  private static final String JIRA_GET_COMPONENTS_COMMAND = "jira1.getComponents";
  @NonNls
  private static final String JIRA_CREATE_ISSUE_COMMAND = "jira1.createIssue";

  private static final int JIRA_NPE_TYPE = 102;
  private static final int JIRA_NORMAL_PRIORITY = 5;

  public String getReportActionText() {
    return ScalaBundle.message("error.report.to.jetbrains.action");
  }

  @SuppressWarnings({"unchecked"})
  public SubmittedReportInfo submit(final IdeaLoggingEvent[] events, final Component parentComponent) {
    final String issueName = events[0].getThrowable().toString();
    try {
// Show dialog to input Error description
      ErrorReportDialog dialog = new ErrorReportDialog(parentComponent);
      dialog.setLabel(ScalaBundle.message("error.report.submit.label",
          issueName, PluginInfoUtil.JIRA_BROWSE, PluginInfoUtil.SCALA_KEY, PLUGIN_REPORTS));
      dialog.setTitle(ScalaBundle.message("error.report.dialog.title"));
      dialog.pack();
      dialog.setSize(parentComponent.getSize());
      dialog.setLocationRelativeTo(parentComponent);
      dialog.setVisible(true);

      if (dialog.getStatus() == ErrorReportDialog.Status.CANCELED) {
        return new SubmittedReportInfo(null,
            ScalaBundle.message("error.report.canceled"),
            SubmittedReportInfo.SubmissionStatus.FAILED);
      }

// Getting environment
      String environment = "Idea build #" + ApplicationInfo.getInstance().getBuildNumber();

// We can obtain revision version and build number only from manifest in jar file
      final Manifest manifest = PluginInfoUtil.getManifest();
      if (manifest != null) {
        final String revision = PluginInfoUtil.getRevision(manifest);
        if (revision != null) {
          environment += "\n" + PluginInfoUtil.REVISION + ": " + revision;
        }
        final String build = PluginInfoUtil.getBuild(manifest);
        if (build != null) {
          environment += "\n" + PluginInfoUtil.BUILD + ": " + build;
        }
      }

// Initialise RPC Client
      final XmlRpcClient rpcClient = new XmlRpcClient(PluginInfoUtil.JIRA_RPC);
// Login and retrieve logon token
      Vector loginParams = dialog.getLoginParams();
      if (loginParams == null) {
        // Login is JetScala Error Reporter
        loginParams = new Vector(2);
        loginParams.add(LOGIN);
        loginParams.add(PASSWORD);
      }
      final String loginToken = (String) rpcClient.execute(PluginInfoUtil.JIRA_LOGIN_COMMAND, loginParams);

// Retrieve Plugin Reports component
      final Vector getComponentsVector = new Vector(2);
      getComponentsVector.add(loginToken);
      getComponentsVector.add(PluginInfoUtil.SCALA_KEY);
      final Vector<Hashtable> components = (Vector<Hashtable>) rpcClient.execute(JIRA_GET_COMPONENTS_COMMAND, getComponentsVector);

      Hashtable pluginReportsComponent = null;
      for (int i = 0; i < components.size(); i++) {
        Hashtable t = components.get(i);
        if (t.get("name").equals(PLUGIN_REPORTS)) {
          pluginReportsComponent = t;
          i = components.capacity();
        }
      }
      assert (pluginReportsComponent != null);

// Create new issue
      final Hashtable<String, Object> issue = new Hashtable<String, Object>();
      issue.put("project", PluginInfoUtil.SCALA_KEY);
      issue.put("summary", issueName);
      issue.put("environment", environment);
      issue.put("description", dialog.getDescription() + "\n\n{code}\n" + events[0].getThrowableText() + "\n{code}");

      final Vector issueComponents = new Vector(1);
      issueComponents.add(pluginReportsComponent);
      issue.put("components", issueComponents);

      issue.put("created", new Date());
      issue.put("type", JIRA_NPE_TYPE);
      issue.put("priority", JIRA_NORMAL_PRIORITY);

      final Vector createIssueVector = new Vector(2);
      createIssueVector.add(loginToken);
      createIssueVector.add(issue);

      final Object result = rpcClient.execute(JIRA_CREATE_ISSUE_COMMAND, createIssueVector);
      if (result == null) {
        return new SubmittedReportInfo(null, ScalaBundle.message("error.report.error.creating.issue"), SubmittedReportInfo.SubmissionStatus.FAILED);
      }

// Log out
      final Vector logoutVector = new Vector(1);
      logoutVector.add(loginToken);
      assert (Boolean) rpcClient.execute(JIRA_LOGOUT_COMMAND, logoutVector);

      final String issueKey = ((Hashtable) result).get("key").toString();
      ErrorMessageDialog.showInfoMessage(ScalaBundle.message("error.report.submit.new.issue.url",
          PluginInfoUtil.JIRA_BROWSE + issueKey),
          ScalaBundle.message("error.report.submit.new.issue.title"),
          parentComponent);
      return new SubmittedReportInfo(PluginInfoUtil.JIRA_BROWSE + issueKey, ScalaBundle.message("error.report.jira.issue", issueKey), SubmittedReportInfo.SubmissionStatus.NEW_ISSUE);

// error processing
    } catch (MalformedURLException e) {
      return new SubmittedReportInfo(null, e.toString(), SubmittedReportInfo.SubmissionStatus.FAILED);
    } catch (IOException e) {
      return new SubmittedReportInfo(null, e.toString(), SubmittedReportInfo.SubmissionStatus.FAILED);
    } catch (XmlRpcException e) {
      return new SubmittedReportInfo(null, e.toString(), SubmittedReportInfo.SubmissionStatus.FAILED);
    } catch (Throwable e) {
      return new SubmittedReportInfo(null, e.toString(), SubmittedReportInfo.SubmissionStatus.FAILED);
    }
  }

}
