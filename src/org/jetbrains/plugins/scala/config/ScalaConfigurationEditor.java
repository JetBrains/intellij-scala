/*
 * Copyright 2000-2008 JetBrains s.r.o.
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

package org.jetbrains.plugins.scala.config;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.ui.FieldPanel;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import org.jetbrains.plugins.scala.ScalaBundle;

/**
 * @author ilyas
 */
public class ScalaConfigurationEditor {
  private JPanel myMainPanel;
  private JLabel scalaDownloadLinkLabel;
  private JLabel scalaDownloadLink;
  private JPanel scalaPathFieldPanel;

  private JTextField scalaPathField;

  public ScalaConfigurationEditor() {
    scalaPathField = new JTextField();
    final BrowseFilesListener scalaBrowseFilesListener = new BrowseFilesListener(scalaPathField,
            ScalaBundle.message("scala.config.label"),
            ScalaBundle.message("scala.config.dscr"),
            BrowseFilesListener.SINGLE_DIRECTORY_DESCRIPTOR);

    final FieldPanel scalaFieldPanel = new FieldPanel(scalaPathField, ScalaBundle.message("scala.config.label"), null, scalaBrowseFilesListener, null);
    scalaFieldPanel.getFieldLabel().setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD));

    scalaPathFieldPanel.setLayout(new BorderLayout());
    scalaPathFieldPanel.add(scalaFieldPanel, BorderLayout.CENTER);
    subclassLink(scalaDownloadLink);
  }

  private void subclassLink(final JLabel link) {
    link.setForeground(Color.BLUE);
    link.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        BrowserUtil.launchBrowser(link.getText());
      }

      public void mouseEntered(MouseEvent e) {
        link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      }

      public void mouseExited(MouseEvent e) {
        link.setCursor(Cursor.getDefaultCursor());
      }
    });
  }

  public JPanel getMainPanel() {
    return myMainPanel;
  }


  public JTextField getScalaPathField() {
    return scalaPathField;
  }



}
