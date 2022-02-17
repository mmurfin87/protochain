package com.markmurfin.blockchain.p2p;

import lombok.AllArgsConstructor;
import lombok.NonNull;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.function.BooleanSupplier;

@AllArgsConstructor
public class Node implements Runnable
{
	public static final int NODE_DEFAULT_PORT = 8111;

	public static void main(final String[] args) throws IOException
	{
		final InetSocketAddress bindAddress = new InetSocketAddress(NODE_DEFAULT_PORT);
		try(final Selector selector = Selector.open())
		{
			final Map<Peer, Protocol4Connection> connectedPeers = new HashMap<>();
			new Node(bindAddress, selector, connectedPeers, () -> true, new Timer(), new NodeReadHandler(selector, connectedPeers)).run();
		}
	}

	public void run()
	{
		System.out.println("Node started");
		try (final Protocol4Listener listener = new Protocol4Listener(bindAddress, selector))
		{
			while (runStatus.getAsBoolean())
			{
				if (!selector.isOpen())
					break;
				selector.select(3000);
				final Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
				while (keyIterator.hasNext())
				{
					final SelectionKey sk = keyIterator.next();

					try
					{
						if (sk.isConnectable())
							finishPeerConnection(sk, (Protocol4Connection) sk.attachment(), "connected outgoing finished");
						if (sk.isAcceptable())
							acceptPeer(((ServerSocketChannel) sk.channel()).accept());
						if (sk.isReadable())
							read(sk, (Protocol4Connection) sk.attachment());
						if (sk.isWritable())
							if (((Protocol4Connection) sk.attachment()).flush())
								sk.interestOps(SelectionKey.OP_READ);
					}
					catch (final CancelledKeyException e)
					{
						// swallow
					}
					keyIterator.remove();
				}
			}
			System.out.println("Selector closed");
		}
		catch (final IOException e)
		{
			e.printStackTrace();
		}
	}

	public void acceptPeer(@NonNull final SocketChannel socketChannel) throws IOException
	{
		socketChannel.configureBlocking(false);
		final Peer peer = new Peer((InetSocketAddress) socketChannel.getRemoteAddress());
		final Protocol4Connection connection = new Protocol4Connection(peer, socketChannel, new Heartbeat(), new ArrayList<>());
		final SelectionKey sk = socketChannel.register(selector, SelectionKey.OP_READ, connection);

		finishPeerConnection(sk, connection, "connected incoming");
	}

	public void connectPeer(@NonNull final InetSocketAddress peerAddress) throws IOException
	{
		final SocketChannel socketChannel = SocketChannel.open();
		socketChannel.configureBlocking(false);
		final Peer peer = new Peer(peerAddress);
		final Protocol4Connection connectionState = new Protocol4Connection(peer, socketChannel, new Heartbeat(), new ArrayList<>());

		if (!socketChannel.connect(peerAddress) && !socketChannel.finishConnect())
			socketChannel.register(selector, SelectionKey.OP_CONNECT, connectionState);
		else
		{
			final SelectionKey sk = socketChannel.register(selector, SelectionKey.OP_READ, connectionState);
			finishPeerConnection(sk, connectionState, "connected outgoing immediate");
		}
	}

	public void finishPeerConnection(@NonNull final SelectionKey sk, @NonNull final Protocol4Connection peerConnection, @NonNull final String message) throws IOException
	{
		if (peerConnection.socketChannel.isConnectionPending())
			peerConnection.socketChannel.finishConnect();
		connectedPeers.put(peerConnection.peer, peerConnection);
		timer.scheduleAtFixedRate(new HeartbeatTimerTask(selector, peerConnection), 0, 5000);
		sk.interestOps(SelectionKey.OP_READ);
		log(peerConnection.peer.address, message);
	}

	public void unpeer(@NonNull final Protocol4Connection peerConnection) throws IOException
	{
		for (final SelectionKey sk : selector.keys())
		{
			if (!Objects.equals(sk.attachment(), peerConnection))
				continue;
			sk.cancel();
			sk.channel().close();
			connectedPeers.remove(peerConnection.peer);
			log(peerConnection.peer.address, "disconnected outgoing");
		};
		// Just to double check
		peerConnection.socketChannel.close();
		if (connectedPeers.remove(peerConnection.peer) != null)
			log(peerConnection.peer.address, "disconnected outgoing");
	}

	public void read(@NonNull final SelectionKey sk, @NonNull final Protocol4Connection peerConnection) throws IOException
	{
		final ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
		final int read = peerConnection.read(byteBuffer, readHandler);
		if (read < 0)
		{
			sk.cancel();
			connectedPeers.remove(peerConnection.peer);
			System.out.format("%s: disconnected incoming %s%n", peerConnection.peer.address, (read == Protocol4Connection.CLOSED_GRACEFULLY ? "gracefully" : "rudely"));
		}
		else
			log(peerConnection.peer.address, "Received %d bytes", read);
	}

	public static void log(@NonNull final InetSocketAddress party, @NonNull final String format, final Object... args)
	{
		System.out.format("%s: %s%n", party, String.format(format, args));
	}

	@NonNull
	private final InetSocketAddress bindAddress;
	@NonNull
	private final Selector selector;
	@NonNull
	private final Map<Peer, Protocol4Connection> connectedPeers;
	@NonNull
	private final BooleanSupplier runStatus;
	@NonNull
	private final Timer timer;
	@NonNull
	private final ReadHandler readHandler;
}
