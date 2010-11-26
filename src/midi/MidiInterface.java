package midi;
public interface MidiInterface {

	/**
	 * @return the channel
	 */
	public abstract int getChannel();

	/**
	 * @param channel
	 *            the channel to set
	 */
	public abstract void setChannel(int channel);

	/**
	 * @param args
	 */
	public abstract void sendNote(int note, int velocity);
	public abstract void sendNote(int note, int velocity, int channel);
	public abstract void killNotes();
}