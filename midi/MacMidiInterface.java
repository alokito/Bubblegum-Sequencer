package midi;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.ShortMessage;

import de.humatic.mmj.MidiOutput;
import de.humatic.mmj.MidiSystem;

public class MacMidiInterface implements MidiInterface {
	private int channel = 1;
	
	MidiOutput out;
	
	/* (non-Javadoc)
	 * @see MidiInterface#killNotes()
	 */
	public void killNotes() {
		// TODO Auto-generated method stub
		
	}

	/* (non-Javadoc)
	 * @see MidiInterface#sendNote(int, int, int)
	 */
	public void sendNote(int note, int velocity, int channel) {
		sendNote(note, velocity);
	}

	/* (non-Javadoc)
	 * @see MidiInterface#getChannel()
	 */
	public int getChannel() {
		return channel;
	}

	/* (non-Javadoc)
	 * @see MidiInterface#setChannel(int)
	 */
	public void setChannel(int channel) {
		this.channel = channel;
	}

	public MacMidiInterface() {
		out = MidiSystem.openMidiOutput(0);
	}

	/* (non-Javadoc)
	 * @see MidiInterface#sendNote(int, int)
	 */
	public void sendNote(int note, int velocity) {
//		System.out.println("receiver: " + out);

		ShortMessage msg = new ShortMessage();
		try {
			msg.setMessage(ShortMessage.NOTE_ON, channel, note, velocity);
			out.sendMidi(msg.getMessage());
//			msg.setMessage(ShortMessage.NOTE_OFF, channel, note, velocity);
//			out.sendMidi(msg.getMessage());
		} catch (InvalidMidiDataException imde) {
			imde.printStackTrace();
		}
		
	}
}
