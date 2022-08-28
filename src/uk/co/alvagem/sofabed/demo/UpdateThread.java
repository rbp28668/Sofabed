package uk.co.alvagem.sofabed.demo;

import javax.swing.SwingUtilities;

public class UpdateThread implements Runnable {

	private MainWindow window;
	private boolean stop = false;
	
	UpdateThread(MainWindow window){
		this.window = window;
	}
	
	@Override
	public void run() {
		while(!stop){
			try {
				SwingUtilities.invokeLater( new Runnable() {

					public void run() {
						window.updateValues();
					}
				});
				Thread.sleep(500);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} 
		}
	}

}
