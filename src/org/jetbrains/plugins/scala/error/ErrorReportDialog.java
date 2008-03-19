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

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.ui.Messages;
import org.apache.xmlrpc.XmlRpcClient;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.ScalaBundle;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.event.*;
import java.util.Vector;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: oleg, Roman Chernyatchik
 * @date: Jan 11, 2007
 */

public class ErrorReportDialog extends JDialog {
    private JPanel myContentPane;
    private JButton myButtonSend;
    private JButton myButtonCancel;
    private JTextArea myDescriptionTextArea;
    private JTextPane myDescriptionPane;
    private JRadioButton myJIRAUserRB;
    private JRadioButton myNotJIRAUserRB;
    private JPasswordField myPasswordTF;
    private JTextField myLoginTF;
    private JLabel myLoginLabel;
    private JLabel myPasswordLabel;
    private JTextPane mySignupForAnAccountTextPane;
    private JCheckBox mySavePasswordCBCheckBox;
    private Status myStatus = null;

    private boolean isJIRAUser() {
        return myJIRAUserRB.isSelected();
    }

    public enum Status {
        SENT,
        CANCELED
    }

    public ErrorReportDialog(final Component parentComponent) {
        super();
        setContentPane(myContentPane);
        setModal(true);

        myNotJIRAUserRB.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                enableFields(false);
            }
        });
        myJIRAUserRB.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                enableFields(true);
            }
        });

        final ButtonGroup buttonGroup = new ButtonGroup();
        buttonGroup.add(myNotJIRAUserRB);
        buttonGroup.add(myJIRAUserRB);
        myJIRAUserRB.doClick();

        getRootPane().setDefaultButton(myButtonSend);
        setLocationRelativeTo(parentComponent);
        myButtonSend.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onSend();
            }
        });

        myButtonCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        });

// call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

// call onCancel() on ESCAPE
        myContentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

        myDescriptionTextArea.setBorder(LineBorder.createGrayLineBorder());
        myDescriptionPane.setEditorKit(new HTMLEditorKit());
        myDescriptionPane.setBackground(parentComponent.getBackground());
        myDescriptionPane.addHyperlinkListener(new HyperlinkListener() {
            public void hyperlinkUpdate(HyperlinkEvent e) {
                final HyperlinkEvent.EventType eventType = e.getEventType();
                if (eventType.equals(HyperlinkEvent.EventType.ACTIVATED)) {
                    BrowserUtil.launchBrowser(e.getURL().toExternalForm());
                } else if (eventType.equals(HyperlinkEvent.EventType.ENTERED)){
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                } else if (eventType.equals(HyperlinkEvent.EventType.EXITED)){
                    setCursor(Cursor.getDefaultCursor());
                }
            }
        });

        mySignupForAnAccountTextPane.setEditorKit(new HTMLEditorKit());
        mySignupForAnAccountTextPane.addHyperlinkListener(new HyperlinkListener() {
            public void hyperlinkUpdate(HyperlinkEvent e) {
                final HyperlinkEvent.EventType eventType = e.getEventType();
                if (eventType.equals(HyperlinkEvent.EventType.ACTIVATED)) {
                    BrowserUtil.launchBrowser(e.getURL().toExternalForm());
                } else if (eventType.equals(HyperlinkEvent.EventType.ENTERED)){
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                } else if (eventType.equals(HyperlinkEvent.EventType.EXITED)){
                    setCursor(Cursor.getDefaultCursor());
                }
            }
        });
        mySignupForAnAccountTextPane.setText(ScalaBundle.message("error.report.submit.register.in.jira",
                                                             PluginInfoUtil.JIRA_REGISTER_URL));
        mySignupForAnAccountTextPane.setBackground(parentComponent.getBackground());

        mySavePasswordCBCheckBox.setText(ScalaBundle.message("error.report.form.jira.password.save"));
        loadInfo();
    }

    private void storeInfo () {
        final ErrorReportConfigurable configurable = ErrorReportConfigurable.getInstance();
        configurable.JIRA_LOGIN = myLoginTF.getText();
        configurable.setPlainJiraPassword(new String(myPasswordTF.getPassword()));
        configurable.KEEP_JIRA_PASSWORD = mySavePasswordCBCheckBox.getModel().isSelected();
    }

    private void loadInfo () {
        final ErrorReportConfigurable configurable = ErrorReportConfigurable.getInstance();
        myLoginTF.setText(configurable.JIRA_LOGIN);
        myPasswordTF.setText(configurable.getPlainJiraPassword());
        mySavePasswordCBCheckBox.getModel().setSelected(configurable.KEEP_JIRA_PASSWORD);
    }

    public void setVisible(boolean visible) {
        if (visible) {
            myDescriptionTextArea.requestFocusInWindow();
        }
        super.setVisible(visible);
    }

    public String getDescription(){
        return myDescriptionTextArea.getText();
    }

    @Nullable
    public Vector<String> getLoginParams() {
        if (!isJIRAUser()) {
            return null;
        }
        final Vector<String> params = new Vector<String>();
        params.add(myLoginTF.getText());
        params.add(String.valueOf(myPasswordTF.getPassword()));
        return params;
    }

    public void setLabel(final String label){
        myDescriptionPane.setText(label);
    }

    public Status getStatus(){
        assert myStatus!=null;
        return myStatus;
    }

    private void enableFields(final boolean JIRA) {
        myLoginTF.setEnabled(JIRA);
        myLoginLabel.setEnabled(JIRA);
        myPasswordTF.setEnabled(JIRA);
        myPasswordLabel.setEnabled(JIRA);
    }

    private void onCancel() {
        myStatus = Status.CANCELED;
        dispose();
    }

    private void onSend() {
        if (isJIRAUser()) {
            boolean isValid;
            final Vector<String> loginParams = getLoginParams();
            try {
                isValid = new XmlRpcClient(PluginInfoUtil.JIRA_RPC).execute(
                        PluginInfoUtil.JIRA_LOGIN_COMMAND,
                        loginParams) != null;
            } catch (Exception e) {
                isValid = false;
            }
            if (!isValid) {
                Messages.showErrorDialog(ScalaBundle.message("error.report.wrong.login"),
                                         ScalaBundle.message("error.report.dialog.wrong.password.title"));
                return;
            }
        }
        storeInfo();
        myStatus = Status.SENT;
        dispose();
    }
}
