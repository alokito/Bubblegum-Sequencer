package midi;

public abstract class MidiInterfaceFactory {
	public static MidiInterface getMidiInterface() {
		ClassLoader loader = ClassLoader.getSystemClassLoader();
		MidiInterface midi = null;
		try {
			String osname = System.getProperty("os.name","").toLowerCase();
		      if ( osname.startsWith("windows") ) {
		         // windows
		    	  midi = (MidiInterface) loader.loadClass("midi.WinMidiInterface").newInstance();
		      } else if ( osname.startsWith("mac") ) {
		    	  midi = (MidiInterface) loader.loadClass("midi.MacMidiInterface").newInstance();
		      }
		} catch (ClassNotFoundException cnfe) {
			
		} catch (IllegalAccessException ie) {
			
		} catch (InstantiationException ine) {
			
		}
		return midi;
	}
}
