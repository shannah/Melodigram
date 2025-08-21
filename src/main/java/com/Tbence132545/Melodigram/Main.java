package com.Tbence132545.Melodigram;

import com.Tbence132545.Melodigram.controller.MainWindowController;
import com.Tbence132545.Melodigram.controller.PlaybackController;
import com.Tbence132545.Melodigram.model.MidiPlayer;
import com.Tbence132545.Melodigram.view.MainWindow;
import com.Tbence132545.Melodigram.view.PianoWindow;

import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.swing.*;
import java.io.FileNotFoundException;
import java.io.InputStream;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiUnavailableException;
public class Main {
        public static void main(String[] args) {
            MainWindowController mainwincon = new MainWindowController(new MainWindow());

        }

    }

