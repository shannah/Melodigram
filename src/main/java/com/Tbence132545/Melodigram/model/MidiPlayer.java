package com.Tbence132545.Melodigram.model;

import javax.sound.midi.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.function.IntConsumer;
public class MidiPlayer {
    private Sequencer sequencer;
    private IntConsumer noteOnListener;
    private IntConsumer noteOffListener;

    public MidiPlayer() {
        try {
            // Open sequencer without default synthesizer
            sequencer = MidiSystem.getSequencer(false);
            sequencer.open();

            // Open synthesizer to actually play the sounds
            Synthesizer synth = MidiSystem.getSynthesizer();
            synth.open();

            // Connect sequencer transmitter to a custom receiver which:
            // - calls your note listeners
            // - forwards all messages to synth receiver for sound playback
            Transmitter transmitter = sequencer.getTransmitter();
            Receiver synthReceiver = synth.getReceiver();

            transmitter.setReceiver(new Receiver() {
                public void send(MidiMessage message, long timeStamp) {
                    if (message instanceof ShortMessage sm) {
                        if (sm.getCommand() == ShortMessage.NOTE_ON && sm.getData2() > 0) {
                            if (noteOnListener != null) noteOnListener.accept(sm.getData1());
                        } else if (sm.getCommand() == ShortMessage.NOTE_OFF ||
                                (sm.getCommand() == ShortMessage.NOTE_ON && sm.getData2() == 0)) {
                            if (noteOffListener != null) noteOffListener.accept(sm.getData1());
                        }
                    }
                    // Forward the message for actual sound playback
                    synthReceiver.send(message, timeStamp);
                }
                public void close() {}
            });

            // Meta event listener for end of track
            sequencer.addMetaEventListener(meta -> {
                if (meta.getType() == 47) {
                    sequencer.stop();
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    // A method later replaced, used for loading from filePath instead of the resources folder
    public void loadMidiFromFile(String filePath) {
        try {
            Sequence sequence = MidiSystem.getSequence(new File(filePath));
            sequencer.setSequence(sequence);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void loadMidiFromResources(String fileName) {
        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream(fileName);
            if (is == null) throw new FileNotFoundException("MIDI file not found: " + fileName);

            Sequence sequence = MidiSystem.getSequence(is);
            sequencer.setSequence(sequence);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    //Function used for extracting the note range, we have to know the range of notes for the animation
    public static int[] extractNoteRange(Sequence sequence) {
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;

        for (Track track : sequence.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                MidiEvent event = track.get(i);
                MidiMessage msg = event.getMessage();
                if (msg instanceof ShortMessage sm) {
                    int cmd = sm.getCommand();
                    int note = sm.getData1();
                    if ((cmd == ShortMessage.NOTE_ON && sm.getData2() > 0) || cmd == ShortMessage.NOTE_OFF) {
                        lowest = Math.min(lowest, note);
                        highest = Math.max(highest, note);
                    }
                }
            }
        }

        if (lowest == Integer.MAX_VALUE || highest == Integer.MIN_VALUE) {
            lowest = 60;
            highest = 72;
        }

        return new int[]{lowest, highest};
    }

    //Starts the sequencer
    public void play() {
            sequencer.start();
    }
    //Checks if the sequencer is running
    public boolean isPlaying(){
        return sequencer.isRunning();
    }
    //Stops the sequencer
    public void stop() {
        sequencer.stop();
    }
    //Sets Listener for NoteON events
    public void setNoteOnListener(IntConsumer listener) {
        this.noteOnListener = listener;
    }
    //Sets Listener for NoteOff events
    public void setNoteOffListener(IntConsumer listener) {
        this.noteOffListener = listener;
    }
    //Returns the sequencer
    public Sequencer getSequencer() {
        return this.sequencer;
    }
    public void SetSequencer(Sequencer sequencer) {
        this.sequencer = sequencer;
    }

}
