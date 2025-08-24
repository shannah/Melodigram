// java
package com.Tbence132545.Melodigram.controller;

import com.Tbence132545.Melodigram.model.MidiFileService;
import com.Tbence132545.Melodigram.model.MidiInputSelector;
import com.Tbence132545.Melodigram.model.MidiPlayer;
import com.Tbence132545.Melodigram.view.ListWindow;
import com.Tbence132545.Melodigram.view.MainWindow;
import com.Tbence132545.Melodigram.view.PianoWindow;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;

public class ListWindowController implements ListWindow.MidiFileActionListener {

    private final ListWindow view;
    private final MidiFileService midiFileService;

    public ListWindowController(ListWindow view) {
        this.view = view;
        this.midiFileService = new MidiFileService();
        setupEventListeners();
        loadAndDisplayMidiFiles();
    }

    private void setupEventListeners() {
        view.setBackButtonListener(e -> handleBackButton());
        view.setImportButtonListener(e -> handleImportButton());
    }

    private void loadAndDisplayMidiFiles() {
        String[] fileNames = midiFileService.getAllMidiFileNames().toArray(new String[0]);
        view.setMidiFileList(fileNames, this);
    }

    private void handleBackButton() {
        view.dispose();
        MainWindow mainWin = new MainWindow();
        new MainWindowController(mainWin).openMainWindow();
    }

    private void handleImportButton() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select a MIDI file to import");
        fileChooser.setFileFilter(new FileNameExtensionFilter("MIDI Files", "mid", "midi"));

        if (fileChooser.showOpenDialog(view) == JFileChooser.APPROVE_OPTION) {
            try {
                midiFileService.importMidiFile(fileChooser.getSelectedFile());
                JOptionPane.showMessageDialog(view, "File imported successfully!");
                loadAndDisplayMidiFiles(); // Refresh the list
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(view, "Could not import file: " + ex.getMessage(), "Import Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    @Override
    public void onAssignHandsClicked(String midiFilename) {
        //This is where I'll handle how the hand assignment window will look like.
    }
    @Override
    public void onWatchAndListenClicked(String midiFilename) {
        // Restore the crucial logic to handle the inconsistent path from the view
        String simpleFilename = midiFilename.replace("midi/", "");
        openPianoWindow(simpleFilename, false);
    }

    @Override
    public void onPracticeClicked(String midiFilename) {
        MidiInputSelector selector = new MidiInputSelector(view, deviceInfo -> {
            if (deviceInfo != null) {
                openPianoWindow(midiFilename, true, deviceInfo);
            }
        });
        selector.setVisible(true);
    }

    private void openPianoWindow(String midiFileName, boolean isPractice, MidiDevice.Info... midiDeviceInfo) {
        SwingUtilities.invokeLater(() -> {
            MidiDevice inputDevice = null;
            try {
                // Load MIDI data using the service - NO MORE DUPLICATION
                MidiFileService.MidiData midiData = midiFileService.loadMidiData(midiFileName);

                int[] range = MidiPlayer.extractNoteRange(midiData.sequence());
                PianoWindow pianoWindow = new PianoWindow(range[0], range[1]);
                PlaybackController playbackController = new PlaybackController(midiData.player(), pianoWindow);

                if (isPractice) {
                    if (midiDeviceInfo.length == 0) throw new IllegalStateException("MIDI device info required for practice mode.");
                    inputDevice = MidiSystem.getMidiDevice(midiDeviceInfo[0]);
                    playbackController.setPracticeMode(true);
                    playbackController.setMidiInputDevice(inputDevice);
                }

                final MidiDevice finalInputDevice = inputDevice;
                pianoWindow.setBackButtonListener(e -> {
                    midiData.player().stop();
                    if (finalInputDevice != null && finalInputDevice.isOpen()) {
                        finalInputDevice.close();
                    }
                    pianoWindow.dispose();
                    view.setVisible(true);
                });

                pianoWindow.setVisible(true);
                view.setVisible(false);

            } catch (Exception e) {
                e.printStackTrace();
                if (inputDevice != null && inputDevice.isOpen()) inputDevice.close();
                String errorTitle = isPractice ? "Error Initializing Practice" : "Error Opening Piano View";
                JOptionPane.showMessageDialog(view, errorTitle + ":\n" + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}