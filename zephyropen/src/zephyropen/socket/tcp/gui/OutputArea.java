package zephyropen.socket.tcp.gui;

import java.io.*;
import java.net.*;
import javax.swing.*;

/**
 * A minimal SWING input field to read from a given socket
 * 
 * @author Brad Zdanivsky Created: 2007.11.3
 */
public class OutputArea extends JTextArea implements Runnable {

	private static final long serialVersionUID = 1L;
	private BufferedReader in = null;

	public OutputArea(Socket socket) {

		// don't allow editing the textArea
		setEditable(false);

		try {
			in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		} catch (Exception e) {
			System.exit(-1);
		}

		// read from sock as a new thread
		Thread thread = new Thread(this);
		thread.start();
	}

	// Manage input coming from server
	public void run() {

		// loop on input from socket
		String input = null;
		while (true) {
			try {

				// block on input and then update text area
				input = in.readLine();
				
				if(input==null) break;

				append(input + "\n");

				// move focus to it new line we just added
				setCaretPosition(getDocument().getLength());

			} catch (Exception e) {
				System.exit(0);
			}
		}
	}
}