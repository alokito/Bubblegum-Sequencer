package midi;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiDevice.Info;

public class MacMidiInterface implements MidiInterface {
	private int channel = 1;
	
	MidiDevice out;

	private Receiver receiver;
	
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

	public MacMidiInterface() throws MidiUnavailableException {
		System.out.println("have " + MidiSystem.getMidiDeviceInfo().length + " devices");
		Info info = null;
		for (Info output : MidiSystem.getMidiDeviceInfo()) {
			System.out.println("have device " + output.getName());
			if (MidiSystem.getMidiDevice(output).getMaxReceivers() != 0) {
				info = output;
				System.out.println("using device for output " + output.getName());
			}
		}
		
		
		out = MidiSystem.getMidiDevice(info);
        try{
            out.open();
            receiver = out.getReceiver();
	    }catch (MidiUnavailableException mue){
            System.out.println("can't open MidiOut : " +mue.toString());
	    }
	}

	/* (non-Javadoc)
	 * @see MidiInterface#sendNote(int, int)
	 */
	public void sendNote(int note, int velocity) {
//		System.out.println("receiver: " + out);

		ShortMessage msg = new ShortMessage();
		try {
			msg.setMessage(ShortMessage.NOTE_ON, channel, note, velocity);
			
			try {
				out.getReceiver().send(msg,0);
			} catch (MidiUnavailableException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
//			msg.setMessage(ShortMessage.NOTE_OFF, channel, note, velocity);
//			out.sendMidi(msg.getMessage());
		} catch (InvalidMidiDataException imde) {
			imde.printStackTrace();
		}
		
	}
}
