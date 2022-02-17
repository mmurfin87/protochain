package com.markmurfin.blockchain.p2p;

import com.markmurfin.blockchain.util.Switch;
import lombok.NonNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Client
{
	private static boolean run = true;

	public static void main(final String[] programArgs) throws IOException, InterruptedException
	{
		final int port = 8112 + (int) (Math.random() * 1000);
		final InetSocketAddress bindAddress = new InetSocketAddress(port);
		final Selector selector = Selector.open();
		final Map<Peer, Protocol4Connection> connectedPeers = new HashMap<>();
		final Timer timer = new Timer();
		final ReadHandler readHandler = new NodeReadHandler(selector, connectedPeers);
		final Node node = new Node(bindAddress, selector, connectedPeers, () -> run, timer, readHandler);
		final Thread nodeThread = new Thread(node);

		nodeThread.start();

		control(selector, readHandler, node, connectedPeers);

		System.out.println("Shutting down...");

		run = false;
		timer.cancel();
		nodeThread.join();
		selector.close();
		for (var peerConnection : connectedPeers.values())
			peerConnection.socketChannel.close();

		System.out.println("Good bye");
	}

	public static void control(@NonNull final Selector selector, @NonNull final ReadHandler readHandler, @NonNull final Node node, @NonNull final Map<Peer, Protocol4Connection> connectedPeers) throws IOException
	{
		final BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		run: while (true)
		{
			final String input = br.readLine();
			final String[] commandArgs = input.split(" ");
			final String command = commandArgs[0].toLowerCase(Locale.ROOT);
			switch (command)
			{
				case "quit":
					break run;
				case "peer":
					if (commandArgs.length != 3) // must be [ "peer", "hostname", "port" ]
						System.out.println("Specify hostname and port. Like: peer <hostname> <port>");
					else
						node.connectPeer(new InetSocketAddress(commandArgs[1], Integer.parseInt(commandArgs[2])));
					break;
				case "unpeer":
					if (commandArgs.length != 3) // must be [ "peer", "hostname", "port" ]
						System.out.println("Specify hostname and port. Like: peer <hostname> <port>");
					else
					{
						final InetSocketAddress peerAddress = new InetSocketAddress(commandArgs[1], Integer.parseInt(commandArgs[2]));
						connectedPeers.values().stream()
							.filter(pc -> Objects.equals(pc.peer.address, peerAddress))
							.forEach(pc ->
							{
								try
								{
									node.unpeer(pc);
								}
								catch (final IOException e)
								{
									throw new RuntimeException(e);
								}
							});
					}
					break;
				case "send":
					connectedPeers.values().forEach(c ->
					{
						try
						{
							if (!c.write(ByteBuffer.wrap(input.substring(5).getBytes(StandardCharsets.UTF_8))))
								c.socketChannel.register(selector, SelectionKey.OP_WRITE, c);
						}
						catch (final IOException e)
						{
							throw new RuntimeException(e);
						}
					});
					System.out.format("Sent: %s%n", input.substring(5));
					break;
				case "h":
					connectedPeers.values().forEach(System.out::println);
					break;
				case "f":
					connectedPeers.values().forEach(peerConnection ->
					{
						try
						{
							final int read = peerConnection.read(readHandler);
							System.out.format("Force Read %d bytesn", read);
						}
						catch (final IOException e)
						{
							throw new RuntimeException(e);
						}
					});
					break;
				default:
					System.out.println("Unrecognized command");
			}
		}
	}
}
