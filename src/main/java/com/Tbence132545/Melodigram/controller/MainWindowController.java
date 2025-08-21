package com.Tbence132545.Melodigram.controller;

import com.Tbence132545.Melodigram.view.ListWindow;
import com.Tbence132545.Melodigram.view.MainWindow;
import com.Tbence132545.Melodigram.view.SettingsWindow;

public class MainWindowController {
    private MainWindow view;

    public MainWindowController(MainWindow view) {
        this.view = view;

        // Attach the new methods to the view's listeners
        this.view.addPlayButtonListener(e -> openListWindow());
        this.view.addSettingsButtonListener(e -> openSettingsWindow());
        this.view.addQuitButtonListener(e -> exitProgram());
    }
    public void openMainWindow(){
        this.view.setVisible(true);
    }
    private void openListWindow() {
        System.out.println("Closing main window and opening list window...");
        view.dispose(); // Close the main window
        ListWindow listView = new ListWindow();
        new ListWindowController(listView);
        listView.setVisible(true);
    }

    private void openSettingsWindow() {
        System.out.println("Opening settings window...");
        // Open the settings window while keeping the main window open
        new SettingsWindow().setVisible(true);
    }

    private void exitProgram() {
        System.out.println("Exiting program...");
        // Terminate the entire application
        System.exit(0);
    }
}

