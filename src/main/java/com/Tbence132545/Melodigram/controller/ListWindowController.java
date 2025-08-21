package com.Tbence132545.Melodigram.controller;

import com.Tbence132545.Melodigram.Main;
import com.Tbence132545.Melodigram.model.MidiInputSelector;
import com.Tbence132545.Melodigram.model.MidiPlayer;
import com.Tbence132545.Melodigram.view.ListWindow;
import com.Tbence132545.Melodigram.view.MainWindow;
import com.Tbence132545.Melodigram.view.PianoWindow;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ListWindowController implements ListWindow.MidiFileActionListener {

    private final ListWindow view;

    public ListWindowController(ListWindow view) {
        this.view = view;
        loadAndDisplayMidiFiles();

        this.view.setBackButtonListener(e -> {
            view.dispose();
            MainWindow mainWin = new MainWindow();
            MainWindowController mainWinCon = new MainWindowController(mainWin);
            mainWinCon.openMainWindow();
        });

        this.view.setImportButtonListener(e -> handleImportMidi());
    }

    private Path getExternalMidiFolderPath() {
        return Paths.get(System.getProperty("user.home"), ".Melodigram", "midi");
    }

    private void handleImportMidi() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select a MIDI file to import");
        FileNameExtensionFilter filter = new FileNameExtensionFilter("MIDI Files", "mid", "midi");
        fileChooser.setFileFilter(filter);

        if (fileChooser.showOpenDialog(view) == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            try {
                Path externalMidiDir = getExternalMidiFolderPath();
                Files.createDirectories(externalMidiDir);
                Path destinationPath = externalMidiDir.resolve(selectedFile.getName());
                Files.copy(selectedFile.toPath(), destinationPath, StandardCopyOption.REPLACE_EXISTING);
                JOptionPane.showMessageDialog(view, "File imported successfully!");
                loadAndDisplayMidiFiles();
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(view, "Could not import file: " + ex.getMessage(), "Import Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void loadAndDisplayMidiFiles() {
        List<String> allFiles = new ArrayList<>(listMidiResources());

        Path externalMidiDir = getExternalMidiFolderPath();
        if (Files.exists(externalMidiDir) && Files.isDirectory(externalMidiDir)) {
            try (Stream<Path> paths = Files.walk(externalMidiDir, 1)) {
                paths.filter(Files::isRegularFile)
                        .map(path -> path.getFileName().toString())
                        .filter(name -> name.toLowerCase().endsWith(".mid") || name.toLowerCase().endsWith(".midi"))
                        .filter(name -> !allFiles.contains(name))
                        .forEach(allFiles::add);
            } catch (Exception e) {
                System.err.println("Error reading external MIDI folder: " + e.getMessage());
            }
        }
        Collections.sort(allFiles);
        view.setMidiFileList(allFiles.toArray(new String[0]), this);
    }

    // Add this helper that lists resources from both filesystem and shaded JAR:
    private List<String> listMidiResources() {
        final String MIDI_DIR = "midi/";
        try {
            ClassLoader cl = getClass().getClassLoader();
            URL url = cl.getResource(MIDI_DIR);
            if (url != null) {
                String protocol = url.getProtocol();
                if ("file".equals(protocol)) {
                    try (var stream = Files.list(Path.of(url.toURI()))) {
                        return stream.filter(Files::isRegularFile)
                                .map(p -> p.getFileName().toString())
                                .filter(n -> n.toLowerCase().endsWith(".mid") || n.toLowerCase().endsWith(".midi"))
                                .collect(Collectors.toList());
                    }
                } else if ("jar".equals(protocol)) {
                    JarURLConnection conn = (JarURLConnection) url.openConnection();
                    try (JarFile jar = conn.getJarFile()) {
                        List<String> names = new ArrayList<>();
                        Enumeration<JarEntry> entries = jar.entries();
                        while (entries.hasMoreElements()) {
                            JarEntry e = entries.nextElement();
                            String name = e.getName();
                            if (name.startsWith(MIDI_DIR) && !name.endsWith("/") &&
                                    (name.toLowerCase().endsWith(".mid") || name.toLowerCase().endsWith(".midi"))) {
                                names.add(name.substring(MIDI_DIR.length()));
                            }
                        }
                        return names;
                    }
                }
            }

            // Fallback: iterate the running JAR if the directory entry is missing
            URL codeSource = getClass().getProtectionDomain().getCodeSource().getLocation();
            if (codeSource != null && "file".equals(codeSource.getProtocol())) {
                Path jarPath = Path.of(codeSource.toURI());
                if (jarPath.toString().endsWith(".jar")) {
                    try (JarFile jar = new JarFile(jarPath.toFile())) {
                        List<String> names = new ArrayList<>();
                        Enumeration<JarEntry> entries = jar.entries();
                        while (entries.hasMoreElements()) {
                            JarEntry e = entries.nextElement();
                            String name = e.getName();
                            if (name.startsWith(MIDI_DIR) && !name.endsWith("/") &&
                                    (name.toLowerCase().endsWith(".mid") || name.toLowerCase().endsWith(".midi"))) {
                                names.add(name.substring(MIDI_DIR.length()));
                            }
                        }
                        return names;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return List.of();
    }

    @Override
    public void onWatchAndListenClicked(String midiFilename) {
        // We strip the "midi/" prefix that the view adds to get the simple filename.
        String simpleFilename = midiFilename.replace("midi/", "");
        openPianoWindow(simpleFilename);
    }

    @Override
    public void onPracticeClicked(String midiFilename) {
        MidiInputSelector selector = new MidiInputSelector(view, selectedDeviceInfo -> {
            SwingUtilities.invokeLater(() -> {
                try {
                    MidiDevice midiDevice = MidiSystem.getMidiDevice(selectedDeviceInfo);
                    if (!midiDevice.isOpen()) {
                        midiDevice.open();
                    }

                    MidiPlayer midiPlayer = new MidiPlayer();
                    Sequence sequence;

                    // FIX: Check if the file is imported or a resource, and call the correct method
                    Path externalFile = getExternalMidiFolderPath().resolve(midiFilename);
                    if (Files.exists(externalFile)) {
                        // It's an imported file, so use loadMidiFromFile
                        midiPlayer.loadMidiFromFile(externalFile.toAbsolutePath().toString());
                        sequence = MidiSystem.getSequence(externalFile.toFile());
                    } else {
                        // It's a built-in file, so use loadMidiFromResources
                        String resourcePath = "midi/" + midiFilename;
                        midiPlayer.loadMidiFromResources(resourcePath);
                        InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);
                        if (is == null) throw new FileNotFoundException(resourcePath);
                        sequence = MidiSystem.getSequence(is);
                    }

                    int[] range = MidiPlayer.extractNoteRange(sequence);
                    PianoWindow pianoWindow = new PianoWindow(range[0], range[1]);

                    PlaybackController playbackController = new PlaybackController(midiPlayer, pianoWindow);
                    playbackController.setPracticeMode(true);
                    playbackController.setMidiInputDevice(midiDevice);

                    pianoWindow.setBackButtonListener(e -> {
                        midiPlayer.stop();
                        if (midiDevice.isOpen()) {
                            midiDevice.close();
                        }
                        pianoWindow.dispose();
                        view.setVisible(true);
                    });

                    pianoWindow.setVisible(true);
                    view.setVisible(false);

                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(view, "Error initializing practice mode:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            });
        });
        selector.setVisible(true);
    }

    private void openPianoWindow(String midiFileName) {
        SwingUtilities.invokeLater(() -> {
            try {
                MidiPlayer midiPlayer = new MidiPlayer();
                Sequence sequence;

                // FIX: Check if the file is imported or a resource, and call the correct method
                Path externalFile = getExternalMidiFolderPath().resolve(midiFileName);
                if (Files.exists(externalFile)) {
                    // It's an imported file, so use loadMidiFromFile
                    midiPlayer.loadMidiFromFile(externalFile.toAbsolutePath().toString());
                    sequence = MidiSystem.getSequence(externalFile.toFile());
                } else {
                    // It's a built-in file, so use loadMidiFromResources
                    String resourcePath = "midi/" + midiFileName;
                    midiPlayer.loadMidiFromResources(resourcePath);
                    InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);
                    if (is == null) throw new FileNotFoundException(resourcePath);
                    sequence = MidiSystem.getSequence(is);
                }

                int[] range = MidiPlayer.extractNoteRange(sequence);
                PianoWindow pianoWindow = new PianoWindow(range[0], range[1]);

                pianoWindow.setBackButtonListener(e -> {
                    midiPlayer.stop();
                    pianoWindow.dispose();
                    view.setVisible(true);
                });

                new PlaybackController(midiPlayer, pianoWindow);
                pianoWindow.setVisible(true);
                view.setVisible(false);
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(view, "Error opening piano view:\n" + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }


}