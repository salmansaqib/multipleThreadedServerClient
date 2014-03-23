import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

//import server.Server.Streams;

public class ServeClient implements Runnable {

	public static void println(Object s) {
		System.out.println(s);
	}

	public static void print(Object s) {
		System.out.print(s);
	}

	private Socket socket;
	private BufferedReader in = null;
	private ArrayList<String> transientReadLockedFilesList = new ArrayList<String>();
	private ArrayList<String> transientWriteLockedFilesList = new ArrayList<String>();

	public ServeClient(Socket client) {
		this.socket = client;
	}

	@Override
	public void run(){
		try {
			in = new BufferedReader(new InputStreamReader(
					socket.getInputStream()));

			String clientMsg;
			boolean run = true;
			while (run == true) {
				clientMsg = in.readLine();
				if (clientMsg != null) {
					switch (clientMsg) {
					case "1":
						System.out.print("\nCommand from " + socket.getInetAddress() + ":"+ socket.getPort()+" : ");
						System.out.println("Upload file");
						DataInputStream in = new DataInputStream(socket.getInputStream());
						DataOutputStream out = new DataOutputStream(socket.getOutputStream());
						String file_name = in.readUTF();

						File file_to_upload = new File(file_name);

						if (file_to_upload.exists()) { //if file exists on server
							out.writeUTF("yes");
							out.writeUTF("A file with the name " + file_name + " already exists on server");
							System.out.println("A file with the name " + file_name + " already exists");
							String wlocked = in.readUTF();

							switch (wlocked) {
							case "go":
								String overwrite = in.readUTF();
								switch (overwrite) {
								case "a":
									receiveFile(file_name);
									backupReceivedFile(file_name);
									System.out.println("Overwrite Successful");
									System.out.println("File "+file_name+" backed up");
									break;
								case "b":
									System.out.println("Overwrite aborted");
									break;
								default:
									System.out.println("Invalid input from client");
									break;						
								}
								break;

							case "nogo":
								System.out.println("Client cannot overwrite " + file_name + " without having a write lock on it");
								break;
							}
						}
						else {
							out.writeUTF("no");
							receiveFile(file_name);
							backupReceivedFile(file_name);
							System.out.println("Download Successful");
						}
						break;

					case "1fail":
						//In case client fails to find the file it wants to upload
						System.out.print("\nCommand from " + socket.getInetAddress() + ":"+ socket.getPort()+" : ");
						System.out.println("Upload file");
						System.out.println("Client could not upload: File did not exist on client machine\n");
						break;					

					case "2":
						//'Read File' command from client
						System.out.print("\nCommand from " + socket.getInetAddress() + ":"+ socket.getPort()+" : ");
						System.out.println("Read file");
						readFile();
						break;

					case "3":
						//Read lock a file
						System.out.print("\nCommand from " + socket.getInetAddress() + ":"+ socket.getPort()+" : ");
						System.out.println("Read lock a file");
						readLock();
						break;

					case "3a":
						//Read lock a file
						remoteReadLock();					
						break;

					case "4":
						//Release read-lock
						System.out.print("\nCommand from " + socket.getInetAddress() + ":"+ socket.getPort()+" : ");
						System.out.println("Release Read lock");
						releaseReadLock();
						break;

					case "4a":
						//Release read-lock
						remoteReleaseReadLock();
						break;

					case "5":
						//Write lock a file
						System.out.print("\nCommand from " + socket.getInetAddress() + ":"+ socket.getPort()+" : ");
						System.out.println("Write lock a file");
						writeLock();
						break;

					case "5a":
						//Write lock a file
						remoteWriteLock();
						break;

					case "6":
						//Release write-lock
						System.out.print("\nCommand from " + socket.getInetAddress() + ":"+ socket.getPort()+" : ");
						System.out.println("Release Write lock");
						releaseWriteLock();
						break;

					case "6a":
						//Release write-lock
						remoteReleaseWriteLock();
						break;	

					case "7":
						//'Delete file' command from client
						System.out.print("\nCommand from " + socket.getInetAddress() + ":"+ socket.getPort()+" : ");
						System.out.println("Delete file");
						deleteFile();
						break;

					case "8":
						//'List files in server storage' command from client
						System.out.print("\nCommand from " + socket.getInetAddress() + ":"+ socket.getPort()+" : ");
						System.out.println("List files on server");
						listFilesOnServer();
						System.out.println("Done");
						break;

					case "9":
						//List client locks
						System.out.print("\nCommand from " + socket.getInetAddress() + ":"
								+ socket.getPort()+" : ");
						System.out.println("List client locks");
						System.out.println("Done");
						break;

					case "10":
						//List all locks
						System.out.print("\nCommand from " + socket.getInetAddress() + ":"
								+ socket.getPort()+" : ");
						System.out.println("List all current locks");
						listAllLocks();
						System.out.println("Done");
						break;

					case "11":
						//'Quit server' command from client (The server still listens 
						//on the same port for other clients to connect)
						System.out.print("\nCommand from " + socket.getInetAddress() + ":"
								+ socket.getPort()+" : ");
						System.out.println("Quit server");
						quitServer();
						run = false;
						return;

					case "12":
						//'Server Shut Down' command from client (Server stops listening on the designated port. New port needs to be 
						//assigned at this point.)
						System.out.print("\nCommand from " + socket.getInetAddress() + ":"+ socket.getPort()+" : ");
						System.out.println("Shut Server Down");
						closeServer();
						break;

					default:
						println("Command not recognized");
						break;
					}
				}
				else {
					run = false;
				}

			}

		}//try run()
		catch (Throwable e) {
			println("_");
		}
		finally{
			for (String readLockedFile: transientReadLockedFilesList ) {
				Server.read_locked_files.remove(readLockedFile);
			}
			for (String writeLockedFile: transientWriteLockedFilesList ) {
				Server.write_locked_files.remove(writeLockedFile);
			}
			println("Connection lost with client");
		}
	}//run def

	public void receiveFile(String fileName) {
		try {
			int bytesRead;
			DataInputStream clientDataStream = new DataInputStream(socket.getInputStream());
			OutputStream output = new FileOutputStream((fileName));
			long size = clientDataStream.readLong();
			byte[] buffer = new byte[1024];

			while (size > 0 && (bytesRead = clientDataStream.read(buffer, 0, (int) Math.min(buffer.length, size))) != -1) {
				output.write(buffer, 0, bytesRead);
				size -= bytesRead;
			}
			output.close();
			println("File "+fileName+" received from client.");
		}
		catch (IOException e) {
			println(e);
		}
	}

	public void readFile() throws IOException {
		DataInputStream in = new DataInputStream(socket.getInputStream());
		DataOutputStream out = new DataOutputStream(socket.getOutputStream());

		String read_file_name = in.readUTF();
		println("Requested file: " + read_file_name);

		if(Files.exists(Paths.get(read_file_name)) == true) {
			out.writeUTF("go");
			String read = in.readUTF();

			switch(read){
			case "go":
				BufferedReader fileread = new BufferedReader(new FileReader(read_file_name));
				BufferedReader fileread1 = new BufferedReader(new FileReader(read_file_name)); 
				String line;
				int i = 0; 
				while((line = fileread.readLine()) != null){
					i++;
				}

				String next_line_count = Integer.toString(i);
				out.writeUTF(next_line_count);

				while((line = fileread1.readLine()) != null){
					out.writeBytes(line + "\n");
				}
				println("Contents of " + read_file_name + " sent to client");
				break;

			case "nogo":
				println("Client does not have read lock on this file");
				break;
			}

		}
		else{
			//find if the file exists on another server
			String otherServerInfo = find(read_file_name);

			//If no server has the file
			if (otherServerInfo.equals("no")){
				out.writeUTF("nogo");
				System.out.println("File does not exist");
			}

			//if another server has the file
			else {
				out.writeUTF("on another server");
				System.out.println(" ");
				out.writeUTF(otherServerInfo);
			}
		}
	}

	public void readLock() throws IOException {
		DataInputStream in = new DataInputStream(socket.getInputStream());
		DataOutputStream out = new DataOutputStream(socket.getOutputStream());
		String read_l_file = in.readUTF();

		String read_l_file_path = read_l_file;
		System.out.println(" ");
		System.out.println("Client requested to read lock " + read_l_file);

		if(Files.exists(Paths.get(read_l_file_path)) == true) {
			if (Server.write_locked_files.contains(read_l_file) == false) {
				out.writeUTF("ok");
				String rlock = in.readUTF();

				switch(rlock){
				case "go":
					Server.read_locked_files.add(read_l_file);

					/* To identify the files locked by the client currently connected to this server
					 * an ArrayList keeps track of all the locked files. This list is then used to clean
					 * up the locks on those files in case the client dies without properly disconnecting. */ 
					transientReadLockedFilesList.add(read_l_file);

					/* If a file is locked by a client, the backup image of that file(if any), on another 
					 * server, must also be locked so that in the event of this server dying unexpectedly 
					 * the client can still retain the lock on the file and access the file as necessary  */					

					String duplicateFileLocation = find(read_l_file);

					if (!duplicateFileLocation.equals("no")) {
						String duplicateFileLocationPort = (duplicateFileLocation.substring(duplicateFileLocation.indexOf(":")+1));
						String duplicateFileLocationHost = (duplicateFileLocation.substring(0, duplicateFileLocation.indexOf(":")));

						for (Server.Streams readLockRedundant: Server.comChannels){
							if (readLockRedundant.host.equals(duplicateFileLocationHost) 
									&& readLockRedundant.port.equals(duplicateFileLocationPort)){
								readLockRedundant.outStream.writeUTF("readLockRedundantFile");
								readLockRedundant.outStream.writeUTF(read_l_file);
								break;
							}
						}
					}

					System.out.println("Read locked " + read_l_file);
					System.out.println("One more client read-locked "+ read_l_file);

					break;

				case "nogo":
					System.out.println("Client already has a read lock on this file");
					break;
				}
			}
			else{
				out.writeUTF("cant");
				System.out.println("Cannot grant read lock\nFile is currently write locked");
			}
		}
		else{
			//find if the file exists on another server
			String otherServerInfo = find(read_l_file);

			//If no server has the file
			if (otherServerInfo.equals("no")){
				out.writeUTF("no");
				System.out.println("File does not exist");
			}

			//if another server has the file
			else {
				out.writeUTF("on another server");
				System.out.println(" ");
				out.writeUTF(otherServerInfo);
			}
		}		
	}

	public void remoteReadLock() throws IOException {
		DataInputStream in = new DataInputStream(socket.getInputStream());
		DataOutputStream out = new DataOutputStream(socket.getOutputStream());

		String read_l_file = in.readUTF();

		switch (read_l_file){
		case " ":
			out.writeUTF("no");
			break;

		default:
			if (Server.write_locked_files.contains(read_l_file) == false) {

				out.writeUTF("ok");
				String rlock = in.readUTF();

				switch(rlock){
				case "go":
					Server.read_locked_files.add(read_l_file);
					/* If a file is locked by a client, the backup image of that file(if any), on another 
					 * server, must also be locked so that in the event of this server dying unexpectedly 
					 * the client can still retain the lock on the file and access the file as necessary  */

					String duplicateFileLocation = find(read_l_file);

					if (!duplicateFileLocation.equals("no")) {
						String duplicateFileLocationPort = (duplicateFileLocation.substring(duplicateFileLocation.indexOf(":")+1));
						String duplicateFileLocationHost = (duplicateFileLocation.substring(0, duplicateFileLocation.indexOf(":")));


						for (Server.Streams readLockRedundant: Server.comChannels){

							if (readLockRedundant.host.equals(duplicateFileLocationHost) 
									&& readLockRedundant.port.equals(duplicateFileLocationPort)){
								readLockRedundant.outStream.writeUTF("readLockRedundantFile");
								readLockRedundant.outStream.writeUTF(read_l_file);
								break;
							}
						}
					}
					break;

				case "nogo":
					break;
				}
			}
			else{
				out.writeUTF("cant");
			}
			break;
		}
	}

	public void releaseReadLock() throws IOException {
		DataInputStream in = new DataInputStream(socket.getInputStream());
		DataOutputStream out = new DataOutputStream(socket.getOutputStream());

		String readunlock_file_name = in.readUTF();
		System.out.println("Client requested to release read lock from " + readunlock_file_name);

		if(Files.exists(Paths.get(readunlock_file_name)) == true){
			out.writeUTF("ok");
			String runlock = in.readUTF();

			switch(runlock){
			case "go":
				if (Server.read_locked_files.contains(readunlock_file_name)==true){
					Server.read_locked_files.remove(readunlock_file_name);
					transientReadLockedFilesList.remove(readunlock_file_name);
					if (Server.write_locked_files.contains(readunlock_file_name)==true) {
						Server.write_locked_files.remove(readunlock_file_name);
						transientWriteLockedFilesList.remove(readunlock_file_name);
					}

					/* If a file is locked by a client, the backup image of that file(if any), on another 
					 * server, must also be locked so that in the event of this server dying unexpectedly 
					 * the client can still retain the lock on the file and access the file as necessary  */
					String duplicateFileLocation = find(readunlock_file_name);

					if (!duplicateFileLocation.equals("no")) {
						String duplicateFileLocationPort = (duplicateFileLocation.substring(duplicateFileLocation.indexOf(":")+1));
						String duplicateFileLocationHost = (duplicateFileLocation.substring(0, duplicateFileLocation.indexOf(":")));

						for (Server.Streams readLockRedundant: Server.comChannels){
							if (readLockRedundant.host.equals(duplicateFileLocationHost) 
									&& readLockRedundant.port.equals(duplicateFileLocationPort)){
								readLockRedundant.outStream.writeUTF("readUnlockRedundantFile");
								readLockRedundant.outStream.writeUTF(readunlock_file_name);
								break;
							}
						}
					}

					System.out.println("Released Read lock from " + readunlock_file_name);
				}
				break;

			case "nogo":
				System.out.println("File is not read-locked by "+socket.getInetAddress() + ":"+ socket.getPort());
				break;
			}
		}
		else{
			//find if the file exists on another server
			String otherServerInfo = find(readunlock_file_name);

			//If no server has the file
			if (otherServerInfo.equals("no")){
				out.writeUTF("no");
				System.out.println("File does not exist");
			}

			//if another server has the file
			else {
				out.writeUTF("on another server");
				System.out.println(" ");
				out.writeUTF(otherServerInfo);
			}
		}
	}

	public void remoteReleaseReadLock() throws IOException {
		DataInputStream in = new DataInputStream(socket.getInputStream());
		DataOutputStream out = new DataOutputStream(socket.getOutputStream());

		String readunlock_file_name = in.readUTF();

		if(Files.exists(Paths.get(readunlock_file_name)) == true) {
			out.writeUTF("ok");
			String runlock = in.readUTF();

			switch(runlock){
			case "go":
				if (Server.read_locked_files.contains(readunlock_file_name)==true){
					Server.read_locked_files.remove(readunlock_file_name);
					if (Server.write_locked_files.contains(readunlock_file_name)==true) {
						Server.write_locked_files.remove(readunlock_file_name);
					}


					/* If a file is locked by a client, the backup image of that file(if any), on another 
					 * server, must also be locked so that in the event of this server dying unexpectedly 
					 * the client can still retain the lock on the file and access the file as necessary  */
					String duplicateFileLocation = find(readunlock_file_name);
					if (!duplicateFileLocation.equals("no")) {
						String duplicateFileLocationPort = (duplicateFileLocation.substring(duplicateFileLocation.indexOf(":")+1));
						String duplicateFileLocationHost = (duplicateFileLocation.substring(0, duplicateFileLocation.indexOf(":")));

						for (Server.Streams readLockRedundant: Server.comChannels){
							if (readLockRedundant.host.equals(duplicateFileLocationHost) 
									&& readLockRedundant.port.equals(duplicateFileLocationPort)){
								readLockRedundant.outStream.writeUTF("readUnlockRedundantFile");
								readLockRedundant.outStream.writeUTF(readunlock_file_name);
								break;
							}
						}
					}
				}
				break;

			case "nogo":
				break;
			}
		}
	}

	public void writeLock() throws IOException {
		DataInputStream in = new DataInputStream(socket.getInputStream());
		DataOutputStream out = new DataOutputStream(socket.getOutputStream());

		String writelock_file_name = in.readUTF();

		switch (writelock_file_name){
		case " ":
			out.writeUTF("no");
			System.out.println("Invalid Input(Trailing character ' ')");
			break;

		default:
			System.out.println("Client requested to write lock " + writelock_file_name);

			if(Files.exists(Paths.get(writelock_file_name)) == true){
				if ((Server.write_locked_files.contains(writelock_file_name) == false) 
						&& (Server.read_locked_files.contains(writelock_file_name) == false)){	
					out.writeUTF("ok");

					Server.write_locked_files.add(writelock_file_name);
					Server.read_locked_files.add(writelock_file_name);

					/* If a file is locked by a client, the backup image of that file(if any), on another 
					 * server, must also be locked so that in the event of this server dying unexpectedly 
					 * the client can still retain the lock on the file and access the file as necessary  */
					String duplicateFileLocation = find(writelock_file_name);

					if (!duplicateFileLocation.equals("no")) {
						String duplicateFileLocationPort = (duplicateFileLocation.substring(duplicateFileLocation.indexOf(":")+1));
						String duplicateFileLocationHost = (duplicateFileLocation.substring(0, duplicateFileLocation.indexOf(":")));

						for (Server.Streams readLockRedundant: Server.comChannels){
							if (readLockRedundant.host.equals(duplicateFileLocationHost) 
									&& readLockRedundant.port.equals(duplicateFileLocationPort)){
								readLockRedundant.outStream.writeUTF("writeLockRedundantFile");
								readLockRedundant.outStream.writeUTF(writelock_file_name);
								break;
							}
						}
					}

					transientReadLockedFilesList.add(writelock_file_name);
					transientWriteLockedFilesList.add(writelock_file_name);

					System.out.println("Write locked " + writelock_file_name);
				}
				else{
					out.writeUTF("cant");
					System.out.println("Cannot grant further write lock\nFile is already write-locked or read-locked");
				}
			}
			else{
				//find if the file exists on another server
				String otherServerInfo = find(writelock_file_name);

				//If no server has the file
				if (otherServerInfo.equals("no")){
					out.writeUTF("no");
					System.out.println("File does not exist");
				}

				//if another server has the file
				else {
					out.writeUTF("on another server");
					System.out.println(" ");
					out.writeUTF(otherServerInfo);
				}
			}
			break;
		}
	}

	public void remoteWriteLock() throws IOException {
		DataInputStream in = new DataInputStream(socket.getInputStream());
		DataOutputStream out = new DataOutputStream(socket.getOutputStream());

		String writelock_file_name = in.readUTF();

		switch (writelock_file_name){
		case " ":
			out.writeUTF("no");
			break;

		default:
			if(Files.exists(Paths.get(writelock_file_name)) == true){
				if ((Server.write_locked_files.contains(writelock_file_name) == false) 
						&& (Server.read_locked_files.contains(writelock_file_name) == false)){	
					out.writeUTF("ok");

					Server.write_locked_files.add(writelock_file_name);
					Server.read_locked_files.add(writelock_file_name);

					/* If a file is locked by a client, the backup image of that file(if any), on another 
					 * server, must also be locked so that in the event of this server dying unexpectedly 
					 * the client can still retain the lock on the file and access the file as necessary  */
					String duplicateFileLocation = find(writelock_file_name);

					if (!duplicateFileLocation.equals("no")) {
						String duplicateFileLocationPort = (duplicateFileLocation.substring(duplicateFileLocation.indexOf(":")+1));
						String duplicateFileLocationHost = (duplicateFileLocation.substring(0, duplicateFileLocation.indexOf(":")));

						for (Server.Streams readLockRedundant: Server.comChannels){
							if (readLockRedundant.host.equals(duplicateFileLocationHost) 
									&& readLockRedundant.port.equals(duplicateFileLocationPort)){
								readLockRedundant.outStream.writeUTF("writeLockRedundantFile");
								readLockRedundant.outStream.writeUTF(writelock_file_name);
								break;
							}
						}
					}
				}
				else{
					out.writeUTF("cant");
				}
			}
			break;
		}
	}


	public void releaseWriteLock() throws IOException {
		DataInputStream in = new DataInputStream(socket.getInputStream());
		DataOutputStream out = new DataOutputStream(socket.getOutputStream());

		String writeunlock_file_name = in.readUTF();

		switch (writeunlock_file_name){
		case " ":
			out.writeUTF("no");
			System.out.println("Invalid Input(Trailing character ' ')");

			break;

		default:

			System.out.println("Client requested to release write lock from " + writeunlock_file_name);

			if(Files.exists(Paths.get(writeunlock_file_name)) == true){
				out.writeUTF("ok");
				String wunlock = in.readUTF();
				switch (wunlock){
				case "go":	
					if (Server.write_locked_files.contains(writeunlock_file_name)==true){
						Server.write_locked_files.remove(writeunlock_file_name);
						transientWriteLockedFilesList.remove(writeunlock_file_name);

						Server.read_locked_files.remove(writeunlock_file_name);
						transientReadLockedFilesList.remove(writeunlock_file_name);

						/* If a file is locked by a client, the backup image of that file(if any), on another 
						 * server, must also be locked so that in the event of this server dying unexpectedly 
						 * the client can still retain the lock on the file and access the file as necessary  */
						String duplicateFileLocation = find(writeunlock_file_name);

						if (!duplicateFileLocation.equals("no")) {
							String duplicateFileLocationPort = (duplicateFileLocation.substring(duplicateFileLocation.indexOf(":")+1));
							String duplicateFileLocationHost = (duplicateFileLocation.substring(0, duplicateFileLocation.indexOf(":")));

							for (Server.Streams readLockRedundant: Server.comChannels){
								if (readLockRedundant.host.equals(duplicateFileLocationHost) 
										&& readLockRedundant.port.equals(duplicateFileLocationPort)){
									readLockRedundant.outStream.writeUTF("writeUnlockRedundantFile");
									readLockRedundant.outStream.writeUTF(writeunlock_file_name);
									break;
								}
							}
						}

						System.out.println("Released Write lock from " + writeunlock_file_name);
					}
					break;

				case "nogo":
					System.out.println("File is not write-locked by "+socket.getInetAddress() + ":"+ socket.getPort());
					break;
				}
			}
			else{
				//find if the file exists on another server
				String otherServerInfo = find(writeunlock_file_name);

				//If no server has the file
				if (otherServerInfo.equals("no")){
					out.writeUTF("no");
					System.out.println("File does not exist");
				}

				//if another server has the file
				else {
					out.writeUTF("on another server");
					System.out.println(" ");
					out.writeUTF(otherServerInfo);
				}
			}
			break;
		}
	}

	public void remoteReleaseWriteLock() throws IOException {
		DataInputStream in = new DataInputStream(socket.getInputStream());
		DataOutputStream out = new DataOutputStream(socket.getOutputStream());

		String writeunlock_file_name = in.readUTF();

		switch (writeunlock_file_name){
		case " ":
			out.writeUTF("no");
			//System.out.println("Invalid Input(Trailing character ' ')");
			break;

		default:
			if(Files.exists(Paths.get(writeunlock_file_name)) == true){
				out.writeUTF("ok");
				String wunlock = in.readUTF();
				switch (wunlock){
				case "go":	
					if (Server.write_locked_files.contains(writeunlock_file_name)==true){
						Server.write_locked_files.remove(writeunlock_file_name);
						transientWriteLockedFilesList.remove(writeunlock_file_name);

						Server.read_locked_files.remove(writeunlock_file_name);

						/* If a file is locked by a client, the backup image of that file(if any), on another 
						 * server, must also be locked so that in the event of this server dying unexpectedly 
						 * the client can still retain the lock on the file and access the file as necessary  */
						String duplicateFileLocation = find(writeunlock_file_name);

						if (!duplicateFileLocation.equals("no")) {
							String duplicateFileLocationPort = (duplicateFileLocation.substring(duplicateFileLocation.indexOf(":")+1));
							String duplicateFileLocationHost = (duplicateFileLocation.substring(0, duplicateFileLocation.indexOf(":")));

							for (Server.Streams readLockRedundant: Server.comChannels){
								if (readLockRedundant.host.equals(duplicateFileLocationHost) 
										&& readLockRedundant.port.equals(duplicateFileLocationPort)){
									readLockRedundant.outStream.writeUTF("writeUnlockRedundantFile");
									readLockRedundant.outStream.writeUTF(writeunlock_file_name);
									break;
								}
							}
						}
					}
					break;

				case "nogo":
					break;
				}
			}
			break;
		}
	}

	public void deleteFile() throws IOException {
		DataInputStream in = new DataInputStream(socket.getInputStream());
		DataOutputStream out = new DataOutputStream(socket.getOutputStream());

		String del_file = in.readUTF();
		String path = del_file;

		if(Files.exists(Paths.get(path)) == true){
			if ((Server.write_locked_files.contains(del_file) == false) &&
					Server.read_locked_files.contains(del_file) == false){
				System.out.println("Deleting file: " + del_file);
				Files.delete(Paths.get(path));
				System.out.println("Deleted " + del_file);
				out.writeUTF("Deleted "+ del_file +" from server");

				String duplicateFileLocation = find(del_file);
								
				if (!duplicateFileLocation.equals("no")) {
					String duplicateFileLocationPort = (duplicateFileLocation.substring(duplicateFileLocation.indexOf(":")+1));
					String duplicateFileLocationHost = (duplicateFileLocation.substring(0, duplicateFileLocation.indexOf(":")));
					
					for (Server.Streams readLockRedundant: Server.comChannels){
						if (readLockRedundant.host.equals(duplicateFileLocationHost) 
								&& readLockRedundant.port.equals(duplicateFileLocationPort)){
							readLockRedundant.outStream.writeUTF("deleteRedundantFile");
							readLockRedundant.outStream.writeUTF(del_file);
							break;
						}
					}
				}
			}
			else{

				System.out.println("Cannot delete file from server. File "
						+ "is write or read locked\n");
				out.writeUTF("Cannot delete file from server. File "
						+ "is write or read locked");
			}
		}
		else{
			System.out.println("Requested file does not exist on this server");
			out.writeUTF("No such file exists.\n"
					+ "\nIf the file exists on the network, it can only\n"
					+ "be deleted by a client directly connected to the\n"
					+ "server responsible for the file "); 
		}

	}

	public void listFilesOnServer() throws IOException {
		DataOutputStream out = new DataOutputStream(socket.getOutputStream());

		File folder = new File(Server.class.getProtectionDomain().
				getCodeSource().getLocation().getPath());
		File[] listOfFiles = folder.listFiles();
		out.write(listOfFiles.length);

		for (int i = 0; i < listOfFiles.length; i++) {
			if (listOfFiles[i].isFile()) {
				out.writeUTF("File " + listOfFiles[i].getName());
			}
			else if (listOfFiles[i].isDirectory()) {
				out.writeUTF("Directory " + listOfFiles[i].getName());
			}
		}
	}

	public void listAllLocks() throws IOException {
		DataOutputStream out = new DataOutputStream(socket.getOutputStream());

		out.write(Server.read_locked_files.size());
		for (String a : Server.read_locked_files){
			out.writeUTF(a);
		}
		out.write(Server.write_locked_files.size());
		for (String s : Server.write_locked_files){
			out.writeUTF(s);
		}
	}

	public void quitServer() throws IOException {
		DataInputStream in = new DataInputStream(socket.getInputStream());
		DataOutputStream out = new DataOutputStream(socket.getOutputStream());

		out.writeUTF("Closing connection to " + socket.getLocalAddress() +":"
				+ "" + socket.getLocalPort());

		int iter1 = in.read();
		if (iter1 != 0){						
			for (int k = 1; k<= iter1; k++){
				String d = in.readUTF();
				Server.write_locked_files.remove(d);

				String duplicateFileLocation = find(d);

				if (!duplicateFileLocation.equals("no")) {
					String duplicateFileLocationPort = (duplicateFileLocation.substring(duplicateFileLocation.indexOf(":")+1));
					String duplicateFileLocationHost = (duplicateFileLocation.substring(0, duplicateFileLocation.indexOf(":")));

					for (Server.Streams readLockRedundant: Server.comChannels){
						if (readLockRedundant.host.equals(duplicateFileLocationHost) 
								&& readLockRedundant.port.equals(duplicateFileLocationPort)
								&& !d.equals(null)) {
							readLockRedundant.outStream.writeUTF("writeUnlockRedundantFile");
							readLockRedundant.outStream.writeUTF(d);
							break;
						}
					}
				}
			}
		}

		int iter = in.read();
		if (iter != 0){
			for (int j = 1; j<= iter; j++){
				String c = in.readUTF();
				Server.read_locked_files.remove(c);

				String duplicateFileLocation = find(c);

				if (!duplicateFileLocation.equals("no")) {
					String duplicateFileLocationPort = (duplicateFileLocation.substring(duplicateFileLocation.indexOf(":")+1));
					String duplicateFileLocationHost = (duplicateFileLocation.substring(0, duplicateFileLocation.indexOf(":")));

					for (Server.Streams readLockRedundant: Server.comChannels){
						if (readLockRedundant.host.equals(duplicateFileLocationHost) 
								&& readLockRedundant.port.equals(duplicateFileLocationPort) 
								&& !c.equals(null)) {
							readLockRedundant.outStream.writeUTF("readUnlockRedundantFile");
							readLockRedundant.outStream.writeUTF(c);
							break;
						}
					}
				}
			}
		}
		System.out.println("Connection to " + socket.getInetAddress()+":"+ socket.getPort()+
				" closing..." + "\n" + "Connection closed");
		this.in.close();
		Integer.parseInt(null);
	}

	public void closeServer() throws IOException {
		DataInputStream in = new DataInputStream(socket.getInputStream());
		DataOutputStream out = new DataOutputStream(socket.getOutputStream());

		String password = in.readUTF();
		switch(password){
		case "master":

			out.writeUTF("ok");
			out.writeUTF("Closing connection to "+ socket.getLocalAddress() + ":" + socket.getLocalPort());
			System.out.println("Connection to " + socket.getInetAddress()+":"+ socket.getPort()+
					" closing..." + "\n" + "Connection closed");
			socket.close();
			System.exit(0);
			break;

		default:
			out.writeUTF("nok");
			System.out.println("incorrect password");
			break;
		}

	}

	public static String find(String fileName){
		String searchResult = null;
		String response = null;
		if (Server.comChannels.size() != 0) {
			for (Server.Streams server  : Server.comChannels) {
				boolean live = true;

				try {
					server.outStream.writeUTF("test");
				}

				catch(IOException e) {
					live = false;
				}

				finally {
					if (live == true) {
						try{
							server.outStream.writeUTF("find");
							server.outStream.writeUTF(fileName);
							response = server.inStream.readUTF();
						} catch (Exception e) {
							System.out.println(" ");
						}

						if (!response.equals("no")) {
							searchResult = response;
							break;
						}
						else {
							searchResult = response;
						}
					}
				}
			}
		}
		else {
			searchResult = "no";
		}
		return searchResult;
	}

	public static void backupReceivedFile(String fileName){
		try {
			File fileToUpload = new File(fileName);
			byte[] mybytearray = new byte[(int) fileToUpload.length()];
			FileInputStream fis = new FileInputStream(fileName);
			BufferedInputStream bis = new BufferedInputStream(fis);

			DataInputStream dis = new DataInputStream(bis);
			dis.readFully(mybytearray, 0, mybytearray.length);

			for (Server.Streams server: Server.comChannels) {
				boolean live = true;

				try {
					server.outStream.writeUTF("test");
				}

				catch(IOException e) {
					live = false;
					//Server.comChannels.remove(server);
				}

				finally {
					if (live == true) {
						server.outStream.writeUTF("backupFile");
						System.out.print(" ");

						server.outStream.writeUTF(fileName);
						System.out.print(" ");

						server.outStream.writeLong(mybytearray.length);
						System.out.print(" ");

						server.outStream.write(mybytearray, 0, mybytearray.length);
						server.outStream.flush();

						break;
					}
				}
			}

			dis.close();
			bis.close();
			fis.close();
		} catch (Exception e) {
			System.err.println("File does not exist!");
		}
	}


}//connection implements runnable