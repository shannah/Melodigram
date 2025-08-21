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
//Class used for connecting the PianoWindow to the logic
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

    private MidiDevice midiInputDevice; // Holds the MIDI input device chosen externally

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
            if (animationPaused) { return; }
            long newMillis = newMicroseconds / 1000;
            animationPanel.updatePlaybackTime(newMillis);
            lastTickTime = System.currentTimeMillis(); // avoid jumpy delta
        });
        pianoWindow.setForwardButtonListener(e -> {
            midiPlayer.forward();
            // Important: Sync the animation panel with the new player time
            long newTimeMillis = midiPlayer.getSequencer().getMicrosecondPosition() / 1000;
            animationPanel.updatePlaybackTime(newTimeMillis);
        });

        // --- AND ADD THIS BLOCK BACK IN ---
        pianoWindow.setBackwardButtonListener(e -> {
            midiPlayer.backward();
            // Important: Sync the animation panel with the new player time
            long newTimeMillis = midiPlayer.getSequencer().getMicrosecondPosition() / 1000;
            animationPanel.updatePlaybackTime(newTimeMillis);
        });
        lastTickTime = System.currentTimeMillis();

        // Unified timer for animation and UI updates
        sharedTimer = new Timer(1000 / 60, e -> {
            long now = System.currentTimeMillis();
            long delta = now - lastTickTime;
            lastTickTime = now;

            if (!playbackStarted) {
                if (midiPlayer.getSequencer().getMicrosecondLength() > 0 &&
                        now - startTime > 3000) {
                    pianoWindow.disableButtons(false);
                    if(!isPracticeMode){
                        midiPlayer.play();
                    }
                    playbackStarted = true;
                } else {
                    pianoWindow.disableButtons(true);
                    return;
                }
            }

            if (animationPaused) return;

            // --- PRACTICE MODE CHECK ---
            if (isPracticeMode) {
                List<Integer> expectedNotes = animationPanel.getActiveNotesToBePlayed(animationPanel.getCurrentTimeMillis());
                for (Integer note :expectedNotes) {
                    pianoWindow.highlightNote(note);
                }
                if (!expectedNotes.isEmpty()) {
                    synchronized (currentlyPressedNotes) {
                        List<Integer> pressed = new ArrayList<>(currentlyPressedNotes);
                        List<Integer> expected = new ArrayList<>(expectedNotes);

                        pressed.sort(Integer::compareTo);
                        expected.sort(Integer::compareTo);

                        if (!pressed.equals(expected)) {
                            return;
                        }
                        else{
                            for (Integer note: expectedNotes){
                                pianoWindow.releaseNote(note);
                            }
                        }
                    }
                }
            }

            animationPanel.tick(delta);
            seekBar.updateProgress();
        });

        startTime = System.currentTimeMillis();
        sharedTimer.start();
    }

    public void setPracticeMode(boolean enabled){
        this.isPracticeMode = enabled;
        pianoWindow.disableButtons(enabled);
        // No automatic MIDI input initialization here anymore
    }

    /**
     * Call this from outside, after the user selects a MIDI input device.
     * It opens the device and installs a Receiver to listen to MIDI input messages.
     */
    public void setMidiInputDevice(MidiDevice device) {
        try {
            if (midiInputDevice != null && midiInputDevice.isOpen()) {
                midiInputDevice.close();
            }
            midiInputDevice = device;
            if (!device.isOpen()) {
                device.open();
            }
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
                                    SwingUtilities.invokeLater(() -> pianoWindow.highlightNote(note));
                                }
                            }
                        } else if (command == ShortMessage.NOTE_OFF || (command == ShortMessage.NOTE_ON && sm.getData2() == 0)) {
                            synchronized (currentlyPressedNotes) {
                                currentlyPressedNotes.remove((Integer) note);
                                SwingUtilities.invokeLater(() -> pianoWindow.releaseNote(note));
                            }
                        }
                    }
                }

                @Override
                public void close() {}
            });
        } catch (MidiUnavailableException e) {
            e.printStackTrace();
        }
    }

    void onNoteOn(int midiNote){
        SwingUtilities.invokeLater(() -> pianoWindow.highlightNote(midiNote));
    }

    private void onNoteOff(int midiNote){
        SwingUtilities.invokeLater(() -> pianoWindow.releaseNote(midiNote));
    }

    void togglePlayback() {
        if (midiPlayer.isPlaying()) {
            midiPlayer.stop();
            animationPaused = true;
            SwingUtilities.invokeLater(() -> pianoWindow.setPlayButtonText("||"));
        } else {
            midiPlayer.play();
            sharedTimer.start();
            animationPaused = false;
            SwingUtilities.invokeLater(() -> pianoWindow.setPlayButtonText("▶"));
        }
    }

    public void preprocessNotes(Sequence sequence) {
        try {
            // Use a **temporary sequencer** just for timing info
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
}