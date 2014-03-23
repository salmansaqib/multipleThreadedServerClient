import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.Socket;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Scanner;

//import server.ServerConnection;



public class Client {
	private static Socket connectToServer;
	private static DataOutputStream introduce;
	private static BufferedReader keybrdIn;
	private static PrintStream msgToServer;
	public static ArrayList<String> read_locked_files = new ArrayList<String>();
	public static ArrayList<String> write_locked_files = new ArrayList<String>();


	public static void println(Object e) {
		System.out.println(e);
	}

	public static void print(Object e) {
		System.out.print(e);
	}

	public static void main(String args[]) throws IOException{
		while (true) {
			try{
				println("\nTo connect to a server, please enter the "
						+ "\nserver's IP address and its corresponding port number");
				print("Enter IP: ");
				String ip = new Scanner(System.in).nextLine();			
				print("Enter port: ");
				int port = new Scanner(System.in).nextInt();
				connectToServer = new Socket(ip, port);
				introduce = new DataOutputStream(connectToServer.getOutputStream());
				introduce.writeUTF("client");
				keybrdIn = new BufferedReader(new InputStreamReader(System.in));
			}catch(IOException e) {
				println(e);
			}
			boolean connected = true;
			msgToServer = new PrintStream(connectToServer.getOutputStream());
			if (connectToServer != null && msgToServer != null) {
				while (connected == true) {
					try {
						switch((action())) {
						case "1":
							DataOutputStream dos = new DataOutputStream(connectToServer.getOutputStream());
							DataInputStream dis = new DataInputStream(connectToServer.getInputStream());

							String fileName;
							String pathName;

							File file_to_upload = null;
							System.out.print("Enter file path: ");
							pathName = keybrdIn.readLine();

							System.out.print("Enter file name: ");
							fileName = keybrdIn.readLine();
							Path path = Paths.get(pathName, fileName);
							file_to_upload = new File(path.toString());

							if (file_to_upload.exists()){	//if file exists on client
								msgToServer.println("1");
								println(" ");
								dos.writeUTF(fileName);
								String existsOnServer = dis.readUTF();

								switch (existsOnServer) {
								case "yes":
									System.out.println('\n'+dis.readUTF() + '\n');

									if (write_locked_files.contains(fileName)) {
										dos.writeUTF("go");
										System.out.println("Do you want to overwrite " + fileName + " ?\na.Yes\tb.no\n");
										Scanner overwrite1 = new Scanner(System.in);
										String overwrite = overwrite1.nextLine();
										dos.writeUTF(overwrite);
										switch (overwrite) {
										case "a":
											sendFile(pathName, fileName);
											break;
										case "b":
											//dos.writeUTF(overwrite + '\n');
											System.out.println("Upload aborted");
											break;
										default:
											//dos.writeUTF(overwrite + '\n');
											System.out.println("Invalid Input");
											break;
										}
									}
									else {
										dos.writeUTF("nogo");
										System.out.println("Please obtain a write lock on this file to overwrite it");
									}
									break;
								case "no":
									sendFile(pathName, fileName);
									println("Done");
									break;
								}
							}
							else{
								System.out.println("File " + path + " does not exist\n");
								msgToServer.println("1fail");
							}
							break;

						case "2":
							msgToServer.println("2");
							readFile(connectToServer);
							break;

						case "3":
							msgToServer.println("3");
							readLock(connectToServer);
							break;

						case "4":
							msgToServer.println("4");
							releaseReadLock(connectToServer);
							break;

						case "5":
							msgToServer.println("5");
							writeLock(connectToServer);
							break;

						case "6":
							msgToServer.println("6");
							releaseWriteLock(connectToServer);
							break;

						case "7":
							msgToServer.println("7");
							deleteFile();
							break;

						case "8":
							msgToServer.println("8");
							listServerFiles();
							break;

						case "9":
							msgToServer.println("9");
							listClientLocks();
							break;

						case "10":
							msgToServer.println("10");
							listAllLocks();
							break;

						case "11":
							msgToServer.println("11");
							quitServer();
							connected = false;
							break;

						case "12":
							msgToServer.println("12");
							shutDownServer();
							connected = false;
							break;

						default:
							println("Invalid input");
							break;
						}
					}catch (IOException e) {
						println(e);
						connected = false;
					}
				}
			}
		}
	}//main

	public static String action() throws IOException {
		println("\nSelect action: \n1. Upload file\t\t2. Read a file"
				+ "\n3. Read-lock a file \t4. Release read-lock\n5. Write-lock a file\t6. Release write-lock"
				+ "\n7. Delete a file \t8. List files on server \n9. List the files you have locks on "
				+ "\n10. List all files on server that are locked \n11. Exit Server"
				+ "\t\t12.Shut Down Server");
		print("Enter choice: ");

		return keybrdIn.readLine();
	}


	@SuppressWarnings("resource")
	public static void sendFile(String pathName, String fileName) throws IOException {
		try {
			Path path = Paths.get(pathName, fileName);
			File fileToUpload = new File(path.toString());
			byte[] mybytearray = new byte[(int) fileToUpload.length()];
			FileInputStream fis = new FileInputStream(path.toString());
			BufferedInputStream bis = new BufferedInputStream(fis);

			DataInputStream dis = new DataInputStream(bis);
			dis.readFully(mybytearray, 0, mybytearray.length);
			
			OutputStream os = connectToServer.getOutputStream();
			DataOutputStream dos = new DataOutputStream(os);

			//Sending file name and file size to the server
			dos.writeLong(mybytearray.length);
			dos.write(mybytearray, 0, mybytearray.length);
			dos.flush();
			System.out.println("File "+fileName+" uploaded");
		} catch (Exception e) {
			System.err.println("File does not exist!");
		}
	}


	@SuppressWarnings("resource")
	public static void readFile(Socket sock) throws IOException {
		DataOutputStream dos = new DataOutputStream(sock.getOutputStream());
		DataInputStream dis = new DataInputStream(sock.getInputStream());

		System.out.print("Enter file name: ");
		Scanner scan2 = new Scanner(System.in);
		String read_file_name = scan2.nextLine();

		if (read_file_name.equals("") || read_file_name.equals(" ") || (read_file_name == null)) {
			System.out.println("Invalid input");
		}
		else {
			dos.writeUTF(read_file_name);
			String read_file_exits = dis.readUTF();

			switch(read_file_exits){

			case "go":
				if (read_locked_files.contains(read_file_name) == true){
					dos.writeUTF("go");
					BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream()));

					String next_line_count = dis.readUTF();

					System.out.println("________"+ read_file_name + "________" + "\n\n");
					int i = Integer.parseInt(next_line_count);

					for (int j = 0; j < i; j++){
						System.out.println(in.readLine());
					}
					System.out.println("\n________"+ "end of file" + "________\n");
				}
				else{
					dos.writeUTF("nogo");
					System.out.println("Please obtain a read lock on " + read_file_name + " first");
				}

				break;

			case "nogo":
				System.out.println("requested file does not exist");
				break;
			
			case "on another server":
				String otherServerInfo = dis.readUTF();
				String otherServerPort = (otherServerInfo.substring(otherServerInfo.indexOf(":")+1));
				String otherServerHost = (otherServerInfo.substring(0, otherServerInfo.indexOf(":")));
				int otherServerPortInt = Integer.parseInt(otherServerPort);

				Socket readSocket = new Socket(otherServerHost, otherServerPortInt); 

				PrintStream msgToOtherServer = new PrintStream(readSocket.getOutputStream());
				DataOutputStream introduceToOtherServer = new DataOutputStream(readSocket.getOutputStream());

				introduceToOtherServer.writeUTF("client");
				print("");
				msgToOtherServer.println("2");
				System.out.println("Please input the file name once more");
				readFile(readSocket);
				readSocket.close();
				break;
			}
		}
	}

	@SuppressWarnings("resource")
	public static void readLock(Socket sock) throws IOException {
		DataOutputStream dos = new DataOutputStream(sock.getOutputStream());
		DataInputStream dis = new DataInputStream(sock.getInputStream());
		System.out.print("Enter file name: ");
		Scanner scan3 = new Scanner(System.in);
		String readlock_file_name = scan3.nextLine();

		if (readlock_file_name.equals("") || readlock_file_name.equals(" ") || (readlock_file_name == null)) {
			System.out.println("Invalid input");
		}
		else {
			dos.writeUTF(readlock_file_name);

			String file_present_r = dis.readUTF();

			switch (file_present_r){
			case "ok":
				if (read_locked_files.contains(readlock_file_name)==false){
					dos.writeUTF("go");
					read_locked_files.add(readlock_file_name);
					System.out.println("Read locked " + readlock_file_name);

				}
				else{
					dos.writeUTF("nogo");
					System.out.println("You already have a read lock on "
							+ readlock_file_name);
				}
				break;

			case "no":
				System.out.println("No such file exists");
				break;

			case "on another server":
				String otherServerInfo = dis.readUTF();
				String otherServerPort = (otherServerInfo.substring(otherServerInfo.indexOf(":")+1));
				String otherServerHost = (otherServerInfo.substring(0, otherServerInfo.indexOf(":")));
				int otherServerPortInt = Integer.parseInt(otherServerPort);

				Socket readLockSocket = new Socket(otherServerHost, otherServerPortInt); 

				PrintStream msgToOtherServer = new PrintStream(readLockSocket.getOutputStream());
				DataOutputStream introduceToOtherServer = new DataOutputStream(readLockSocket.getOutputStream());

				introduceToOtherServer.writeUTF("client");
				println(" ");
				msgToOtherServer.println("3a");
				System.out.println("Please input the file name once more");
				readLock(readLockSocket);
				readLockSocket.close();
				break;

			case "cant":
				System.out.println("Server cannot grant request\nFile is currently write locked");
				break;
			}
		}
	}

	@SuppressWarnings("resource")
	public static void releaseReadLock(Socket sock) throws IOException {
		DataOutputStream dos = new DataOutputStream(sock.getOutputStream());
		DataInputStream dis = new DataInputStream(sock.getInputStream());

		System.out.print("Enter file name: ");
		Scanner scan4 = new Scanner(System.in);
		String readunlock_file_name = scan4.nextLine();

		if (readunlock_file_name.equals("") || readunlock_file_name.equals(" ") || (readunlock_file_name == null)) {
			System.out.println("Invalid input");
		}
		else {
			dos.writeUTF(readunlock_file_name);

			String file_present_r_un = dis.readUTF();

			switch (file_present_r_un){
			case "ok":
				if (read_locked_files.contains(readunlock_file_name)==true){
					dos.writeUTF("go");
					read_locked_files.remove(readunlock_file_name);
					if (write_locked_files.contains(readunlock_file_name)==true) {
						write_locked_files.remove(readunlock_file_name);
						println("(You had a write lock on this file"
								+ " which has been released as well)");
					}
					System.out.println("Released Read lock from " + readunlock_file_name);
				}
				else{
					dos.writeUTF("nogo");
					System.out.println("You do not have a read lock on " +readunlock_file_name);

				}
				break;

			case "no":
				System.out.println("No such file exists");
				break;

			case "on another server":
				String otherServerInfo = dis.readUTF();
				String otherServerPort = (otherServerInfo.substring(otherServerInfo.indexOf(":")+1));
				String otherServerHost = (otherServerInfo.substring(0, otherServerInfo.indexOf(":")));
				int otherServerPortInt = Integer.parseInt(otherServerPort);

				Socket readLockSocket = new Socket(otherServerHost, otherServerPortInt); 

				PrintStream msgToOtherServer = new PrintStream(readLockSocket.getOutputStream());
				DataOutputStream introduceToOtherServer = new DataOutputStream(readLockSocket.getOutputStream());

				introduceToOtherServer.writeUTF("client");
				msgToOtherServer.println("4a");
				System.out.println("Please input the file name once more");
				releaseReadLock(readLockSocket);
				readLockSocket.close();
				break;
			}
		}
	}

	@SuppressWarnings("resource")
	public static void writeLock(Socket sock) throws IOException {
		DataOutputStream dos = new DataOutputStream(sock.getOutputStream());
		DataInputStream dis = new DataInputStream(sock.getInputStream());

		System.out.print("Enter file name: ");
		Scanner scan5 = new Scanner(System.in);
		String writelock_file_name = scan5.nextLine();

		if (writelock_file_name.equals("") || writelock_file_name.equals(" ") || (writelock_file_name == null)) {
			System.out.println("Invalid input");
		}
		else {
			dos.writeUTF(writelock_file_name);

			String file_present_w = dis.readUTF();

			switch (file_present_w){
			case "ok":
				if (write_locked_files.contains(writelock_file_name)==false){
					write_locked_files.add(writelock_file_name);
					read_locked_files.add(writelock_file_name);
					System.out.println("Write locked " + writelock_file_name);
				}
				else{
					System.out.println("You already have a write lock on "
							+ writelock_file_name);
				}
				break;

			case "no":
				System.out.println("No such file exists");
				break;

			case "cant":
				System.out.println("File is already read locked or write locked");
				break;

			case "on another server":
				String otherServerInfo = dis.readUTF();
				String otherServerPort = (otherServerInfo.substring(otherServerInfo.indexOf(":")+1));
				String otherServerHost = (otherServerInfo.substring(0, otherServerInfo.indexOf(":")));
				int otherServerPortInt = Integer.parseInt(otherServerPort);

				Socket writeLockSocket = new Socket(otherServerHost, otherServerPortInt); 

				PrintStream msgToOtherServer = new PrintStream(writeLockSocket.getOutputStream());
				DataOutputStream introduceToOtherServer = new DataOutputStream(writeLockSocket.getOutputStream());

				introduceToOtherServer.writeUTF("client");
				msgToOtherServer.println("5a");
				System.out.println("Please input the file name once more");
				writeLock(writeLockSocket);
				writeLockSocket.close();
				break;	
			}
		}
	}

	@SuppressWarnings("resource")
	public static void releaseWriteLock(Socket sock) throws IOException {
		DataOutputStream dos = new DataOutputStream(sock.getOutputStream());
		DataInputStream dis = new DataInputStream(sock.getInputStream());

		System.out.print("Enter file name: ");
		Scanner scan6 = new Scanner(System.in);
		String writeunlock_file_name = scan6.nextLine();

		if (writeunlock_file_name.equals("") || writeunlock_file_name.equals(" ") || (writeunlock_file_name == null)) {
			System.out.println("Invalid input");
		}
		else {
			dos.writeUTF(writeunlock_file_name);

			String file_present_w_un = dis.readUTF();

			switch (file_present_w_un){
			case "ok":
				if (write_locked_files.contains(writeunlock_file_name)==true){
					dos.writeUTF("go");
					write_locked_files.remove(writeunlock_file_name);
					read_locked_files.remove(writeunlock_file_name);
					System.out.println("Released Write lock from " + writeunlock_file_name);

				}
				else{
					dos.writeUTF("nogo");
					System.out.println("You do not have a write lock on " +writeunlock_file_name);

				}
				break;

			case "no":
				System.out.println("No such file exists");
				break;

			case "on another server":
				String otherServerInfo = dis.readUTF();
				String otherServerPort = (otherServerInfo.substring(otherServerInfo.indexOf(":")+1));
				String otherServerHost = (otherServerInfo.substring(0, otherServerInfo.indexOf(":")));
				int otherServerPortInt = Integer.parseInt(otherServerPort);

				Socket releaseWriteLockSocket = new Socket(otherServerHost, otherServerPortInt); 

				PrintStream msgToOtherServer = new PrintStream(releaseWriteLockSocket.getOutputStream());
				DataOutputStream introduceToOtherServer = new DataOutputStream(releaseWriteLockSocket.getOutputStream());

				introduceToOtherServer.writeUTF("client");
				msgToOtherServer.println("6a");
				System.out.println("Please input the file name once more");
				releaseWriteLock(releaseWriteLockSocket);
				releaseWriteLockSocket.close();
				break;	
			}
		}
	}

	@SuppressWarnings("resource")
	public static void deleteFile() throws IOException {
		DataOutputStream dos = new DataOutputStream(connectToServer.getOutputStream());
		DataInputStream dis = new DataInputStream(connectToServer.getInputStream());

		System.out.print("Enter file name: ");
		Scanner scan7 = new Scanner(System.in);
		String del_file_name = scan7.nextLine();

		if (del_file_name.equals("") || del_file_name.equals(" ") || (del_file_name == null)) {
			System.out.println("Invalid input");
		}
		else {
			dos.writeUTF(del_file_name);
			System.out.println("\n" + dis.readUTF() + "\n");
		}
	}

	public static void listServerFiles() throws IOException {
		DataInputStream dis = new DataInputStream(connectToServer.getInputStream());

		int file_count = dis.read();

		for (int a = 0; a < file_count; a++){ 
			System.out.println(dis.readUTF());
		}

	}

	public static void listClientLocks() throws IOException {
		System.out.println("Read Locks:");
		for (String s: read_locked_files){
			System.out.println(s);
		}
		println("");

		System.out.println("Write Locks:");
		for (String a: write_locked_files){
			System.out.println(a);
		}

	}

	public static void listAllLocks() throws IOException {
		DataInputStream dis = new DataInputStream(connectToServer.getInputStream());

		int iter = dis.read();

		System.out.println("Read Locks");
		for (int j = 1; j<= iter; j++){
			System.out.println(dis.readUTF());
		}
		println("");

		int iter1 = dis.read();

		System.out.println("Write Locks");
		for (int k = 1; k<= iter1; k++){
			System.out.println(dis.readUTF());
		}
	}

	public static void quitServer() throws IOException {
		DataOutputStream dos = new DataOutputStream(connectToServer.getOutputStream());
		DataInputStream dis = new DataInputStream(connectToServer.getInputStream());

		System.out.println("\n" + dis.readUTF() + "\n");

		dos.write(read_locked_files.size());
		if (read_locked_files.size() != 0){
			for (String a : read_locked_files){
				dos.writeUTF(a);
			}
		}
		dos.write(write_locked_files.size());
		if (write_locked_files.size() != 0){							
			for (String s : write_locked_files){
				dos.writeUTF(s);
			}
		}
		
		read_locked_files.clear();
		write_locked_files.clear();

		connectToServer.close();
		System.out.println("Connection Closed\n");

	}

	@SuppressWarnings("resource")
	public static void shutDownServer() throws IOException {
		DataOutputStream dos = new DataOutputStream(connectToServer.getOutputStream());
		DataInputStream dis = new DataInputStream(connectToServer.getInputStream());

		System.out.print("Enter password: ");
		Scanner password = new Scanner(System.in);
		String pass = password.nextLine();

		if (pass.equals("") || pass.equals(" ") || (pass == null)) {
			System.out.println("Invalid input");
		}
		else {
			dos.writeUTF(pass);

			String server_pass = dis.readUTF();
			switch (server_pass){
			case "ok":
				System.out.println("\n" + dis.readUTF() + "\n");
				System.out.println("Server Shut Down\n");

				read_locked_files.clear();
				write_locked_files.clear();

				System.exit(0);
				break;
			case "nok":
				System.out.println("Password incorrect!");
				break;
			}
		}
	}
}