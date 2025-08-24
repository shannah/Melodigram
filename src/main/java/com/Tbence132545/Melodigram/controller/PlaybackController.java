// java
package com.Tbence132545.Melodigram.controller;

import com.Tbence132545.Melodigram.model.MidiPlayer;
import com.Tbence132545.Melodigram.view.AnimationPanel;
import com.Tbence132545.Melodigram.view.PianoWindow;
import com.Tbence132545.Melodigram.view.SeekBar;

import javax.sound.midi.*;
import javax.swing.*;
import javax.swing.Timer;
import java.util.*;

public class PlaybackController {

    //   Constants for Readability  
    private static final int TARGET_FPS = 60;
    private static final int TIMER_DELAY_MS = 1000 / TARGET_FPS;
    private static final long STARTUP_DELAY_MS = 3000;

    private final MidiPlayer midiPlayer;
    private final PianoWindow pianoWindow;
    private final AnimationPanel animationPanel;
    private final SeekBar seekBar;
    private final Timer sharedTimer;

    //   State Fields  
    private long startTime;
    private long lastTickTime;
    private boolean playbackStarted = false;
    private boolean animationPaused = false;
    private boolean isPracticeMode = false;
    private boolean wasPlayingBeforeDrag = false;

    //   MIDI Input and Practice Mode State  
    private MidiDevice midiInputDevice;
    private final List<Integer> currentlyPressedNotes = new ArrayList<>();
    private final List<Integer> awaitedNotes = new ArrayList<>();
    private final Set<Integer> notesPressedInChordAttempt = new HashSet<>();

    public PlaybackController(MidiPlayer midiPlayer, PianoWindow pianoWindow) {
        // 1. Initialize core components
        this.midiPlayer = midiPlayer;
        this.pianoWindow = pianoWindow;
        this.animationPanel = pianoWindow.getAnimationPanel();
        this.seekBar = new SeekBar(midiPlayer.getSequencer());

        // 2. Prepare data and initial UI state
        preprocessNotes(midiPlayer.getSequencer().getSequence());
        animationPanel.setTotalDurationMillis(midiPlayer.getSequencer().getMicrosecondLength() / 1000);
        pianoWindow.addSeekBar(seekBar);

        // 3. Set up all event listeners and callbacks
        setupEventListeners();

        // 4. Initialize and start the main timer
        this.sharedTimer = new Timer(TIMER_DELAY_MS, e -> onTimerTick());
        initializePlayback();
    }

    //   Initialization and Setup  

    private void initializePlayback() {
        startTime = System.currentTimeMillis();
        lastTickTime = startTime;
        sharedTimer.start();
    }

    private void setupEventListeners() {
        midiPlayer.setNoteOnListener(this::onNoteOn);
        midiPlayer.setNoteOffListener(this::onNoteOff);

        pianoWindow.setPlayButtonListener(e -> togglePlayback());
        pianoWindow.setForwardButtonListener(e -> seekAndPreserveState(midiPlayer.getSequencer().getMicrosecondPosition() + 10_000_000));
        pianoWindow.setBackwardButtonListener(e -> seekAndPreserveState(midiPlayer.getSequencer().getMicrosecondPosition() - 10_000_000));

        seekBar.setSeekListener(this::seekAndPreserveState);

        animationPanel.setOnDragStart(this::handleDragStart);
        animationPanel.setOnTimeChange(this::handleDragChange);
        animationPanel.setOnDragEnd(this::handleDragEnd);
    }

    //   Main Timer Logic  

    private void onTimerTick() {
        long now = System.currentTimeMillis();
        long delta = now - lastTickTime;
        lastTickTime = now;

        if (!playbackStarted) {
            handleInitialStartup(now);
            return;
        }

        if (animationPaused) {
            return;
        }

        if (isPracticeMode) {
            handlePracticeModeTick(delta);
        } else {
            // Pass delta to the handler
            handlePlaybackModeTick(delta);
        }

        seekBar.updateProgress();
    }

    private void handleInitialStartup(long now) {
        if (midiPlayer.getSequencer().getMicrosecondLength() > 0 && now - startTime > STARTUP_DELAY_MS) {
            pianoWindow.disableButtons(false);
            if (!isPracticeMode) {
                midiPlayer.play();
            }
            playbackStarted = true;
        } else {
            pianoWindow.disableButtons(true);
        }
    }

    // ==================== FIX IS HERE ====================
    private void handlePlaybackModeTick(long delta) {
        // THIS IS THE KEY: We need both tick() and updatePlaybackTime().
        // tick(delta) is responsible for the smooth frame-by-frame MOVEMENT of notes.
        animationPanel.tick(delta);

        // updatePlaybackTime() is responsible for keeping the animation perfectly
        // SYNCHRONIZED with the audio source of truth.
        if (midiPlayer.isPlaying()) {
            long newTimeMillis = midiPlayer.getSequencer().getMicrosecondPosition() / 1000;
            animationPanel.updatePlaybackTime(newTimeMillis);
        }
    }
    // ======================================================

    private void handlePracticeModeTick(long delta) {
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
                            if (!currentlyPressedNotes.contains(note)) pianoWindow.highlightNote(note);
                        }
                    }
                });
                return;
            }
        }

        long prevTime = animationPanel.getCurrentTimeMillis();
        animationPanel.tick(delta);
        long nextTime = animationPanel.getCurrentTimeMillis();

        List<Integer> onsets = animationPanel.getNotesStartingBetween((prevTime == 0) ? -1 : prevTime, nextTime);

        if (!onsets.isEmpty()) {
            awaitedNotes.clear();
            awaitedNotes.addAll(onsets);
            notesPressedInChordAttempt.clear();
            SwingUtilities.invokeLater(() -> {
                pianoWindow.releaseAllKeys();
                awaitedNotes.forEach(pianoWindow::highlightNote);
            });
        } else if (chordIsSatisfied) {
            awaitedNotes.clear();
        }
    }

    //Event Handling Methods

    private void handleDragStart() {
        wasPlayingBeforeDrag = midiPlayer.isPlaying();
        if (wasPlayingBeforeDrag) {
            midiPlayer.stop();
        }
        animationPaused = true;
    }

    private void handleDragChange(long newTimeMillis) {
        updateSequencerPosition(newTimeMillis * 1000);
    }

    private void handleDragEnd() {
        if (isPracticeMode) {
            // In practice mode, dragging is for navigation. Always resume the animation.
            animationPaused = false;
        } else {
            // In listen/watch mode, respect the state from before the drag began.
            // If it was paused, it stays paused. If it was playing, it resumes.
            animationPaused = !wasPlayingBeforeDrag;
            if (wasPlayingBeforeDrag) {
                midiPlayer.play();
            }
        }
        // Reset the drag state for the next operation in either mode.
        wasPlayingBeforeDrag = false;
    }

    private void seekAndPreserveState(long newMicroseconds) {
        if (isPracticeMode) {
            updateSequencerPosition(newMicroseconds);
            return;
        }

        boolean wasPlaying = midiPlayer.isPlaying();
        if (wasPlaying) midiPlayer.stop();

        updateSequencerPosition(newMicroseconds);

        if (wasPlaying) midiPlayer.play();
    }

    private void updateSequencerPosition(long newMicroseconds) {
        long clampedMicroseconds = Math.max(0, Math.min(newMicroseconds, midiPlayer.getSequencer().getMicrosecondLength()));
        midiPlayer.getSequencer().setMicrosecondPosition(clampedMicroseconds);
        animationPanel.updatePlaybackTime(clampedMicroseconds / 1000);
        resetPracticeState();
        lastTickTime = System.currentTimeMillis();
    }

    void togglePlayback() {
        if(!isPracticeMode){
            if (midiPlayer.isPlaying()) {
                midiPlayer.stop();
                animationPaused = true;
                pianoWindow.setPlayButtonText("▶");

            } else {
                midiPlayer.play();
                animationPaused = false;
                lastTickTime = System.currentTimeMillis();
                pianoWindow.setPlayButtonText("||");
            }
        }

    }

    //   Public API and MIDI Methods  

    public void setPracticeMode(boolean enabled) {
        this.isPracticeMode = enabled;
        pianoWindow.disableButtons(enabled);
        if (enabled) {
            midiPlayer.stop();
            resetPracticeState();
        }
    }

    public void setMidiInputDevice(MidiDevice device) {
        try {
            if (midiInputDevice != null && midiInputDevice.isOpen()) midiInputDevice.close();
            midiInputDevice = device;
            if (!device.isOpen()) device.open();
            device.getTransmitter().setReceiver(new MidiInputReceiver());
        } catch (MidiUnavailableException e) {
            e.printStackTrace();
        }
    }

    //   Private MIDI Callbacks and Helpers  

    private void onNoteOn(int midiNote) {
        if (!isPracticeMode) {
            SwingUtilities.invokeLater(() -> pianoWindow.highlightNote(midiNote));
        }
    }

    private void onNoteOff(int midiNote) {
        if (!isPracticeMode) {
            SwingUtilities.invokeLater(() -> pianoWindow.releaseNote(midiNote));
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

    public void preprocessNotes(Sequence sequence) {
        try (Sequencer tempSequencer = MidiSystem.getSequencer(false)) {
            tempSequencer.open();
            tempSequencer.setSequence(sequence);

            Map<Integer, List<Long>> activeNotes = new HashMap<>();

            for (Track track : sequence.getTracks()) {
                for (int i = 0; i < track.size(); i++) {
                    MidiEvent event = track.get(i);
                    MidiMessage msg = event.getMessage();
                    if (msg instanceof ShortMessage sm) {
                        int cmd = sm.getCommand();
                        int note = sm.getData1();
                        tempSequencer.setTickPosition(event.getTick());
                        long timeMillis = tempSequencer.getMicrosecondPosition() / 1000;

                        if (cmd == ShortMessage.NOTE_ON && sm.getData2() > 0) {
                            activeNotes.computeIfAbsent(note, k -> new ArrayList<>()).add(timeMillis);
                        } else if (cmd == ShortMessage.NOTE_OFF || (cmd == ShortMessage.NOTE_ON && sm.getData2() == 0)) {
                            List<Long> onTimes = activeNotes.get(note);
                            if (onTimes != null && !onTimes.isEmpty()) {
                                long onTime = onTimes.remove(0);
                                animationPanel.addFallingNote(note, onTime, timeMillis, pianoWindow.isBlackKey(note));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //   Inner Class for MIDI Receiver  

    private class MidiInputReceiver implements Receiver {
        @Override
        public void send(MidiMessage message, long timeStamp) {
            if (!isPracticeMode || !(message instanceof ShortMessage sm)) return;

            int command = sm.getCommand();
            int note = sm.getData1();
            int velocity = sm.getData2();

            if (command == ShortMessage.NOTE_ON && velocity > 0) {
                synchronized (currentlyPressedNotes) {
                    if (!currentlyPressedNotes.contains(note)) {
                        currentlyPressedNotes.add(note);
                        notesPressedInChordAttempt.add(note);
                        SwingUtilities.invokeLater(() -> pianoWindow.highlightNote(note));
                    }
                }
            } else if (command == ShortMessage.NOTE_OFF || (command == ShortMessage.NOTE_ON && velocity == 0)) {
                synchronized (currentlyPressedNotes) {
                    currentlyPressedNotes.remove((Integer) note);
                    SwingUtilities.invokeLater(() -> pianoWindow.releaseNote(note));
                }
            }
        }

        @Override
        public void close() {}
    }
}