package com.Tbence132545.Melodigram.controller;

import com.Tbence132545.Melodigram.view.ListWindow;
import com.Tbence132545.Melodigram.view.MainWindow;
import com.Tbence132545.Melodigram.view.SettingsWindow;

import javax.swing.*;

public class MainWindowController {
    private final MainWindow view;

    public MainWindowController(MainWindow view) {
        this.view = view;
        this.view.addPlayButtonListener(e -> openListWindow());
        this.view.addSettingsButtonListener(e -> openSettingsWindow());
        this.view.addQuitButtonListener(e -> exitProgram());
    }
    public void openMainWindow(){
        SwingUtilities.invokeLater(() -> this.view.setVisible(true));
    }
    private void openListWindow() {
        SwingUtilities.invokeLater(this.view::dispose);
        ListWindow listView = new ListWindow();
        new ListWindowController(listView);
        SwingUtilities.invokeLater(()-> listView.setVisible(true));
    }

    private void openSettingsWindow() {
        System.out.println("Opening settings window...");
       SwingUtilities.invokeLater(() -> new SettingsWindow().setVisible(true));
    }

    private void exitProgram() {
        System.exit(0);
    }
}

