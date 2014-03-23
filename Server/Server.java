import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Scanner;



public class Server {
	private static ServerSocket serverSocket;
	private static Socket socket = null;

	public static void println(Object s) {
		System.out.println(s);
	}


	public static int listeningPort;
	public static ArrayList<String> read_locked_files = new ArrayList<String>();
	public static ArrayList<String> write_locked_files = new ArrayList<String>();
	public static ArrayList<ArrayList<Object>> connectedServers = new ArrayList<ArrayList<Object>>();

	public static HashSet<Streams> comChannels = new HashSet<Streams>();
	
	public static void main (String args[]) throws IOException {

		boolean boot = boothost();

		if (boot == true) {
			listeningPort = getListeningPort();
		}
		else {
			listeningPort = getListeningPort();
			String host = takeInput("Host");
			int port = takeInputInt("Port");
			
			String portString = Integer.toString(port);

			Socket a = connectToServer(host, port);

			if (a != null) { 

				DataOutputStream introductionMsgOut = new DataOutputStream(a.getOutputStream());
				DataInputStream otherServerinfo = new DataInputStream(a.getInputStream());
				
				Streams aS = new Streams(introductionMsgOut, otherServerinfo, host, portString);
				comChannels.add(aS);
				
				aS.outStream.writeUTF("server");
				aS.outStream.writeUTF(Integer.toString(listeningPort));

				String moreServers = aS.inStream.readUTF();
				if (moreServers.equals("yes")) {
					int numberOfMoreServers = aS.inStream.readInt();
					int i = 0;
					while (i < numberOfMoreServers){
						String addHost = aS.inStream.readUTF();
						String addPort = aS.inStream.readUTF();
						int intAddPort = Integer.parseInt(addPort);

						Socket b = connectToServer(addHost, intAddPort);

						if (b != null) { 
							DataOutputStream introductionMsgOutB = new DataOutputStream(b.getOutputStream());
							DataInputStream otherServerinfoB = new DataInputStream(b.getInputStream());
							
							Streams bS = new Streams(introductionMsgOutB, otherServerinfoB, addHost, addPort);
							comChannels.add(bS);

							bS.outStream.writeUTF("serverB");
							bS.outStream.writeUTF(Integer.toString(listeningPort));
							i++;
						}
					}
				}
			}
		}

		try{
			serverSocket = new ServerSocket(listeningPort);
		}
		catch(IOException e) {
			System.out.println(e);
		}

		while (true) {
			try {
				socket = serverSocket.accept();
				println("Connected to " + socket);				

				DataOutputStream otherServerInfo = new DataOutputStream(socket.getOutputStream());
				DataInputStream introductionMsgIn = new DataInputStream(socket.getInputStream());

				String specie = introductionMsgIn.readUTF();

				switch (specie) {
				case "client": 
					Thread client = new Thread(new ServeClient(socket));
					client.start();
					break;

				case "server": 
					String port = introductionMsgIn.readUTF();
					if (connectedServers.size() != 0) {

						otherServerInfo.writeUTF("yes");

						int numberOfConnectedServers = connectedServers.size();
						otherServerInfo.writeInt(numberOfConnectedServers);
						int i = 0;
						while (i < numberOfConnectedServers){
							otherServerInfo.writeUTF(connectedServers.get(i).get(0).toString());
							otherServerInfo.writeUTF(connectedServers.get(i).get(1).toString());
							i++;
						}
					}
					else{
						otherServerInfo.writeUTF("no");
					}

					Socket a = connectBack(socket.getInetAddress().toString().substring(1), port);

					DataOutputStream alreadyFriendsOut = new DataOutputStream(a.getOutputStream());
					DataInputStream alreadyFriendsIn = new DataInputStream(a.getInputStream());

					Streams cS = new Streams(alreadyFriendsOut, alreadyFriendsIn, 
							socket.getInetAddress().toString().substring(1), port);
					comChannels.add(cS);

					cS.outStream.writeUTF("recurrent");
					cS.outStream.writeUTF(Integer.toString(socket.getLocalPort()));
					Thread server = new Thread(new ServeServers(socket,port));
					server.start();
					break;

				case "serverB":
					String portB = introductionMsgIn.readUTF();
					Socket b = connectBack(socket.getInetAddress().toString().substring(1), portB);

					DataOutputStream alreadyFriendsOutB = new DataOutputStream(b.getOutputStream());
					DataInputStream alreadyFriendsInB = new DataInputStream(b.getInputStream());
					
					Streams dS = new Streams(alreadyFriendsOutB, alreadyFriendsInB,
							socket.getInetAddress().toString().substring(1), portB);
					comChannels.add(dS);

					dS.outStream.writeUTF("recurrent");
					dS.outStream.writeUTF(Integer.toString(socket.getLocalPort()));
					Thread serverB = new Thread(new ServeServers(socket,portB));
					serverB.start();
					break;

				case "recurrent":
					String recurrentPort = introductionMsgIn.readUTF();
					Thread connectedBack = new Thread(new ServeServers(socket,recurrentPort));

					connectedBack.start();
					break;					
				}
			}
			catch (IOException e) {
				println(e);
			}
		}
	}


	
	
	public static class Streams {
		public String host;
	    public String port;
		public DataOutputStream outStream;
	    public DataInputStream  inStream;
	    
	    public Streams(DataOutputStream out, DataInputStream in, String host, String port) {
	        this.outStream = out;
	        this.inStream = in;
	        this.host = host;
	        this.port = port;
	    }
	}

	public static int getListeningPort() throws IOException {
		System.out.print("Specify listening port: ");
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		String listeningPort = br.readLine();

		return Integer.parseInt(listeningPort);
	}


	public static boolean boothost() {
		boolean booter = false;
		boolean loop = true;
		while (loop) {
			System.out.println("Start as boot Server?\n1. Yes\t2. No, connect to an existing network");
			BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
			String boot = null;
			try {
				boot = br.readLine();
			} catch (IOException e) {
				e.printStackTrace();
			}
			switch (boot) {
			case "1":
				booter = true;
				loop = false;
				break;
			case "2":
				booter = false;
				loop = false;
				break;
			default:
				System.out.println("Invalid input");
				break;
			}
		}
		return booter;
	}

	public static String takeInput(String inputName) throws IOException {
		System.out.print(inputName+": ");
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		String input = br.readLine();
		return input;
	}

	public static int takeInputInt(String inputName) throws IOException {
		System.out.print(inputName+": ");
		int input = 0;
		try{
			Scanner inputInt = new Scanner(System.in);
			input = inputInt.nextInt();
		}catch(Exception e){
			e.printStackTrace();
		}
		return input;
	}

	public static Socket connectToServer(String host, int port) throws IOException {
		ArrayList<Object> element = new ArrayList<>();
		element.add(host);
		element.add(port);

		Socket fellowServer = null;
		if (connectedServers.contains(element) != true) {
			try {
				fellowServer = new Socket(host, port);

			}catch(Exception e){
				e.printStackTrace();
			}
			element.remove(0);
			element.remove(0);

			element.add(fellowServer.getInetAddress().toString().substring(fellowServer.getInetAddress().toString().indexOf("/")+1));
			element.add(port);

			if ((element.get(0) != null) && (element.get(1) != null)) {
				connectedServers.add(element);
			}
			return fellowServer;
		}
		else{
			return null;
		}
	}

	public static Socket connectBack(String host, String port) throws IOException {
		ArrayList<Object> element = new ArrayList<>();
		element.add(host);
		int backPort = Integer.parseInt(port);
		element.add(backPort);
		Socket fellowServer = new Socket(host, backPort);
		if ((element.get(0) != null) && (element.get(1) != null)) {
			connectedServers.add(element);
		}
		return fellowServer;
	}
}

