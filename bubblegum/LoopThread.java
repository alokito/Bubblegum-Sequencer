package bubblegum;

import midi.MidiInterface;
import midi.MidiInterfaceFactory;
//import serial.SerialComm;
//import serial.SerialCommFactory;

public class LoopThread extends Thread {
	MidiInterface midiOut = MidiInterfaceFactory.getMidiInterface();

	Sequencer seq;

	int pause = 125;

//	SerialComm serial;
	private boolean useSerial = false;
	
	private boolean running = false;

//	Visualization vis;

	public void run() {
		running = true;
		while (running) {
			try {
				boolean playedNote;
				for (int i = 0; i < 16; i++) {
					int[] notes = seq.getNotesAt(i);
					if (i % 2 == 0)
						midiOut.killNotes();
					playedNote = false;
					for (int j = 0; j < notes.length; j++) {
						if (notes[j] != 0) {
							switch (notes[j]) {
							case 39: {
								if (!playedNote) {
									if (j == 0) {
										if (notes[j + 1] == 39) {
											midiOut.sendNote(42, 127, 0);
											playedNote = true;
										} else {
											midiOut.sendNote(43, 127, 0);
											playedNote = true;
										}
									} else if (j == 1) {
										if (notes[j + 1] == 39) {
											midiOut.sendNote(39, 127, 0);
											playedNote = true;
										} else {
											midiOut.sendNote(41, 127, 0);
											playedNote = true;
										}
									} else if (j == 2) {
										if (notes[j + 1] == 39) {
											midiOut.sendNote(34, 127, 0);
											playedNote = true;
										} else {
											midiOut.sendNote(36, 127, 0);
											playedNote = true;
										}
									} else {
										midiOut.sendNote(31, 127, 0);
										playedNote = true;
									}
									break;
								}
							}
							default: {
								if (notes[j] != 39) {
									midiOut.sendNote(notes[j], 127);
//									System.out.println("sent " + notes[j]);
								}
							}
							}
						}
						// System.out.println("i: " + i + ", j: " + j);
//						if (seq.isVisualize() && notes[j] > 0) {
//							vis.showBubble(i, j, notes[j]);
//						}
					}
//					if (i % 4 == 0 && useSerial) {
//						serial.LightLed((i / 4) + 1);
//					}
					Thread.sleep(pause);
//					if (seq.isVisualize()){
//						int c = (i + 15) % 16;
//						vis.clearColumn(c);
//					}
				}
			} catch (InterruptedException ie) {
			}
		}
	}
	
	public void stopPlayback() {
		running = false;
	}

	public LoopThread(Sequencer seq) {
		this.seq = seq;
//		if (seq.isVisualize()) {
//			this.vis = new Visualization();
//		}
		if (useSerial) {
//		serial = SerialCommFactory.getSerialComm();
			// Pass reference to sequencer (used for bpm callback).
//		serial.setSequencer(seq);
		}
	}

	public void setTempo(int bpm) {
		pause = (int) (15000 / bpm);
	}
}
