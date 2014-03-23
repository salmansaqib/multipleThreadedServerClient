import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

public class ServeServers implements Runnable {

	private Socket socket;
	private String remoteListeningPort;

	public ServeServers(Socket server, String port) {
		this.socket = server;
		this.remoteListeningPort = port;
	}


	@Override
	public void run(){
		try{
			System.out.println("Connected to " + socket.getInetAddress().toString().substring(1) + ":"+ remoteListeningPort);
			DataInputStream in = new DataInputStream(socket.getInputStream());
			DataOutputStream out = new DataOutputStream(socket.getOutputStream());
			while (true) {
				String input = in.readUTF();

				if (input.equals("find")) {
					String fileName = in.readUTF();

					File fileToFind = new File(fileName);

					if (fileToFind.exists()) {
						out.writeUTF(socket.getLocalAddress().toString().substring(1) + ":"+ Server.listeningPort);
					}
					else{
						out.writeUTF("no");
					}
				}

				else if (input.equals("backupFile")) {
					try {
						String fileName = in.readUTF();
						int bytesRead;
						OutputStream output = new FileOutputStream((fileName));
						long size = in.readLong();
						byte[] buffer = new byte[1024];

						while (size > 0 && (bytesRead = in.read(buffer, 0, (int) Math.min(buffer.length, size))) != -1) {
							output.write(buffer, 0, bytesRead);
							size -= bytesRead;
						}
						output.close();
						System.out.println("File "+fileName+" backed up");
					}
					catch (Throwable e) {
						e.printStackTrace();
					}					
				}

				else if (input.equals("readLockRedundantFile")) {
					String fileName = in.readUTF();
					Server.read_locked_files.add(fileName);
				}

				else if (input.equals("readUnlockRedundantFile")) {
					String fileName = in.readUTF();
					if (Server.read_locked_files.contains(fileName)){
						Server.read_locked_files.remove(fileName);
					}
				}

				else if (input.equals("writeLockRedundantFile")) {
					String fileName = in.readUTF();
					if (!Server.write_locked_files.contains(fileName)) {
						Server.write_locked_files.add(fileName);
						Server.read_locked_files.add(fileName);
					}
				}

				else if (input.equals("writeUnlockRedundantFile")) {
					String fileName = in.readUTF();
					if (Server.read_locked_files.contains(fileName) &&
							Server.write_locked_files.contains(fileName)){
						Server.write_locked_files.remove(fileName);
						Server.read_locked_files.remove(fileName);
					}
				}
				else if (input.equals("deleteRedundantFile")) {
					String fileName = in.readUTF();
					Files.delete(Paths.get(fileName));
				}

				else {
					;
				}
			}
		}
		catch(Throwable e){
			System.out.println("Connection lost with "+socket.getInetAddress().toString().substring(1) + ":"+ remoteListeningPort);
		}
		finally{
			ArrayList<Object> element = new ArrayList<>();
			element.add(socket.getInetAddress().toString().substring(1));
			element.add(Integer.parseInt(remoteListeningPort));

			int i = Server.connectedServers.indexOf(element);
			Server.connectedServers.remove(i);

			for (Server.Streams connection: Server.comChannels) {
				boolean live = true;

				try {
					connection.outStream.writeUTF("test");
				}

				catch(IOException e) {
					live = false;
				}

				finally {
					if (live == true) {
						if (connection.host.equals(socket.getInetAddress().toString().substring(1)) 
								&& connection.port.equals(remoteListeningPort)) {
							Server.comChannels.remove(connection);
						}
					}
				}
			}
		}
	}
}
