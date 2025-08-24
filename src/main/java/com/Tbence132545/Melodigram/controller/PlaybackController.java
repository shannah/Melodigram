// java
package com.Tbence132545.Melodigram.controller;

import com.Tbence132545.Melodigram.model.MidiPlayer;
import com.Tbence132545.Melodigram.view.AnimationPanel;
import com.Tbence132545.Melodigram.view.PianoWindow;
import com.Tbence132545.Melodigram.view.SeekBar;

import javax.sound.midi.*;
import javax.swing.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

public class PlaybackController {
    private final MidiPlayer midiPlayer;
    private final PianoWindow pianoWindow;
    private final AnimationPanel animationPanel;
    private final SeekBar seekBar;
    private long startTime;
    private final Timer sharedTimer;
    private long lastTickTime;
    private boolean playbackStarted = false;
    private boolean animationPaused = false;
    private boolean isPracticeMode = false;
    private final List<Integer> currentlyPressedNotes = new ArrayList<>();

    private MidiDevice midiInputDevice;

    // Drag state
    private boolean wasPlayingBeforeDrag = false;

    // Practice gating state
    private final List<Integer> awaitedNotes = new ArrayList<>();
    private final Set<Integer> notesPressedInChordAttempt = new HashSet<>();


    public PlaybackController(MidiPlayer midiPlayer, PianoWindow pianoWindow) {
        this.midiPlayer = midiPlayer;
        this.pianoWindow = pianoWindow;
        this.animationPanel = pianoWindow.getAnimationPanel();
        preprocessNotes(midiPlayer.getSequencer().getSequence());
        midiPlayer.setNoteOnListener(this::onNoteOn);
        midiPlayer.setNoteOffListener(this::onNoteOff);
        pianoWindow.setPlayButtonListener(e -> togglePlayback());
        seekBar = new SeekBar(midiPlayer.getSequencer());
        pianoWindow.addSeekBar(seekBar);

        seekBar.setSeekListener(newMicroseconds -> {
            long newMillis = newMicroseconds / 1000;
            animationPanel.updatePlaybackTime(newMillis);
            resetPracticeState();
            lastTickTime = System.currentTimeMillis();
        });

        pianoWindow.setForwardButtonListener(e -> {
            midiPlayer.forward();
            long newTimeMillis = midiPlayer.getSequencer().getMicrosecondPosition() / 1000;
            animationPanel.updatePlaybackTime(newTimeMillis);
            resetPracticeState();
        });
        pianoWindow.setBackwardButtonListener(e -> {
            midiPlayer.backward();
            long newTimeMillis = midiPlayer.getSequencer().getMicrosecondPosition() / 1000;
            animationPanel.updatePlaybackTime(newTimeMillis);
            resetPracticeState();
        });

        long totalMillis = midiPlayer.getSequencer().getMicrosecondLength() / 1000;
        animationPanel.setTotalDurationMillis(totalMillis);
        animationPanel.setOnDragStart(() -> {
            wasPlayingBeforeDrag = midiPlayer.isPlaying();
            if (wasPlayingBeforeDrag) midiPlayer.stop();
            animationPaused = true;
            resetPracticeState();
            lastTickTime = System.currentTimeMillis();
        });
        animationPanel.setOnTimeChange(newTimeMillis -> {
            try {
                midiPlayer.getSequencer().setMicrosecondPosition(newTimeMillis * 1000);
            } catch (Exception ignored) {}
            lastTickTime = System.currentTimeMillis();
        });
        animationPanel.setOnDragEnd(() -> {
            animationPaused = false;
            if (wasPlayingBeforeDrag && !isPracticeMode) {
                midiPlayer.play();
            }
            lastTickTime = System.currentTimeMillis();
        });

        lastTickTime = System.currentTimeMillis();

        // Unified timer
        sharedTimer = new Timer(1000 / 60, e -> {
            long now = System.currentTimeMillis();
            long delta = now - lastTickTime;
            lastTickTime = now;

            if (!playbackStarted) {
                if (midiPlayer.getSequencer().getMicrosecondLength() > 0 && now - startTime > 3000) {
                    pianoWindow.disableButtons(false);
                    if (!isPracticeMode) {
                        midiPlayer.play();
                    }
                    playbackStarted = true;
                } else {
                    pianoWindow.disableButtons(true);
                    return;
                }
            }

            if (animationPaused) return;

            if (isPracticeMode) {
                boolean chordIsSatisfied = false;
                if (!awaitedNotes.isEmpty()) {
                    Set<Integer> currentlyHeldSet = new HashSet<>(currentlyPressedNotes);
                    Set<Integer> expectedSet = new HashSet<>(awaitedNotes);

                    if (notesPressedInChordAttempt.containsAll(expectedSet) && currentlyHeldSet.equals(expectedSet)) {
                        chordIsSatisfied = true;
                    } else {
                        SwingUtilities.invokeLater(() -> {
                            for (int note : awaitedNotes) {
                                synchronized (currentlyPressedNotes) {
                                    if (!currentlyPressedNotes.contains(note)) {
                                        pianoWindow.highlightNote(note);
                                    }
                                }
                            }
                        });
                        return; // Halt animation and wait for user input.
                    }
                }

                long prevTime = animationPanel.getCurrentTimeMillis();
                animationPanel.tick(delta);
                long nextTime = animationPanel.getCurrentTimeMillis();

                long onsetStart = (prevTime == 0) ? -1 : prevTime;
                List<Integer> onsets = animationPanel.getNotesStartingBetween(onsetStart, nextTime);

                if (!onsets.isEmpty()) {
                    awaitedNotes.clear();
                    awaitedNotes.addAll(onsets);
                    notesPressedInChordAttempt.clear();

                    SwingUtilities.invokeLater(() -> {
                        pianoWindow.releaseAllKeys();
                        for (Integer note : awaitedNotes) {
                            pianoWindow.highlightNote(note);
                        }
                    });
                } else if (chordIsSatisfied) {
                    awaitedNotes.clear();
                }

            } else {
                animationPanel.tick(delta);
            }

            seekBar.updateProgress();
        });

        startTime = System.currentTimeMillis();
        sharedTimer.start();
    }

    // ... (rest of the class methods: setPracticeMode, setMidiInputDevice, etc. are unchanged) ...
    // ... Please use the methods from the previous response ...
    public void setPracticeMode(boolean enabled){
        this.isPracticeMode = enabled;
        pianoWindow.disableButtons(enabled);
        if (enabled) {
            midiPlayer.stop(); // ensure sequencer doesn't drive UI in practice
            resetPracticeState();
        }
    }

    public void setMidiInputDevice(MidiDevice device) {
        try {
            if (midiInputDevice != null && midiInputDevice.isOpen()) {
                midiInputDevice.close();
            }
            midiInputDevice = device;
            if (!device.isOpen()) device.open();
            device.getTransmitter().setReceiver(new Receiver() {
                @Override
                public void send(MidiMessage message, long timeStamp) {
                    if (!isPracticeMode) return;

                    if (message instanceof ShortMessage sm) {
                        int command = sm.getCommand();
                        int note = sm.getData1();

                        if (command == ShortMessage.NOTE_ON && sm.getData2() > 0) {
                            synchronized (currentlyPressedNotes) {
                                if (!currentlyPressedNotes.contains(note)) {
                                    currentlyPressedNotes.add(note);
                                    notesPressedInChordAttempt.add(note);
                                    // This highlights the key the user *just pressed*.
                                    SwingUtilities.invokeLater(() -> pianoWindow.highlightNote(note));
                                }
                            }
                        } else if (command == ShortMessage.NOTE_OFF || (command == ShortMessage.NOTE_ON && sm.getData2() == 0)) {
                            synchronized (currentlyPressedNotes) {
                                currentlyPressedNotes.remove((Integer) note);
                                // This releases the highlight when the user lets go.
                                SwingUtilities.invokeLater(() -> pianoWindow.releaseNote(note));
                            }
                        }
                    }
                }
                @Override public void close() {}
            });
        } catch (MidiUnavailableException e) {
            e.printStackTrace();
        }
    }

    void onNoteOn(int midiNote){
        if (isPracticeMode) return;
        SwingUtilities.invokeLater(() -> pianoWindow.highlightNote(midiNote));
    }

    private void onNoteOff(int midiNote){
        if (isPracticeMode) return;
        SwingUtilities.invokeLater(() -> pianoWindow.releaseNote(midiNote));
    }

    void togglePlayback() {
        if (isPracticeMode) {
            animationPaused = !animationPaused;
            if (!animationPaused) {
                lastTickTime = System.currentTimeMillis();
                sharedTimer.start();
            }
            SwingUtilities.invokeLater(() -> pianoWindow.setPlayButtonText(animationPaused ? "▶" : "||"));
            return;
        }
        if (midiPlayer.isPlaying()) {
            midiPlayer.stop();
            animationPaused = true;
            SwingUtilities.invokeLater(() -> pianoWindow.setPlayButtonText("▶"));
        } else {
            midiPlayer.play();
            sharedTimer.start();
            animationPaused = false;
            SwingUtilities.invokeLater(() -> pianoWindow.setPlayButtonText("||"));
        }
    }

    public void preprocessNotes(Sequence sequence) {
        try {
            Sequencer tempSequencer = MidiSystem.getSequencer(false);
            tempSequencer.open();
            tempSequencer.setSequence(sequence);
            tempSequencer.start();
            tempSequencer.stop();

            Map<Integer, List<Long>> activeNotes = new HashMap<>();

            for (Track track : sequence.getTracks()) {
                for (int i = 0; i < track.size(); i++) {
                    MidiEvent event = track.get(i);
                    MidiMessage msg = event.getMessage();

                    if (msg instanceof ShortMessage sm) {
                        int cmd = sm.getCommand();
                        int note = sm.getData1();

                        tempSequencer.setTickPosition(event.getTick());
                        long microTime = tempSequencer.getMicrosecondPosition();
                        long timeMillis = microTime / 1000;

                        if (cmd == ShortMessage.NOTE_ON && sm.getData2() > 0) {
                            activeNotes.computeIfAbsent(note, k -> new ArrayList<>()).add(timeMillis);
                        } else if (cmd == ShortMessage.NOTE_OFF || (cmd == ShortMessage.NOTE_ON && sm.getData2() == 0)) {
                            List<Long> onTimes = activeNotes.get(note);
                            if (onTimes != null && !onTimes.isEmpty()) {
                                long onTime = onTimes.remove(0);
                                boolean isBlackKey = pianoWindow.isBlackKey(note);
                                animationPanel.addFallingNote(note, onTime, timeMillis, isBlackKey);
                            }
                        }
                    }
                }
            }

            tempSequencer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void resetPracticeState() {
        synchronized (currentlyPressedNotes) {
            currentlyPressedNotes.clear();
        }
        awaitedNotes.clear();
        notesPressedInChordAttempt.clear();
        SwingUtilities.invokeLater(pianoWindow::releaseAllKeys);
    }
}