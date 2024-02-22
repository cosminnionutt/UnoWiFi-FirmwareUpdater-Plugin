/*
 * This file is part of WiFi101 Updater Arduino-IDE Plugin.
 * Copyright 2016 Arduino LLC (http://www.arduino.cc/)
 *
 * Arduino is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * As a special exception, you may use this file as part of a free software
 * library without restriction.  Specifically, if other files instantiate
 * templates or use macros or inline functions from this file, or you compile
 * this file and link it with other files to produce an executable, this
 * file does not by itself cause the resulting executable to be covered by
 * the GNU General Public License.  This exception does not however
 * invalidate any other reasons why the executable file might be covered by
 * the GNU General Public License.
 */

 /*
  * This file is based on WiFi101 Updater Arduino-IDE Plugin and it's part
  * of Uno WiFi Updater Arduino-IDE Plugin.
  *
  * Jan 2017
  *
  */

package cc.arduino.plugins.unowifi.flashers.cli;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Map;
import java.util.HashMap;

import cc.arduino.plugins.unowifi.firmwares.UnoWiFiFirmware;
import cc.arduino.plugins.unowifi.flashers.Flasher;
import processing.app.debug.MessageSiphon;
import processing.app.helpers.OSUtils;
import processing.app.helpers.ProcessUtils;

public abstract class CLIFlasher implements Flasher {

	private File executable;
	private int progressCounter;

	public CLIFlasher() throws Exception {
		try {
			String jarPath = CLIFlasher.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
			File jarFolder = new File(jarPath).getParentFile();

			String toolName = "bin/esptool";
			if (OSUtils.isWindows()) {
				toolName += "-windows.exe";
			} else if (OSUtils.isMacOS()) {
				toolName += "-macosx";
			} else if (OSUtils.isLinux()) {
				String arch = System.getProperty("os.arch");
				if (arch.equals("arm")) {
					toolName += "-linuxarm";
				} else if (arch.contains("amd64")) {
					toolName += "-linux64";
				} else {
					toolName += "-linux32";
				}
			}
			executable = new File(jarFolder, toolName);
		} catch (URISyntaxException e) {
			throw new Exception("Can't find flasher tool");
		}
	}

	@Override
	public void testConnection(String port) throws Exception {
		List<String> cmd = new ArrayList<>();
		cmd.add(executable.getAbsolutePath());
		cmd.add("--port");
		cmd.add(port);

		cmd.add("--baud");
		cmd.add("9600");

		cmd.add("read_mac");

		int r = runTool(cmd.toArray(new String[0]), 60*1000);
		if (r != 0)
			throw new Exception("Can't communicate with programmer!");
	}

	@Override
	public void updateFirmware(String port, UnoWiFiFirmware fw) throws Exception {
		List<String> cmd = new ArrayList<>();
		cmd.add(executable.getAbsolutePath());
		//PORT
		cmd.add("--port");cmd.add(port);
		//BAUD RATE
		cmd.add("--baud");cmd.add("9600");
		//COMMAND
		cmd.add("write_flash");
		//COMMAND OPTION FLASH FREQ
		cmd.add("-ff");cmd.add("80m");
		//COMMAND OPTION FLASH MODE
		cmd.add("-fm");cmd.add("qio");
		//COMMAND OPTION FLASH SIZE
		cmd.add("-fs");cmd.add("32m");
		//COMMAND OPTION ADDRESSES and FILES
		for (String key : fw.getFiles().keySet()) {
			cmd.add(key);
			cmd.add(fw.getFiles().get(key).getAbsolutePath());
		}

		int r = runTool(cmd.toArray(new String[0]),  10*60*1000);
		if (r != 0)
			throw new Exception("An error occurred while programming");
	}

/*	@Override
	public void uploadCertificates(String port, List<String> websites) throws Exception {
		List<String> cmd = new ArrayList<>();
		cmd.add(executable.getAbsolutePath());
		cmd.add("-port");
		cmd.add(port);
		for (String website : websites) {
			cmd.add("-address");
			cmd.add(website);
		}
		int r = runTool(cmd.toArray(new String[0]), 60000);
		if (r != 0)
			throw new Exception("An error occurred while programming");
	}*/

	private int runTool(String[] cmd, int timeout) throws IOException, InterruptedException {
		Process proc = ProcessUtils.exec(cmd);
		progressCounter = 0;

		MessageSiphon in = new MessageSiphon(proc.getInputStream(), (msg) -> {
			progressCounter += 10;
			if (msg.length() >= 20) {
				// Crop timestamp
				msg = msg.substring(0,20) + "...";
			}

			progress(progressCounter, msg);
			try { java.util.concurrent.TimeUnit.SECONDS.sleep(1); } catch(InterruptedException ex) {}
		});
		MessageSiphon err = new MessageSiphon(proc.getErrorStream(), (msg) -> {
			progressCounter += 10;
			if (msg.length() >= 20) {
				// Crop timestamp
				msg = msg.substring(0,20) + "...";
			}

			progress(progressCounter, msg);
			try { java.util.concurrent.TimeUnit.SECONDS.sleep(1); } catch(InterruptedException ex) {}
		});


		Timer terminationTimer = new Timer();
		terminationTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				proc.destroy();
			}
		}, timeout);
		in.join();
		err.join();
		int r = proc.waitFor();
		terminationTimer.cancel();
		return r;
	}

}
